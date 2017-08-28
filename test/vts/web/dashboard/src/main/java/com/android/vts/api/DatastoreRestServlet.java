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

package com.android.vts.api;

import com.android.vts.proto.VtsReportMessage.DashboardPostMessage;
import com.android.vts.proto.VtsReportMessage.TestReportMessage;
import com.android.vts.util.DatastoreHelper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.codec.binary.Base64;

/** REST endpoint for posting data to the Dashboard. */
public class DatastoreRestServlet extends HttpServlet {
    private static final String SERVICE_CLIENT_ID = System.getProperty("SERVICE_CLIENT_ID");
    private static final Logger logger = Logger.getLogger(DatastoreRestServlet.class.getName());

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        // Retrieve the params
        String payload = new String();
        DashboardPostMessage postMessage;
        try {
            String line = null;
            BufferedReader reader = request.getReader();
            while ((line = reader.readLine()) != null) {
                payload += line;
            }
            byte[] value = Base64.decodeBase64(payload);
            postMessage = DashboardPostMessage.parseFrom(value);
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            logger.log(Level.WARNING, "Invalid proto: " + payload);
            return;
        }

        // Verify service account access token.
        boolean authorized = false;
        if (postMessage.hasAccessToken()) {
            String accessToken = postMessage.getAccessToken();
            GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
            Oauth2 oauth2 =
                    new Oauth2.Builder(new NetHttpTransport(), new JacksonFactory(), credential)
                            .build();
            Tokeninfo tokenInfo = oauth2.tokeninfo().setAccessToken(accessToken).execute();
            if (tokenInfo.getIssuedTo().equals(SERVICE_CLIENT_ID)) {
                authorized = true;
            }
        }

        if (!authorized) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        for (TestReportMessage testReportMessage : postMessage.getTestReportList()) {
            DatastoreHelper.insertData(testReportMessage);
        }

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
