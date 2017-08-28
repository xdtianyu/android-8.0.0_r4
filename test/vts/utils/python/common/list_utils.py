#
# Copyright (C) 2016 The Android Open Source Project
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
#

import itertools


def ExpandItemDelimiters(input_list,
                         delimiter,
                         strip=False,
                         to_str=False,
                         remove_empty=True):
    '''Expand list items that contain the given delimiter.

    Args:
        input_list: list of string, a list whose item may contain a delimiter
        delimiter: string
        strip: bool, whether to strip items after expanding. Default is False
        to_str: bool, whether to convert output items in string.
                Default is False
        remove_empty: bool, whether to remove empty string in result list.
                      Will not remove None items. Default: True

    Returns:
        The expended list, which may be the same with input list
        if no delimiter found; None if input list is None
    '''
    if input_list is None:
        return None

    do_strip = lambda s: s.strip() if strip else s
    do_str = lambda s: str(s) if to_str else s

    expended_list_generator = (item.split(delimiter) for item in input_list)
    result = [do_strip(do_str(s))
              for s in itertools.chain.from_iterable(expended_list_generator)]
    return filter(lambda s: str(s) != '', result) if remove_empty else result
