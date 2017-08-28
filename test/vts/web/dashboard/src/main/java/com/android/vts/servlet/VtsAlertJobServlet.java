/*
 * Copyright (c) 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.vts.servlet;

import com.android.vts.entity.DeviceInfoEntity;
import com.android.vts.entity.TestCaseRunEntity;
import com.android.vts.entity.TestEntity;
import com.android.vts.entity.TestRunEntity;
import com.android.vts.proto.VtsReportMessage.TestCaseResult;
import com.android.vts.util.EmailHelper;
import com.android.vts.util.FilterUtil;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;

/** Represents the notifications service which is automatically called on a fixed schedule. */
public class VtsAlertJobServlet extends BaseServlet {

    @Override
    public List<String[]> getNavbarLinks(HttpServletRequest request) {
        return null;
    }

    /**
     * Creates an email footer with the provided link.
     *
     * @param link The (string) link to provide in the footer.
     * @return The full HTML email footer.
     */
    private String getFooter(String link) {
        return "<br><br>For details, visit the"
                + " <a href='" + link + "'>"
                + "VTS dashboard.</a>";
    }

    /**
     * Compose an email if the test is inactive.
     *
     * @param test The TestEntity document storing the test status.
     * @param link Fully specified link to the test's status page.
     * @param emails The list of email addresses to send the email.
     * @param messages The message list in which to insert the inactivity notification email.
     * @return True if the test is inactive, false otherwise.
     */
    private boolean notifyIfInactive(
            TestEntity test, String link, List<String> emails, List<Message> messages) {
        long now = TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis());
        long diff = now - test.timestamp;
        // Send an email daily to notify that the test hasn't been running.
        // After 7 full days have passed, notifications will no longer be sent (i.e. the
        // test is assumed to be deprecated).
        if (diff > TimeUnit.DAYS.toMicros(1) && diff < TimeUnit.DAYS.toMicros(8)
                && diff % TimeUnit.DAYS.toMicros(1) < TimeUnit.MINUTES.toMicros(3)) {
            Date lastUpload = new Date(TimeUnit.MICROSECONDS.toMillis(test.timestamp));
            String uploadTimeString =
                    new SimpleDateFormat("MM/dd/yyyy HH:mm:ss").format(lastUpload);
            String subject = "Warning! Inactive test: " + test;
            String body = "Hello,<br><br>Test \"" + test + "\" is inactive. "
                    + "No new data has been uploaded since " + uploadTimeString + "."
                    + getFooter(link);
            try {
                messages.add(EmailHelper.composeEmail(emails, subject, body));
                return true;
            } catch (MessagingException | UnsupportedEncodingException e) {
                logger.log(Level.WARNING, "Error composing email : ", e);
            }
        }
        return false;
    }

    /**
     * Checks whether any new failures have occurred beginning since (and including) startTime.
     *
     * @param test The TestEntity object for the test.
     * @param link The string URL linking to the test's status table.
     * @param failedTestcaseIdMap The map of test case names to ID for those failing in the
     *     last status update.
     * @param emailAddresses The list of email addresses to send notifications to.
     * @param messages The email Message queue.
     * @returns latest TestStatusMessage or null if no update is available.
     * @throws IOException
     */
    public TestEntity getTestStatus(TestEntity test, String link,
            Map<String, Long> failedTestcaseIdMap, List<String> emailAddresses,
            List<Message> messages) throws IOException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        String footer = getFooter(link);

        TestRunEntity mostRecentRun = null;
        Map<String, TestCaseRunEntity> mostRecentTestCaseResults = new HashMap<>();
        Map<String, Long> testBreakageIdMap = new HashMap<>();
        int passingTestcaseCount = 0;
        List<Long> failingTestcaseIds = new ArrayList<>();
        Set<String> fixedTestcases = new HashSet<>();
        Set<String> newTestcaseFailures = new HashSet<>();
        Set<String> continuedTestcaseFailures = new HashSet<>();
        Set<String> skippedTestcaseFailures = new HashSet<>();
        Set<String> transientTestcaseFailures = new HashSet<>();

        String testName = test.testName;
        Key testKey = KeyFactory.createKey(TestEntity.KIND, testName);
        Filter testTypeFilter = FilterUtil.getTestTypeFilter(false, true, false);
        Filter runFilter =
                FilterUtil.getTimeFilter(testKey, test.timestamp + 1, null, testTypeFilter);
        Query q = new Query(TestRunEntity.KIND)
                          .setAncestor(testKey)
                          .setFilter(runFilter)
                          .addSort(Entity.KEY_RESERVED_PROPERTY, SortDirection.DESCENDING);

        for (Entity testRun : datastore.prepare(q).asIterable()) {
            TestRunEntity testRunEntity = TestRunEntity.fromEntity(testRun);
            if (testRunEntity == null) {
                logger.log(Level.WARNING, "Invalid test run detected: " + testRun.getKey());
            }
            if (mostRecentRun == null) {
                mostRecentRun = testRunEntity;
            }
            for (long testCaseId : testRunEntity.testCaseIds) {
                Entity testCaseRun;
                try {
                    testCaseRun =
                            datastore.get(KeyFactory.createKey(TestCaseRunEntity.KIND, testCaseId));
                } catch (EntityNotFoundException e) {
                    logger.log(Level.WARNING,
                            "Test case \"" + testCaseId + "\" from test " + testName);
                    continue;
                }
                TestCaseRunEntity testCaseRunEntity = TestCaseRunEntity.fromEntity(testCaseRun);
                if (testCaseRunEntity == null) {
                    logger.log(Level.WARNING, "Invalid test case run: " + testCaseRun.getKey());
                    continue;
                }
                String testCaseName = testCaseRunEntity.testCaseName;
                TestCaseResult result = TestCaseResult.valueOf(testCaseRunEntity.result);

                if (mostRecentRun == testRunEntity) {
                    mostRecentTestCaseResults.put(testCaseName, testCaseRunEntity);
                } else {
                    if (!mostRecentTestCaseResults.containsKey(testCaseName)) {
                        // Deprecate notifications for tests that are not present on newer runs
                        continue;
                    }
                    TestCaseResult mostRecentRes = TestCaseResult.valueOf(
                            mostRecentTestCaseResults.get(testCaseName).result);
                    if (mostRecentRes == TestCaseResult.TEST_CASE_RESULT_SKIP) {
                        mostRecentTestCaseResults.put(testCaseName, testCaseRunEntity);
                    } else if (mostRecentRes == TestCaseResult.TEST_CASE_RESULT_PASS) {
                        // Test is passing now, witnessed a transient failure
                        if (result != TestCaseResult.TEST_CASE_RESULT_PASS
                                && result != TestCaseResult.TEST_CASE_RESULT_SKIP) {
                            transientTestcaseFailures.add(testCaseName);
                        }
                    }
                }

                // Record test case breakages
                if (result != TestCaseResult.TEST_CASE_RESULT_PASS
                        && result != TestCaseResult.TEST_CASE_RESULT_SKIP) {
                    testBreakageIdMap.put(testCaseName, testCaseRunEntity.key.getId());
                }
            }
        }

        if (mostRecentRun == null) {
            notifyIfInactive(test, link, emailAddresses, messages);
            return null;
        }

        for (String testCaseName : mostRecentTestCaseResults.keySet()) {
            TestCaseRunEntity testCaseRunEntity = mostRecentTestCaseResults.get(testCaseName);
            TestCaseResult mostRecentResult = TestCaseResult.valueOf(testCaseRunEntity.result);
            boolean previouslyFailed = failedTestcaseIdMap.containsKey(testCaseName);
            if (mostRecentResult == TestCaseResult.TEST_CASE_RESULT_SKIP) {
                // persist previous status
                if (previouslyFailed) {
                    failingTestcaseIds.add(failedTestcaseIdMap.get(testCaseName));
                } else {
                    ++passingTestcaseCount;
                }
            } else if (mostRecentResult == TestCaseResult.TEST_CASE_RESULT_PASS) {
                ++passingTestcaseCount;
                if (previouslyFailed && !transientTestcaseFailures.contains(testCaseName)) {
                    fixedTestcases.add(testCaseName);
                }
            } else {
                if (!previouslyFailed) {
                    newTestcaseFailures.add(testCaseName);
                    failingTestcaseIds.add(testBreakageIdMap.get(testCaseName));
                } else {
                    continuedTestcaseFailures.add(testCaseName);
                    failingTestcaseIds.add(failedTestcaseIdMap.get(testCaseName));
                }
            }
        }

        Set<String> buildIdList = new HashSet<>();
        Query deviceQuery = new Query(DeviceInfoEntity.KIND).setAncestor(mostRecentRun.key);
        for (Entity device : datastore.prepare(deviceQuery).asIterable()) {
            DeviceInfoEntity deviceEntity = DeviceInfoEntity.fromEntity(device);
            if (deviceEntity == null) {
                continue;
            }
            buildIdList.add(deviceEntity.buildId);
        }
        String buildId = StringUtils.join(buildIdList, ",");
        String summary = new String();
        if (newTestcaseFailures.size() + continuedTestcaseFailures.size() > 0) {
            summary += "The following test cases failed in the latest test run:<br>";

            // Add new test case failures to top of summary in bold font.
            List<String> sortedNewTestcaseFailures = new ArrayList<>(newTestcaseFailures);
            Collections.sort(sortedNewTestcaseFailures);
            for (String testcaseName : sortedNewTestcaseFailures) {
                summary += "- "
                        + "<b>" + testcaseName + "</b><br>";
            }

            // Add continued test case failures to summary.
            List<String> sortedContinuedTestcaseFailures =
                    new ArrayList<>(continuedTestcaseFailures);
            Collections.sort(sortedContinuedTestcaseFailures);
            for (String testcaseName : sortedContinuedTestcaseFailures) {
                summary += "- " + testcaseName + "<br>";
            }
        }
        if (fixedTestcases.size() > 0) {
            // Add fixed test cases to summary.
            summary += "<br><br>The following test cases were fixed in the latest test run:<br>";
            List<String> sortedFixedTestcases = new ArrayList<>(fixedTestcases);
            Collections.sort(sortedFixedTestcases);
            for (String testcaseName : sortedFixedTestcases) {
                summary += "- <i>" + testcaseName + "</i><br>";
            }
        }
        if (transientTestcaseFailures.size() > 0) {
            // Add transient test case failures to summary.
            summary += "<br><br>The following transient test case failures occured:<br>";
            List<String> sortedTransientTestcaseFailures =
                    new ArrayList<>(transientTestcaseFailures);
            Collections.sort(sortedTransientTestcaseFailures);
            for (String testcaseName : sortedTransientTestcaseFailures) {
                summary += "- " + testcaseName + "<br>";
            }
        }
        if (skippedTestcaseFailures.size() > 0) {
            // Add skipped test case failures to summary.
            summary += "<br><br>The following test cases have not been run since failing:<br>";
            List<String> sortedSkippedTestcaseFailures = new ArrayList<>(skippedTestcaseFailures);
            Collections.sort(sortedSkippedTestcaseFailures);
            for (String testcaseName : sortedSkippedTestcaseFailures) {
                summary += "- " + testcaseName + "<br>";
            }
        }

        if (newTestcaseFailures.size() > 0) {
            String subject = "New test failures in " + testName + " @ " + buildId;
            String body = "Hello,<br><br>Test cases are failing in " + testName
                    + " for device build ID(s): " + buildId + ".<br><br>" + summary + footer;
            try {
                messages.add(EmailHelper.composeEmail(emailAddresses, subject, body));
            } catch (MessagingException | UnsupportedEncodingException e) {
                logger.log(Level.WARNING, "Error composing email : ", e);
            }
        } else if (continuedTestcaseFailures.size() > 0) {
            String subject = "Continued test failures in " + testName + " @ " + buildId;
            String body = "Hello,<br><br>Test cases are failing in " + testName
                    + " for device build ID(s): " + buildId + ".<br><br>" + summary + footer;
            try {
                messages.add(EmailHelper.composeEmail(emailAddresses, subject, body));
            } catch (MessagingException | UnsupportedEncodingException e) {
                logger.log(Level.WARNING, "Error composing email : ", e);
            }
        } else if (transientTestcaseFailures.size() > 0) {
            String subject = "Transient test failure in " + testName + " @ " + buildId;
            String body = "Hello,<br><br>Some test cases failed in " + testName + " but tests all "
                    + "are passing in the latest device build(s): " + buildId + ".<br><br>"
                    + summary + footer;
            try {
                messages.add(EmailHelper.composeEmail(emailAddresses, subject, body));
            } catch (MessagingException | UnsupportedEncodingException e) {
                logger.log(Level.WARNING, "Error composing email : ", e);
            }
        } else if (fixedTestcases.size() > 0) {
            String subject = "All test cases passing in " + testName + " @ " + buildId;
            String body = "Hello,<br><br>All test cases passed in " + testName
                    + " for device build ID(s): " + buildId + "!<br><br>" + summary + footer;
            try {
                messages.add(EmailHelper.composeEmail(emailAddresses, subject, body));
            } catch (MessagingException | UnsupportedEncodingException e) {
                logger.log(Level.WARNING, "Error composing email : ", e);
            }
        }
        return new TestEntity(test.testName, mostRecentRun.startTimestamp, passingTestcaseCount,
                failingTestcaseIds.size(), failingTestcaseIds);
    }

    /**
     * Process the current test case failures for a test.
     *
     * @param testEntity The TestEntity object for the test.
     * @returns a map from test case name to the test case run ID for which the test case failed.
     */
    public static Map<String, Long> getCurrentFailures(TestEntity testEntity) {
        if (testEntity.failingTestcaseIds == null) {
            return new HashMap<>();
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Map<String, Long> failingTestcases = new HashMap<>();
        for (long testCaseId : testEntity.failingTestcaseIds) {
            try {
                Entity testCaseRun =
                        datastore.get(KeyFactory.createKey(TestCaseRunEntity.KIND, testCaseId));
                TestCaseRunEntity testCaseRunEntity = TestCaseRunEntity.fromEntity(testCaseRun);
                if (testCaseRunEntity != null) {
                    failingTestcases.put(testCaseRunEntity.testCaseName, testCaseId);
                }
            } catch (EntityNotFoundException e) {
                // not found
            }
        }
        return failingTestcases;
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doGetHandler(request, response);
    }

    @Override
    public void doGetHandler(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        Query q = new Query(TestEntity.KIND);
        for (Entity test : datastore.prepare(q).asIterable()) {
            TestEntity testEntity = TestEntity.fromEntity(test);
            if (testEntity == null) {
                logger.log(Level.WARNING, "Corrupted test entity: " + test.getKey().getName());
                continue;
            }
            List<String> emails = EmailHelper.getSubscriberEmails(test.getKey());

            StringBuffer fullUrl = request.getRequestURL();
            String baseUrl = fullUrl.substring(0, fullUrl.indexOf(request.getRequestURI()));
            String link = baseUrl + "/show_table?testName=" + testEntity.testName;

            List<Message> messageQueue = new ArrayList<>();
            Map<String, Long> failedTestcaseMap = getCurrentFailures(testEntity);

            TestEntity newTestEntity =
                    getTestStatus(testEntity, link, failedTestcaseMap, emails, messageQueue);

            // Send any inactivity notifications
            if (newTestEntity == null) {
                if (messageQueue.size() > 0) {
                    EmailHelper.sendAll(messageQueue);
                }
                continue;
            }

            Transaction txn = datastore.beginTransaction();
            try {
                try {
                    testEntity = TestEntity.fromEntity(datastore.get(test.getKey()));

                    // Another job updated the test entity
                    if (testEntity == null || testEntity.timestamp >= newTestEntity.timestamp) {
                        txn.rollback();
                    } else { // This update is most recent.
                        datastore.put(newTestEntity.toEntity());
                        txn.commit();
                        EmailHelper.sendAll(messageQueue);
                    }
                } catch (EntityNotFoundException e) {
                    logger.log(Level.INFO,
                            "Test disappeared during updated: " + newTestEntity.testName);
                }
            } finally {
                if (txn.isActive()) {
                    txn.rollback();
                }
            }
        }
    }
}
