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
package android.support.car.app;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.car.Car;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentController;
import android.support.v4.app.FragmentHostCallback;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.util.SimpleArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for CarActivities that want to use the support-based Fragment and Loader APIs.
 * <p/>
 * This is mostly a copy of {@link android.support.v4.app.FragmentActivity} retro fitted for use
 * with {@link CarActivity}.
 *
 * @hide
 */
public class CarFragmentActivity extends CarActivity implements
        CarActivity.RequestPermissionsRequestCodeValidator {

    public CarFragmentActivity(Proxy proxy, Context context, Car car) {
        super(proxy, context, car);
    }

    private static final String TAG = "CarFragmentActivity";

    static final String FRAGMENTS_TAG = "android:support:car:fragments";

    // This is the SDK API version of Honeycomb (3.0).
    private static final int HONEYCOMB = 11;

    static final int MSG_REALLY_STOPPED = 1;
    static final int MSG_RESUME_PENDING = 2;

    final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REALLY_STOPPED:
                    if (mStopped) {
                        doReallyStop(false);
                    }
                    break;
                case MSG_RESUME_PENDING:
                    onResumeFragments();
                    mFragments.execPendingActions();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }

    };
    final FragmentController mFragments = FragmentController.createController(new HostCallbacks());

    boolean mCreated;
    boolean mResumed;
    boolean mStopped;
    boolean mReallyStopped;
    boolean mRetaining;

    boolean mRequestedPermissionsFromFragment;

    static final class NonConfigurationInstances {
        Object custom;
        List<Fragment> fragments;
        SimpleArrayMap<String, LoaderManager> loaders;
    }

    public void setContentFragment(Fragment fragment, int containerId) {
        getSupportFragmentManager().beginTransaction()
                .replace(containerId, fragment)
                .commit();
    }

    // ------------------------------------------------------------------------
    // HOOKS INTO ACTIVITY
    // ------------------------------------------------------------------------

    /**
     * See {@link FragmentActivity#onActivityResult(int, int, Intent)}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mFragments.noteStateNotSaved();
        int index = requestCode>>16;
        if (index > 0) {
            index--;
            final int activeFragmentsCount = mFragments.getActiveFragmentsCount();
            if (activeFragmentsCount == 0 || index < 0 || index >= activeFragmentsCount) {
                Log.w(TAG, "Activity result fragment index out of range: 0x"
                        + Integer.toHexString(requestCode));
                return;
            }
            final List<Fragment> activeFragments =
                    mFragments.getActiveFragments(new ArrayList<Fragment>(activeFragmentsCount));
            Fragment frag = activeFragments.get(index);
            if (frag == null) {
                Log.w(TAG, "Activity result no fragment exists for index: 0x"
                        + Integer.toHexString(requestCode));
            } else {
                frag.onActivityResult(requestCode&0xffff, resultCode, data);
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * See {@link FragmentActivity#startActivityFromFragment(android.app.Fragment, Intent, int)}
     */
    public void startActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
        if (requestCode == -1) {
            startActivityForResult(intent, -1);
            return;
        }
        if ((requestCode&0xffff0000) != 0) {
            throw new IllegalArgumentException("Can only use lower 16 bits for requestCode");
        }
        super.startActivityForResult(intent,
                ((getFragmentIndex(fragment)+1)<<16) + (requestCode&0xffff));
    }

    /**
     * See {@link FragmentActivity#startActivityForResult(Intent, int)}
     */
    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode != -1 && (requestCode&0xffff0000) != 0) {
            throw new IllegalArgumentException("Can only use lower 16 bits for requestCode");
        }
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * See {@link FragmentActivity#onBackPressed()}
     */
    @Override
    public void onBackPressed() {
        if (!mFragments.getSupportFragmentManager().popBackStackImmediate()) {
            supportFinishAfterTransition();
        }
    }

    /**
     * See {@link FragmentActivity#supportFinishAfterTransition()}
     */
    public void supportFinishAfterTransition() {
        super.finishAfterTransition();
    }

    /**
     * See {@link FragmentActivity#onConfigurationChanged(Configuration)}
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mFragments.dispatchConfigurationChanged(newConfig);
    }

    /**
     * Perform initialization of all fragments and loaders.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mFragments.attachHost(null /*parent*/);

        super.onCreate(savedInstanceState);

        NonConfigurationInstances nc =
                (NonConfigurationInstances) getLastNonConfigurationInstance();
        if (nc != null) {
            mFragments.restoreLoaderNonConfig(nc.loaders);
        }
        if (savedInstanceState != null) {
            Parcelable p = savedInstanceState.getParcelable(FRAGMENTS_TAG);
            mFragments.restoreAllState(p, nc != null ? nc.fragments : null);
        }
        mFragments.dispatchCreate();
    }

    /**
     * See {@link FragmentActivity#onCreatePanelMenu(int, Menu)}
     */
    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (featureId == Window.FEATURE_OPTIONS_PANEL && getMenuInflater() != null) {
            boolean show = super.onCreatePanelMenu(featureId, menu);
            show |= mFragments.dispatchCreateOptionsMenu(menu, getMenuInflater());
            if (android.os.Build.VERSION.SDK_INT >= HONEYCOMB) {
                return show;
            }
            // Prior to Honeycomb, the framework can't invalidate the options
            // menu, so we must always say we have one in case the app later
            // invalidates it and needs to have it shown.
            return true;
        }
        return super.onCreatePanelMenu(featureId, menu);
    }

    /**
     * See
     * {@link FragmentActivity#dispatchFragmentsOnCreateView(View, String, Context, AttributeSet)}
     */
    final View dispatchFragmentsOnCreateView(View parent, String name, Context context,
                                             AttributeSet attrs) {
        return mFragments.onCreateView(parent, name, context, attrs);
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        if (!"fragment".equals(name)) {
            return super.onCreateView(parent, name, context, attrs);
        }

        final View v = dispatchFragmentsOnCreateView(parent, name, context, attrs);
        if (v == null) {
            return super.onCreateView(parent, name, context, attrs);
        }
        return v;
    }

    /**
     * See {@link FragmentActivity#onDestroy()}
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        doReallyStop(false);

        mFragments.dispatchDestroy();
        mFragments.doLoaderDestroy();
    }

    /**
     * See {@link FragmentActivity#onLowMemory()}
     */
    @Override
    public void onLowMemory() {
        mFragments.dispatchLowMemory();
    }

    /**
     * See {@link FragmentActivity#onPause()}
     */
    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        if (mHandler.hasMessages(MSG_RESUME_PENDING)) {
            mHandler.removeMessages(MSG_RESUME_PENDING);
            onResumeFragments();
        }
        mFragments.dispatchPause();
    }

    /**
     * See {@link FragmentActivity#onNewIntent(Intent)}
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mFragments.noteStateNotSaved();
    }

    /**
     * See {@link FragmentActivity#onStateNotSaved()}
     */
    public void onStateNotSaved() {
        mFragments.noteStateNotSaved();
    }

    /**
     * See {@link FragmentActivity#onResume()}
     */
    @Override
    protected void onResume() {
        super.onResume();
        mHandler.sendEmptyMessage(MSG_RESUME_PENDING);
        mResumed = true;
        mFragments.execPendingActions();
    }

    /**
     * See {@link FragmentActivity#onPostResume()}
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        mHandler.removeMessages(MSG_RESUME_PENDING);
        onResumeFragments();
        mFragments.execPendingActions();
    }

    /**
     * See {@link FragmentActivity#onResumeFragments()}
     */
    protected void onResumeFragments() {
        mFragments.dispatchResume();
    }

    /**
     * See {@link FragmentActivity#onRetainNonConfigurationInstance()}
     */
    @Override
    public final Object onRetainNonConfigurationInstance() {
        if (mStopped) {
            doReallyStop(true);
        }

        Object custom = onRetainCustomNonConfigurationInstance();

        List<Fragment> fragments = mFragments.retainNonConfig();
        SimpleArrayMap<String, LoaderManager> loaders = mFragments.retainLoaderNonConfig();

        if (fragments == null && loaders == null && custom == null) {
            return null;
        }

        NonConfigurationInstances nci = new NonConfigurationInstances();
        nci.custom = custom;
        nci.fragments = fragments;
        nci.loaders = loaders;
        return nci;
    }

    /**
     * See {@link FragmentActivity#onSaveInstanceState(Bundle)}
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Parcelable p = mFragments.saveAllState();
        if (p != null) {
            outState.putParcelable(FRAGMENTS_TAG, p);
        }
    }

    /**
     * See {@link FragmentActivity#onStart()}
     */
    @Override
    protected void onStart() {
        super.onStart();

        mStopped = false;
        mReallyStopped = false;
        mHandler.removeMessages(MSG_REALLY_STOPPED);

        if (!mCreated) {
            mCreated = true;
            mFragments.dispatchActivityCreated();
        }

        mFragments.noteStateNotSaved();
        mFragments.execPendingActions();

        mFragments.doLoaderStart();

        // NOTE: HC onStart goes here.

        mFragments.dispatchStart();
        mFragments.reportLoaderStart();
    }

    /**
     * See {@link FragmentActivity#onStop()}
     */
    @Override
    protected void onStop() {
        super.onStop();

        mStopped = true;
        mHandler.sendEmptyMessage(MSG_REALLY_STOPPED);

        mFragments.dispatchStop();
    }

    // ------------------------------------------------------------------------
    // NEW METHODS
    // ------------------------------------------------------------------------

    /**
     * See {@link FragmentActivity#onRetainNonConfigurationInstance()}
     */
    public Object onRetainCustomNonConfigurationInstance() {
        return null;
    }

    /**
     * See {@link FragmentActivity#getLastNonConfigurationInstance()}
     */
    @SuppressWarnings("deprecation")
    public Object getLastCustomNonConfigurationInstance() {
        NonConfigurationInstances nc = (NonConfigurationInstances)
                getLastNonConfigurationInstance();
        return nc != null ? nc.custom : null;
    }

    /**
     * See {@link FragmentActivity#dump(String, FileDescriptor, PrintWriter, String[])}
     */
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (android.os.Build.VERSION.SDK_INT >= HONEYCOMB) {
            // XXX This can only work if we can call the super-class impl. :/
            //ActivityCompatHoneycomb.dump(this, prefix, fd, writer, args);
        }
        writer.print(prefix); writer.print("Local FragmentActivity ");
        writer.print(Integer.toHexString(System.identityHashCode(this)));
        writer.println(" State:");
        String innerPrefix = prefix + "  ";
        writer.print(innerPrefix); writer.print("mCreated=");
        writer.print(mCreated); writer.print("mResumed=");
        writer.print(mResumed); writer.print(" mStopped=");
        writer.print(mStopped); writer.print(" mReallyStopped=");
        writer.println(mReallyStopped);
        mFragments.dumpLoaders(innerPrefix, fd, writer, args);
        mFragments.getSupportFragmentManager().dump(prefix, fd, writer, args);
        writer.print(prefix); writer.println("View Hierarchy:");
        dumpViewHierarchy(prefix + "  ", writer, getWindow().getDecorView());
    }

    private static String viewToString(View view) {
        StringBuilder out = new StringBuilder(128);
        out.append(view.getClass().getName());
        out.append('{');
        out.append(Integer.toHexString(System.identityHashCode(view)));
        out.append(' ');
        switch (view.getVisibility()) {
            case View.VISIBLE: out.append('V'); break;
            case View.INVISIBLE: out.append('I'); break;
            case View.GONE: out.append('G'); break;
            default: out.append('.'); break;
        }
        out.append(view.isFocusable() ? 'F' : '.');
        out.append(view.isEnabled() ? 'E' : '.');
        out.append(view.willNotDraw() ? '.' : 'D');
        out.append(view.isHorizontalScrollBarEnabled()? 'H' : '.');
        out.append(view.isVerticalScrollBarEnabled() ? 'V' : '.');
        out.append(view.isClickable() ? 'C' : '.');
        out.append(view.isLongClickable() ? 'L' : '.');
        out.append(' ');
        out.append(view.isFocused() ? 'F' : '.');
        out.append(view.isSelected() ? 'S' : '.');
        out.append(view.isPressed() ? 'P' : '.');
        out.append(' ');
        out.append(view.getLeft());
        out.append(',');
        out.append(view.getTop());
        out.append('-');
        out.append(view.getRight());
        out.append(',');
        out.append(view.getBottom());
        final int id = view.getId();
        if (id != View.NO_ID) {
            out.append(" #");
            out.append(Integer.toHexString(id));
            final Resources r = view.getResources();
            if (id != 0 && r != null) {
                try {
                    String pkgname;
                    switch (id&0xff000000) {
                        case 0x7f000000:
                            pkgname="app";
                            break;
                        case 0x01000000:
                            pkgname="android";
                            break;
                        default:
                            pkgname = r.getResourcePackageName(id);
                            break;
                    }
                    String typename = r.getResourceTypeName(id);
                    String entryname = r.getResourceEntryName(id);
                    out.append(" ");
                    out.append(pkgname);
                    out.append(":");
                    out.append(typename);
                    out.append("/");
                    out.append(entryname);
                } catch (Resources.NotFoundException e) {
                }
            }
        }
        out.append("}");
        return out.toString();
    }

    private void dumpViewHierarchy(String prefix, PrintWriter writer, View view) {
        writer.print(prefix);
        if (view == null) {
            writer.println("null");
            return;
        }
        writer.println(viewToString(view));
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup grp = (ViewGroup)view;
        final int N = grp.getChildCount();
        if (N <= 0) {
            return;
        }
        prefix = prefix + "  ";
        for (int i=0; i<N; i++) {
            dumpViewHierarchy(prefix, writer, grp.getChildAt(i));
        }
    }

    void doReallyStop(boolean retaining) {
        if (!mReallyStopped) {
            mReallyStopped = true;
            mRetaining = retaining;
            mHandler.removeMessages(MSG_REALLY_STOPPED);
            onReallyStop();
        }
    }

    /**
     * Pre-HC, we didn't have a way to determine whether an activity was
     * being stopped for a config change or not until we saw
     * onRetainNonConfigurationInstance() called after onStop().  However
     * we need to know this, to know whether to retain fragments.  This will
     * tell us what we need to know.
     */
    void onReallyStop() {
        mFragments.doLoaderStop(mRetaining);

        mFragments.dispatchReallyStop();
    }

    // ------------------------------------------------------------------------
    // FRAGMENT SUPPORT
    // ------------------------------------------------------------------------

    /**
     * Returns the index of a fragment inside {@link #mFragments}.
     *
     * This is a workaround for getting {@link android.support.v4.app.Fragment}'s internal index,
     * which is package visible.
     */
    @SuppressWarnings("ReferenceEquality")
    private int getFragmentIndex(Fragment f) {
        List<Fragment> fragments = mFragments.getActiveFragments(null);
        int index = 0;
        boolean found = false;
        for (Fragment frag : fragments) {
            if (frag == f) {
                found = true;
                break;
            }
            index++;
        }
        if (found) {
            return index;
        }
        return -1;
    }

    @Override
    public final void validateRequestPermissionsRequestCode(int requestCode) {
        // We use 8 bits of the request code to encode the fragment id when
        // requesting permissions from a fragment. Hence, requestPermissions()
        // should validate the code against that but we cannot override it as
        // we can not then call super and also the ActivityCompat would call
        // back to this override. To handle this we use dependency inversion
        // where we are the validator of request codes when requesting
        // permissions in ActivityCompat.
        if (mRequestedPermissionsFromFragment) {
            mRequestedPermissionsFromFragment = false;
        } else if ((requestCode & 0xffffff00) != 0) {
            throw new IllegalArgumentException("Can only use lower 8 bits for requestCode");
        }
    }

    /**
     * See {@link FragmentActivity#onRequestPermissionsResult(int, String[], int[])}
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        int index = (requestCode>>8)&0xff;
        if (index != 0) {
            index--;
            final int activeFragmentsCount = mFragments.getActiveFragmentsCount();
            if (activeFragmentsCount == 0 || index < 0 || index >= activeFragmentsCount) {
                Log.w(TAG, "Activity result fragment index out of range: 0x"
                        + Integer.toHexString(requestCode));
                return;
            }
            final List<Fragment> activeFragments =
                    mFragments.getActiveFragments(new ArrayList<Fragment>(activeFragmentsCount));
            Fragment frag = activeFragments.get(index);
            if (frag == null) {
                Log.w(TAG, "Activity result no fragment exists for index: 0x"
                        + Integer.toHexString(requestCode));
            } else {
                frag.onRequestPermissionsResult(requestCode&0xff, permissions, grantResults);
            }
        }
    }

    /**
     * Called by Fragment.requestPermissions() to implement its behavior.
     */
    private void requestPermissionsFromFragment(Fragment fragment, String[] permissions,
                                                int requestCode) {
        if (requestCode == -1) {
            super.requestPermissions(permissions, requestCode);
            return;
        }

        if ((requestCode&0xffffff00) != 0) {
            throw new IllegalArgumentException("Can only use lower 8 bits for requestCode");
        }
        mRequestedPermissionsFromFragment = true;
        super.requestPermissions(permissions,
                ((getFragmentIndex(fragment) + 1) << 8) + (requestCode & 0xff));
    }

    /**
     * See {@link FragmentActivity#getSupportFragmentManager()}
     */
    public FragmentManager getSupportFragmentManager() {
        return mFragments.getSupportFragmentManager();
    }

    class HostCallbacks extends FragmentHostCallback<CarFragmentActivity> {
        public HostCallbacks() {
            super(CarFragmentActivity.this.getContext(), mHandler, 0 /*window animation*/);
        }

        @Override
        public void onDump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
            CarFragmentActivity.this.dump(prefix, fd, writer, args);
        }

        @Override
        public boolean onShouldSaveFragmentState(Fragment fragment) {
            return !isFinishing();
        }

        @Override
        public LayoutInflater onGetLayoutInflater() {
            return CarFragmentActivity.this.getLayoutInflater()
                    .cloneInContext(CarFragmentActivity.this.getContext());
        }

        @Override
        public void onStartActivityFromFragment(Fragment fragment, Intent intent, int requestCode) {
            CarFragmentActivity.this.startActivityFromFragment(fragment, intent, requestCode);
        }

        @Override
        public void onRequestPermissionsFromFragment(@NonNull Fragment fragment,
                @NonNull String[] permissions, int requestCode) {
            CarFragmentActivity.this.requestPermissionsFromFragment(fragment,
                    permissions, requestCode);
        }

        @Override
        public CarFragmentActivity onGetHost() {
            return CarFragmentActivity.this;
        }

        @Override
        public boolean onHasWindowAnimations() {
            return getWindow() != null;
        }

        @Override
        public int onGetWindowAnimations() {
            final Window w = getWindow();
            return (w == null) ? 0 : w.getAttributes().windowAnimations;
        }

        @Nullable
        @Override
        public View onFindViewById(int id) {
            return CarFragmentActivity.this.findViewById(id);
        }

        @Override
        public boolean onHasView() {
            final Window w = getWindow();
            return (w != null && w.peekDecorView() != null);
        }
    }
}
