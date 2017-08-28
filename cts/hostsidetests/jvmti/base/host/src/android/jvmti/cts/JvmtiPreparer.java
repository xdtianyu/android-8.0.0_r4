package android.jvmti.cts;

import com.android.compatibility.common.tradefed.targetprep.ApkInstaller;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.targetprep.TargetSetupError;
import java.io.File;

@OptionClass(alias="jvmti-installer")
public class JvmtiPreparer extends ApkInstaller {
    // We re-use test-file-name to find the APK. But we need to know the package name.
    @Option(name = "package-name",
            description = "The package name of the device test",
            mandatory = true)
    private String mPackageName = null;

    private String storedApkName;

    public final static String PACKAGE_NAME_ATTRIBUTE = "jvmti-package-name";
    public final static String APK_ATTRIBUTE = "jvmti-apk";

    @Override
    public void setUp(ITestDevice arg0, IBuildInfo arg1)
            throws TargetSetupError, DeviceNotAvailableException {
        super.setUp(arg0, arg1);

        arg1.addBuildAttribute(PACKAGE_NAME_ATTRIBUTE, mPackageName);
        arg1.addBuildAttribute(APK_ATTRIBUTE, storedApkName);
    }

    @Override
    protected File getLocalPathForFilename(IBuildInfo arg0, String arg1, ITestDevice arg2)
            throws TargetSetupError {
        storedApkName = arg1;
        return super.getLocalPathForFilename(arg0, arg1, arg2);
    }
}
