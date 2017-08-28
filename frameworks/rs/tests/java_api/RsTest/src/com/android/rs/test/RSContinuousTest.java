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
 * limitations under the License.
 */

package com.android.rs.test;

import android.content.Context;
import android.os.RemoteException;
import android.support.test.rule.ActivityTestRule;
import android.test.suitebuilder.annotation.MediumTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * RsTest, functional test for platform RenderScript APIs.
 * To run the test, please use command
 *
 * adb shell am instrument -w com.android.rs.test/android.support.test.runner.AndroidJUnitRunner
 *
 */
public class RSContinuousTest {
    private Context mContext;

    @Rule
    // A rule to create stub activity for RenderScript context.
    public ActivityTestRule<RSContinuousTestActivity> mActivityRule = new ActivityTestRule(RSContinuousTestActivity.class);

    @Before
    public void before() throws RemoteException {
        mContext = mActivityRule.getActivity().getApplication().getApplicationContext();
    }

    @Test
    @MediumTest
    public void test_UT_alloc() {
        UT_alloc test = new UT_alloc(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_array_alloc() {
        UT_array_alloc test = new UT_array_alloc(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_array_init() {
        UT_array_init test = new UT_array_init(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_atomic() {
        UT_atomic test = new UT_atomic(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_bug_char() {
        UT_bug_char test = new UT_bug_char(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_check_dims() {
        UT_check_dims test = new UT_check_dims(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_clamp() {
        UT_clamp test = new UT_clamp(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_clamp_relaxed() {
        UT_clamp_relaxed test = new UT_clamp_relaxed(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_constant() {
        UT_constant test = new UT_constant(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_convert() {
        UT_convert test = new UT_convert(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_convert_relaxed() {
        UT_convert_relaxed test = new UT_convert_relaxed(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_copy_test() {
        UT_copy_test test = new UT_copy_test(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_ctxt_default() {
        UT_ctxt_default test = new UT_ctxt_default(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_element() {
        UT_element test = new UT_element(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_foreach() {
        UT_foreach test = new UT_foreach(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_foreach_bounds() {
        UT_foreach_bounds test = new UT_foreach_bounds(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_foreach_multi() {
        UT_foreach_multi test = new UT_foreach_multi(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_fp16() {
        UT_fp16 test = new UT_fp16(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_fp16_globals() {
        UT_fp16_globals test = new UT_fp16_globals(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_fp_mad() {
        UT_fp_mad test = new UT_fp_mad(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_int4() {
        UT_int4 test = new UT_int4(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_kernel() {
        UT_kernel test = new UT_kernel(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_kernel2d() {
        UT_kernel2d test = new UT_kernel2d(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_kernel2d_oldstyle() {
        UT_kernel2d_oldstyle test = new UT_kernel2d_oldstyle(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_kernel3d() {
        UT_kernel3d test = new UT_kernel3d(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_kernel_struct() {
        UT_kernel_struct test = new UT_kernel_struct(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_math() {
        UT_math test = new UT_math(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_math_agree() {
        UT_math_agree test = new UT_math_agree(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_math_conformance() {
        UT_math_conformance test = new UT_math_conformance(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_math_fp16() {
        UT_math_fp16 test = new UT_math_fp16(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_min() {
        UT_min test = new UT_min(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_noroot() {
        UT_noroot test = new UT_noroot(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_primitives() {
        UT_primitives test = new UT_primitives(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_reduce() {
        UT_reduce test = new UT_reduce(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_reduce_backward() {
        UT_reduce_backward test = new UT_reduce_backward(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_refcount() {
        UT_refcount test = new UT_refcount(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_rsdebug() {
        UT_rsdebug test = new UT_rsdebug(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_rstime() {
        UT_rstime test = new UT_rstime(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_rstypes() {
        UT_rstypes test = new UT_rstypes(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_sampler() {
        UT_sampler test = new UT_sampler(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_script_group2_float() {
        UT_script_group2_float test = new UT_script_group2_float(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_script_group2_gatherscatter() {
        UT_script_group2_gatherscatter test = new UT_script_group2_gatherscatter(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_script_group2_nochain() {
        UT_script_group2_nochain test = new UT_script_group2_nochain(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_script_group2_pointwise() {
        UT_script_group2_pointwise test = new UT_script_group2_pointwise(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_single_source_alloc() {
        UT_single_source_alloc test = new UT_single_source_alloc(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_single_source_ref_count() {
        UT_single_source_ref_count test = new UT_single_source_ref_count(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_single_source_script() {
        UT_single_source_script test = new UT_single_source_script(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_small_struct() {
        UT_small_struct test = new UT_small_struct(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_static_globals() {
        UT_static_globals test = new UT_static_globals(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_struct() {
        UT_struct test = new UT_struct(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_unsigned() {
        UT_unsigned test = new UT_unsigned(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }

    @Test
    @MediumTest
    public void test_UT_vector() {
        UT_vector test = new UT_vector(null, mContext);
        test.run();
        Assert.assertTrue(test.getResult() == UnitTest.TEST_PASSED);
    }
}
