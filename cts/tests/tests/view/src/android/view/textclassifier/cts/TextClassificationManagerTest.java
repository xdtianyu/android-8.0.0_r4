/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.view.textclassifier.cts;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import android.os.LocaleList;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.textclassifier.TextClassificationManager;
import android.view.textclassifier.TextClassifier;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassificationManagerTest {

    private static final LocaleList LOCALES = LocaleList.forLanguageTags("en");

    private TextClassificationManager mTcm;
    private TextClassifier mClassifier;

    @Before
    public void setup() {
        mTcm = InstrumentationRegistry.getTargetContext()
                .getSystemService(TextClassificationManager.class);
        mTcm.setTextClassifier(null); // Resets the classifier.
        mClassifier = mTcm.getTextClassifier();
    }

    @Test
    public void testSmartSelectionDoesNotThrowException() {
        mClassifier.suggestSelection("text", 2, 3, LOCALES);
    }

    @Test
    public void testClassifyTextDoesNotThrowException() {
        mClassifier.classifyText("text", 2, 3, LOCALES);
    }

    @Test
    public void testSetTextClassifier() {
        TextClassifier classifier = mock(TextClassifier.class);
        mTcm.setTextClassifier(classifier);
        assertEquals(classifier, mTcm.getTextClassifier());
    }
}

