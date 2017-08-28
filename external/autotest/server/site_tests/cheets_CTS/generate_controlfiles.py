#!/usr/bin/env python
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import argparse
import contextlib
import logging
import os
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile

_CONTROLFILE_TEMPLATE = """\
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This file has been automatically generated. Do not edit!

AUTHOR = 'ARC Team'
NAME = '{name}'
ATTRIBUTES = '{attributes}'
DEPENDENCIES = '{dependencies}'
JOB_RETRIES = {retries}
TEST_TYPE = 'server'
TIME = 'LENGTHY'

DOC = ('Run package {package} of the '
       'Android {revision} Compatibility Test Suite (CTS), build {build},'
       'using {abi} ABI in the ARC container.')

def run_CTS(machine):
    host = hosts.create_host(machine)
    job.run_test(
        'cheets_CTS',
        host=host,
        iterations=1,{retry}
        tag='{tag}',
        target_package='{package}',
        {source},
        timeout={timeout})

parallel_simple(run_CTS, machines)"""

_CTS_TIMEOUT = {
    'android.core.tests.libcore.package.org': 90,
    'android.core.vm-tests-tf': 90,
    'android.host.security': 90,
    'android.media': 360,
    'android.mediastress': 480,
    'android.print': 180,
    'com.android.cts.filesystemperf': 180,
    'com.drawelements.deqp.gles3': 240,
    'com.drawelements.deqp.gles31': 90,
    'com.drawelements.deqp.gles31.copy_image_mixed': 120,
    'com.drawelements.deqp.gles31.copy_image_non_compressed': 240,
}

_SUITE_SMOKE = [
    'android.core.tests.libcore.package.harmony_java_math'
]

# Any test in SMOKE (VMTest) should also be in CQ (HWTest).
_SUITE_BVT_CQ = _SUITE_SMOKE + [
    'com.android.cts.dram'
]

_SUITE_ARC_BVT_CQ = _SUITE_BVT_CQ

_SUITE_BVT_PERBUILD = [
    'android.signature',
    'android.speech',
    'android.systemui',
    'android.telecom',
    'android.telephony',
    'android.theme',
    'android.transition',
    'android.tv',
    'android.uiautomation',
    'android.usb',
    'android.voicesettings',
    'com.android.cts.filesystemperf',
    'com.android.cts.jank',
    'com.android.cts.opengl',
    'com.android.cts.simplecpu',
]

def get_tradefed_build(line):
    """Gets the build of Android CTS from tradefed.

    @param line Tradefed identification output on startup. Example:
                Android CTS 6.0_r6 build:2813453
    @return Tradefed CTS build. Example: 2813453.
    """
    # Sample string: Android CTS 6.0_r6 build:2813453.
    m = re.search(r'(?<=build:)(.*)', line)
    if m:
        return m.group(0)
    logging.warning('Could not identify build in line "%s".', line)
    return '<unknown>'


def get_tradefed_revision(line):
    """Gets the revision of Android CTS from tradefed.

    @param line Tradefed identification output on startup. Example:
                Android CTS 6.0_r6 build:2813453
    @return Tradefed CTS revision. Example: 6.0_r6.
    """
    m = re.search(r'(?<=Android CTS )(.*) build:', line)
    if m:
        return m.group(1)
    logging.warning('Could not identify revision in line "%s".', line)
    return None


def get_bundle_abi(filename):
    """Makes an educated guess about the ABI.

    In this case we chose to guess by filename, but we could also parse the
    xml files in the package. (Maybe this needs to be done in the future.)
    """
    if filename.endswith('_x86-arm.zip'):
        return 'arm'
    if filename.endswith('_x86-x86.zip'):
        return 'x86'
    raise Exception('Could not determine ABI from "%s".' % filename)


def get_bundle_revision(filename):
    """Makes an educated guess about the revision.

    In this case we chose to guess by filename, but we could also parse the
    xml files in the package.
    """
    m = re.search(r'(?<=android-cts-)(.*)-linux', filename)
    if m is not None:
        return m.group(1)
    return None


def get_extension(package, abi, revision):
    """Defines a unique string.

    Notice we chose package revision first, then abi, as the package revision
    changes at least on a monthly basis. This ordering makes it simpler to
    add/remove packages.
    """
    return '%s.%s.%s' % (revision, abi, package)


def get_controlfile_name(package, abi, revision):
    """Defines the control file name."""
    return 'control.%s' % get_extension(package, abi, revision)


def get_public_extension(package, abi):
    return '%s.%s' % (abi, package)


def get_public_controlfile_name(package, abi):
    """Defines the public control file name."""
    return 'control.%s' % get_public_extension(package, abi)


def get_attribute_suites(package, abi):
    """Defines the suites associated with a package."""
    attributes = 'suite:arc-cts'
    # Get a minmum amount of coverage on cq (one quick CTS package only).
    if package in _SUITE_SMOKE:
        attributes += ', suite:smoke'
    if package in _SUITE_BVT_CQ:
        attributes += ', suite:bvt-cq'
    if package in _SUITE_ARC_BVT_CQ:
        attributes += ', suite:arc-bvt-cq'
    if package in _SUITE_BVT_PERBUILD and abi == 'arm':
        attributes += ', suite:bvt-perbuild'
    # Adding arc-cts-stable runs all packages twice on stable.
    return attributes


def get_dependencies(abi):
    """Defines lab dependencies needed to schedule a package.

    Currently we only care about x86 ABI tests, which must run on Intel boards.
    """
    # TODO(ihf): Enable this once crbug.com/618509 has labeled all lab DUTs.
    if abi == 'x86':
        return 'arc, cts_abi_x86'
    return 'arc'

# suite_scheduler retries
_RETRIES = 2


