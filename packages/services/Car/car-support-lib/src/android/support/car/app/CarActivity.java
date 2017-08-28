/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.LayoutRes;
import android.support.car.Car;
import android.support.car.app.menu.CarDrawerActivity;
import android.support.car.input.CarInputManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.Window;

/**
 * A car specific activity class.  It allows an application to run on both "projected" and
 * "native" platforms.  For the phone-only mode we only support media and messaging apps at this
 * time. Please see our guides for writing
 * <a href="https://developer.android.com/training/auto/index.html#media">media</a> and
 * <a href="https://developer.android.com/training/auto/index.html#messaging">messaging</a> apps.
 * <ul>
 *     <li>
 *         For "native" systems you'll additionally need to implement a
 *         {@link CarProxyActivity} and add it to your application's manifest (see
 *         {@link CarProxyActivity}) for details).
 *     </li>
 *     <li>
 *         For "projected" systems you'll need to implement a
 *         {@link com.google.android.apps.auto.sdk.activity.CarProxyProjectionActivityService}
 *     </li>
 * </ul>
 *
 * Applications wishing to write Android Auto applications will need to extend this class or one of
 * it's sub classes. You'll most likely want to use {@link CarFragmentActivity} or
 * {@link CarDrawerActivity} instead or this class as this one does not support fragments.
 * <p/>
 * This class has the look and feel of {@link Activity} however, it does not extend {@link Activity}
 * or {@link Context}. Applications should use {@link #getContext()} to access the {@link Context}.
 *
 * @hide
 */
public abstract class CarActivity {
    private static final String TAG = "CarActivity";
    public interface RequestPermissionsRequestCodeValidator {
        public void validateRequestPermissionsRequestCode(int requestCode);
    }
    /**
     * Interface to connect {@link CarActivity} to {@link android.app.Activity} or other app model.
     * This interface provides utility for {@link CarActivity} to do things like manipulating view,
     * handling menu, and etc.
     */
    public abstract static class Proxy {
        abstract public void setIntent(Intent i);
        abstract public void setContentView(View view);
        abstract public void setContentView(int layoutResID);
        abstract public Resources getResources();
        abstract public View findViewById(int id);
        abstract public LayoutInflater getLayoutInflater();
        abstract public Intent getIntent();
        abstract public void finish();
        abstract public CarInputManager getCarInputManager();
        abstract public boolean isFinishing();
        abstract public MenuInflater getMenuInflater();
        abstract public void finishAfterTransition();
        abstract public Window getWindow();
        abstract public void setResult(int resultCode);
        abstract public void setResult(int resultCode, Intent data);

        public void requestPermissions(String[] permissions, int requestCode) {
            Log.w(TAG, "No support for requestPermissions");
        }
        public boolean shouldShowRequestPermissionRationale(String permission) {
            Log.w(TAG, "No support for shouldShowRequestPermissionRationale");
            return false;
        }
        public void startActivityForResult(Intent intent, int requestCode) {
            Log.w(TAG, "No support for startActivityForResult");
        };
    }

    /** @hide */
    public static final int CMD_ON_CREATE = 0;
    /** @hide */
    public static final int CMD_ON_START = 1;
    /** @hide */
    public static final int CMD_ON_RESTART = 2;
    /** @hide */
    public static final int CMD_ON_RESUME = 3;
    /** @hide */
    public static final int CMD_ON_PAUSE = 4;
    /** @hide */
    public static final int CMD_ON_STOP = 5;
    /** @hide */
    public static final int CMD_ON_DESTROY = 6;
    /** @hide */
    public static final int CMD_ON_BACK_PRESSED = 7;
    /** @hide */
    public static final int CMD_ON_SAVE_INSTANCE_STATE = 8;
    /** @hide */
    public static final int CMD_ON_RESTORE_INSTANCE_STATE = 9;
    /** @hide */
    public static final int CMD_ON_CONFIG_CHANGED = 10;
    /** @hide */
    public static final int CMD_ON_REQUEST_PERMISSIONS_RESULT = 11;
    /** @hide */
    public static final int CMD_ON_NEW_INTENT = 12;
    /** @hide */
    public static final int CMD_ON_ACTIVITY_RESULT = 13;
    /** @hide */
    public static final int CMD_ON_POST_RESUME = 14;
    /** @hide */
    public static final int CMD_ON_LOW_MEMORY = 15;

    private final Proxy mProxy;
    private final Context mContext;
    private final Car mCar;
    private final Handler mHandler = new Handler();

    public CarActivity(Proxy proxy, Context context, Car car) {
        mProxy = proxy;
        mContext = context;
        mCar = car;
    }

