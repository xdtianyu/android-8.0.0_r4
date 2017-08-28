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

package com.android.storagemanager.deletionhelper;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView.BufferType;
import com.android.settingslib.widget.LinkTextView;
import com.android.storagemanager.ButtonBarProvider;
import com.android.storagemanager.R;

/**
 * The DeletionHelperActivity is an activity for deleting apps, photos, and downloaded files which
 * have not been recently used.
 */
public class DeletionHelperActivity extends Activity implements ButtonBarProvider {

    private ViewGroup mButtonBar;
    private Button mNextButton, mSkipButton;
    private DeletionHelperSettings mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_main_prefs);

        setIsEmptyState(false /* isEmptyState */);

        // If we are not returning from an existing activity, create a new fragment.
        if (savedInstanceState == null) {
            FragmentManager manager = getFragmentManager();
            mFragment = DeletionHelperSettings.newInstance(AppsAsyncLoader.NORMAL_THRESHOLD);
            manager.beginTransaction().replace(R.id.main_content, mFragment).commit();
        }
        SpannableString linkText =
                new SpannableString(
                        getString(R.string.empty_state_review_items_link).toUpperCase());
        LinkTextView emptyStateLink = (LinkTextView) findViewById(R.id.all_items_link);
        linkText = NoThresholdSpan.linkify(linkText, this);
        emptyStateLink.setText(linkText, BufferType.SPANNABLE);

        mButtonBar = (ViewGroup) findViewById(R.id.button_bar);
        mNextButton = (Button) findViewById(R.id.next_button);
        mSkipButton = (Button) findViewById(R.id.skip_button);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mFragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    void setIsEmptyState(boolean isEmptyState) {
        final View emptyContent = findViewById(R.id.empty_state);
        final View mainContent = findViewById(R.id.main_content);
        // Check if we need to animate now since we will modify visibility before the animation
        // starts
        final boolean shouldAnimate = isEmptyState && emptyContent.getVisibility() != View.VISIBLE;

        // Update UI
        mainContent.setVisibility(isEmptyState ? View.GONE : View.VISIBLE);
        emptyContent.setVisibility(isEmptyState ? View.VISIBLE : View.GONE);
        findViewById(R.id.button_bar).setVisibility(isEmptyState ? View.GONE : View.VISIBLE);
        setTitle(isEmptyState ? R.string.empty_state_title : R.string.deletion_helper_title);

        // Animate UI changes
        if (!shouldAnimate) {
            return;
        }
        animateToEmptyState();
    }

    private void animateToEmptyState() {
        View content = findViewById(R.id.empty_state);

        // Animate the empty state in
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        final float oldX = content.getTranslationX();
        content.setTranslationX(oldX + displayMetrics.widthPixels);
        content.animate()
                .translationX(oldX)
                .withEndAction(
                        () -> {
                            content.setTranslationX(oldX);
                        });
    }

    @Override
    public ViewGroup getButtonBar() {
        return mButtonBar;
    }

    @Override
    public Button getNextButton() {
        return mNextButton;
    }

    @Override
    public Button getSkipButton() {
        return mSkipButton;
    }

    private static class NoThresholdSpan extends ClickableSpan {
        private final DeletionHelperActivity mParent;

        public NoThresholdSpan(DeletionHelperActivity parent) {
            super();
            mParent = parent;
        }

        @Override
        public void onClick(View widget) {
            FragmentManager manager = mParent.getFragmentManager();
            Fragment fragment = DeletionHelperSettings.newInstance(AppsAsyncLoader.NO_THRESHOLD);
            manager.beginTransaction().replace(R.id.main_content, fragment).commit();
            mParent.setIsEmptyState(false);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            // Remove underline
            ds.setUnderlineText(false);
        }

        /**
         * This method takes a string and turns it into a url span that will launch a
         * SupportSystemInformationDialogFragment
         *
         * @param msg The text to turn into a link
         * @param parent The dialog the text is in
         * @return A CharSequence containing the original text content as a url
         */
        public static SpannableString linkify(SpannableString msg, DeletionHelperActivity parent) {
            NoThresholdSpan link = new NoThresholdSpan(parent);
            msg.setSpan(link, 0, msg.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return msg;
        }
    }
}