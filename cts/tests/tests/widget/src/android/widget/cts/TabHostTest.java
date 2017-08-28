/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.Activity;
import android.app.ActivityGroup;
import android.app.Instrumentation;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.SmallTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test {@link TabHost}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TabHostTest {
    private static final String TAG_TAB1 = "tab 1";
    private static final String TAG_TAB2 = "tab 2";
    private static final int TAB_HOST_ID = android.R.id.tabhost;

    private Instrumentation mInstrumentation;
    private TabHostCtsActivity mActivity;

    @Rule
    public ActivityTestRule<TabHostCtsActivity> mActivityRule =
            new ActivityTestRule<>(TabHostCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testConstructor() {
        new TabHost(mActivity);

        new TabHost(mActivity, null);
    }

    @Test
    public void testNewTabSpec() {
        TabHost tabHost = new TabHost(mActivity);

        assertNotNull(tabHost.newTabSpec(TAG_TAB2));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testNewTabSpecWithNullTag() {
        TabHost tabHost = new TabHost(mActivity);

        tabHost.newTabSpec(null);
    }

    /*
     * Check points:
     * 1. the tabWidget view and tabContent view associated with tabHost are created.
     * 2. no exception occurs when doing normal operation after setup().
     */
    @Test
    public void testSetup1() throws Throwable {
        final Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.setClassName("android.widget.cts", CtsActivity.class.getName());
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final Activity activity = mInstrumentation.startActivitySync(launchIntent);
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            activity.setContentView(R.layout.tabhost_layout);

            TabHost tabHost = (TabHost) activity.findViewById(TAB_HOST_ID);
            assertNull(tabHost.getTabWidget());
            assertNull(tabHost.getTabContentView());
            tabHost.setup();
            assertNotNull(tabHost.getTabWidget());
            assertNotNull(tabHost.getTabContentView());

            TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB1);
            tabSpec.setIndicator(TAG_TAB1);
            tabSpec.setContent(new MyTabContentFactoryList());
            tabHost.addTab(tabSpec);
            tabHost.setCurrentTab(0);
        });
        mInstrumentation.waitForIdleSync();

        activity.finish();
    }

    /*
     * Check points:
     * 1. the tabWidget view and tabContent view associated with tabHost are created.
     * 2. no exception occurs when uses TabSpec.setContent(android.content.Intent) after setup().
     */
    @Test
    public void testSetup2() throws Throwable {
        final Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.setClassName("android.widget.cts", ActivityGroup.class.getName());
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        final ActivityGroup activity =
                (ActivityGroup) mInstrumentation.startActivitySync(launchIntent);
        mInstrumentation.waitForIdleSync();

        mActivityRule.runOnUiThread(() -> {
            activity.setContentView(R.layout.tabhost_layout);

            TabHost tabHost = (TabHost) activity.findViewById(TAB_HOST_ID);
            assertNull(tabHost.getTabWidget());
            assertNull(tabHost.getTabContentView());
            tabHost.setup(activity.getLocalActivityManager());
            assertNotNull(tabHost.getTabWidget());
            assertNotNull(tabHost.getTabContentView());

            TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB1);
            tabSpec.setIndicator(TAG_TAB1);
            Intent intent = new Intent(Intent.ACTION_VIEW, null,
                    mActivity, CtsActivity.class);
            tabSpec.setContent(intent);
            tabHost.addTab(tabSpec);
            tabHost.setCurrentTab(0);
        });
        mInstrumentation.waitForIdleSync();

        activity.finish();
    }

    @UiThreadTest
    @Test
    public void testAddTab() {
        TabHost tabHost = mActivity.getTabHost();
        // there is a initial tab
        assertEquals(1, tabHost.getTabWidget().getChildCount());

        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryList());
        tabHost.addTab(tabSpec);
        assertEquals(2, tabHost.getTabWidget().getChildCount());
        tabHost.setCurrentTab(1);
        assertTrue(tabHost.getCurrentView() instanceof ListView);
        assertEquals(TAG_TAB2, tabHost.getCurrentTabTag());
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddTabNoIndicatorNoContent() {
        TabHost tabHost = mActivity.getTabHost();
        tabHost.addTab(tabHost.newTabSpec("tab 3"));
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddTabNoContent() {
        TabHost tabHost = mActivity.getTabHost();
        tabHost.addTab(tabHost.newTabSpec("tab 3").setIndicator("tab 3"));
    }

    @UiThreadTest
    @Test(expected=IllegalArgumentException.class)
    public void testAddTabNoIndicator() {
        TabHost tabHost = mActivity.getTabHost();
        tabHost.addTab(tabHost.newTabSpec("tab 3").setContent(new MyTabContentFactoryText()));
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testAddTabNull() {
        TabHost tabHost = mActivity.getTabHost();
        tabHost.addTab(null);
    }

    @UiThreadTest
    @Test
    public void testClearAllTabs() {
        TabHost tabHost = mActivity.getTabHost();
        MyTabContentFactoryText tcf = new MyTabContentFactoryText();
        // add two additional tabs
        tabHost.addTab(tabHost.newTabSpec(TAG_TAB1).setIndicator(TAG_TAB1).setContent(tcf));
        tabHost.addTab(tabHost.newTabSpec(TAG_TAB2).setIndicator(TAG_TAB2).setContent(tcf));
        assertEquals(3, tabHost.getTabWidget().getChildCount());
        assertEquals(3, tabHost.getTabContentView().getChildCount());
        assertEquals(0, tabHost.getCurrentTab());
        assertNotNull(tabHost.getCurrentView());

        tabHost.clearAllTabs();

        assertEquals(0, tabHost.getTabWidget().getChildCount());
        assertEquals(0, tabHost.getTabContentView().getChildCount());
        assertEquals(-1, tabHost.getCurrentTab());
        assertNull(tabHost.getCurrentView());
    }

    @Test
    public void testGetTabWidget() {
        TabHost tabHost = mActivity.getTabHost();

        // The attributes defined in tabhost_layout.xml
        assertEquals(android.R.id.tabs, tabHost.getTabWidget().getId());
        WidgetTestUtils.assertScaledPixels(1, tabHost.getTabWidget().getPaddingLeft(), mActivity);
        WidgetTestUtils.assertScaledPixels(1, tabHost.getTabWidget().getPaddingRight(), mActivity);
        WidgetTestUtils.assertScaledPixels(4, tabHost.getTabWidget().getPaddingTop(), mActivity);
    }

    @UiThreadTest
    @Test
    public void testAccessCurrentTab() {
        TabHost tabHost = mActivity.getTabHost();
        assertEquals(0, tabHost.getCurrentTab());

        // normal value
        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryText());
        tabHost.addTab(tabSpec);
        tabHost.setCurrentTab(1);
        assertEquals(1, tabHost.getCurrentTab());
        tabHost.setCurrentTab(0);
        assertEquals(0, tabHost.getCurrentTab());

        // exceptional value
        tabHost.setCurrentTab(tabHost.getTabWidget().getChildCount() + 1);
        assertEquals(0, tabHost.getCurrentTab());
        tabHost.setCurrentTab(-1);
        assertEquals(0, tabHost.getCurrentTab());
    }

    @UiThreadTest
    @Test
    public void testGetCurrentTabView() {
        TabHost tabHost = mActivity.getTabHost();
        // current tab view is the first child of tabWidget.
        assertSame(tabHost.getTabWidget().getChildAt(0), tabHost.getCurrentTabView());

        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryText());
        tabHost.addTab(tabSpec);
        tabHost.setCurrentTab(1);
        // current tab view is the second child of tabWidget.
        assertSame(tabHost.getTabWidget().getChildAt(1), tabHost.getCurrentTabView());
    }

    @UiThreadTest
    @Test
    public void testGetCurrentView() {
        TabHost tabHost = mActivity.getTabHost();
        TextView textView = (TextView) tabHost.getCurrentView();
        assertEquals(TabHostCtsActivity.INITIAL_VIEW_TEXT, textView.getText().toString());

        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryList());
        tabHost.addTab(tabSpec);
        tabHost.setCurrentTab(1);
        assertTrue(tabHost.getCurrentView() instanceof ListView);
    }

    @UiThreadTest
    @Test
    public void testSetCurrentTabByTag() {
        TabHost tabHost = mActivity.getTabHost();

        // set CurrentTab
        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryText());
        tabHost.addTab(tabSpec);

        tabHost.setCurrentTabByTag(TAG_TAB2);
        assertEquals(1, tabHost.getCurrentTab());

        tabHost.setCurrentTabByTag(TabHostCtsActivity.INITIAL_TAB_TAG);
        assertEquals(0, tabHost.getCurrentTab());

        // exceptional value
        tabHost.setCurrentTabByTag(null);
        assertEquals(0, tabHost.getCurrentTab());

        tabHost.setCurrentTabByTag("unknown tag");
        assertEquals(0, tabHost.getCurrentTab());
    }

    @UiThreadTest
    @Test
    public void testGetTabContentView() {
        TabHost tabHost = mActivity.getTabHost();
        assertEquals(3, tabHost.getTabContentView().getChildCount());

        TextView child0 = (TextView) tabHost.getTabContentView().getChildAt(0);
        assertEquals(mActivity.getResources().getString(R.string.hello_world),
                child0.getText().toString());
        assertTrue(tabHost.getTabContentView().getChildAt(1) instanceof ListView);
        TextView child2 = (TextView) tabHost.getTabContentView().getChildAt(2);
        tabHost.setCurrentTab(0);
        assertEquals(TabHostCtsActivity.INITIAL_VIEW_TEXT, child2.getText().toString());

        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryList());
        tabHost.addTab(tabSpec);
        assertEquals(3, tabHost.getTabContentView().getChildCount());
        tabHost.setCurrentTab(1);
        assertEquals(4, tabHost.getTabContentView().getChildCount());

        child0 = (TextView) tabHost.getTabContentView().getChildAt(0);
        assertEquals(mActivity.getResources().getString(R.string.hello_world),
                child0.getText().toString());
        assertTrue(tabHost.getTabContentView().getChildAt(1) instanceof ListView);
        child2 = (TextView) tabHost.getTabContentView().getChildAt(2);
        tabHost.setCurrentTab(0);
        assertEquals(TabHostCtsActivity.INITIAL_VIEW_TEXT, child2.getText().toString());
    }

    /**
     * Check points:
     * 1. the specified callback should be invoked when the selected state of any of the items
     * in this list changes
     */
    @UiThreadTest
    @Test
    public void testSetOnTabChangedListener() {
        TabHost tabHost = mActivity.getTabHost();

        // add a tab, and change current tab to the new tab
        OnTabChangeListener mockTabChangeListener = mock(OnTabChangeListener.class);
        tabHost.setOnTabChangedListener(mockTabChangeListener);

        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryList());
        tabHost.addTab(tabSpec);
        tabHost.setCurrentTab(1);
        verify(mockTabChangeListener, times(1)).onTabChanged(TAG_TAB2);

        // change current tab to the first one
        tabHost.setCurrentTab(0);
        verify(mockTabChangeListener, times(1)).onTabChanged(TabHostCtsActivity.INITIAL_TAB_TAG);

        // set the same tab
        tabHost.setCurrentTab(0);
        verifyNoMoreInteractions(mockTabChangeListener);
    }

    @UiThreadTest
    @Test
    public void testGetCurrentTabTag() {
        TabHost tabHost = mActivity.getTabHost();
        assertEquals(TabHostCtsActivity.INITIAL_TAB_TAG, tabHost.getCurrentTabTag());

        TabSpec tabSpec = tabHost.newTabSpec(TAG_TAB2);
        tabSpec.setIndicator(TAG_TAB2);
        tabSpec.setContent(new MyTabContentFactoryList());
        tabHost.addTab(tabSpec);
        tabHost.setCurrentTab(1);
        assertEquals(TAG_TAB2, tabHost.getCurrentTabTag());
    }

    private class MyTabContentFactoryText implements TabHost.TabContentFactory {
        public View createTabContent(String tag) {
            final TextView tv = new TextView(mActivity);
            tv.setText(tag);
            return tv;
        }
    }

    private class MyTabContentFactoryList implements TabHost.TabContentFactory {
        public View createTabContent(String tag) {
            final ListView lv = new ListView(mActivity);
            return lv;
        }
    }
}
