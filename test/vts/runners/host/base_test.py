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

import logging
import os

from vts.proto import VtsReportMessage_pb2 as ReportMsg
from vts.runners.host import asserts
from vts.runners.host import errors
from vts.runners.host import keys
from vts.runners.host import logger
from vts.runners.host import records
from vts.runners.host import signals
from vts.runners.host import utils
from vts.runners.host import const
from vts.utils.python.common import list_utils
from vts.utils.python.coverage import coverage_utils
from vts.utils.python.profiling import profiling_utils
from vts.utils.python.reporting import log_uploading_utils
from vts.utils.python.systrace import systrace_utils
from vts.utils.python.web import feature_utils
from vts.utils.python.web import web_utils

# Macro strings for test result reporting
TEST_CASE_TOKEN = "[Test Case]"
RESULT_LINE_TEMPLATE = TEST_CASE_TOKEN + " %s %s"
STR_TEST = "test"
STR_GENERATE = "generate"


class BaseTestClass(object):
    """Base class for all test classes to inherit from.

    This class gets all the controller objects from test_runner and executes
    the test cases requested within itself.

    Most attributes of this class are set at runtime based on the configuration
    provided.

    Attributes:
        tests: A list of strings, each representing a test case name.
        TAG: A string used to refer to a test class. Default is the test class
             name.
        results: A records.TestResult object for aggregating test results from
                 the execution of test cases.
        currentTestName: A string that's the name of the test case currently
                           being executed. If no test is executing, this should
                           be None.
        include_filer: A list of string, each representing a test case name to
                       include.
        exclude_filer: A list of string, each representing a test case name to
                       exclude. Has no effect if include_filer is not empty.
        abi_name: String, name of abi in use
        abi_bitness: String, bitness of abi in use
        web: WebFeature, object storing web feature util for test run
        coverage: CoverageFeature, object storing coverage feature util for test run
        profiling: ProfilingFeature, object storing profiling feature util for test run
        _skip_all_testcases: A boolean, can be set by a subclass in
                             setUpClass() to skip all test cases.
    """

    TAG = None

    def __init__(self, configs):
        self.tests = []
        if not self.TAG:
            self.TAG = self.__class__.__name__
        # Set all the controller objects and params.
        for name, value in configs.items():
            setattr(self, name, value)
        self.results = records.TestResult()
        self.currentTestName = None

        # Setup test filters (optional)
        if keys.ConfigKeys.KEY_TEST_SUITE in self.user_params:
            test_suite = self.user_params[keys.ConfigKeys.KEY_TEST_SUITE]
            filters = [keys.ConfigKeys.KEY_INCLUDE_FILTER,
                       keys.ConfigKeys.KEY_EXCLUDE_FILTER]
            for filter in filters:
                if filter in test_suite:
                    setattr(self, filter, test_suite[filter])

        # TODO: get abi information differently for multi-device support.
        # Set other optional parameters
        opt_param_names = [keys.ConfigKeys.IKEY_ABI_NAME,
                           keys.ConfigKeys.IKEY_ABI_BITNESS,
                           keys.ConfigKeys.IKEY_SKIP_ON_32BIT_ABI,
                           keys.ConfigKeys.IKEY_SKIP_ON_64BIT_ABI,
                           keys.ConfigKeys.IKEY_RUN_32BIT_ON_64BIT_ABI]
        self.getUserParams(opt_param_names=opt_param_names)
        self.web = web_utils.WebFeature(self.user_params)
        self.coverage = coverage_utils.CoverageFeature(
            self.user_params, web=self.web)
        self.profiling = profiling_utils.ProfilingFeature(
            self.user_params, web=self.web)
        self.systrace = systrace_utils.SystraceFeature(
            self.user_params, web=self.web)
        self.log_uploading = log_uploading_utils.LogUploadingFeature(
            self.user_params, web=self.web)
        self._skip_all_testcases = False

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self._exec_func(self.cleanUp)

    def getUserParams(self, req_param_names=[], opt_param_names=[], **kwargs):
        """Unpacks user defined parameters in test config into individual
        variables.

        Instead of accessing the user param with self.user_params["xxx"], the
        variable can be directly accessed with self.xxx.

        A missing required param will raise an exception. If an optional param
        is missing, an INFO line will be logged.

        Args:
            req_param_names: A list of names of the required user params.
            opt_param_names: A list of names of the optional user params.
            **kwargs: Arguments that provide default values.
                e.g. getUserParams(required_list, opt_list, arg_a="hello")
                     self.arg_a will be "hello" unless it is specified again in
                     required_list or opt_list.

        Raises:
            BaseTestError is raised if a required user params is missing from
            test config.
        """
        for k, v in kwargs.items():
            setattr(self, k, v)
        for name in req_param_names:
            if name not in self.user_params:
                raise errors.BaseTestError(("Missing required user param '%s' "
                                            "in test configuration.") % name)
            setattr(self, name, self.user_params[name])
        for name in opt_param_names:
            if name not in self.user_params:
                logging.info(("Missing optional user param '%s' in "
                              "configuration, continue."), name)
            else:
                setattr(self, name, self.user_params[name])

    def getUserParam(self,
                     param_name,
                     error_if_not_found=False,
                     log_warning_and_continue_if_not_found=False,
                     default_value=None):
        """Get the value of a single user parameter.

        This method returns the value of specified user parameter.
        Note: this method will not automatically set attribute using the parameter name and value.

        Args:
            param_name: string or list of string, denoting user parameter names. If provided
                        a single string, self.user_params["<param_name>"] will be accessed.
                        If provided multiple strings,
                        self.user_params["<param_name1>"]["<param_name2>"]["<param_name3>"]...
                        will be accessed.
            error_if_not_found: bool, whether to raise error if parameter not exists. Default:
                                False
            log_warning_and_continue_if_not_found: bool, log a warning message if parameter value
                                                   not found.
            default_value: object, default value to return if not found. If error_if_not_found is
                           True, this parameter has no effect. Default: None

        Returns:
            object, value of the specified parameter name chain if exists;
            <default_value> if not exists.
        """
        if not param_name:
            if error_if_not_found:
                raise errors.BaseTestError("empty param_name provided")
            logging.error("empty param_name")
            return default_value

        if not isinstance(param_name, list):
            param_name = [param_name]

        curr_obj = self.user_params
        for param in param_name:
            if param not in curr_obj:
                msg = "Missing user param '%s' in test configuration." % param_name
                if error_if_not_found:
                    raise errors.BaseTestError(msg)
                elif log_warning_and_continue_if_not_found:
                    logging.warn(msg)
                return default_value
            curr_obj = curr_obj[param]

        return curr_obj

    def _setUpClass(self):
        """Proxy function to guarantee the base implementation of setUpClass
        is called.
        """
        return self.setUpClass()

    def setUpClass(self):
        """Setup function that will be called before executing any test case in
        the test class.

        To signal setup failure, return False or raise an exception. If
        exceptions were raised, the stack trace would appear in log, but the
        exceptions would not propagate to upper levels.

        Implementation is optional.
        """
        pass

    def _tearDownClass(self):
        """Proxy function to guarantee the base implementation of tearDownClass
        is called.
        """
        ret = self.tearDownClass()
        if self.log_uploading.enabled:
            self.log_uploading.UploadLogs()
        if self.web.enabled:
            self.web.Upload(self.results.requested, self.results.executed)
        return ret

    def tearDownClass(self):
        """Teardown function that will be called after all the selected test
        cases in the test class have been executed.

        Implementation is optional.
        """
        pass

    def _testEntry(self, test_name):
        """Internal function to be called upon entry of a test case."""
        self.currentTestName = test_name
        if self.web.enabled:
            self.web.AddTestReport(test_name)

    def _setUp(self, test_name):
        """Proxy function to guarantee the base implementation of setUp is
        called.
        """
        if self.systrace.enabled:
            self.systrace.StartSystrace()
        return self.setUp()

    def setUp(self):
        """Setup function that will be called every time before executing each
        test case in the test class.

        To signal setup failure, return False or raise an exception. If
        exceptions were raised, the stack trace would appear in log, but the
        exceptions would not propagate to upper levels.

        Implementation is optional.
        """

    def _testExit(self, test_name):
        """Internal function to be called upon exit of a test."""
        self.currentTestName = None

    def _tearDown(self, test_name):
        """Proxy function to guarantee the base implementation of tearDown
        is called.
        """
        if self.systrace.enabled:
            self.systrace.ProcessAndUploadSystrace(test_name)
        self.tearDown()

    def tearDown(self):
        """Teardown function that will be called every time a test case has
        been executed.

        Implementation is optional.
        """

    def _onFail(self, record):
        """Proxy function to guarantee the base implementation of onFail is
        called.

        Args:
            record: The records.TestResultRecord object for the failed test
                    case.
        """
        test_name = record.test_name
        logging.error(record.details)
        begin_time = logger.epochToLogLineTimestamp(record.begin_time)
        logging.info(RESULT_LINE_TEMPLATE, test_name, record.result)
        if self.web.enabled:
            self.web.SetTestResult(ReportMsg.TEST_CASE_RESULT_FAIL)
        self.onFail(test_name, begin_time)

    def onFail(self, test_name, begin_time):
        """A function that is executed upon a test case failure.

        User implementation is optional.

        Args:
            test_name: Name of the test that triggered this function.
            begin_time: Logline format timestamp taken when the test started.
        """

    def _onPass(self, record):
        """Proxy function to guarantee the base implementation of onPass is
        called.

        Args:
            record: The records.TestResultRecord object for the passed test
                    case.
        """
        test_name = record.test_name
        begin_time = logger.epochToLogLineTimestamp(record.begin_time)
        msg = record.details
        if msg:
            logging.info(msg)
        logging.info(RESULT_LINE_TEMPLATE, test_name, record.result)
        if self.web.enabled:
            self.web.SetTestResult(ReportMsg.TEST_CASE_RESULT_PASS)
        self.onPass(test_name, begin_time)

    def onPass(self, test_name, begin_time):
        """A function that is executed upon a test case passing.

        Implementation is optional.

        Args:
            test_name: Name of the test that triggered this function.
            begin_time: Logline format timestamp taken when the test started.
        """

    def _onSkip(self, record):
        """Proxy function to guarantee the base implementation of onSkip is
        called.

        Args:
            record: The records.TestResultRecord object for the skipped test
                    case.
        """
        test_name = record.test_name
        begin_time = logger.epochToLogLineTimestamp(record.begin_time)
        logging.info(RESULT_LINE_TEMPLATE, test_name, record.result)
        logging.info("Reason to skip: %s", record.details)
        if self.web.enabled:
            self.web.SetTestResult(ReportMsg.TEST_CASE_RESULT_SKIP)
        self.onSkip(test_name, begin_time)

    def onSkip(self, test_name, begin_time):
        """A function that is executed upon a test case being skipped.

        Implementation is optional.

        Args:
            test_name: Name of the test that triggered this function.
            begin_time: Logline format timestamp taken when the test started.
        """

    def _onSilent(self, record):
        """Proxy function to guarantee the base implementation of onSilent is
        called.

        Args:
            record: The records.TestResultRecord object for the skipped test
                    case.
        """
        test_name = record.test_name
        begin_time = logger.epochToLogLineTimestamp(record.begin_time)
        if self.web.enabled:
            self.web.SetTestResult(None)
        self.onSilent(test_name, begin_time)

    def onSilent(self, test_name, begin_time):
        """A function that is executed upon a test case being marked as silent.

        Implementation is optional.

        Args:
            test_name: Name of the test that triggered this function.
            begin_time: Logline format timestamp taken when the test started.
        """

    def _onException(self, record):
        """Proxy function to guarantee the base implementation of onException
        is called.

        Args:
            record: The records.TestResultRecord object for the failed test
                    case.
        """
        test_name = record.test_name
        logging.exception(record.details)
        begin_time = logger.epochToLogLineTimestamp(record.begin_time)
        if self.web.enabled:
            self.web.SetTestResult(ReportMsg.TEST_CASE_RESULT_EXCEPTION)
        self.onException(test_name, begin_time)

    def onException(self, test_name, begin_time):
        """A function that is executed upon an unhandled exception from a test
        case.

        Implementation is optional.

        Args:
            test_name: Name of the test that triggered this function.
            begin_time: Logline format timestamp taken when the test started.
        """

    def _exec_procedure_func(self, func, tr_record):
        """Executes a procedure function like onPass, onFail etc.

        This function will alternate the 'Result' of the test's record if
        exceptions happened when executing the procedure function.

        This will let signals.TestAbortAll through so abortAll works in all
        procedure functions.

        Args:
            func: The procedure function to be executed.
            tr_record: The TestResultRecord object associated with the test
                       case executed.
        """
        try:
            func(tr_record)
        except signals.TestAbortAll:
            raise
        except Exception as e:
            logging.exception("Exception happened when executing %s for %s.",
                              func.__name__, self.currentTestName)
            tr_record.addError(func.__name__, e)

    def filterOneTest(self, test_name):
        """Check test filters for a test name.

        The first layer of filter is user defined test filters:
        if a include filter is not empty, only tests in include filter will
        be executed regardless whether they are also in exclude filter. Else
        if include filter is empty, only tests not in exclude filter will be
        executed.

        The second layer of filter is checking _skip_all_testcases flag:
        the subclass may set _skip_all_testcases to True in its implementation
        of setUpClass. If the flag is set, this method raises signals.TestSkip.

        The third layer of filter is checking abi bitness:
        if a test has a suffix indicating the intended architecture bitness,
        and the current abi bitness information is available, non matching tests
        will be skipped. By our convention, this function will look for bitness in suffix
        formated as "32bit", "32Bit", "32BIT", or 64 bit equivalents.

        This method assumes const.SUFFIX_32BIT and const.SUFFIX_64BIT are in lower cases.

        Args:
            test_name: string, name of a test

        Raises:
            signals.TestSilent if a test should not be executed
            signals.TestSkip if a test should be logged but not be executed
        """
        if (hasattr(self, keys.ConfigKeys.KEY_INCLUDE_FILTER) and
                getattr(self, keys.ConfigKeys.KEY_INCLUDE_FILTER)):
            if test_name not in getattr(self,
                                        keys.ConfigKeys.KEY_INCLUDE_FILTER):
                logging.info("Test case '%s' not in include filter." %
                             test_name)
                raise signals.TestSilent(
                    "Test case '%s' not in include filter." % test_name)
        elif (hasattr(self, keys.ConfigKeys.KEY_EXCLUDE_FILTER) and
              test_name in getattr(self, keys.ConfigKeys.KEY_EXCLUDE_FILTER)):
            logging.info("Test case '%s' in exclude filter." % test_name)
            raise signals.TestSilent("Test case '%s' in exclude filter." %
                                     test_name)

        if self._skip_all_testcases:
            raise signals.TestSkip("All test cases skipped.")

        if hasattr(self, keys.ConfigKeys.IKEY_ABI_BITNESS):
            bitness = getattr(self, keys.ConfigKeys.IKEY_ABI_BITNESS)
            run_32bit_on_64bit_abi = getattr(
                self, keys.ConfigKeys.IKEY_RUN_32BIT_ON_64BIT_ABI, False)

            skip_on_32bit_abi = getattr(
                self, keys.ConfigKeys.IKEY_SKIP_ON_32BIT_ABI, False)
            skip_on_64bit_abi = getattr(
                self, keys.ConfigKeys.IKEY_SKIP_ON_64BIT_ABI, False)

            asserts.skipIf(
                ((skip_on_32bit_abi is True) and bitness == "32") or (
                    (skip_on_64bit_abi is True) and bitness == "64") or
                (test_name.lower().endswith(const.SUFFIX_32BIT) and
                 bitness != "32") or (
                     test_name.lower().endswith(const.SUFFIX_64BIT) and
                     bitness != "64" and not run_32bit_on_64bit_abi),
                "Test case '{}' excluded as ABI bitness is {}.".format(
                    test_name, bitness))

    def execOneTest(self, test_name, test_func, args, **kwargs):
        """Executes one test case and update test results.

        Executes one test case, create a records.TestResultRecord object with
        the execution information, and add the record to the test class's test
        results.

        Args:
            test_name: Name of the test.
            test_func: The test function.
            args: A tuple of params.
            kwargs: Extra kwargs.
        """
        is_silenced = False
        tr_record = records.TestResultRecord(test_name, self.TAG)
        tr_record.testBegin()
        logging.info("%s %s", TEST_CASE_TOKEN, test_name)
        verdict = None
        try:
            ret = self._testEntry(test_name)
            asserts.assertTrue(ret is not False,
                               "Setup test entry for %s failed." % test_name)
            self.filterOneTest(test_name)
            try:
                ret = self._setUp(test_name)
                asserts.assertTrue(ret is not False,
                                   "Setup for %s failed." % test_name)

                if args or kwargs:
                    verdict = test_func(*args, **kwargs)
                else:
                    verdict = test_func()
            finally:
                self._tearDown(test_name)
        except (signals.TestFailure, AssertionError) as e:
            tr_record.testFail(e)
            self._exec_procedure_func(self._onFail, tr_record)
        except signals.TestSkip as e:
            # Test skipped.
            tr_record.testSkip(e)
            self._exec_procedure_func(self._onSkip, tr_record)
        except (signals.TestAbortClass, signals.TestAbortAll) as e:
            # Abort signals, pass along.
            tr_record.testFail(e)
            raise e
        except signals.TestPass as e:
            # Explicit test pass.
            tr_record.testPass(e)
            self._exec_procedure_func(self._onPass, tr_record)
        except signals.TestSilent as e:
            # Suppress test reporting.
            is_silenced = True
            self._exec_procedure_func(self._onSilent, tr_record)
            self.results.requested.remove(test_name)
        except Exception as e:
            # Exception happened during test.
            logging.exception(e)
            tr_record.testError(e)
            self._exec_procedure_func(self._onException, tr_record)
            self._exec_procedure_func(self._onFail, tr_record)
        else:
            # Keep supporting return False for now.
            # TODO(angli): Deprecate return False support.
            if verdict or (verdict is None):
                # Test passed.
                tr_record.testPass()
                self._exec_procedure_func(self._onPass, tr_record)
                return
            # Test failed because it didn't return True.
            # This should be removed eventually.
            tr_record.testFail()
            self._exec_procedure_func(self._onFail, tr_record)
        finally:
            if not is_silenced:
                self.results.addRecord(tr_record)
            self._testExit(test_name)

    def runGeneratedTests(self,
                          test_func,
                          settings,
                          args=None,
                          kwargs=None,
                          tag="",
                          name_func=None):
        """Runs generated test cases.

        Generated test cases are not written down as functions, but as a list
        of parameter sets. This way we reduce code repetition and improve
        test case scalability.

        Args:
            test_func: The common logic shared by all these generated test
                       cases. This function should take at least one argument,
                       which is a parameter set.
            settings: A list of strings representing parameter sets. These are
                      usually json strings that get loaded in the test_func.
            args: Iterable of additional position args to be passed to
                  test_func.
            kwargs: Dict of additional keyword args to be passed to test_func
            tag: Name of this group of generated test cases. Ignored if
                 name_func is provided and operates properly.
            name_func: A function that takes a test setting and generates a
                       proper test name. The test name should be shorter than
                       utils.MAX_FILENAME_LEN. Names over the limit will be
                       truncated.

        Returns:
            A list of settings that did not pass.
        """
        args = args or ()
        kwargs = kwargs or {}
        failed_settings = []
        for s in settings:
            test_name = "{} {}".format(tag, s)
            if name_func:
                try:
                    test_name = name_func(s, *args, **kwargs)
                except:
                    logging.exception(("Failed to get test name from "
                                       "test_func. Fall back to default %s"),
                                      test_name)
            self.results.requested.append(test_name)
            if len(test_name) > utils.MAX_FILENAME_LEN:
                test_name = test_name[:utils.MAX_FILENAME_LEN]
            previous_success_cnt = len(self.results.passed)
            self.execOneTest(test_name, test_func, (s, ) + args, **kwargs)
            if len(self.results.passed) - previous_success_cnt != 1:
                failed_settings.append(s)
        return failed_settings

    def _exec_func(self, func, *args):
        """Executes a function with exception safeguard.

        This will let signals.TestAbortAll through so abortAll works in all
        procedure functions.

        Args:
            func: Function to be executed.
            args: Arguments to be passed to the function.

        Returns:
            Whatever the function returns, or False if unhandled exception
            occured.
        """
        try:
            return func(*args)
        except signals.TestAbortAll:
            raise
        except:
            logging.exception("Exception happened when executing %s in %s.",
                              func.__name__, self.TAG)
            return False

    def _get_all_test_names(self):
        """Finds all the function names that match the test case naming
        convention in this class.

        Returns:
            A list of strings, each is a test case name.
        """
        test_names = []
        for name in dir(self):
            if name.startswith(STR_TEST) or name.startswith(STR_GENERATE):
                attr_func = getattr(self, name)
                if hasattr(attr_func, "__call__"):
                    test_names.append(name)
        return test_names

    def _get_test_funcs(self, test_names):
        """Obtain the actual functions of test cases based on test names.

        Args:
            test_names: A list of strings, each string is a test case name.

        Returns:
            A list of tuples of (string, function). String is the test case
            name, function is the actual test case function.

        Raises:
            errors.USERError is raised if the test name does not follow
            naming convention "test_*". This can only be caused by user input
            here.
        """
        test_funcs = []
        for test_name in test_names:
            if not hasattr(self, test_name):
                logging.warning("%s does not have test case %s.", self.TAG,
                                test_name)
            elif (test_name.startswith(STR_TEST) or
                  test_name.startswith(STR_GENERATE)):
                test_funcs.append((test_name, getattr(self, test_name)))
            else:
                msg = ("Test case name %s does not follow naming convention "
                       "test*, abort.") % test_name
                raise errors.USERError(msg)

        return test_funcs

    def run(self, test_names=None):
        """Runs test cases within a test class by the order they appear in the
        execution list.

        One of these test cases lists will be executed, shown here in priority
        order:
        1. The test_names list, which is passed from cmd line. Invalid names
           are guarded by cmd line arg parsing.
        2. The self.tests list defined in test class. Invalid names are
           ignored.
        3. All function that matches test case naming convention in the test
           class.

        Args:
            test_names: A list of string that are test case names requested in
                cmd line.

        Returns:
            The test results object of this class.
        """
        logging.info("==========> %s <==========", self.TAG)
        # Devise the actual test cases to run in the test class.
        if not test_names:
            if self.tests:
                # Specified by run list in class.
                test_names = list(self.tests)
            else:
                # No test case specified by user, execute all in the test class
                test_names = self._get_all_test_names()
        self.results.requested = [test_name for test_name in test_names
                                  if test_name.startswith(STR_TEST)]
        tests = self._get_test_funcs(test_names)
        # Setup for the class.
        try:
            if self._setUpClass() is False:
                raise signals.TestFailure("Failed to setup %s." % self.TAG)
        except Exception as e:
            logging.exception("Failed to setup %s.", self.TAG)
            self.results.failClass(self.TAG, e)
            self._exec_func(self._tearDownClass)
            return self.results
        # Run tests in order.
        try:
            for test_name, test_func in tests:
                if test_name.startswith(STR_GENERATE):
                    logging.info(
                        "Executing generated test trigger function '%s'",
                        test_name)
                    test_func()
                    logging.info("Finished '%s'", test_name)
                else:
                    self.execOneTest(test_name, test_func, None)
            if self._skip_all_testcases and not self.results.executed:
                self.results.skipClass(self.TAG,
                    "All test cases skipped; unable to find any test case.")
            return self.results
        except signals.TestAbortClass:
            logging.info("Received TestAbortClass signal")
            return self.results
        except signals.TestAbortAll as e:
            logging.info("Received TestAbortAll signal")
            # Piggy-back test results on this exception object so we don't lose
            # results from this test class.
            setattr(e, "results", self.results)
            raise e
        except Exception as e:
            # Exception happened during test.
            logging.exception(e)
            raise e
        finally:
            self._exec_func(self._tearDownClass)
            logging.info("Summary for test class %s: %s", self.TAG,
                         self.results.summary())

    def cleanUp(self):
        """A function that is executed upon completion of all tests cases
        selected in the test class.

        This function should clean up objects initialized in the constructor by
        user.
        """
