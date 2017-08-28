/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.text.method.cts;

import static org.junit.Assert.assertEquals;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.method.WordIterator;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.BreakIterator;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WordIteratorTest {
    private WordIterator mWordIterator = new WordIterator();

    private void verifyIsWordWithSurrogate(int beginning, int end, int surrogateIndex) {
        for (int i = beginning; i <= end; i++) {
            if (i == surrogateIndex) continue;
            assertEquals(beginning, mWordIterator.getBeginning(i));
            assertEquals(end, mWordIterator.getEnd(i));
        }
    }

    private void setCharSequence(String string) {
        mWordIterator.setCharSequence(string, 0, string.length());
    }

    private void verifyIsWord(int beginning, int end) {
        verifyIsWordWithSurrogate(beginning, end, -1);
    }

    private void verifyIsNotWord(int beginning, int end) {
        for (int i = beginning; i <= end; i++) {
            assertEquals(BreakIterator.DONE, mWordIterator.getBeginning(i));
            assertEquals(BreakIterator.DONE, mWordIterator.getEnd(i));
        }
    }

    @Test
    public void testEmptyString() {
        setCharSequence("");
        assertEquals(BreakIterator.DONE, mWordIterator.following(0));
        assertEquals(BreakIterator.DONE, mWordIterator.preceding(0));

        assertEquals(BreakIterator.DONE, mWordIterator.getBeginning(0));
        assertEquals(BreakIterator.DONE, mWordIterator.getEnd(0));
    }

    @Test
    public void testOneWord() {
        setCharSequence("I");
        verifyIsWord(0, 1);

        setCharSequence("am");
        verifyIsWord(0, 2);

        setCharSequence("zen");
        verifyIsWord(0, 3);
    }

    @Test
    public void testSpacesOnly() {
        setCharSequence(" ");
        verifyIsNotWord(0, 1);

        setCharSequence(", ");
        verifyIsNotWord(0, 2);

        setCharSequence(":-)");
        verifyIsNotWord(0, 3);
    }

    @Test
    public void testBeginningEnd() {
        setCharSequence("Well hello,   there! ");
        //                  0123456789012345678901
        verifyIsWord(0, 4);
        verifyIsWord(5, 10);
        verifyIsNotWord(11, 13);
        verifyIsWord(14, 19);
        verifyIsNotWord(20, 21);

        setCharSequence("  Another - sentence");
        //                  012345678901234567890
        verifyIsNotWord(0, 1);
        verifyIsWord(2, 9);
        verifyIsNotWord(10, 11);
        verifyIsWord(12, 20);

        setCharSequence("This is \u0644\u0627 tested"); // Lama-aleph
        //                  012345678     9     01234567
        verifyIsWord(0, 4);
        verifyIsWord(5, 7);
        verifyIsWord(8, 10);
        verifyIsWord(11, 17);
    }

    @Test
    public void testSurrogate() {
        final String BAIRKAN = "\uD800\uDF31";

        setCharSequence("one we" + BAIRKAN + "ird word");
        //                  012345    67         890123456

        verifyIsWord(0, 3);
        // Skip index 7 (there is no point in starting between the two surrogate characters)
        verifyIsWordWithSurrogate(4, 11, 7);
        verifyIsWord(12, 16);

        setCharSequence("one " + BAIRKAN + "xxx word");
        //                  0123    45         678901234

        verifyIsWord(0, 3);
        verifyIsWordWithSurrogate(4, 9, 5);
        verifyIsWord(10, 14);

        setCharSequence("one xxx" + BAIRKAN + " word");
        //                  0123456    78         901234

        verifyIsWord(0, 3);
        verifyIsWordWithSurrogate(4, 9, 8);
        verifyIsWord(10, 14);
    }
}
