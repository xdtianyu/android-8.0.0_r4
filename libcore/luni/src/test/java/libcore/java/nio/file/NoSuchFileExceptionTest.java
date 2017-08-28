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
import java.nio.file.NoSuchFileException;
import libcore.util.SerializationTester;

public class NoSuchFileExceptionTest extends TestCase {
    public void test_constructor$String() {
        NoSuchFileException exception = new NoSuchFileException("file");
        assertEquals("file", exception.getFile());
        assertNull(exception.getOtherFile());
        assertNull(exception.getReason());

        assertTrue(exception instanceof FileSystemException);
    }

    public void test_constructor$String$String$String() {
        NoSuchFileException exception = new NoSuchFileException("file", "otherFile", "reason");
        assertEquals("file", exception.getFile());
        assertEquals("otherFile", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }

    public void test_serialization() throws IOException, ClassNotFoundException {
        String hex = "aced0005737200216a6176612e6e696f2e66696c652e4e6f5375636846696c654578636570746"
                + "96f6eecb4b0fef4cd7a85020000787200216a6176612e6e696f2e66696c652e46696c65537973746"
                + "56d457863657074696f6ed598f27876d360fc0200024c000466696c657400124c6a6176612f6c616"
                + "e672f537472696e673b4c00056f7468657271007e0002787200136a6176612e696f2e494f4578636"
                + "57074696f6e6c8073646525f0ab020000787200136a6176612e6c616e672e457863657074696f6ed"
                + "0fd1f3e1a3b1cc4020000787200136a6176612e6c616e672e5468726f7761626c65d5c635273977b"
                + "8cb0300044c000563617573657400154c6a6176612f6c616e672f5468726f7761626c653b4c000d6"
                + "4657461696c4d65737361676571007e00025b000a737461636b547261636574001e5b4c6a6176612"
                + "f6c616e672f537461636b5472616365456c656d656e743b4c0014737570707265737365644578636"
                + "57074696f6e737400104c6a6176612f7574696c2f4c6973743b787071007e0009740006726561736"
                + "f6e7572001e5b4c6a6176612e6c616e672e537461636b5472616365456c656d656e743b02462a3c3"
                + "cfd22390200007870000000097372001b6a6176612e6c616e672e537461636b5472616365456c656"
                + "d656e746109c59a2636dd8502000449000a6c696e654e756d6265724c000e6465636c6172696e674"
                + "36c61737371007e00024c000866696c654e616d6571007e00024c000a6d6574686f644e616d65710"
                + "07e000278700000002374002d6c6962636f72652e6a6176612e6e696f2e66696c652e4e6f5375636"
                + "846696c65457863657074696f6e5465737474001c4e6f5375636846696c65457863657074696f6e5"
                + "46573742e6a617661740025746573745f636f6e7374727563746f7224537472696e6724537472696"
                + "e6724537472696e677371007e000dfffffffe7400186a6176612e6c616e672e7265666c6563742e4"
                + "d6574686f6474000b4d6574686f642e6a617661740006696e766f6b657371007e000d000000f9740"
                + "028766f6761722e7461726765742e6a756e69742e4a756e69743324566f6761724a556e697454657"
                + "37474000b4a756e6974332e6a61766174000372756e7371007e000d00000063740020766f6761722"
                + "e7461726765742e6a756e69742e4a556e697452756e6e657224317400104a556e697452756e6e657"
                + "22e6a61766174000463616c6c7371007e000d0000005c740020766f6761722e7461726765742e6a7"
                + "56e69742e4a556e697452756e6e657224317400104a556e697452756e6e65722e6a6176617400046"
                + "3616c6c7371007e000d000000ed74001f6a6176612e7574696c2e636f6e63757272656e742e46757"
                + "47572655461736b74000f4675747572655461736b2e6a61766174000372756e7371007e000d00000"
                + "46d7400276a6176612e7574696c2e636f6e63757272656e742e546872656164506f6f6c457865637"
                + "5746f72740017546872656164506f6f6c4578656375746f722e6a61766174000972756e576f726b6"
                + "5727371007e000d0000025f74002e6a6176612e7574696c2e636f6e63757272656e742e546872656"
                + "164506f6f6c4578656375746f7224576f726b6572740017546872656164506f6f6c4578656375746"
                + "f722e6a61766174000372756e7371007e000d000002f97400106a6176612e6c616e672e546872656"
                + "16474000b5468726561642e6a61766174000372756e7372001f6a6176612e7574696c2e436f6c6c6"
                + "56374696f6e7324456d7074794c6973747ab817b43ca79ede02000078707874000466696c6574000"
                + "96f7468657246696c65";
        NoSuchFileException exception = (NoSuchFileException) SerializationTester
                .deserializeHex(hex);

        String hex1 = SerializationTester.serializeHex(exception).toString();
        assertEquals(hex, hex1);
        assertEquals("file", exception.getFile());
        assertEquals("otherFile", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }
}
