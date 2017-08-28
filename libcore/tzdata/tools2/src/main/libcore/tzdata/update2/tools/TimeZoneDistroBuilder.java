/*
 * Copyright (C) 2015 The Android Open Source Project
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
package libcore.tzdata.update2.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import libcore.tzdata.shared2.DistroException;
import libcore.tzdata.shared2.DistroVersion;
import libcore.tzdata.shared2.TimeZoneDistro;

/**
 * A class for creating a {@link TimeZoneDistro} containing timezone update data. Used in real
 * distro creation code and tests.
 */
public final class TimeZoneDistroBuilder {

    private DistroVersion distroVersion;
    private byte[] tzData;
    private byte[] icuData;
    private String tzLookupXml;

    public TimeZoneDistroBuilder setDistroVersion(DistroVersion distroVersion) {
        this.distroVersion = distroVersion;
        return this;
    }

    public TimeZoneDistroBuilder clearVersionForTests() {
        // This has the effect of omitting the version file in buildUnvalidated().
        this.distroVersion = null;
        return this;
    }

    public TimeZoneDistroBuilder replaceFormatVersionForTests(int majorVersion, int minorVersion) {
        try {
            distroVersion = new DistroVersion(
                    majorVersion, minorVersion, distroVersion.rulesVersion, distroVersion.revision);
        } catch (DistroException e) {
            throw new IllegalArgumentException();
        }
        return this;
    }

    public TimeZoneDistroBuilder setTzDataFile(File tzDataFile) throws IOException {
        return setTzDataFile(readFileAsByteArray(tzDataFile));
    }

    public TimeZoneDistroBuilder setTzDataFile(byte[] tzData) {
        this.tzData = tzData;
        return this;
    }

    // For use in tests.
    public TimeZoneDistroBuilder clearTzDataForTests() {
        this.tzData = null;
        return this;
    }

    public TimeZoneDistroBuilder setIcuDataFile(File icuDataFile) throws IOException {
        return setIcuDataFile(readFileAsByteArray(icuDataFile));
    }

    public TimeZoneDistroBuilder setIcuDataFile(byte[] icuData) {
        this.icuData = icuData;
        return this;
    }

    public TimeZoneDistroBuilder setTzLookupFile(File tzLookupFile) throws IOException {
        return setTzLookupXml(readFileAsUtf8(tzLookupFile));
    }

    public TimeZoneDistroBuilder setTzLookupXml(String tzlookupXml) {
        this.tzLookupXml = tzlookupXml;
        return this;
    }

    // For use in tests.
    public TimeZoneDistroBuilder clearIcuDataForTests() {
        this.icuData = null;
        return this;
    }

    /**
     * For use in tests. Use {@link #build()}.
     */
    public TimeZoneDistro buildUnvalidated() throws DistroException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            if (distroVersion != null) {
                addZipEntry(zos, TimeZoneDistro.DISTRO_VERSION_FILE_NAME, distroVersion.toBytes());
            }

            if (tzData != null) {
                addZipEntry(zos, TimeZoneDistro.TZDATA_FILE_NAME, tzData);
            }
            if (icuData != null) {
                addZipEntry(zos, TimeZoneDistro.ICU_DATA_FILE_NAME, icuData);
            }
            if (tzLookupXml != null) {
                addZipEntry(zos, TimeZoneDistro.TZLOOKUP_FILE_NAME,
                        tzLookupXml.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new DistroException("Unable to create zip file", e);
        }
        return new TimeZoneDistro(baos.toByteArray());
    }

    /**
     * Builds a {@link TimeZoneDistro}.
     */
    public TimeZoneDistro build() throws DistroException {
        if (distroVersion == null) {
            throw new IllegalStateException("Missing distroVersion");
        }
        if (icuData == null) {
            throw new IllegalStateException("Missing icuData");
        }
        if (tzData == null) {
            throw new IllegalStateException("Missing tzData");
        }
        return buildUnvalidated();
    }

    private static void addZipEntry(ZipOutputStream zos, String name, byte[] content)
            throws DistroException {
        try {
            ZipEntry zipEntry = new ZipEntry(name);
            zipEntry.setSize(content.length);
            zos.putNextEntry(zipEntry);
            zos.write(content);
            zos.closeEntry();
        } catch (IOException e) {
            throw new DistroException("Unable to add zip entry", e);
        }
    }

    /**
     * Returns the contents of 'path' as a byte array.
     */
    private static byte[] readFileAsByteArray(File file) throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream  fis = new FileInputStream(file)) {
            int count;
            while ((count = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, count);
            }
        }
        return baos.toByteArray();
    }

    /**
     * Returns the contents of 'path' as a String, having interpreted the file as UTF-8.
     */
    private String readFileAsUtf8(File file) throws IOException {
        return new String(readFileAsByteArray(file), StandardCharsets.UTF_8);
    }
}

