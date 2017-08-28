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
 * limitations under the License
 */

package libcore.java.nio.file;

import junit.framework.TestCase;

import java.io.IOException;
import java.nio.file.FileSystemException;
import libcore.util.SerializationTester;

public class FileSystemExceptionTest extends TestCase {

    public void test_constructor$String() {
        FileSystemException exception = new FileSystemException("file");
        assertEquals("file", exception.getFile());
        assertNull(exception.getOtherFile());
        assertNull(exception.getReason());
        assertTrue(exception instanceof IOException);
    }

    public void test_constructor$String$String$String() {
        FileSystemException exception = new FileSystemException("file", "otherFile", "reason");
        assertEquals("file", exception.getFile());
        assertEquals("otherFile", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }

    public void test_serialization() throws IOException, ClassNotFoundException {
        String hex = "aced0005737200216a6176612e6e696f2e66696c652e46696c6553797374656d4578636570746"
                + "96f6ed598f27876d360fc0200024c000466696c657400124c6a6176612f6c616e672f537472696e6"
                + "73b4c00056f7468657271007e0001787200136a6176612e696f2e494f457863657074696f6e6c807"
                + "3646525f0ab020000787200136a6176612e6c616e672e457863657074696f6ed0fd1f3e1a3b1cc40"
                + "20000787200136a6176612e6c616e672e5468726f7761626c65d5c635273977b8cb0300044c00056"
                + "3617573657400154c6a6176612f6c616e672f5468726f7761626c653b4c000d64657461696c4d657"
                + "37361676571007e00015b000a737461636b547261636574001e5b4c6a6176612f6c616e672f53746"
                + "1636b5472616365456c656d656e743b4c001473757070726573736564457863657074696f6e73740"
                + "0104c6a6176612f7574696c2f4c6973743b787071007e0008740006726561736f6e7572001e5b4c6"
                + "a6176612e6c616e672e537461636b5472616365456c656d656e743b02462a3c3cfd2239020000787"
                + "0000000097372001b6a6176612e6c616e672e537461636b5472616365456c656d656e746109c59a2"
                + "636dd8502000449000a6c696e654e756d6265724c000e6465636c6172696e67436c61737371007e0"
                + "0014c000866696c654e616d6571007e00014c000a6d6574686f644e616d6571007e0001787000000"
                + "02374002d6c6962636f72652e6a6176612e6e696f2e66696c652e46696c6553797374656d4578636"
                + "57074696f6e5465737474001c46696c6553797374656d457863657074696f6e546573742e6a61766"
                + "1740025746573745f636f6e7374727563746f7224537472696e6724537472696e6724537472696e6"
                + "77371007e000cfffffffe7400186a6176612e6c616e672e7265666c6563742e4d6574686f6474000"
                + "b4d6574686f642e6a617661740006696e766f6b657371007e000c000000f9740028766f6761722e7"
                + "461726765742e6a756e69742e4a756e69743324566f6761724a556e69745465737474000b4a756e6"
                + "974332e6a61766174000372756e7371007e000c00000063740020766f6761722e7461726765742e6"
                + "a756e69742e4a556e697452756e6e657224317400104a556e697452756e6e65722e6a61766174000"
                + "463616c6c7371007e000c0000005c740020766f6761722e7461726765742e6a756e69742e4a556e6"
                + "97452756e6e657224317400104a556e697452756e6e65722e6a61766174000463616c6c7371007e0"
                + "00c000000ed74001f6a6176612e7574696c2e636f6e63757272656e742e4675747572655461736b7"
                + "4000f4675747572655461736b2e6a61766174000372756e7371007e000c0000046d7400276a61766"
                + "12e7574696c2e636f6e63757272656e742e546872656164506f6f6c4578656375746f72740017546"
                + "872656164506f6f6c4578656375746f722e6a61766174000972756e576f726b65727371007e000c0"
                + "000025f74002e6a6176612e7574696c2e636f6e63757272656e742e546872656164506f6f6c45786"
                + "56375746f7224576f726b6572740017546872656164506f6f6c4578656375746f722e6a617661740"
                + "00372756e7371007e000c000002f97400106a6176612e6c616e672e54687265616474000b5468726"
                + "561642e6a61766174000372756e7372001f6a6176612e7574696c2e436f6c6c656374696f6e73244"
                + "56d7074794c6973747ab817b43ca79ede02000078707874000466696c657400096f7468657246696"
                + "c65";

        FileSystemException exception = (FileSystemException) SerializationTester
                .deserializeHex(hex);

        String hex1 = SerializationTester.serializeHex(exception).toString();
        assertEquals(hex, hex1);
        assertEquals("file", exception.getFile());
        assertEquals("otherFile", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }

    public void test_getMessage() {
        FileSystemException exception = new FileSystemException("file", "otherFile", "reason");
        assertEquals("file -> otherFile: reason", exception.getMessage());

        exception = new FileSystemException("file", "otherFile", null);
        assertEquals("file -> otherFile", exception.getMessage());

        exception = new FileSystemException(null, "otherFile", "reason");
        assertEquals(" -> otherFile: reason", exception.getMessage());

        exception = new FileSystemException("file", null, "reason");
        assertEquals("file: reason", exception.getMessage());
    }
}
