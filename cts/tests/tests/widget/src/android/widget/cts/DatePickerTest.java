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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.content.Context;
import android.os.Parcelable;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.SparseArray;
import android.view.View;
import android.widget.DatePicker;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Test {@link DatePicker}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class DatePickerTest {
    private Activity mActivity;
    private DatePicker mDatePickerSpinnerMode;
    private DatePicker mDatePickerCalendarMode;

    @Rule
    public ActivityTestRule<DatePickerCtsActivity> mActivityRule =
            new ActivityTestRule<>(DatePickerCtsActivity.class);

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
        mDatePickerSpinnerMode = (DatePicker) mActivity.findViewById(R.id.date_picker_spinner_mode);
        mDatePickerCalendarMode =
                (DatePicker) mActivity.findViewById(R.id.date_picker_calendar_mode);
    }

    @Test
    public void testConstructor() {
        new DatePicker(mActivity);

        new DatePicker(mActivity, null);

        new DatePicker(mActivity, null, android.R.attr.datePickerStyle);

        new DatePicker(mActivity, null, 0, android.R.style.Widget_DeviceDefault_DatePicker);

        new DatePicker(mActivity, null, 0, android.R.style.Widget_Material_DatePicker);

        new DatePicker(mActivity, null, 0, android.R.style.Widget_Material_Light_DatePicker);
    }

    @UiThreadTest
    @Test
    public void testSetEnabled() {
        assertTrue(mDatePickerCalendarMode.isEnabled());

        mDatePickerCalendarMode.setEnabled(false);
        assertFalse(mDatePickerCalendarMode.isEnabled());

        mDatePickerCalendarMode.setEnabled(true);
        assertTrue(mDatePickerCalendarMode.isEnabled());
    }

    private void verifyInit(DatePicker datePicker) {
        final DatePicker.OnDateChangedListener mockDateChangeListener =
                mock(DatePicker.OnDateChangedListener.class);

        datePicker.init(2000, 10, 15, mockDateChangeListener);
        assertEquals(2000, datePicker.getYear());
        assertEquals(10, datePicker.getMonth());
        assertEquals(15, datePicker.getDayOfMonth());

        verifyZeroInteractions(mockDateChangeListener);
    }

    @UiThreadTest
    @Test
    public void testInit() {
        verifyInit(mDatePickerSpinnerMode);
        verifyInit(mDatePickerCalendarMode);
    }

    private void verifyAccessDate(DatePicker datePicker) {
        final DatePicker.OnDateChangedListener mockDateChangeListener =
                mock(DatePicker.OnDateChangedListener.class);

        datePicker.init(2000, 10, 15, mockDateChangeListener);
        assertEquals(2000, datePicker.getYear());
        assertEquals(10, datePicker.getMonth());
        assertEquals(15, datePicker.getDayOfMonth());
        verify(mockDateChangeListener, never()).onDateChanged(any(DatePicker.class), anyInt(),
                anyInt(), anyInt());

        datePicker.updateDate(1989, 9, 19);
        assertEquals(1989, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(19, datePicker.getDayOfMonth());
        verify(mockDateChangeListener, times(1)).onDateChanged(datePicker, 1989, 9, 19);

        verifyNoMoreInteractions(mockDateChangeListener);
    }

    @UiThreadTest
    @Test
    public void testAccessDate() {
        verifyAccessDate(mDatePickerSpinnerMode);
        verifyAccessDate(mDatePickerCalendarMode);
    }

    private void verifySetOnDateChangedListener(DatePicker datePicker) {
        final DatePicker.OnDateChangedListener mockDateChangeListener1 =
                mock(DatePicker.OnDateChangedListener.class);
        final DatePicker.OnDateChangedListener mockDateChangeListener2 =
                mock(DatePicker.OnDateChangedListener.class);

        datePicker.init(2000, 10, 15, mockDateChangeListener1);
        datePicker.updateDate(1989, 9, 19);
        assertEquals(1989, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(19, datePicker.getDayOfMonth());
        verify(mockDateChangeListener1, times(1)).onDateChanged(datePicker, 1989, 9, 19);
        verify(mockDateChangeListener2, times(0)).onDateChanged(datePicker, 1989, 9, 19);

        datePicker.setOnDateChangedListener(mockDateChangeListener2);
        datePicker.updateDate(2000, 10, 15);
        assertEquals(2000, datePicker.getYear());
        assertEquals(10, datePicker.getMonth());
        assertEquals(15, datePicker.getDayOfMonth());
        verify(mockDateChangeListener1, times(0)).onDateChanged(datePicker, 2000, 10, 15);
        verify(mockDateChangeListener2, times(1)).onDateChanged(datePicker, 2000, 10, 15);
    }

    @UiThreadTest
    @Test
    public void testSetOnDateChangedListener() {
        verifySetOnDateChangedListener(mDatePickerSpinnerMode);
        verifySetOnDateChangedListener(mDatePickerCalendarMode);
    }

    private void verifyUpdateDate(DatePicker datePicker) {
        datePicker.updateDate(1989, 9, 19);
        assertEquals(1989, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(19, datePicker.getDayOfMonth());
    }

    @UiThreadTest
    @Test
    public void testUpdateDate() {
        verifyUpdateDate(mDatePickerSpinnerMode);
        verifyUpdateDate(mDatePickerCalendarMode);
    }

    private void verifyMinMaxDate(DatePicker datePicker) {
        // Use a range of minus/plus one year as min/max dates
        final Calendar minCalendar = new GregorianCalendar();
        minCalendar.set(Calendar.YEAR, minCalendar.get(Calendar.YEAR) - 1);
        final Calendar maxCalendar = new GregorianCalendar();
        maxCalendar.set(Calendar.YEAR, maxCalendar.get(Calendar.YEAR) + 1);

        final long minDate = minCalendar.getTime().getTime();
        final long maxDate = maxCalendar.getTime().getTime();

        datePicker.setMinDate(minDate);
        datePicker.setMaxDate(maxDate);

        assertEquals(datePicker.getMinDate(), minDate);
        assertEquals(datePicker.getMaxDate(), maxDate);
    }

    @UiThreadTest
    @Test
    public void testMinMaxDate() {
        verifyMinMaxDate(mDatePickerSpinnerMode);
        verifyMinMaxDate(mDatePickerCalendarMode);
    }

    private void verifyFirstDayOfWeek(DatePicker datePicker) {
        datePicker.setFirstDayOfWeek(Calendar.TUESDAY);
        assertEquals(Calendar.TUESDAY, datePicker.getFirstDayOfWeek());

        datePicker.setFirstDayOfWeek(Calendar.SUNDAY);
        assertEquals(Calendar.SUNDAY, datePicker.getFirstDayOfWeek());
    }

    @UiThreadTest
    @Test
    public void testFirstDayOfWeek() {
        verifyFirstDayOfWeek(mDatePickerSpinnerMode);
        verifyFirstDayOfWeek(mDatePickerCalendarMode);
    }

    @UiThreadTest
    @Test
    public void testCalendarViewInSpinnerMode() {
        assertNotNull(mDatePickerSpinnerMode.getCalendarView());

        // Update the DatePicker and test that its CalendarView is synced to the same date
        final Calendar calendar = new GregorianCalendar();
        calendar.set(Calendar.YEAR, 2008);
        calendar.set(Calendar.MONTH, Calendar.SEPTEMBER);
        calendar.set(Calendar.DAY_OF_MONTH, 23);
        mDatePickerSpinnerMode.updateDate(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));

        final Calendar calendarFromSpinner = new GregorianCalendar();
        final long timeFromSpinnerCalendar = mDatePickerSpinnerMode.getCalendarView().getDate();
        calendarFromSpinner.setTimeInMillis(timeFromSpinnerCalendar);

        assertEquals(calendar.get(Calendar.YEAR), calendarFromSpinner.get(Calendar.YEAR));
        assertEquals(calendar.get(Calendar.MONTH), calendarFromSpinner.get(Calendar.MONTH));
        assertEquals(calendar.get(Calendar.DAY_OF_MONTH),
                calendarFromSpinner.get(Calendar.DAY_OF_MONTH));
    }

    @UiThreadTest
    @Test
    public void testPartsVisibilityInSpinnerMode() {
        assertTrue(mDatePickerSpinnerMode.getSpinnersShown());
        assertTrue(mDatePickerSpinnerMode.getCalendarViewShown());

        mDatePickerSpinnerMode.setSpinnersShown(false);
        assertFalse(mDatePickerSpinnerMode.getSpinnersShown());
        assertTrue(mDatePickerSpinnerMode.getCalendarViewShown());

        mDatePickerSpinnerMode.setCalendarViewShown(false);
        assertFalse(mDatePickerSpinnerMode.getSpinnersShown());
        assertFalse(mDatePickerSpinnerMode.getCalendarViewShown());

        mDatePickerSpinnerMode.setSpinnersShown(true);
        assertTrue(mDatePickerSpinnerMode.getSpinnersShown());
        assertFalse(mDatePickerSpinnerMode.getCalendarViewShown());

        mDatePickerSpinnerMode.setCalendarViewShown(true);
        assertTrue(mDatePickerSpinnerMode.getSpinnersShown());
        assertTrue(mDatePickerSpinnerMode.getCalendarViewShown());
    }

    @UiThreadTest
    @Test
    public void testAccessInstanceState() {
        MockDatePicker datePicker = new MockDatePicker(mActivity);

        datePicker.updateDate(2008, 9, 10);
        SparseArray<Parcelable> container = new SparseArray<Parcelable>();

        // Test saveHierarchyState -> onSaveInstanceState path
        assertEquals(View.NO_ID, datePicker.getId());
        datePicker.setId(99);
        assertFalse(datePicker.hasCalledOnSaveInstanceState());
        datePicker.saveHierarchyState(container);
        assertEquals(1, datePicker.getChildCount());
        assertTrue(datePicker.hasCalledOnSaveInstanceState());

        // Test dispatchRestoreInstanceState -> onRestoreInstanceState path
        datePicker = new MockDatePicker(mActivity);
        datePicker.setId(99);
        assertFalse(datePicker.hasCalledOnRestoreInstanceState());
        datePicker.dispatchRestoreInstanceState(container);
        assertEquals(2008, datePicker.getYear());
        assertEquals(9, datePicker.getMonth());
        assertEquals(10, datePicker.getDayOfMonth());
        assertTrue(datePicker.hasCalledOnRestoreInstanceState());
    }

    private class MockDatePicker extends DatePicker {
        private boolean mCalledOnSaveInstanceState = false;
        private boolean mCalledOnRestoreInstanceState = false;

        public MockDatePicker(Context context) {
            super(context);
        }

        @Override
        protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
            super.dispatchRestoreInstanceState(container);
        }

        @Override
        protected Parcelable onSaveInstanceState() {
            mCalledOnSaveInstanceState = true;
            return super.onSaveInstanceState();
        }

        public boolean hasCalledOnSaveInstanceState() {
            return mCalledOnSaveInstanceState;
        }

        @Override
        protected void onRestoreInstanceState(Parcelable state) {
            mCalledOnRestoreInstanceState = true;
            super.onRestoreInstanceState(state);
        }

        public boolean hasCalledOnRestoreInstanceState() {
            return mCalledOnRestoreInstanceState;
        }
    }
}
