# Copyright 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import os.path
import subprocess
import sys
import tempfile
import time

import its.device
# from its.device import ItsSession

NUM_RUNS = 2
SCENE_NAME = 'sensor_fusion'
SKIP_RET_CODE = 101
TEST_NAME = 'test_sensor_fusion'
TEST_DIR = os.path.join(os.getcwd(), 'tests', SCENE_NAME)


def main():
    """Run all the test_sensor_fusion NUM_RUNS times.

    Save intermediate files, and producing a summary/report of the results.

    Script should be run from the top-level CameraITS directory.

    Command line Arguments:
        camera:  the camera(s) to be tested. Use comma to separate multiple
                 camera Ids. Ex: 'camera=0,1' or 'camera=1'
        device:  the device id for adb
        rotator: string for rotator id in for vid:pid:ch
    """

    camera_id = '0'
    rotator_ids = 'default'
    for s in sys.argv[1:]:
        if s[:7] == 'camera=' and len(s) > 7:
            camera_id = s[7:]
        elif s[:8] == 'rotator=' and len(s) > 8:
            rotator_ids = s[8:]

    if camera_id not in ['0', '1']:
        print 'Need to specify camera 0 or 1'
        sys.exit()

    # Make output directories to hold the generated files.
    tmpdir = tempfile.mkdtemp()
    print 'Saving output files to:', tmpdir, '\n'

    device_id = its.device.get_device_id()
    device_id_arg = 'device=' + device_id
    print 'Testing device ' + device_id

    camera_id_arg = 'camera=' + camera_id
    if rotator_ids:
        rotator_id_arg = 'rotator=' + rotator_ids
    print 'Preparing to run sensor_fusion on camera', camera_id

    os.mkdir(os.path.join(tmpdir, camera_id))

    # Run test multiple times, capturing stdout and stderr.
    numpass = 0
    numfail = 0
    numskip = 0
    for i in range(NUM_RUNS):
        os.mkdir(os.path.join(tmpdir, camera_id, SCENE_NAME+'_'+str(i)))
        cmd = ('python tools/rotation_rig.py rotator=%s' % rotator_ids)
        subprocess.Popen(cmd.split())
        cmd = ['python', os.path.join(TEST_DIR, TEST_NAME+'.py'),
               device_id_arg, camera_id_arg, rotator_id_arg]
        outdir = os.path.join(tmpdir, camera_id, SCENE_NAME+'_'+str(i))
        outpath = os.path.join(outdir, TEST_NAME+'_stdout.txt')
        errpath = os.path.join(outdir, TEST_NAME+'_stderr.txt')
        t0 = time.time()
        with open(outpath, 'w') as fout, open(errpath, 'w') as ferr:
            retcode = subprocess.call(
                cmd, stderr=ferr, stdout=fout, cwd=outdir)
        t1 = time.time()

        if retcode == 0:
            retstr = 'PASS '
            numpass += 1
        elif retcode == SKIP_RET_CODE:
            retstr = 'SKIP '
            numskip += 1
        else:
            retstr = 'FAIL '
            numfail += 1
        msg = '%s %s/%s [%.1fs]' % (retstr, SCENE_NAME, TEST_NAME, t1-t0)
        print msg

    test_result = '%d / %d tests passed (%.1f%%)' % (
        numpass+numskip, NUM_RUNS, 100.0*float(numpass+numskip)/NUM_RUNS)
    print test_result

if __name__ == '__main__':
    main()

