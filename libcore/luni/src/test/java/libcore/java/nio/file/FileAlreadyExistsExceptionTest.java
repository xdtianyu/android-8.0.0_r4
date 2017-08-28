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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import libcore.util.SerializationTester;

public class FileAlreadyExistsExceptionTest extends TestCase {
    public void test_constructor$String() {
        FileAlreadyExistsException exception = new FileAlreadyExistsException("file");
        assertEquals("file", exception.getFile());
        assertNull(exception.getOtherFile());
        assertNull(exception.getReason());

        assertTrue(exception instanceof FileSystemException);
    }

    public void test_constructor$String$String$String() {
        FileAlreadyExistsException exception = new FileAlreadyExistsException("file", "otherFile",
                "reason");
        assertEquals("file", exception.getFile());
        assertEquals("otherFile", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }

    public void test_serialization() throws IOException, ClassNotFoundException {
        String hex = "aced0005737200286a6176612e6e696f2e66696c652e46696c65416c726561647945786973747"
                + "3457863657074696f6e692ff0526155cb4d020000787200216a6176612e6e696f2e66696c652e466"
                + "96c6553797374656d457863657074696f6ed598f27876d360fc0200024c000466696c657400124c6"
                + "a6176612f6c616e672f537472696e673b4c00056f7468657271007e0002787200136a6176612e696"
                + "f2e494f457863657074696f6e6c8073646525f0ab020000787200136a6176612e6c616e672e45786"
                + "3657074696f6ed0fd1f3e1a3b1cc4020000787200136a6176612e6c616e672e5468726f7761626c6"
                + "5d5c635273977b8cb0300044c000563617573657400154c6a6176612f6c616e672f5468726f77616"
                + "26c653b4c000d64657461696c4d65737361676571007e00025b000a737461636b547261636574001"
                + "e5b4c6a6176612f6c616e672f537461636b5472616365456c656d656e743b4c00147375707072657"
                + "3736564457863657074696f6e737400104c6a6176612f7574696c2f4c6973743b787071007e00097"
                + "40006726561736f6e7572001e5b4c6a6176612e6c616e672e537461636b5472616365456c656d656"
                + "e743b02462a3c3cfd22390200007870000000097372001b6a6176612e6c616e672e537461636b547"
                + "2616365456c656d656e746109c59a2636dd8502000449000a6c696e654e756d6265724c000e64656"
                + "36c6172696e67436c61737371007e00024c000866696c654e616d6571007e00024c000a6d6574686"
                + "f644e616d6571007e000278700000002d7400346c6962636f72652e6a6176612e6e696f2e66696c6"
                + "52e46696c65416c7265616479457869737473457863657074696f6e5465737474002346696c65416"
                + "c7265616479457869737473457863657074696f6e546573742e6a617661740012746573745f73657"
                + "269616c697a6174696f6e7371007e000dfffffffe7400186a6176612e6c616e672e7265666c65637"
                + "42e4d6574686f6474000b4d6574686f642e6a617661740006696e766f6b657371007e000d000000f"
                + "9740028766f6761722e7461726765742e6a756e69742e4a756e69743324566f6761724a556e69745"
                + "465737474000b4a756e6974332e6a61766174000372756e7371007e000d00000063740020766f676"
                + "1722e7461726765742e6a756e69742e4a556e697452756e6e657224317400104a556e697452756e6"
                + "e65722e6a61766174000463616c6c7371007e000d0000005c740020766f6761722e7461726765742"
                + "e6a756e69742e4a556e697452756e6e657224317400104a556e697452756e6e65722e6a617661740"
                + "00463616c6c7371007e000d000000ed74001f6a6176612e7574696c2e636f6e63757272656e742e4"
                + "675747572655461736b74000f4675747572655461736b2e6a61766174000372756e7371007e000d0"
                + "000046d7400276a6176612e7574696c2e636f6e63757272656e742e546872656164506f6f6c45786"
                + "56375746f72740017546872656164506f6f6c4578656375746f722e6a61766174000972756e576f7"
                + "26b65727371007e000d0000025f74002e6a6176612e7574696c2e636f6e63757272656e742e54687"
                + "2656164506f6f6c4578656375746f7224576f726b6572740017546872656164506f6f6c457865637"
                + "5746f722e6a61766174000372756e7371007e000d000002f97400106a6176612e6c616e672e54687"
                + "265616474000b5468726561642e6a61766174000372756e7372001f6a6176612e7574696c2e436f6"
                + "c6c656374696f6e7324456d7074794c6973747ab817b43ca79ede02000078707874000466696c657"
                + "400096f7468657246696c65";

        FileAlreadyExistsException exception = (FileAlreadyExistsException) SerializationTester
                .deserializeHex(hex);

        String hex1 = SerializationTester.serializeHex(exception).toString();
        assertEquals(hex, hex1);
        assertEquals("file", exception.getFile());
        assertEquals("otherFile", exception.getOtherFile());
        assertEquals("reason", exception.getReason());
    }
}
