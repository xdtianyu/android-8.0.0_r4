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
import sys
import time

def GenericRetry(handler, max_retry, functor,
                 sleep=0, backoff_factor=1, success_functor=lambda x: None,
                 raise_first_exception_on_failure=True, *args, **kwargs):
    """Generic retry loop w/ optional break out depending on exceptions.

    To retry based on the return value of |functor| see the timeout_util module.

    Keep in mind that the total sleep time will be the triangular value of
    max_retry multiplied by the sleep value.  e.g. max_retry=5 and sleep=10
    will be T5 (i.e. 5+4+3+2+1) times 10, or 150 seconds total.  Rather than
    use a large sleep value, you should lean more towards large retries and
    lower sleep intervals, or by utilizing backoff_factor.

    Args:
        handler: A functor invoked w/ the exception instance that
            functor(*args, **kwargs) threw.  If it returns True, then a
            retry is attempted.  If False, the exception is re-raised.
        max_retry: A positive integer representing how many times to retry
            the command before giving up.  Worst case, the command is invoked
            max_retry + 1) times before failing.
        functor: A callable to pass args and kwargs to.
        sleep: Optional keyword.  Multiplier for how long to sleep between
            retries; will delay (1*sleep) the first time, then (2*sleep),
            continuing via attempt * sleep.
        backoff_factor: Optional keyword. If supplied and > 1, subsequent sleeps
                        will be of length (backoff_factor ^ (attempt - 1)) * sleep,
                        rather than the default behavior of attempt * sleep.
        success_functor: Optional functor that accepts 1 argument. Will be called
                         after successful call to |functor|, with the argument
                         being the number of attempts (1 = |functor| succeeded on
                         first try).
        raise_first_exception_on_failure: Optional boolean which determines which
                                          exception is raised upon failure after
                                          retries. If True, the first exception
                                          that was encountered. If False, the
                                          final one. Default: True.
        *args: Positional args passed to functor.
        **kwargs: Optional args passed to functor.

    Returns:
        Whatever functor(*args, **kwargs) returns.

    Raises:
        Exception: Whatever exceptions functor(*args, **kwargs) throws and
            isn't suppressed is raised.  Note that the first exception encountered
            is what's thrown.
    """

    if max_retry < 0:
        raise ValueError('max_retry needs to be zero or more: %s' % max_retry)

    if backoff_factor < 1:
        raise ValueError('backoff_factor must be 1 or greater: %s'
                          % backoff_factor)

    ret, success = (None, False)
    attempt = 0

    exc_info = None
    for attempt in xrange(max_retry + 1):
        if attempt and sleep:
            if backoff_factor > 1:
                sleep_time = sleep * backoff_factor ** (attempt - 1)
            else:
                sleep_time = sleep * attempt
            time.sleep(sleep_time)
        try:
            ret = functor(*args, **kwargs)
            success = True
            break
        except Exception as e:
            # Note we're not snagging BaseException, so MemoryError/KeyboardInterrupt
            # and friends don't enter this except block.
            if not handler(e):
                raise
            # If raise_first_exception_on_failure, we intentionally ignore
            # any failures in later attempts since we'll throw the original
            # failure if all retries fail.
            if exc_info is None or not raise_first_exception_on_failure:
                exc_info = sys.exc_info()

    if success:
        success_functor(attempt + 1)
        return ret

    raise exc_info[0], exc_info[1], exc_info[2]

def RetryException(exc_retry, max_retry, functor, *args, **kwargs):
    """Convenience wrapper for GenericRetry based on exceptions.

    Args:
        exc_retry: A class (or tuple of classes).  If the raised exception
            is the given class(es), a retry will be attempted.  Otherwise,
            the exception is raised.
        max_retry: See GenericRetry.
        functor: See GenericRetry.
        *args: See GenericRetry.
        **kwargs: See GenericRetry.

    Returns:
        Return what functor returns.

    Raises:
        TypeError, if exc_retry is of an unexpected type.
    """
    if not isinstance(exc_retry, (tuple, type)):
        raise TypeError("exc_retry should be an exception (or tuple), not %r" %
                         exc_retry)
    def _Handler(exc, values=exc_retry):
        return isinstance(exc, values)
    return GenericRetry(_Handler, max_retry, functor, *args, **kwargs)
