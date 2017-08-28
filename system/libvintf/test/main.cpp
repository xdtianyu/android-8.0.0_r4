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

#define LOG_TAG "LibHidlTest"

#include <functional>

#include <vintf/parse_string.h>
#include <vintf/parse_xml.h>
#include <vintf/CompatibilityMatrix.h>
#include <vintf/VintfObject.h>

#include <android-base/logging.h>
#include <android-base/parseint.h>
#include <gtest/gtest.h>

namespace android {
namespace vintf {

extern const XmlConverter<Version> &gVersionConverter;
extern const XmlConverter<ManifestHal> &gManifestHalConverter;
extern const XmlConverter<MatrixHal> &gMatrixHalConverter;
extern const XmlConverter<KernelConfigTypedValue> &gKernelConfigTypedValueConverter;
extern const XmlConverter<HalManifest> &gHalManifestConverter;
extern const XmlConverter<CompatibilityMatrix> &gCompatibilityMatrixConverter;

struct LibVintfTest : public ::testing::Test {
public:
    virtual void SetUp() override {
    }
    virtual void TearDown() override {
    }
    bool add(CompatibilityMatrix &cm, MatrixHal &&hal) {
        return cm.add(std::move(hal));
    }
    bool add(CompatibilityMatrix &cm, MatrixKernel &&kernel) {
        return cm.add(std::move(kernel));
    }
    bool add(HalManifest &vm, ManifestHal &&hal) {
        return vm.add(std::move(hal));
    }
    void set(CompatibilityMatrix &cm, Sepolicy &&sepolicy) {
        cm.framework.mSepolicy = sepolicy;
    }
    void set(CompatibilityMatrix &cm, SchemaType type) {
        cm.mType = type;
    }
    void set(CompatibilityMatrix &cm, VndkVersionRange &&range, std::set<std::string> &&libs) {
        cm.device.mVndk.mVersionRange = range;
        cm.device.mVndk.mLibraries = libs;
    }
    void setAvb(RuntimeInfo &ki, Version vbmeta, Version boot) {
        ki.mBootVbmetaAvbVersion = vbmeta;
        ki.mBootAvbVersion = boot;
    }
    void setAvb(CompatibilityMatrix &cm, Version &&avbVersion) {
        cm.framework.mAvbMetaVersion = avbVersion;
    }
    Version getAvb(CompatibilityMatrix &cm) {
        return cm.framework.mAvbMetaVersion;
    }
    const ManifestHal *getAnyHal(HalManifest &vm, const std::string &name) {
        return vm.getAnyHal(name);
    }
    MatrixHal *getAnyHal(CompatibilityMatrix &cm, const std::string &name) {
        return cm.getAnyHal(name);
    }
    ConstMultiMapValueIterable<std::string, ManifestHal> getHals(HalManifest &vm) {
        return vm.getHals();
    }
    bool isValid(const ManifestHal &mh) {
        return mh.isValid();
    }

    std::map<std::string, HalInterface> testHalInterfaces() {
        HalInterface intf;
        intf.name = "IFoo";
        intf.instances.insert("default");
        std::map<std::string, HalInterface> map;
        map[intf.name] = intf;
        return map;
    }

