# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


import re
import sys

import common
from autotest_lib.server.cros import provision_actionables as actionables


### Constants for label prefixes
CROS_VERSION_PREFIX = 'cros-version'
ANDROID_BUILD_VERSION_PREFIX = 'ab-version'
TESTBED_BUILD_VERSION_PREFIX = 'testbed-version'
FW_RW_VERSION_PREFIX = 'fwrw-version'
FW_RO_VERSION_PREFIX = 'fwro-version'

_ANDROID_BUILD_REGEX = r'.+/.+/P?([0-9]+|LATEST)'
_ANDROID_TESTBED_BUILD_REGEX = _ANDROID_BUILD_REGEX + '(,|(#[0-9]+))'

# Special label to skip provision and run reset instead.
SKIP_PROVISION = 'skip_provision'

# Default number of provisions attempts to try if we believe the devserver is
# flaky.
FLAKY_DEVSERVER_ATTEMPTS = 2


### Helpers to convert value to label
def get_version_label_prefix(image):
    """
    Determine a version label prefix from a given image name.

    Parses `image` to determine what kind of image it refers
    to, and returns the corresponding version label prefix.

    Known version label prefixes are:
      * `CROS_VERSION_PREFIX` for Chrome OS version strings.
        These images have names like `cave-release/R57-9030.0.0`.
      * `ANDROID_BUILD_VERSION_PREFIX` for Android build versions
        These images have names like
        `git_mnc-release/shamu-userdebug/2457013`.
      * `TESTBED_BUILD_VERSION_PREFIX` for Android testbed version
        specifications.  These are either comma separated lists of
        Android versions, or an Android version with a suffix like
        '#2', indicating two devices running the given build.

    @param image: The image name to be parsed.
    @returns: A string that is the prefix of version labels for the type
              of image identified by `image`.

    """
    if re.match(_ANDROID_TESTBED_BUILD_REGEX, image, re.I):
        return TESTBED_BUILD_VERSION_PREFIX
    elif re.match(_ANDROID_BUILD_REGEX, image, re.I):
        return ANDROID_BUILD_VERSION_PREFIX
    else:
        return CROS_VERSION_PREFIX


def image_version_to_label(image):
    """
    Return a version label appropriate to the given image name.

    The type of version label is as determined described for
    `get_version_label_prefix()`, meaning the label will identify a
    CrOS, Android, or Testbed version.

    @param image: The image name to be parsed.
    @returns: A string that is the appropriate label name.

    """
    return get_version_label_prefix(image) + ':' + image


def cros_version_to_label(image):
    """
    Returns the proper label name for a ChromeOS build of |image|.

    @param image: A string of the form 'lumpy-release/R28-3993.0.0'
    @returns: A string that is the appropriate label name.

    """
    return CROS_VERSION_PREFIX + ':' + image


def fwro_version_to_label(image):
    """
    Returns the proper label name for a RO firmware build of |image|.

    @param image: A string of the form 'lumpy-release/R28-3993.0.0'
    @returns: A string that is the appropriate label name.

    """
    return FW_RO_VERSION_PREFIX + ':' + image


def fwrw_version_to_label(image):
    """
    Returns the proper label name for a RW firmware build of |image|.

    @param image: A string of the form 'lumpy-release/R28-3993.0.0'
    @returns: A string that is the appropriate label name.

    """
    return FW_RW_VERSION_PREFIX + ':' + image


class _SpecialTaskAction(object):
    """
    Base class to give a template for mapping labels to tests.
    """

    # A dictionary mapping labels to test names.
    _actions = {}

    # The name of this special task to be used in output.
    name = None;

    # Some special tasks require to run before others, e.g., ChromeOS image
    # needs to be updated before firmware provision. List `_priorities` defines
    # the order of each label prefix. An element with a smaller index has higher
    # priority. Not listed ones have the lowest priority.
    # This property should be overriden in subclass to define its own priorities
    # across available label prefixes.
    _priorities = []


    @classmethod
    def acts_on(cls, label):
        """
        Returns True if the label is a label that we recognize as something we
        know how to act on, given our _actions.

        @param label: The label as a string.
        @returns: True if there exists a test to run for this label.

        """
        return label.split(':')[0] in cls._actions


    @classmethod
    def test_for(cls, label):
        """
        Returns the test associated with the given (string) label name.

        @param label: The label for which the action is being requested.
        @returns: The string name of the test that should be run.
        @raises KeyError: If the name was not recognized as one we care about.

        """
        return cls._actions[label]


    @classmethod
    def partition(cls, labels):
        """
        Filter a list of labels into two sets: those labels that we know how to
        act on and those that we don't know how to act on.

        @param labels: A list of strings of labels.
        @returns: A tuple where the first element is a set of unactionable
                  labels, and the second element is a set of the actionable
                  labels.
        """
        capabilities = set()
        configurations = set()

        for label in labels:
            if label == SKIP_PROVISION:
                # skip_provision is neither actionable or a capability label.
                # It doesn't need any handling.
                continue
            elif cls.acts_on(label):
                configurations.add(label)
            else:
                capabilities.add(label)

        return capabilities, configurations


    @classmethod
    def sort_configurations(cls, configurations):
        """
        Sort configurations based on the priority defined in cls._priorities.

        @param configurations: A list of actionable labels.

        @return: A sorted list of tuple of (label_prefix, value), the tuples are
                sorted based on the label_prefix's index in cls._priorities.
        """
        # Split a list of labels into a dict mapping name to value.  All labels
        # must be provisionable labels, or else a ValueError
        # For example, label 'cros-version:lumpy-release/R28-3993.0.0' is split
        # to  {'cros-version': 'lumpy-release/R28-3993.0.0'}
        split_configurations = dict()
        for label in configurations:
            name, _, value = label.partition(':')
            split_configurations[name] = value

        sort_key = (lambda config:
                (cls._priorities.index(config[0])
                 if (config[0] in cls._priorities) else sys.maxint))
        return sorted(split_configurations.items(), key=sort_key)


