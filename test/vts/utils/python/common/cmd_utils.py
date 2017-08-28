#
# Copyright 2016 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
import subprocess

STDOUT = 'stdouts'
STDERR = 'stderrs'
EXIT_CODE = 'return_codes'


def RunCommand(command):
    """Runs a unix command and stashes the result.

    Args:
        command: the command to run.
    Returns:
        code of the subprocess.
    """
    proc = subprocess.Popen(
        command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    (stdout, stderr) = proc.communicate()
    if proc.returncode != 0:
        logging.error('Fail to execute command: %s  '
                      '(stdout: %s\n  stderr: %s\n)' % (command, stdout,
                                                        stderr))
    return proc.returncode


def ExecuteOneShellCommand(cmd):
    """Execute one shell command and return (stdout, stderr, exit_code).

    Args:
        cmd: string, a shell command

    Returns:
        tuple(string, string, int), containing stdout, stderr, exit_code of the shell command
    """
    p = subprocess.Popen(
        str(cmd), shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout, stderr = p.communicate()
    return (stdout, stderr, p.returncode)


def ExecuteShellCommand(cmd):
    """Execute one shell cmd or a list of shell commands.

    Args:
        cmd: string or a list of strings, shell command(s)

    Returns:
        dict{int->string}, containing stdout, stderr, exit_code of the shell command(s)
    """
    if not isinstance(cmd, list):
        cmd = [cmd]

    results = [ExecuteOneShellCommand(command) for command in cmd]
    stdout, stderr, exit_code = zip(*results)
    return {STDOUT: stdout, STDERR: stderr, EXIT_CODE: exit_code}
