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
import java.nio.file.NotDirectoryException;
import libcore.util.SerializationTester;

public class NotDirectoryExceptionTest extends TestCase {
    public void test_constructor$String() {
        NotDirectoryException exception = new NotDirectoryException("file");
        assertEquals("file", exception.getFile());
        assertNull(exception.getOtherFile());
        assertNull(exception.getReason());

        assertTrue(exception instanceof FileSystemException);
    }

    public void test_serialization() throws IOException, ClassNotFoundException {
        String hex = "aced0005737200236a6176612e6e696f2e66696c652e4e6f744469726563746f7279457863657"
                + "074696f6e82f0df36f87ce379020000787200216a6176612e6e696f2e66696c652e46696c6553797"
                + "374656d457863657074696f6ed598f27876d360fc0200024c000466696c657400124c6a6176612f6"
                + "c616e672f537472696e673b4c00056f7468657271007e0002787200136a6176612e696f2e494f457"
                + "863657074696f6e6c8073646525f0ab020000787200136a6176612e6c616e672e457863657074696"
                + "f6ed0fd1f3e1a3b1cc4020000787200136a6176612e6c616e672e5468726f7761626c65d5c635273"
                + "977b8cb0300044c000563617573657400154c6a6176612f6c616e672f5468726f7761626c653b4c0"
                + "00d64657461696c4d65737361676571007e00025b000a737461636b547261636574001e5b4c6a617"
                + "6612f6c616e672f537461636b5472616365456c656d656e743b4c001473757070726573736564457"
                + "863657074696f6e737400104c6a6176612f7574696c2f4c6973743b787071007e0009707572001e5"
                + "b4c6a6176612e6c616e672e537461636b5472616365456c656d656e743b02462a3c3cfd223902000"
                + "07870000000097372001b6a6176612e6c616e672e537461636b5472616365456c656d656e746109c"
                + "59a2636dd8502000449000a6c696e654e756d6265724c000e6465636c6172696e67436c617373710"
                + "07e00024c000866696c654e616d6571007e00024c000a6d6574686f644e616d6571007e000278700"
                + "000002574002f6c6962636f72652e6a6176612e6e696f2e66696c652e4e6f744469726563746f727"
                + "9457863657074696f6e5465737474001e4e6f744469726563746f7279457863657074696f6e54657"
                + "3742e6a617661740012746573745f73657269616c697a6174696f6e7371007e000cfffffffe74001"
                + "86a6176612e6c616e672e7265666c6563742e4d6574686f6474000b4d6574686f642e6a617661740"
                + "006696e766f6b657371007e000c000000f9740028766f6761722e7461726765742e6a756e69742e4"
                + "a756e69743324566f6761724a556e69745465737474000b4a756e6974332e6a61766174000372756"
                + "e7371007e000c00000063740020766f6761722e7461726765742e6a756e69742e4a556e697452756"
                + "e6e657224317400104a556e697452756e6e65722e6a61766174000463616c6c7371007e000c00000"
                + "05c740020766f6761722e7461726765742e6a756e69742e4a556e697452756e6e657224317400104"
                + "a556e697452756e6e65722e6a61766174000463616c6c7371007e000c000000ed74001f6a6176612"
                + "e7574696c2e636f6e63757272656e742e4675747572655461736b74000f4675747572655461736b2"
                + "e6a61766174000372756e7371007e000c0000046d7400276a6176612e7574696c2e636f6e6375727"
                + "2656e742e546872656164506f6f6c4578656375746f72740017546872656164506f6f6c457865637"
                + "5746f722e6a61766174000972756e576f726b65727371007e000c0000025f74002e6a6176612e757"
                + "4696c2e636f6e63757272656e742e546872656164506f6f6c4578656375746f7224576f726b65727"
                + "40017546872656164506f6f6c4578656375746f722e6a61766174000372756e7371007e000c00000"
                + "2f97400106a6176612e6c616e672e54687265616474000b5468726561642e6a61766174000372756"
                + "e7372001f6a6176612e7574696c2e436f6c6c656374696f6e7324456d7074794c6973747ab817b43"
                + "ca79ede02000078707874000466696c6570";
        NotDirectoryException exception = (NotDirectoryException) SerializationTester
                .deserializeHex(hex);

        String hex1 = SerializationTester.serializeHex(exception).toString();
        assertEquals(hex, hex1);
        assertEquals("file", exception.getFile());
        assertNull(exception.getOtherFile());
        assertNull(exception.getReason());
    }
}