class Verify(_SpecialTaskAction):
    """
    Tests to verify that the DUT is in a sane, known good state that we can run
    tests on.  Failure to verify leads to running Repair.
    """

    _actions = {
        'modem_repair': actionables.TestActionable('cellular_StaleModemReboot'),
        # TODO(crbug.com/404421): set rpm action to power_RPMTest after the RPM
        # is stable in lab (destiny). The power_RPMTest failure led to reset job
        # failure and that left dut in Repair Failed. Since the test will fail
        # anyway due to the destiny lab issue, and test retry will retry the
        # test in another DUT.
        # This change temporarily disable the RPM check in reset job.
        # Another way to do this is to remove rpm dependency from tests' control
        # file. That will involve changes on multiple control files. This one
        # line change here is a simple temporary fix.
        'rpm': actionables.TestActionable('dummy_PassServer'),
    }

    name = 'verify'


class Provision(_SpecialTaskAction):
    """
    Provisioning runs to change the configuration of the DUT from one state to
    another.  It will only be run on verified DUTs.
    """

    # ChromeOS update must happen before firmware install, so the dut has the
    # correct ChromeOS version label when firmware install occurs. The ChromeOS
    # version label is used for firmware update to stage desired ChromeOS image
    # on to the servo USB stick.
    _priorities = [CROS_VERSION_PREFIX,
                   FW_RO_VERSION_PREFIX,
                   FW_RW_VERSION_PREFIX]

    # TODO(milleral): http://crbug.com/249555
    # Create some way to discover and register provisioning tests so that we
    # don't need to hand-maintain a list of all of them.
    _actions = {
        CROS_VERSION_PREFIX: actionables.TestActionable(
                'provision_AutoUpdate',
                extra_kwargs={'disable_sysinfo': False,
                              'disable_before_test_sysinfo': False,
                              'disable_before_iteration_sysinfo': True,
                              'disable_after_test_sysinfo': True,
                              'disable_after_iteration_sysinfo': True}),
        FW_RO_VERSION_PREFIX: actionables.TestActionable(
                'provision_FirmwareUpdate'),
        FW_RW_VERSION_PREFIX: actionables.TestActionable(
                'provision_FirmwareUpdate',
                extra_kwargs={'rw_only': True,
                              'tag': 'rw_only'}),
        ANDROID_BUILD_VERSION_PREFIX : actionables.TestActionable(
                'provision_AndroidUpdate'),
        TESTBED_BUILD_VERSION_PREFIX : actionables.TestActionable(
                'provision_TestbedUpdate'),
    }

    name = 'provision'


class Cleanup(_SpecialTaskAction):
    """
    Cleanup runs after a test fails to try and remove artifacts of tests and
    ensure the DUT will be in a sane state for the next test run.
    """

    _actions = {
        'cleanup-reboot': actionables.RebootActionable(),
    }

    name = 'cleanup'


class Repair(_SpecialTaskAction):
    """
    Repair runs when one of the other special tasks fails.  It should be able
    to take a component of the DUT that's in an unknown state and restore it to
    a good state.
    """

    _actions = {
    }

    name = 'repair'


# TODO(milleral): crbug.com/364273
# Label doesn't really mean label in this context.  We're putting things into
# DEPENDENCIES that really aren't DEPENDENCIES, and we should probably stop
# doing that.
def is_for_special_action(label):
    """
    If any special task handles the label specially, then we're using the label
    to communicate that we want an action, and not as an actual dependency that
    the test has.

    @param label: A string label name.
    @return True if any special task handles this label specially,
            False if no special task handles this label.
    """
    return (Verify.acts_on(label) or
            Provision.acts_on(label) or
            Cleanup.acts_on(label) or
            Repair.acts_on(label) or
            label == SKIP_PROVISION)


def join(provision_type, provision_value):
    """
    Combine the provision type and value into the label name.

    @param provision_type: One of the constants that are the label prefixes.
    @param provision_value: A string of the value for this provision type.
    @returns: A string that is the label name for this (type, value) pair.

    >>> join(CROS_VERSION_PREFIX, 'lumpy-release/R27-3773.0.0')
    'cros-version:lumpy-release/R27-3773.0.0'

    """
    return '%s:%s' % (provision_type, provision_value)


class SpecialTaskActionException(Exception):
    """
    Exception raised when a special task fails to successfully run a test that
    is required.

    This is also a literally meaningless exception.  It's always just discarded.
    """


def run_special_task_actions(job, host, labels, task):
    """
    Iterate through all `label`s and run any tests on `host` that `task` has
    corresponding to the passed in labels.

    Emits status lines for each run test, and INFO lines for each skipped label.

    @param job: A job object from a control file.
    @param host: The host to run actions on.
    @param labels: The list of job labels to work on.
    @param task: An instance of _SpecialTaskAction.
    @returns: None
    @raises: SpecialTaskActionException if a test fails.

    """
    capabilities, configurations = task.partition(labels)

    for label in capabilities:
        job.record('INFO', None, task.name,
                   "Can't %s label '%s'." % (task.name, label))

    # Sort the configuration labels based on `task._priorities`.
    sorted_configurations = task.sort_configurations(configurations)
    for name, value in sorted_configurations:
        action_item = task.test_for(name)
        success = action_item.execute(job=job, host=host, value=value)
        if not success:
            raise SpecialTaskActionException()
