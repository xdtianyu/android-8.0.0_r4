/**
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.metrics;

/**
 * Logs netd events.
 *
 * {@hide}
 */
oneway interface INetdEventListener {
    const int EVENT_GETADDRINFO = 1;
    const int EVENT_GETHOSTBYNAME = 2;

    const int REPORTING_LEVEL_NONE = 0;
    const int REPORTING_LEVEL_METRICS = 1;
    const int REPORTING_LEVEL_FULL = 2;

    // Maximum number of IP addresses logged for DNS lookups before we truncate the full list.
    const int DNS_REPORTED_IP_ADDRESSES_LIMIT = 10;

    /**
     * Logs a DNS lookup function call (getaddrinfo and gethostbyname).
     *
     * @param netId the ID of the network the lookup was performed on.
     * @param eventType one of the EVENT_* constants in this interface.
     * @param returnCode the return value of the function call.
     * @param latencyMs the latency of the function call.
     * @param hostname the name that was looked up.
     * @param ipAddresses (possibly a subset of) the IP addresses returned.
     *        At most {@link #DNS_REPORTED_IP_ADDRESSES_LIMIT} addresses are logged.
     * @param ipAddressesCount the number of IP addresses returned. May be different from the length
     *        of ipAddresses if there were too many addresses to log.
     * @param uid the UID of the application that performed the query.
     */
    void onDnsEvent(int netId, int eventType, int returnCode, int latencyMs, String hostname,
            in String[] ipAddresses, int ipAddressesCount, int uid);

    /**
     * Logs a single connect library call.
     *
     * @param netId the ID of the network the connect was performed on.
     * @param error 0 if the connect call succeeded, otherwise errno if it failed.
     * @param latencyMs the latency of the connect call.
     * @param ipAddr destination IP address.
     * @param port destination port number.
     * @param uid the UID of the application that performed the connection.
     */
    void onConnectEvent(int netId, int error, int latencyMs, String ipAddr, int port, int uid);
}