    HalManifest testDeviceManifest() {
        HalManifest vm;
        vm.mType = SchemaType::DEVICE;
        vm.device.mSepolicyVersion = {25, 0};
        vm.add(ManifestHal{
            .format = HalFormat::HIDL,
            .name = "android.hardware.camera",
            .versions = {Version(2, 0)},
            .transportArch = {Transport::HWBINDER, Arch::ARCH_EMPTY},
            .interfaces = {
                {"ICamera", {"ICamera", {"legacy/0", "default"}}},
                {"IBetterCamera", {"IBetterCamera", {"camera"}}}
            }
        });
        vm.add(ManifestHal{
            .format = HalFormat::HIDL,
            .name = "android.hardware.nfc",
            .versions = {Version(1, 0)},
            .transportArch = {Transport::PASSTHROUGH, Arch::ARCH_32_64},
            .interfaces = {
                {"INfc", {"INfc", {"default"}}}
            }
        });

        return vm;
    }
    HalManifest testFrameworkManfiest() {
        HalManifest vm;
        vm.mType = SchemaType::FRAMEWORK;
        vm.add(ManifestHal{
            .format = HalFormat::HIDL,
            .name = "android.hidl.manager",
            .versions = {Version(1, 0)},
            .transportArch = {Transport::HWBINDER, Arch::ARCH_EMPTY},
            .interfaces = {
                {"IServiceManager", {"IServiceManager", {"default"}}},
            }
        });
        Vndk vndk2505;
        vndk2505.mVersionRange = {25, 0, 5};
        vndk2505.mLibraries = { "libjpeg.so", "libbase.so" };
        Vndk vndk2513;
        vndk2513.mVersionRange = {25, 1, 3};
        vndk2513.mLibraries = { "libjpeg.so", "libbase.so", "libtinyxml2.so" };
        vm.framework.mVndks = { std::move(vndk2505), std::move(vndk2513) };

        return vm;
    }
    RuntimeInfo testRuntimeInfo() {
        RuntimeInfo info;
        info.mOsName = "Linux";
        info.mNodeName = "localhost";
        info.mOsRelease = "3.18.31-g936f9a479d0f";
        info.mKernelVersion = {3, 18, 31};
        info.mOsVersion = "#4 SMP PREEMPT Wed Feb 1 18:10:52 PST 2017";
        info.mHardwareId = "aarch64";
        info.mKernelSepolicyVersion = 30;
        info.mKernelConfigs = {
            {"CONFIG_64BIT", "y"},
            {"CONFIG_ANDROID_BINDER_DEVICES", "\"binder,hwbinder\""},
            {"CONFIG_ARCH_MMAP_RND_BITS", "24"},
            {"CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES", "\"\""},
            {"CONFIG_ILLEGAL_POINTER_VALUE", "0xdead000000000000"}
        };
        setAvb(info, {2, 1}, {2, 1});
        return info;
    }
};


TEST_F(LibVintfTest, Stringify) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(dump(vm), "hidl/android.hardware.camera/hwbinder/2.0:"
                        "hidl/android.hardware.nfc/passthrough32+64/1.0");

    EXPECT_EQ(to_string(HalFormat::HIDL), "hidl");
    EXPECT_EQ(to_string(HalFormat::NATIVE), "native");

    VersionRange v(1, 2, 3);
    EXPECT_EQ(to_string(v), "1.2-3");
    VersionRange v2;
    EXPECT_TRUE(parse("1.2-3", &v2));
    EXPECT_EQ(v, v2);
}

TEST_F(LibVintfTest, GetTransport) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(Transport::HWBINDER, vm.getTransport("android.hardware.camera",
            {2, 0}, "ICamera", "default"));
}

