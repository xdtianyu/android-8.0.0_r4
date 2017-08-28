/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.loganalysis.parser;

import com.android.loganalysis.item.IItem;
import com.android.loganalysis.item.LatencyItem;
import com.android.loganalysis.item.TransitionDelayItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse the events logs. </p>
 */
public class EventsLogParser implements IParser {

    // 08-21 17:53:53.876 1053 2135
    private static final String EVENTS_PREFIX = "^\\d{2}-\\d{2} \\d{2}:\\d{2}"
            + ":\\d{2}.\\d{3}\\s+\\d+\\s+\\d+ ";

    // 01-01 01:38:44.863  1037  1111 I sysui_multi_action:
    // [319,64,321,64,322,99,325,5951,757,761,758,9,759,4,806,com.google.android.gm,871,
    // com.google.android.gm.welcome.WelcomeTourActivity,905,0]
    private static final Pattern TRANSITION_STARTING_DELAY = Pattern.compile(
            String.format("%s%s", EVENTS_PREFIX, "I sysui_multi_action: \\[319,(.*),321,(.*)"
                    + ",322,(.*),806,(.*),871,(.*),905.*\\]$"));

    // 01-01 01:38:44.863  1037  1111 I sysui_multi_action:
    // [319,64,322,99,325,5951,757,761,758,9,759,4,806,com.google.android.gm,871,
    // com.google.android.gm.welcome.WelcomeTourActivity,905,0]
    private static final Pattern TRANSITION_DELAY = Pattern.compile(
            String.format("%s%s", EVENTS_PREFIX, "I sysui_multi_action: \\[319,(.*),322,(.*)"
                    + ",806,(.*),871,(.*),905.*\\]$"));

    // 08-21 17:53:53.876 1053 2135 I sysui_latency: [1,50]
    private static final Pattern ACTION_LATENCY = Pattern.compile(
            String.format("%s%s", EVENTS_PREFIX, "I sysui_latency: \\[(?<action>.*),"
                    + "(?<delay>.*)\\]$"));

    @Override
    public IItem parse(List<String> lines) {
        throw new UnsupportedOperationException("Method has not been implemented in lieu"
                + " of others");
    }

    /**
     * Method to parse the transition delay information from the events log
     *
     * @param input
     * @return
     * @throws IOException
     */
    public List<TransitionDelayItem> parseTransitionDelayInfo(BufferedReader input)
            throws IOException {
        List<TransitionDelayItem> transitionDelayItems = new ArrayList<TransitionDelayItem>();
        String line;
        while ((line = input.readLine()) != null) {
            Matcher match = null;
            if (((match = matches(TRANSITION_STARTING_DELAY, line)) != null)) {
                TransitionDelayItem delayItem = new TransitionDelayItem();
                delayItem.setComponentName(match.group(4) + "/" + match.group(5));
                delayItem.setTransitionDelay(Long.parseLong(match.group(1)));
                delayItem.setStartingWindowDelay(Long.parseLong(match.group(2)));
                transitionDelayItems.add(delayItem);
            } else if (((match = matches(TRANSITION_DELAY, line)) != null)) {
                TransitionDelayItem delayItem = new TransitionDelayItem();
                delayItem.setComponentName(match.group(3) + "/" + match.group(4));
                delayItem.setTransitionDelay(Long.parseLong(match.group(1)));
                transitionDelayItems.add(delayItem);
            }
        }
        return transitionDelayItems;
    }

    /**
     * Method to parse the latency information from the events log
     *
     * @param input
     * @return
     * @throws IOException
     */
    public List<LatencyItem> parseLatencyInfo(BufferedReader input) throws IOException {
        List<LatencyItem> latencyItems = new ArrayList<LatencyItem>();
        String line;
        while ((line = input.readLine()) != null) {
            Matcher match = null;
            if (((match = matches(ACTION_LATENCY, line))) != null) {
                LatencyItem latencyItem = new LatencyItem();
                latencyItem.setActionId(Integer.parseInt(match.group("action")));
                latencyItem.setDelay(Long.parseLong(match.group("delay")));
                latencyItems.add(latencyItem);
            }
        }
        return latencyItems;
    }

    /**
     * Checks whether {@code line} matches the given {@link Pattern}.
     *
     * @return The resulting {@link Matcher} obtained by matching the {@code line} against
     *         {@code pattern}, or null if the {@code line} does not match.
     */
    private static Matcher matches(Pattern pattern, String line) {
        Matcher ret = pattern.matcher(line);
        return ret.matches() ? ret : null;
    }

}