    /**
     * Returns a standard app {@link Context} object since this class does not extend {@link
     * Context} link {@link Activity} does.
     */
    public Context getContext() {
        return mContext;
    }

    /**
     * Returns an instance of the {@link Car} object.  This is the main entry point to interact
     * with the car and it's data.
     *
     * <p/>
     * Note: For "native" platform uses cases you'll need to construct the CarProxy activity with
     * the createCar boolean set to true if you want to use the getCar() method.
     * <pre>
     * {@code
     *
     *   class FooProxyActivity extends CarProxyActivity {
     *     public FooProxyActivity() {
     *       super(FooActivity.class, true);
     *     }
     *   }
     * }
     * </pre>
     *
     * "Projected" use cases will create a Car instance by default.
     *
     * @throws IllegalStateException if the Car object is not available.
     */
    public Car getCar() {
        if (mCar == null) {
            throw new IllegalStateException("The default Car is not available. You can either " +
                    "create a Car by yourself or indicate the need of the default Car in the " +
                    "CarProxyActivity's constructor.");
        }
        return mCar;
    }

    /**
     * Returns the input manager for car activities.
     */
    public CarInputManager getInputManager() {
        return mProxy.getCarInputManager();
    }

    /**
     * See {@link Activity#getResources()}.
     */
    public Resources getResources() {
        return mProxy.getResources();
    }

    /**
     * See {@link Activity#setContentView(View)}.
     */
    public void setContentView(View view) {
        mProxy.setContentView(view);
    }

    /**
     * See {@link Activity#setContentView(int)}.
     */
    public void setContentView(@LayoutRes int resourceId) {
        mProxy.setContentView(resourceId);
    }

    /**
     * See {@link Activity#getLayoutInflater()}.
     */
    public LayoutInflater getLayoutInflater() {
        return mProxy.getLayoutInflater();
    }

    /**
     * See {@link Activity#getIntent()}
     */
    public Intent getIntent() {
        return mProxy.getIntent();
    }

    /**
     * See {@link Activity#setResult(int)}  }
     */
    public void setResult(int resultCode){
        mProxy.setResult(resultCode);
    }


    /**
     * See {@link Activity#setResult(int, Intent)}  }
     */
    public void setResult(int resultCode, Intent data) {
        mProxy.setResult(resultCode, data);
    }

    /**
     * See {@link Activity#findViewById(int)}  }
     */
    public View findViewById(int id) {
        return mProxy.findViewById(id);
    }

    /**
     * See {@link Activity#finish()}
     */
    public void finish() {
        mProxy.finish();
    }

    /**
     * See {@link Activity#isFinishing()}
     */
    public boolean isFinishing() {
        return mProxy.isFinishing();
    }

    /**
     * See {@link Activity#shouldShowRequestPermissionRationale(String)}
     */
    public boolean shouldShowRequestPermissionRationale(String permission) {
        return mProxy.shouldShowRequestPermissionRationale(permission);
    }

    /**
     * See {@link Activity#requestPermissions(String[], int)}
     */
    public void requestPermissions(String[] permissions, int requestCode) {
        if (this instanceof RequestPermissionsRequestCodeValidator) {
            ((RequestPermissionsRequestCodeValidator) this)
                    .validateRequestPermissionsRequestCode(requestCode);
        }
        mProxy.requestPermissions(permissions, requestCode);
    }

    /**
     * See {@link Activity#setIntent(Intent)}
     */
    public void setIntent(Intent i) {
        mProxy.setIntent(i);
    }

    /**
     * See {@link Activity#finishAfterTransition()}
     */
    public void finishAfterTransition() {
        mProxy.finishAfterTransition();
    }

    /**
     * See {@link Activity#getMenuInflater()}
     */
    public MenuInflater getMenuInflater() {
        return mProxy.getMenuInflater();
    }

    /**
     * See {@link Activity#getWindow()}
     */
    public Window getWindow() {
        return mProxy.getWindow();
    }

    /**
     * See {@link Activity#getLastNonConfigurationInstance()}
     */
    public Object getLastNonConfigurationInstance() {
        return null;
    }

    /**
     * See {@link Activity#startActivityForResult(Intent, int)}
     */
    public void startActivityForResult(Intent intent, int requestCode) {
        mProxy.startActivityForResult(intent, requestCode);
    }

    /**
     * See {@link Activity#runOnUiThread(Runnable)}
     */
    public void runOnUiThread(Runnable runnable) {
        if (Thread.currentThread() == mHandler.getLooper().getThread()) {
            runnable.run();
        } else {
            mHandler.post(runnable);
        }
    }