TEST_F(LibVintfTest, HalManifestConverter) {
    HalManifest vm = testDeviceManifest();
    std::string xml = gHalManifestConverter(vm);
    EXPECT_EQ(xml,
        "<manifest version=\"1.0\" type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>IBetterCamera</name>\n"
        "            <instance>camera</instance>\n"
        "        </interface>\n"
        "        <interface>\n"
        "            <name>ICamera</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>legacy/0</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport arch=\"32+64\">passthrough</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>25.0</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n");
    HalManifest vm2;
    EXPECT_TRUE(gHalManifestConverter(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestConverterFramework) {
    HalManifest vm = testFrameworkManfiest();
    std::string xml = gHalManifestConverter(vm);
    EXPECT_EQ(xml,
        "<manifest version=\"1.0\" type=\"framework\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hidl.manager</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IServiceManager</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <vndk>\n"
        "        <version>25.0.5</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "    </vndk>\n"
        "    <vndk>\n"
        "        <version>25.1.3</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "        <library>libtinyxml2.so</library>\n"
        "    </vndk>\n"
        "</manifest>\n");
    HalManifest vm2;
    EXPECT_TRUE(gHalManifestConverter(&vm2, xml));
    EXPECT_EQ(vm, vm2);
}

TEST_F(LibVintfTest, HalManifestOptional) {
    HalManifest vm;
    EXPECT_TRUE(gHalManifestConverter(&vm,
            "<manifest version=\"1.0\" type=\"device\"></manifest>"));
    EXPECT_TRUE(gHalManifestConverter(&vm,
            "<manifest version=\"1.0\" type=\"device\">"
            "    <hal>"
            "        <name>android.hidl.manager</name>"
            "        <transport>hwbinder</transport>"
            "        <version>1.0</version>"
            "    </hal>"
            "</manifest>"));
    EXPECT_FALSE(gHalManifestConverter(&vm,
            "<manifest version=\"1.0\" type=\"device\">"
            "    <hal>"
            "        <name>android.hidl.manager</name>"
            "        <version>1.0</version>"
            "    </hal>"
            "</manifest>"));
}

TEST_F(LibVintfTest, HalManifestDuplicate) {
    HalManifest vm;
    EXPECT_FALSE(gHalManifestConverter(&vm,
                                       "<manifest version=\"1.0\" type=\"device\">"
                                       "    <hal>"
                                       "        <name>android.hidl.manager</name>"
                                       "        <transport>hwbinder</transport>"
                                       "        <version>1.0</version>"
                                       "        <version>1.1</version>"
                                       "    </hal>"
                                       "</manifest>"))
        << "Should not allow duplicated major version in <hal>";
    EXPECT_FALSE(gHalManifestConverter(&vm,
                                       "<manifest version=\"1.0\" type=\"device\">"
                                       "    <hal>"
                                       "        <name>android.hidl.manager</name>"
                                       "        <transport>hwbinder</transport>"
                                       "        <version>1.0</version>"
                                       "    </hal>"
                                       "    <hal>"
                                       "        <name>android.hidl.manager</name>"
                                       "        <transport arch=\"32+64\">passthrough</transport>"
                                       "        <version>1.1</version>"
                                       "    </hal>"
                                       "</manifest>"))
        << "Should not allow duplicated major version across <hal>";
}

TEST_F(LibVintfTest, HalManifestGetTransport) {
    HalManifest vm;
    EXPECT_TRUE(gHalManifestConverter(&vm,
                                      "<manifest version=\"1.0\" type=\"device\">"
                                      "    <hal>"
                                      "        <name>android.hidl.manager</name>"
                                      "        <transport>hwbinder</transport>"
                                      "        <version>1.0</version>"
                                      "        <interface>"
                                      "            <name>IServiceManager</name>"
                                      "            <instance>default</instance>"
                                      "        </interface>"
                                      "    </hal>"
                                      "    <hal>"
                                      "        <name>android.hidl.manager</name>"
                                      "        <transport arch=\"32+64\">passthrough</transport>"
                                      "        <version>2.1</version>"
                                      "        <interface>"
                                      "            <name>IServiceManager</name>"
                                      "            <instance>default</instance>"
                                      "        </interface>"
                                      "    </hal>"
                                      "</manifest>"));
    EXPECT_EQ(Transport::PASSTHROUGH,
              vm.getTransport("android.hidl.manager", {2, 1}, "IServiceManager", "default"));
    EXPECT_EQ(Transport::PASSTHROUGH,
              vm.getTransport("android.hidl.manager", {2, 0}, "IServiceManager", "default"));
    EXPECT_EQ(Transport::EMPTY,
              vm.getTransport("android.hidl.manager", {2, 2}, "IServiceManager", "default"));
    EXPECT_EQ(Transport::HWBINDER,
              vm.getTransport("android.hidl.manager", {1, 0}, "IServiceManager", "default"));
}

TEST_F(LibVintfTest, HalManifestInstances) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(vm.getInstances("android.hardware.camera", "ICamera"),
            std::set<std::string>({"default", "legacy/0"}));
    EXPECT_EQ(vm.getInstances("android.hardware.camera", "IBetterCamera"),
            std::set<std::string>({"camera"}));
    EXPECT_EQ(vm.getInstances("android.hardware.camera", "INotExist"),
            std::set<std::string>({}));
    EXPECT_EQ(vm.getInstances("android.hardware.nfc", "INfc"),
            std::set<std::string>({"default"}));

    EXPECT_TRUE(vm.hasInstance("android.hardware.camera", "ICamera", "default"));
    EXPECT_TRUE(vm.hasInstance("android.hardware.camera", "ICamera", "legacy/0"));
    EXPECT_TRUE(vm.hasInstance("android.hardware.camera", "IBetterCamera", "camera"));
    EXPECT_TRUE(vm.hasInstance("android.hardware.nfc", "INfc", "default"));

    EXPECT_FALSE(vm.hasInstance("android.hardware.camera", "INotExist", "default"));
    EXPECT_FALSE(vm.hasInstance("android.hardware.camera", "ICamera", "notexist"));
    EXPECT_FALSE(vm.hasInstance("android.hardware.camera", "IBetterCamera", "default"));
    EXPECT_FALSE(vm.hasInstance("android.hardware.camera", "INotExist", "notexist"));
    EXPECT_FALSE(vm.hasInstance("android.hardware.nfc", "INfc", "notexist"));
}

TEST_F(LibVintfTest, VersionConverter) {
    Version v(3, 6);
    std::string xml = gVersionConverter(v);
    EXPECT_EQ(xml, "<version>3.6</version>\n");
    Version v2;
    EXPECT_TRUE(gVersionConverter(&v2, xml));
    EXPECT_EQ(v, v2);
}

static bool insert(std::map<std::string, HalInterface>* map, HalInterface&& intf) {
    std::string name{intf.name};
    return map->emplace(std::move(name), std::move(intf)).second;
}

TEST_F(LibVintfTest, MatrixHalConverter) {
    MatrixHal mh{HalFormat::NATIVE, "android.hardware.camera",
            {{VersionRange(1,2,3), VersionRange(4,5,6)}},
            false /* optional */, {}};
    EXPECT_TRUE(insert(&mh.interfaces, {"IBetterCamera", {"default", "great"}}));
    EXPECT_TRUE(insert(&mh.interfaces, {"ICamera", {"default"}}));
    std::string xml = gMatrixHalConverter(mh);
    EXPECT_EQ(xml,
        "<hal format=\"native\" optional=\"false\">\n"
        "    <name>android.hardware.camera</name>\n"
        "    <version>1.2-3</version>\n"
        "    <version>4.5-6</version>\n"
        "    <interface>\n"
        "        <name>IBetterCamera</name>\n"
        "        <instance>default</instance>\n"
        "        <instance>great</instance>\n"
        "    </interface>\n"
        "    <interface>\n"
        "        <name>ICamera</name>\n"
        "        <instance>default</instance>\n"
        "    </interface>\n"
        "</hal>\n");
    MatrixHal mh2;
    EXPECT_TRUE(gMatrixHalConverter(&mh2, xml));
    EXPECT_EQ(mh, mh2);
}

TEST_F(LibVintfTest, KernelConfigTypedValueConverter) {

    KernelConfigTypedValue converted;

    auto testOne = [] (const KernelConfigTypedValue &original,
                    const std::string &expectXml) {
        std::string xml;
        KernelConfigTypedValue converted;
        xml = gKernelConfigTypedValueConverter(original);
        EXPECT_EQ(xml, expectXml);
        EXPECT_TRUE(gKernelConfigTypedValueConverter(&converted, xml));
        EXPECT_EQ(original, converted);
    };

    auto testParse = [] (const KernelConfigTypedValue &original,
                    const std::string &xml) {
        KernelConfigTypedValue converted;
        EXPECT_TRUE(gKernelConfigTypedValueConverter(&converted, xml));
        EXPECT_EQ(original, converted);
    };

    testOne(KernelConfigTypedValue("stringvalue"),
        "<value type=\"string\">stringvalue</value>\n");
    testOne(KernelConfigTypedValue(""),
        "<value type=\"string\"></value>\n");

    testOne(KernelConfigTypedValue(Tristate::YES),
        "<value type=\"tristate\">y</value>\n");
    testOne(KernelConfigTypedValue(Tristate::NO),
        "<value type=\"tristate\">n</value>\n");
    testOne(KernelConfigTypedValue(Tristate::MODULE),
        "<value type=\"tristate\">m</value>\n");
    EXPECT_FALSE(gKernelConfigTypedValueConverter(&converted,
        "<value type=\"tristate\">q</value>\n"));

    testOne(KernelConfigTypedValue(KernelConfigRangeValue{4, 20}),
        "<value type=\"range\">4-20</value>\n");
    testOne(KernelConfigTypedValue(KernelConfigRangeValue{0, UINT64_MAX}),
        "<value type=\"range\">0-18446744073709551615</value>\n");
    testParse(KernelConfigTypedValue(KernelConfigRangeValue{0, UINT64_MAX}),
            "<value type=\"range\">0x0-0xffffffffffffffff</value>\n");

    EXPECT_FALSE(gKernelConfigTypedValueConverter(&converted,
            "<value type=\"int\">-18446744073709551616</value>\n"));

    testOne(KernelConfigTypedValue(INT64_MIN),
         "<value type=\"int\">-9223372036854775808</value>\n");
    testParse(KernelConfigTypedValue(INT64_MIN),
            "<value type=\"int\">0x8000000000000000</value>\n");
    testParse(KernelConfigTypedValue(INT64_MIN),
            "<value type=\"int\">-0X8000000000000000</value>\n");

    testParse(KernelConfigTypedValue(INT64_MIN + 1),
            "<value type=\"int\">-0X7FFFFFFFFFFFFFFF</value>\n");

    testParse(KernelConfigTypedValue(-0x50),
            "<value type=\"int\">-0x50</value>\n");

    testOne(KernelConfigTypedValue(0),
         "<value type=\"int\">0</value>\n");

    // Truncation for underflow.
    testParse(KernelConfigTypedValue(1),
            "<value type=\"int\">-0xffffffffffffffff</value>\n");
    testParse(KernelConfigTypedValue(1),
            "<value type=\"int\">-18446744073709551615</value>\n");

    testOne(KernelConfigTypedValue(INT64_MAX),
         "<value type=\"int\">9223372036854775807</value>\n");
    testParse(KernelConfigTypedValue(INT64_MAX),
            "<value type=\"int\">0x7FFFFFFFFFFFFFFF</value>\n");
    // Truncation for underflow.
    testParse(KernelConfigTypedValue(INT64_MAX),
            "<value type=\"int\">-9223372036854775809</value>\n");

    testParse(KernelConfigTypedValue(-1),
            "<value type=\"int\">18446744073709551615</value>\n");
    testParse(KernelConfigTypedValue(-1),
            "<value type=\"int\">0xffffffffffffffff</value>\n");

    EXPECT_FALSE(gKernelConfigTypedValueConverter(&converted,
            "<value type=\"int\">18446744073709551616</value>\n"));
}

TEST_F(LibVintfTest, CompatibilityMatrixConverter) {
    CompatibilityMatrix cm;
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE, "android.hardware.camera",
            {{VersionRange(1,2,3), VersionRange(4,5,6)}},
            false /* optional */, testHalInterfaces()}));
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE, "android.hardware.nfc",
            {{VersionRange(4,5,6), VersionRange(10,11,12)}},
            true /* optional */, testHalInterfaces()}));
    EXPECT_TRUE(add(cm, MatrixKernel{KernelVersion(3, 18, 22),
            {KernelConfig{"CONFIG_FOO", Tristate::YES}, KernelConfig{"CONFIG_BAR", "stringvalue"}}}));
    EXPECT_TRUE(add(cm, MatrixKernel{KernelVersion(4, 4, 1),
            {KernelConfig{"CONFIG_BAZ", 20}, KernelConfig{"CONFIG_BAR", KernelConfigRangeValue{3, 5} }}}));
    set(cm, Sepolicy(30, {{25, 0}, {26, 0, 3}}));
    setAvb(cm, Version{2, 1});
    std::string xml = gCompatibilityMatrixConverter(cm);
    EXPECT_EQ(xml,
            "<compatibility-matrix version=\"1.0\" type=\"framework\">\n"
            "    <hal format=\"native\" optional=\"false\">\n"
            "        <name>android.hardware.camera</name>\n"
            "        <version>1.2-3</version>\n"
            "        <version>4.5-6</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <hal format=\"native\" optional=\"true\">\n"
            "        <name>android.hardware.nfc</name>\n"
            "        <version>4.5-6</version>\n"
            "        <version>10.11-12</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <kernel version=\"3.18.22\">\n"
            "        <config>\n"
            "            <key>CONFIG_FOO</key>\n"
            "            <value type=\"tristate\">y</value>\n"
            "        </config>\n"
            "        <config>\n"
            "            <key>CONFIG_BAR</key>\n"
            "            <value type=\"string\">stringvalue</value>\n"
            "        </config>\n"
            "    </kernel>\n"
            "    <kernel version=\"4.4.1\">\n"
            "        <config>\n"
            "            <key>CONFIG_BAZ</key>\n"
            "            <value type=\"int\">20</value>\n"
            "        </config>\n"
            "        <config>\n"
            "            <key>CONFIG_BAR</key>\n"
            "            <value type=\"range\">3-5</value>\n"
            "        </config>\n"
            "    </kernel>\n"
            "    <sepolicy>\n"
            "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
            "        <sepolicy-version>25.0</sepolicy-version>\n"
            "        <sepolicy-version>26.0-3</sepolicy-version>\n"
            "    </sepolicy>\n"
            "    <avb>\n"
            "        <vbmeta-version>2.1</vbmeta-version>\n"
            "    </avb>\n"
            "</compatibility-matrix>\n");
    CompatibilityMatrix cm2;
    EXPECT_TRUE(gCompatibilityMatrixConverter(&cm2, xml));
    EXPECT_EQ(cm, cm2);
}

