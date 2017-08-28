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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystemException;
import libcore.util.SerializationTester;

public class AtomicMoveNotSupportedExceptionTest extends TestCase {

    public void test_constructor$String$String$String() {
        AtomicMoveNotSupportedException exception = new AtomicMoveNotSupportedException("source",
                "target", "reason");
        assertEquals("source", exception.getFile());
        assertEquals("target", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
        assertTrue(exception instanceof FileSystemException);
    }

    public void test_serialization() throws IOException, ClassNotFoundException {
        String hex = "aced00057372002d6a6176612e6e696f2e66696c652e41746f6d69634d6f76654e6f745375707"
                + "06f72746564457863657074696f6e4afa75ccc59748db020000787200216a6176612e6e696f2e666"
                + "96c652e46696c6553797374656d457863657074696f6ed598f27876d360fc0200024c000466696c6"
                + "57400124c6a6176612f6c616e672f537472696e673b4c00056f7468657271007e0002787200136a6"
                + "176612e696f2e494f457863657074696f6e6c8073646525f0ab020000787200136a6176612e6c616"
                + "e672e457863657074696f6ed0fd1f3e1a3b1cc4020000787200136a6176612e6c616e672e5468726"
                + "f7761626c65d5c635273977b8cb0300044c000563617573657400154c6a6176612f6c616e672f546"
                + "8726f7761626c653b4c000d64657461696c4d65737361676571007e00025b000a737461636b54726"
                + "1636574001e5b4c6a6176612f6c616e672f537461636b5472616365456c656d656e743b4c0014737"
                + "57070726573736564457863657074696f6e737400104c6a6176612f7574696c2f4c6973743b78707"
                + "1007e0009740006726561736f6e7572001e5b4c6a6176612e6c616e672e537461636b54726163654"
                + "56c656d656e743b02462a3c3cfd22390200007870000000097372001b6a6176612e6c616e672e537"
                + "461636b5472616365456c656d656e746109c59a2636dd8502000449000a6c696e654e756d6265724"
                + "c000e6465636c6172696e67436c61737371007e00024c000866696c654e616d6571007e00024c000"
                + "a6d6574686f644e616d6571007e00027870000000247400396c6962636f72652e6a6176612e6e696"
                + "f2e66696c652e41746f6d69634d6f76654e6f74537570706f72746564457863657074696f6e54657"
                + "37474002841746f6d69634d6f76654e6f74537570706f72746564457863657074696f6e546573742"
                + "e6a617661740012746573745f73657269616c697a6174696f6e7371007e000dfffffffe7400186a6"
                + "176612e6c616e672e7265666c6563742e4d6574686f6474000b4d6574686f642e6a6176617400066"
                + "96e766f6b657371007e000d000000f9740028766f6761722e7461726765742e6a756e69742e4a756"
                + "e69743324566f6761724a556e69745465737474000b4a756e6974332e6a61766174000372756e737"
                + "1007e000d00000063740020766f6761722e7461726765742e6a756e69742e4a556e697452756e6e6"
                + "57224317400104a556e697452756e6e65722e6a61766174000463616c6c7371007e000d0000005c7"
                + "40020766f6761722e7461726765742e6a756e69742e4a556e697452756e6e657224317400104a556"
                + "e697452756e6e65722e6a61766174000463616c6c7371007e000d000000ed74001f6a6176612e757"
                + "4696c2e636f6e63757272656e742e4675747572655461736b74000f4675747572655461736b2e6a6"
                + "1766174000372756e7371007e000d0000046d7400276a6176612e7574696c2e636f6e63757272656"
                + "e742e546872656164506f6f6c4578656375746f72740017546872656164506f6f6c4578656375746"
                + "f722e6a61766174000972756e576f726b65727371007e000d0000025f74002e6a6176612e7574696"
                + "c2e636f6e63757272656e742e546872656164506f6f6c4578656375746f7224576f726b657274001"
                + "7546872656164506f6f6c4578656375746f722e6a61766174000372756e7371007e000d000002f97"
                + "400106a6176612e6c616e672e54687265616474000b5468726561642e6a61766174000372756e737"
                + "2001f6a6176612e7574696c2e436f6c6c656374696f6e7324456d7074794c6973747ab817b43ca79"
                + "ede020000787078740006736f75726365740006746172676574";

        AtomicMoveNotSupportedException exception = (AtomicMoveNotSupportedException)
                SerializationTester.deserializeHex(hex);

        String hex1 = SerializationTester.serializeHex(exception).toString();
        assertEquals(hex, hex1);
        assertEquals("source", exception.getFile());
        assertEquals("target", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }
}