def get_controlfile_content(package, abi, revision, build, uri):
    """Returns the text inside of a control file."""
    # For test_that the NAME should be the same as for the control file name.
    # We could try some trickery here to get shorter extensions for a default
    # suite/ARM. But with the monthly uprevs this will quickly get confusing.
    name = 'cheets_CTS.%s' % get_extension(package, abi, revision)
    if is_public(uri):
        name = 'cheets_CTS.%s' % get_public_extension(package, abi)
    dependencies = get_dependencies(abi)
    retries = _RETRIES
    # We put all revisions and all abi in the same tag for wmatrix, otherwise
    # the list at https://wmatrix.googleplex.com/tests would grow way too large.
    # Example: for all values of |REVISION| and |ABI| we map control file
    # cheets_CTS.REVISION.ABI.PACKAGE on wmatrix to cheets_CTS.PACKAGE.
    # This way people can find results independent of |REVISION| and |ABI| at
    # the same URL.
    tag = package
    timeout = 3600
    if package in _CTS_TIMEOUT:
        timeout = 60 * _CTS_TIMEOUT[package]
    if is_public(uri):
        source = "bundle='%s'" % abi
        attributes = 'suite:cts'
    else:
        source = "uri='%s'" % uri
        attributes = '%s, test_name:%s' % (get_attribute_suites(package, abi),
                                           name)
    # cheets_CTS internal retries limited due to time constraints on cq.
    retry = ''
    if (package in (_SUITE_SMOKE + _SUITE_BVT_CQ + _SUITE_ARC_BVT_CQ) or
        package in _SUITE_BVT_PERBUILD and abi == 'arm'):
        retry = '\n        max_retry=3,'
    return _CONTROLFILE_TEMPLATE.format(
        name=name,
        attributes=attributes,
        dependencies=dependencies,
        retries=retries,
        package=package,
        revision=revision,
        build=build,
        abi=abi,
        tag=tag,
        source=source,
        retry=retry,
        timeout=timeout)


def get_tradefed_data(path):
    """Queries tradefed to provide us with a list of packages."""
    tradefed = os.path.join(path, 'android-cts/tools/cts-tradefed')
    # Forgive me for I have sinned. Same as: chmod +x tradefed.
    os.chmod(tradefed, os.stat(tradefed).st_mode | stat.S_IEXEC)
    cmd_list = [tradefed, 'list', 'packages']
    logging.info('Calling tradefed for list of packages.')
    # TODO(ihf): Get a tradefed command which terminates then refactor.
    p = subprocess.Popen(cmd_list, stdout=subprocess.PIPE)
    packages = []
    build = '<unknown>'
    line = ''
    revision = None
    # The process does not terminate, but we know the last test starts with zzz.
    while not line.startswith('zzz'):
        line = p.stdout.readline().strip()
        if line:
            print line
        if line.startswith('Android CTS '):
            logging.info('Unpacking: %s.', line)
            build = get_tradefed_build(line)
            revision = get_tradefed_revision(line)
        elif re.search(r'^(android\.|com\.|zzz\.)', line):
            packages.append(line)
    p.kill()
    p.wait()
    return packages, build, revision


def is_public(uri):
    if uri.startswith('gs:'):
        return False
    elif uri.startswith('https://dl.google.com'):
        return True
    raise


def download(uri, destination):
    """Download |uri| to local |destination|."""
    if is_public(uri):
        subprocess.check_call(['wget', uri, '-P', destination])
    else:
        subprocess.check_call(['gsutil', 'cp', uri, destination])


@contextlib.contextmanager
def pushd(d):
    """Defines pushd."""
    current = os.getcwd()
    os.chdir(d)
    try:
        yield
    finally:
        os.chdir(current)


def unzip(filename, destination):
    """Unzips a zip file to the destination directory."""
    with pushd(destination):
        # We are trusting Android to have a sane zip file for us.
        with zipfile.ZipFile(filename) as zf:
            zf.extractall()


@contextlib.contextmanager
def TemporaryDirectory(prefix):
    """Poor man's python 3.2 import."""
    tmp = tempfile.mkdtemp(prefix=prefix)
    try:
        yield tmp
    finally:
        shutil.rmtree(tmp)


def main(uris):
    """Downloads each package in |uris| and generates control files."""
    for uri in uris:
        abi = get_bundle_abi(uri)
        with TemporaryDirectory(prefix='cts-android_') as tmp:
            logging.info('Downloading to %s.', tmp)
            bundle = os.path.join(tmp, 'cts-android.zip')
            download(uri, tmp)
            bundle = os.path.join(tmp, os.path.basename(uri))
            logging.info('Extracting %s.', bundle)
            unzip(bundle, tmp)
            packages, build, revision = get_tradefed_data(tmp)
            if not revision:
                raise Exception('Could not determine revision.')
            # And write all control files.
            logging.info('Writing all control files.')
            for package in packages:
                name = get_controlfile_name(package, abi, revision)
                if is_public(uri):
                    name = get_public_controlfile_name(package, abi)
                content = get_controlfile_content(package, abi, revision, build,
                                                  uri)
                with open(name, 'w') as f:
                    f.write(content)


if __name__ == '__main__':
    logging.basicConfig(level=logging.INFO)
    parser = argparse.ArgumentParser(
        description='Create control files for a CTS bundle on GS.',
        formatter_class=argparse.RawTextHelpFormatter)
    parser.add_argument(
        'uris',
        nargs='+',
        help='List of Google Storage URIs to CTS bundles. Example:\n'
        'gs://chromeos-arc-images/cts/bundle/2016-06-02/'
        'android-cts-6.0_r6-linux_x86-arm.zip')
    args = parser.parse_args()
    main(args.uris)