TEST_F(LibVintfTest, DeviceCompatibilityMatrixCoverter) {
    CompatibilityMatrix cm;
    EXPECT_TRUE(add(cm, MatrixHal{HalFormat::NATIVE, "android.hidl.manager",
            {{VersionRange(1,0)}},
            false /* optional */, testHalInterfaces()}));
    set(cm, SchemaType::DEVICE);
    set(cm, VndkVersionRange{25,0,1,5}, {"libjpeg.so", "libbase.so"});
    std::string xml = gCompatibilityMatrixConverter(cm);
    EXPECT_EQ(xml,
        "<compatibility-matrix version=\"1.0\" type=\"device\">\n"
        "    <hal format=\"native\" optional=\"false\">\n"
        "        <name>android.hidl.manager</name>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>IFoo</name>\n"
        "            <instance>default</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <vndk>\n"
        "        <version>25.0.1-5</version>\n"
        "        <library>libbase.so</library>\n"
        "        <library>libjpeg.so</library>\n"
        "    </vndk>\n"
        "</compatibility-matrix>\n");
    CompatibilityMatrix cm2;
    EXPECT_TRUE(gCompatibilityMatrixConverter(&cm2, xml));
    EXPECT_EQ(cm, cm2);
}

TEST_F(LibVintfTest, IsValid) {
    EXPECT_TRUE(isValid(ManifestHal()));

    ManifestHal invalidHal{
        .format = HalFormat::HIDL,
        .name = "android.hardware.camera",
        .versions = {{Version(2, 0), Version(2, 1)}},
        .transportArch = {Transport::PASSTHROUGH, Arch::ARCH_32_64}
    };

    EXPECT_FALSE(isValid(invalidHal));
    HalManifest vm2;
    EXPECT_FALSE(add(vm2, std::move(invalidHal)));
}

