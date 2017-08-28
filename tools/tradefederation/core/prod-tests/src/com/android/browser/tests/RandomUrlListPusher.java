/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.browser.tests;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.BuildError;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a list of random URLs for browser stability test and push onto device
 *
 * Two file needs to be specified as URL pools, one for HTTP, the other for HTTPS; also a number
 * needs to be provided to indicate that how many URLs shall be randomly drawn from each pool;
 * finally the selected HTTP and HTTPS URLs will be joined alternately to form the final URL list,
 * and pushed to the specified location on device.
 */
public class RandomUrlListPusher implements ITargetPreparer {

    @Option(name = "http-pool", description = "path to the pool of HTTP urls",
            importance = Importance.ALWAYS)
    private File mHttpPool;
    @Option(name = "https-pool", description = "path to the pool of HTTPS urls",
            importance = Importance.ALWAYS)
    private File mHttpsPool;

    @Option(name = "num-urls", description = "number of URLs to draw from each pool",
            importance = Importance.ALWAYS)
    private int mNumUrl = 0;

    @Option(name = "device-location",
            description = "location on device where the generate URL list should be pushed")
    private String mDeviceLocation = "${EXTERNAL_STORAGE}/popular_urls.txt";

    @Override
    public void setUp(ITestDevice device, IBuildInfo buildInfo) throws TargetSetupError,
            BuildError, DeviceNotAvailableException {
        File tmp = null;
        try {
            tmp = FileUtil.createTempFile("random_url_list", ".txt");
            List<String> httpUrls = getRandomLinesFromFile(mHttpPool, mNumUrl);
            List<String> httpsUrls = getRandomLinesFromFile(mHttpsPool, mNumUrl);
            PrintWriter pw =new PrintWriter(tmp);
            for (int i = 0; i < mNumUrl; i++) {
                pw.println(httpUrls.get(i));
                pw.println(httpsUrls.get(i));
            }
            pw.flush();
            pw.close();
        } catch (IOException ioe) {
            throw new TargetSetupError("IOException while creating URL list", ioe,
                    device.getDeviceDescriptor());
        }
        device.pushFile(tmp, mDeviceLocation);
        if (tmp.exists()) {
            tmp.delete();
        }
    }

    List<String> getRandomLinesFromFile(File file, int numLines) throws IOException {
        List<String> ret = new ArrayList<String>();
        int totalLines = countNumLines(file);
        if (totalLines < numLines) {
            throw new IOException("not enough lines in the file");
        }
        Random rng = new Random(System.currentTimeMillis());
        int currentLines = 0;
        while (currentLines < numLines) {
            String line = null;
            BufferedReader br = new BufferedReader(new FileReader(file));
            while ((line = br.readLine()) != null) {
                // generate a random number r uniformly distributed in [0, totalLines - 1)
                // the p(r < numLines) ~= numLines/totalLines, so after the current while loop
                // we should have around numLines in the list ret
                if (rng.nextInt(totalLines) < numLines) {
                    ret.add(line);
                    currentLines++;
                    if (currentLines == numLines) break;
                }
            }
            br.close();
            // if the size check fails at next iteration start, we rescan the file; however at this
            // point we should be pretty close to 'numLines'
        }
        return ret;
    }

    /**
     * Counts number of lines inside the text file as given in path
     * @param file path to the text file to be counted
     * @return number of lines
     * @throws IOException
     */
    int countNumLines(File file) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        int lines = 0;
        while (br.readLine() != null) {
            lines++;
        }
        br.close();
        return lines;
    }
}