    /** @hide */
    public void dispatchCmd(int cmd, Object... args) {

        switch (cmd) {
            case CMD_ON_CREATE:
                assertArgsLength(1, args);
                onCreate((Bundle) args[0]);
                break;
            case CMD_ON_START:
                onStart();
                break;
            case CMD_ON_RESTART:
                onRestart();
                break;
            case CMD_ON_RESUME:
                onResume();
                break;
            case CMD_ON_POST_RESUME:
                onPostResume();
                break;
            case CMD_ON_PAUSE:
                onPause();
                break;
            case CMD_ON_STOP:
                onStop();
                break;
            case CMD_ON_DESTROY:
                onDestroy();
                break;
            case CMD_ON_BACK_PRESSED:
                onBackPressed();
                break;
            case CMD_ON_SAVE_INSTANCE_STATE:
                assertArgsLength(1, args);
                onSaveInstanceState((Bundle) args[0]);
                break;
            case CMD_ON_RESTORE_INSTANCE_STATE:
                assertArgsLength(1, args);
                onRestoreInstanceState((Bundle) args[0]);
                break;
            case CMD_ON_REQUEST_PERMISSIONS_RESULT:
                assertArgsLength(3, args);
                onRequestPermissionsResult(((Integer) args[0]).intValue(),
                        (String[]) args[1], convertArray((Integer[]) args[2]));
                break;
            case CMD_ON_CONFIG_CHANGED:
                assertArgsLength(1, args);
                onConfigurationChanged((Configuration) args[0]);
                break;
            case CMD_ON_NEW_INTENT:
                assertArgsLength(1, args);
                onNewIntent((Intent) args[0]);
                break;
            case CMD_ON_ACTIVITY_RESULT:
                assertArgsLength(3, args);
                onActivityResult(((Integer) args[0]).intValue(), ((Integer) args[1]).intValue(),
                        (Intent) args[2]);
                break;
            case CMD_ON_LOW_MEMORY:
                onLowMemory();
                break;
            default:
                throw new RuntimeException("Unknown dispatch cmd for CarActivity, " + cmd);
        }

    }

    /**
     * See {@link Activity#onCreate(Bundle)}
     */
    protected void onCreate(Bundle savedInstanceState) {
    }

    /**
     * See {@link Activity#onStart()}
     */
    protected void onStart() {
    }

    /**
     * See {@link Activity#onRestart()}
     */
    protected void onRestart() {
    }

    /**
     * See {@link Activity#onResume()}
     */
    protected void onResume() {
    }

    /**
     * See {@link Activity#onPostResume()}
     */
    protected void onPostResume() {
    }

    /**
     * See {@link Activity#onPause()}
     */
    protected void onPause() {
    }

    /**
     * See {@link Activity#onStop()}
     */
    protected void onStop() {
    }

    /**
     * See {@link Activity#onDestroy()}
     */
    protected void onDestroy() {
    }

    /**
     * See {@link Activity#onRestoreInstanceState(Bundle)}
     */
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
    }

    /**
     * See {@link Activity#onSaveInstanceState(Bundle)}
     */
    protected void onSaveInstanceState(Bundle outState) {
    }

    /**
     * See {@link Activity#onBackPressed()}
     */
    protected void onBackPressed() {
    }

    /**
     * See {@link Activity#onConfigurationChanged(Configuration)}
     */
    protected void onConfigurationChanged(Configuration newConfig) {
    }

    /**
     * See {@link Activity#onNewIntent(Intent)}
     */
    protected void onNewIntent(Intent intent) {
    }

    /**
     * See {@link Activity#onRequestPermissionsResult(int, String[], int[])}
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
    }

    /**
     * See {@link Activity#onActivityResult(int, int, Intent)}
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    /**
     * See {@link Activity#onRetainNonConfigurationInstance()}
     */
    public Object onRetainNonConfigurationInstance() {
        return null;
    }

    // TODO: hook up panel menu if it's needed in any apps.
    /**
     * Currently always returns false.
     * See {@link Activity#onCreatePanelMenu(int, Menu)}
     */
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return false; // default menu will not be displayed.
    }

    /**
     * See {@link Activity#onCreateView(View, String, Context, AttributeSet)}
     */
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        // CarFragmentActivity can override this to dispatch onCreateView to fragments
        return null;
    }

    /**
     * See {@link Activity#onLowMemory()}
     */
    public void onLowMemory() {
    }

    private void assertArgsLength(int length, Object... args) {
        if (args == null || args.length != length) {
            throw new IllegalArgumentException(
                    String.format("Wrong number of parameters. Expected: %d Actual: %d",
                            length, args == null ? 0 : args.length));
        }
    }

    private static int[] convertArray(Integer[] array) {
        int[] results = new int[array.length];
        for(int i = 0; i < results.length; i++) {
            results[i] = array[i].intValue();
        }
        return results;
    }
}