TEST_F(LibVintfTest, HalManifestGetHalNames) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(vm.getHalNames(), std::set<std::string>(
                  {"android.hardware.camera", "android.hardware.nfc"}));
}

TEST_F(LibVintfTest, HalManifestGetInterfaceNames) {
    HalManifest vm = testDeviceManifest();
    EXPECT_EQ(vm.getInterfaceNames("android.hardware.camera"),
              std::set<std::string>({"ICamera", "IBetterCamera"}));
    EXPECT_EQ(vm.getInterfaceNames("android.hardware.nfc"),
              std::set<std::string>({"INfc"}));
}

TEST_F(LibVintfTest, HalManifestGetHal) {
    HalManifest vm = testDeviceManifest();
    EXPECT_NE(getAnyHal(vm, "android.hardware.camera"), nullptr);
    EXPECT_EQ(getAnyHal(vm, "non-existent"), nullptr);

    std::vector<std::string> arr{"android.hardware.camera", "android.hardware.nfc"};
    size_t i = 0;
    for (const auto &hal : getHals(vm)) {
        EXPECT_EQ(hal.name, arr[i++]);
    }
}

TEST_F(LibVintfTest, RuntimeInfo) {
    RuntimeInfo ki = testRuntimeInfo();
    using KernelConfigs = std::vector<KernelConfig>;
    const KernelConfigs configs {
            KernelConfig{"CONFIG_64BIT", Tristate::YES},
            KernelConfig{"CONFIG_ANDROID_BINDER_DEVICES", "binder,hwbinder"},
            KernelConfig{"CONFIG_ARCH_MMAP_RND_BITS", 24},
            KernelConfig{"CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES", ""},
            KernelConfig{"CONFIG_ILLEGAL_POINTER_VALUE", 0xdead000000000000},
            KernelConfig{"CONFIG_NOTEXIST", Tristate::NO},
    };

    auto testMatrix = [&] (MatrixKernel &&kernel) {
        CompatibilityMatrix cm;
        add(cm, std::move(kernel));
        set(cm, {30, {{25, 0}}});
        setAvb(cm, {2, 1});
        return cm;
    };

    std::string error;

    {
        MatrixKernel kernel(KernelVersion{4, 4, 1}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Kernel version shouldn't match";
    }

    {
        MatrixKernel kernel(KernelVersion{3, 18, 22}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_TRUE(ki.checkCompatibility(cm, &error)) << error;
    }

    {
        MatrixKernel kernel(KernelVersion{3, 18, 22}, KernelConfigs(configs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        set(cm, Sepolicy{22, {{25, 0}}});
        EXPECT_FALSE(ki.checkCompatibility(cm, &error))
            << "kernel-sepolicy-version shouldn't match";
        set(cm, Sepolicy{40, {{25, 0}}});
        EXPECT_FALSE(ki.checkCompatibility(cm, &error))
            << "kernel-sepolicy-version shouldn't match";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[0] = KernelConfig{"CONFIG_64BIT", Tristate::NO};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Value shouldn't match for tristate";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[0] = KernelConfig{"CONFIG_64BIT", 20};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Type shouldn't match";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[1] = KernelConfig{"CONFIG_ANDROID_BINDER_DEVICES", "binder"};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Value shouldn't match for string";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[1] = KernelConfig{"CONFIG_ANDROID_BINDER_DEVICES", Tristate::YES};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Type shouldn't match";
    }

    {
        KernelConfigs newConfigs(configs);
        newConfigs[2] = KernelConfig{"CONFIG_ARCH_MMAP_RND_BITS", 30};
        MatrixKernel kernel(KernelVersion{3, 18, 22}, std::move(newConfigs));
        CompatibilityMatrix cm = testMatrix(std::move(kernel));
        EXPECT_FALSE(ki.checkCompatibility(cm)) << "Value shouldn't match for integer";
    }

    RuntimeInfo badAvb = testRuntimeInfo();
    CompatibilityMatrix cm = testMatrix(MatrixKernel(KernelVersion{3, 18, 31}, {}));
    {
        setAvb(badAvb, {1, 0}, {2, 1});
        EXPECT_FALSE(badAvb.checkCompatibility(cm, &error));
        EXPECT_STREQ(error.c_str(), "Vbmeta version 1.0 does not match framework matrix 2.1");
    }
    {
        setAvb(badAvb, {2, 1}, {3, 0});
        EXPECT_FALSE(badAvb.checkCompatibility(cm, &error));
    }
    {
        setAvb(badAvb, {2, 1}, {2, 3});
        EXPECT_TRUE(badAvb.checkCompatibility(cm, &error));
    }
    {
        setAvb(badAvb, {2, 3}, {2, 1});
        EXPECT_TRUE(badAvb.checkCompatibility(cm, &error));
    }
}

TEST_F(LibVintfTest, MissingAvb) {
    std::string xml =
        "<compatibility-matrix version=\"1.0\" type=\"framework\">\n"
        "    <kernel version=\"3.18.31\"></kernel>"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "    </sepolicy>\n"
        "</compatibility-matrix>\n";
    CompatibilityMatrix cm;
    EXPECT_TRUE(gCompatibilityMatrixConverter(&cm, xml));
    EXPECT_EQ(getAvb(cm), Version(0, 0));
}

// This is the test extracted from VINTF Object doc
TEST_F(LibVintfTest, HalCompat) {
    CompatibilityMatrix matrix;
    std::string error;

    std::string matrixXml =
            "<compatibility-matrix version=\"1.0\" type=\"framework\">\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>1.0</version>\n"
            "        <version>3.1-2</version>\n"
            "        <interface>\n"
            "            <name>IFoo</name>\n"
            "            <instance>default</instance>\n"
            "            <instance>specific</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <hal format=\"hidl\" optional=\"false\">\n"
            "        <name>android.hardware.foo</name>\n"
            "        <version>2.0</version>\n"
            "        <interface>\n"
            "            <name>IBar</name>\n"
            "            <instance>default</instance>\n"
            "        </interface>\n"
            "    </hal>\n"
            "    <sepolicy>\n"
            "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
            "        <sepolicy-version>25.5</sepolicy-version>\n"
            "    </sepolicy>\n"
            "</compatibility-matrix>\n";
    EXPECT_TRUE(gCompatibilityMatrixConverter(&matrix, matrixXml))
            << gCompatibilityMatrixConverter.lastError();

    {
        std::string manifestXml =
                "<manifest version=\"1.0\" type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";

        HalManifest manifest;
        EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    }

    {
        std::string manifestXml =
                "<manifest version=\"1.0\" type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
                << "should not be compatible because IBar is missing";
    }

    {
        std::string manifestXml =
                "<manifest version=\"1.0\" type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
                << "should not be compatible because IFoo/default is missing";
    }

    {
        std::string manifestXml =
                "<manifest version=\"1.0\" type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>3.3</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml));
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    }

    {
        std::string manifestXml =
                "<manifest version=\"1.0\" type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>3.2</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml));
        EXPECT_FALSE(manifest.checkCompatibility(matrix, &error))
                << "should not be compatible even though @1.0::IFoo/default "
                << "and @3.2::IFoo/specific present";
    }

    {
        std::string manifestXml =
                "<manifest version=\"1.0\" type=\"device\">\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>1.0</version>\n"
                "        <interface>\n"
                "            <name>IFoo</name>\n"
                "            <instance>default</instance>\n"
                "            <instance>specific</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <hal format=\"hidl\">\n"
                "        <name>android.hardware.foo</name>\n"
                "        <transport>hwbinder</transport>\n"
                "        <version>2.0</version>\n"
                "        <interface>\n"
                "            <name>IBar</name>\n"
                "            <instance>default</instance>\n"
                "        </interface>\n"
                "    </hal>\n"
                "    <sepolicy>\n"
                "        <version>25.5</version>\n"
                "    </sepolicy>\n"
                "</manifest>\n";
        HalManifest manifest;
        EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml))
            << gHalManifestConverter.lastError();
        EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    }
}

TEST_F(LibVintfTest, Compat) {
    std::string manifestXml =
        "<manifest version=\"1.0\" type=\"device\">\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>3.5</version>\n"
        "        <interface>\n"
        "            <name>IBetterCamera</name>\n"
        "            <instance>camera</instance>\n"
        "        </interface>\n"
        "        <interface>\n"
        "            <name>ICamera</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>legacy/0</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>1.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>nfc_nci</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <transport>hwbinder</transport>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>nfc_nci</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <version>25.5</version>\n"
        "    </sepolicy>\n"
        "</manifest>\n";

    std::string matrixXml =
        "<compatibility-matrix version=\"1.0\" type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <version>2.0-5</version>\n"
        "        <version>3.4-16</version>\n"
        "        <interface>\n"
        "            <name>IBetterCamera</name>\n"
        "            <instance>camera</instance>\n"
        "        </interface>\n"
        "        <interface>\n"
        "            <name>ICamera</name>\n"
        "            <instance>default</instance>\n"
        "            <instance>legacy/0</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.nfc</name>\n"
        "        <version>1.0</version>\n"
        "        <version>2.0</version>\n"
        "        <interface>\n"
        "            <name>INfc</name>\n"
        "            <instance>nfc_nci</instance>\n"
        "        </interface>\n"
        "    </hal>\n"
        "    <hal format=\"hidl\" optional=\"true\">\n"
        "        <name>android.hardware.foo</name>\n"
        "        <version>1.0</version>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "        <sepolicy-version>26.0-3</sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb>\n"
        "        <vbmeta-version>2.1</vbmeta-version>\n"
        "    </avb>\n"
        "</compatibility-matrix>\n";

    HalManifest manifest;
    CompatibilityMatrix matrix;
    std::string error;
    EXPECT_TRUE(gHalManifestConverter(&manifest, manifestXml));
    EXPECT_TRUE(gCompatibilityMatrixConverter(&matrix, matrixXml));
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;

    // some smaller test cases
    matrixXml =
        "<compatibility-matrix version=\"1.0\" type=\"framework\">\n"
        "    <hal format=\"hidl\" optional=\"false\">\n"
        "        <name>android.hardware.camera</name>\n"
        "        <version>3.4</version>\n"
        "    </hal>\n"
        "    <sepolicy>\n"
        "        <kernel-sepolicy-version>30</kernel-sepolicy-version>\n"
        "        <sepolicy-version>25.5</sepolicy-version>\n"
        "    </sepolicy>\n"
        "    <avb><vbmeta-version>2.1</vbmeta-version></avb>\n"
        "</compatibility-matrix>\n";
    matrix = {};
    EXPECT_TRUE(gCompatibilityMatrixConverter(&matrix, matrixXml));
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    MatrixHal *camera = getAnyHal(matrix, "android.hardware.camera");
    EXPECT_NE(camera, nullptr);
    camera->versionRanges[0] = {3, 5};
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
    camera->versionRanges[0] = {3, 6};
    EXPECT_FALSE(manifest.checkCompatibility(matrix));

    // reset it
    matrix = {};
    EXPECT_TRUE(gCompatibilityMatrixConverter(&matrix, matrixXml));
    set(matrix, Sepolicy{30, {{26, 0}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix));
    set(matrix, Sepolicy{30, {{25, 6}}});
    EXPECT_FALSE(manifest.checkCompatibility(matrix));
    set(matrix, Sepolicy{30, {{25, 4}}});
    EXPECT_TRUE(manifest.checkCompatibility(matrix, &error)) << error;
}

} // namespace vintf
} // namespace android

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
