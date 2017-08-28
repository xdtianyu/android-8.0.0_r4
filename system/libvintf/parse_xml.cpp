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

// Convert objects from and to xml.

#include <tinyxml2.h>

#include "parse_string.h"
#include "parse_xml.h"

namespace android {
namespace vintf {

// --------------- tinyxml2 details

using NodeType = tinyxml2::XMLElement;
using DocType = tinyxml2::XMLDocument;

// caller is responsible for deleteDocument() call
inline DocType *createDocument() {
    return new tinyxml2::XMLDocument();
}

// caller is responsible for deleteDocument() call
inline DocType *createDocument(const std::string &xml) {
    DocType *doc = new tinyxml2::XMLDocument();
    if (doc->Parse(xml.c_str()) == tinyxml2::XML_NO_ERROR) {
        return doc;
    }
    delete doc;
    return nullptr;
}

inline void deleteDocument(DocType *d) {
    delete d;
}

inline std::string printDocument(DocType *d) {
    tinyxml2::XMLPrinter p;
    d->Print(&p);
    return std::string{p.CStr()};
}

inline NodeType *createNode(const std::string &name, DocType *d) {
    return d->NewElement(name.c_str());
}

inline void appendChild(NodeType *parent, NodeType *child) {
    parent->InsertEndChild(child);
}

inline void appendChild(DocType *parent, NodeType *child) {
    parent->InsertEndChild(child);
}

inline void appendStrAttr(NodeType *e, const std::string &attrName, const std::string &attr) {
    e->SetAttribute(attrName.c_str(), attr.c_str());
}

// text -> text
inline void appendText(NodeType *parent, const std::string &text, DocType *d) {
    parent->InsertEndChild(d->NewText(text.c_str()));
}

inline std::string nameOf(NodeType *root) {
    return root->Name() == NULL ? "" : root->Name();
}

inline std::string getText(NodeType *root) {
    return root->GetText() == NULL ? "" : root->GetText();
}

inline NodeType *getChild(NodeType *parent, const std::string &name) {
    return parent->FirstChildElement(name.c_str());
}

inline NodeType *getRootChild(DocType *parent) {
    return parent->FirstChildElement();
}

inline std::vector<NodeType *> getChildren(NodeType *parent, const std::string &name) {
    std::vector<NodeType *> v;
    for (NodeType *child = parent->FirstChildElement(name.c_str());
         child != nullptr;
         child = child->NextSiblingElement(name.c_str())) {
        v.push_back(child);
    }
    return v;
}

inline bool getAttr(NodeType *root, const std::string &attrName, std::string *s) {
    const char *c = root->Attribute(attrName.c_str());
    if (c == NULL)
        return false;
    *s = c;
    return true;
}

// --------------- tinyxml2 details end.

// Helper functions for XmlConverter
static bool parse(const std::string &attrText, bool *attr) {
    if (attrText == "true" || attrText == "1") {
        *attr = true;
        return true;
    }
    if (attrText == "false" || attrText == "0") {
        *attr = false;
        return true;
    }
    return false;
}

// ---------------------- XmlNodeConverter definitions

template<typename Object>
struct XmlNodeConverter : public XmlConverter<Object> {
    XmlNodeConverter() {}
    virtual ~XmlNodeConverter() {}

    // sub-types should implement these.
    virtual void mutateNode(const Object &o, NodeType *n, DocType *d) const = 0;
    virtual bool buildObject(Object *o, NodeType *n) const = 0;
    virtual std::string elementName() const = 0;

    // convenience methods for user
    inline const std::string &lastError() const { return mLastError; }
    inline NodeType *serialize(const Object &o, DocType *d) const {
        NodeType *root = createNode(this->elementName(), d);
        this->mutateNode(o, root, d);
        return root;
    }
    inline std::string serialize(const Object &o) const {
        DocType *doc = createDocument();
        appendChild(doc, serialize(o, doc));
        std::string s = printDocument(doc);
        deleteDocument(doc);
        return s;
    }
    inline bool deserialize(Object *object, NodeType *root) const {
        if (nameOf(root) != this->elementName()) {
            return false;
        }
        return this->buildObject(object, root);
    }
    inline bool deserialize(Object *o, const std::string &xml) const {
        DocType *doc = createDocument(xml);
        if (doc == nullptr) {
            this->mLastError = "Not a valid XML";
            return false;
        }
        bool ret = deserialize(o, getRootChild(doc));
        deleteDocument(doc);
        return ret;
    }
    inline NodeType *operator()(const Object &o, DocType *d) const {
        return serialize(o, d);
    }
    inline std::string operator()(const Object &o) const {
        return serialize(o);
    }
    inline bool operator()(Object *o, NodeType *node) const {
        return deserialize(o, node);
    }
    inline bool operator()(Object *o, const std::string &xml) const {
        return deserialize(o, xml);
    }

    // convenience methods for implementor.

    // All append* functions helps mutateNode() to serialize the object into XML.
    template <typename T>
    inline void appendAttr(NodeType *e, const std::string &attrName, const T &attr) const {
        return appendStrAttr(e, attrName, ::android::vintf::to_string(attr));
    }

    inline void appendAttr(NodeType *e, const std::string &attrName, bool attr) const {
        return appendStrAttr(e, attrName, attr ? "true" : "false");
    }

    // text -> <name>text</name>
    inline void appendTextElement(NodeType *parent, const std::string &name,
                const std::string &text, DocType *d) const {
        NodeType *c = createNode(name, d);
        appendText(c, text, d);
        appendChild(parent, c);
    }

    // text -> <name>text</name>
    template<typename Array>
    inline void appendTextElements(NodeType *parent, const std::string &name,
                const Array &array, DocType *d) const {
        for (const std::string &text : array) {
            NodeType *c = createNode(name, d);
            appendText(c, text, d);
            appendChild(parent, c);
        }
    }

    template<typename T, typename Array>
    inline void appendChildren(NodeType *parent, const XmlNodeConverter<T> &conv,
            const Array &array, DocType *d) const {
        for (const T &t : array) {
            appendChild(parent, conv(t, d));
        }
    }

    // All parse* functions helps buildObject() to deserialize XML to the object. Returns
    // true if deserialization is successful, false if any error, and mLastError will be
    // set to error message.
    template <typename T>
    inline bool parseOptionalAttr(NodeType *root, const std::string &attrName,
            T &&defaultValue, T *attr) const {
        std::string attrText;
        bool success = getAttr(root, attrName, &attrText) &&
                       ::android::vintf::parse(attrText, attr);
        if (!success) {
            *attr = std::move(defaultValue);
        }
        return true;
    }

    template <typename T>
    inline bool parseAttr(NodeType *root, const std::string &attrName, T *attr) const {
        std::string attrText;
        bool ret = getAttr(root, attrName, &attrText) && ::android::vintf::parse(attrText, attr);
        if (!ret) {
            mLastError = "Could not find/parse attr with name \"" + attrName + "\" for element <"
                    + elementName() + ">";
        }
        return ret;
    }

    inline bool parseAttr(NodeType *root, const std::string &attrName, std::string *attr) const {
        bool ret = getAttr(root, attrName, attr);
        if (!ret) {
            mLastError = "Could not find attr with name \"" + attrName + "\" for element <"
                    + elementName() + ">";
        }
        return ret;
    }

    inline bool parseTextElement(NodeType *root,
            const std::string &elementName, std::string *s) const {
        NodeType *child = getChild(root, elementName);
        if (child == nullptr) {
            mLastError = "Could not find element with name <" + elementName + "> in element <"
                    + this->elementName() + ">";
            return false;
        }
        *s = getText(child);
        return true;
    }

    inline bool parseTextElements(NodeType *root, const std::string &elementName,
            std::vector<std::string> *v) const {
        auto nodes = getChildren(root, elementName);
        v->resize(nodes.size());
        for (size_t i = 0; i < nodes.size(); ++i) {
            v->at(i) = getText(nodes[i]);
        }
        return true;
    }

    template <typename T>
    inline bool parseChild(NodeType *root, const XmlNodeConverter<T> &conv, T *t) const {
        NodeType *child = getChild(root, conv.elementName());
        if (child == nullptr) {
            mLastError = "Could not find element with name <" + conv.elementName() + "> in element <"
                    + this->elementName() + ">";
            return false;
        }
        bool success = conv.deserialize(t, child);
        if (!success) {
            mLastError = conv.lastError();
        }
        return success;
    }

    template <typename T>
    inline bool parseOptionalChild(NodeType *root, const XmlNodeConverter<T> &conv,
            T &&defaultValue, T *t) const {
        NodeType *child = getChild(root, conv.elementName());
        if (child == nullptr) {
            *t = std::move(defaultValue);
            return true;
        }
        bool success = conv.deserialize(t, child);
        if (!success) {
            mLastError = conv.lastError();
        }
        return success;
    }

    template <typename T>
    inline bool parseChildren(NodeType *root, const XmlNodeConverter<T> &conv, std::vector<T> *v) const {
        auto nodes = getChildren(root, conv.elementName());
        v->resize(nodes.size());
        for (size_t i = 0; i < nodes.size(); ++i) {
            if (!conv.deserialize(&v->at(i), nodes[i])) {
                mLastError = "Could not parse element with name <" + conv.elementName()
                        + "> in element <" + this->elementName() + ">: " + conv.lastError();
                return false;
            }
        }
        return true;
    }

    template <typename T>
    inline bool parseChildren(NodeType *root, const XmlNodeConverter<T> &conv, std::set<T> *s) const {
        std::vector<T> vec;
        if (!parseChildren(root, conv, &vec)) {
            return false;
        }
        s->clear();
        s->insert(vec.begin(), vec.end());
        if (s->size() != vec.size()) {
            mLastError = "Duplicated elements <" + conv.elementName() + "> in element <"
                    + this->elementName() + ">";
            s->clear();
            return false;
        }
        return true;
    }

    inline bool parseText(NodeType *node, std::string *s) const {
        *s = getText(node);
        return true;
    }

    template <typename T>
    inline bool parseText(NodeType *node, T *s) const {
        std::string text = getText(node);
        bool ret = ::android::vintf::parse(text, s);
        if (!ret) {
            mLastError = "Could not parse text \"" + text + "\" in element <" + elementName() + ">";
        }
        return ret;
    }
protected:
    mutable std::string mLastError;
};

template<typename Object>
struct XmlTextConverter : public XmlNodeConverter<Object> {
    XmlTextConverter(const std::string &elementName)
        : mElementName(elementName) {}

    virtual void mutateNode(const Object &object, NodeType *root, DocType *d) const override {
        appendText(root, ::android::vintf::to_string(object), d);
    }
    virtual bool buildObject(Object *object, NodeType *root) const override {
        return this->parseText(root, object);
    }
    virtual std::string elementName() const { return mElementName; };
private:
    std::string mElementName;
};

// ---------------------- XmlNodeConverter definitions end

const XmlTextConverter<Version> versionConverter{"version"};

const XmlTextConverter<VersionRange> versionRangeConverter{"version"};

const XmlTextConverter<KernelConfigKey> kernelConfigKeyConverter{"key"};

struct TransportArchConverter : public XmlNodeConverter<TransportArch> {
    std::string elementName() const override { return "transport"; }
    void mutateNode(const TransportArch &object, NodeType *root, DocType *d) const override {
        if (object.arch != Arch::ARCH_EMPTY) {
            appendAttr(root, "arch", object.arch);
        }
        appendText(root, ::android::vintf::to_string(object.transport), d);
    }
    bool buildObject(TransportArch *object, NodeType *root) const override {
        if (!parseOptionalAttr(root, "arch", Arch::ARCH_EMPTY, &object->arch) ||
            !parseText(root, &object->transport)) {
            return false;
        }
        if (!object->isValid()) {
            this->mLastError = "transport == " + ::android::vintf::to_string(object->transport) +
                    " and arch == " + ::android::vintf::to_string(object->arch) +
                    " is not a valid combination.";
            return false;
        }
        return true;
    }
};

const TransportArchConverter transportArchConverter{};

struct KernelConfigTypedValueConverter : public XmlNodeConverter<KernelConfigTypedValue> {
    std::string elementName() const override { return "value"; }
    void mutateNode(const KernelConfigTypedValue &object, NodeType *root, DocType *d) const override {
        appendAttr(root, "type", object.mType);
        appendText(root, ::android::vintf::to_string(object), d);
    }
    bool buildObject(KernelConfigTypedValue *object, NodeType *root) const override {
        std::string stringValue;
        if (!parseAttr(root, "type", &object->mType) ||
            !parseText(root, &stringValue)) {
            return false;
        }
        if (!::android::vintf::parseKernelConfigValue(stringValue, object)) {
            this->mLastError = "Could not parse kernel config value \"" + stringValue + "\"";
            return false;
        }
        return true;
    }
};

const KernelConfigTypedValueConverter kernelConfigTypedValueConverter{};

struct KernelConfigConverter : public XmlNodeConverter<KernelConfig> {
    std::string elementName() const override { return "config"; }
    void mutateNode(const KernelConfig &object, NodeType *root, DocType *d) const override {
        appendChild(root, kernelConfigKeyConverter(object.first, d));
        appendChild(root, kernelConfigTypedValueConverter(object.second, d));
    }
    bool buildObject(KernelConfig *object, NodeType *root) const override {
        if (   !parseChild(root, kernelConfigKeyConverter, &object->first)
            || !parseChild(root, kernelConfigTypedValueConverter, &object->second)) {
            return false;
        }
        return true;
    }
};

const KernelConfigConverter kernelConfigConverter{};

struct HalInterfaceConverter : public XmlNodeConverter<HalInterface> {
    std::string elementName() const override { return "interface"; }
    void mutateNode(const HalInterface &intf, NodeType *root, DocType *d) const override {
        appendTextElement(root, "name", intf.name, d);
        appendTextElements(root, "instance", intf.instances, d);
    }
    bool buildObject(HalInterface *intf, NodeType *root) const override {
        std::vector<std::string> instances;
        if (!parseTextElement(root, "name", &intf->name) ||
            !parseTextElements(root, "instance", &instances)) {
            return false;
        }
        intf->instances.clear();
        intf->instances.insert(instances.begin(), instances.end());
        if (intf->instances.size() != instances.size()) {
            this->mLastError = "Duplicated instances in " + intf->name;
            return false;
        }
        return true;
    }
};

const HalInterfaceConverter halInterfaceConverter{};

struct MatrixHalConverter : public XmlNodeConverter<MatrixHal> {
    std::string elementName() const override { return "hal"; }
    void mutateNode(const MatrixHal &hal, NodeType *root, DocType *d) const override {
        appendAttr(root, "format", hal.format);
        appendAttr(root, "optional", hal.optional);
        appendTextElement(root, "name", hal.name, d);
        appendChildren(root, versionRangeConverter, hal.versionRanges, d);
        appendChildren(root, halInterfaceConverter, iterateValues(hal.interfaces), d);
    }
    bool buildObject(MatrixHal *object, NodeType *root) const override {
        std::vector<HalInterface> interfaces;
        if (!parseOptionalAttr(root, "format", HalFormat::HIDL, &object->format) ||
            !parseOptionalAttr(root, "optional", false /* defaultValue */, &object->optional) ||
            !parseTextElement(root, "name", &object->name) ||
            !parseChildren(root, versionRangeConverter, &object->versionRanges) ||
            !parseChildren(root, halInterfaceConverter, &interfaces)) {
            return false;
        }
        for (auto&& interface : interfaces) {
            std::string name{interface.name};
            auto res = object->interfaces.emplace(std::move(name), std::move(interface));
            if (!res.second) {
                this->mLastError = "Duplicated instance entry " + res.first->first;
                return false;
            }
        }
        return true;
    }
};

const MatrixHalConverter matrixHalConverter{};

struct MatrixKernelConverter : public XmlNodeConverter<MatrixKernel> {
    std::string elementName() const override { return "kernel"; }
    void mutateNode(const MatrixKernel &kernel, NodeType *root, DocType *d) const override {
        appendAttr(root, "version", kernel.mMinLts);
        appendChildren(root, kernelConfigConverter, kernel.mConfigs, d);
    }
    bool buildObject(MatrixKernel *object, NodeType *root) const override {
        if (!parseAttr(root, "version", &object->mMinLts) ||
            !parseChildren(root, kernelConfigConverter, &object->mConfigs)) {
            return false;
        }
        return true;
    }
};

const MatrixKernelConverter matrixKernelConverter{};

struct ManifestHalConverter : public XmlNodeConverter<ManifestHal> {
    std::string elementName() const override { return "hal"; }
    void mutateNode(const ManifestHal &hal, NodeType *root, DocType *d) const override {
        appendAttr(root, "format", hal.format);
        appendTextElement(root, "name", hal.name, d);
        if (!hal.transportArch.empty()) {
            appendChild(root, transportArchConverter(hal.transportArch, d));
        }
        appendChildren(root, versionConverter, hal.versions, d);
        appendChildren(root, halInterfaceConverter, iterateValues(hal.interfaces), d);
    }
    bool buildObject(ManifestHal *object, NodeType *root) const override {
        std::vector<HalInterface> interfaces;
        if (!parseOptionalAttr(root, "format", HalFormat::HIDL, &object->format) ||
            !parseTextElement(root, "name", &object->name) ||
            !parseChild(root, transportArchConverter, &object->transportArch) ||
            !parseChildren(root, versionConverter, &object->versions) ||
            !parseChildren(root, halInterfaceConverter, &interfaces)) {
            return false;
        }
        object->interfaces.clear();
        for (auto &&interface : interfaces) {
            auto res = object->interfaces.emplace(interface.name,
                                                  std::move(interface));
            if (!res.second) {
                this->mLastError = "Duplicated instance entry " + res.first->first;
                return false;
            }
        }
        if (!object->isValid()) {
            this->mLastError = "'" + object->name + "' is not a valid Manifest HAL.";
            return false;
        }
        return true;
    }
};

// Convert ManifestHal from and to XML. Returned object is guaranteed to have
// .isValid() == true.
const ManifestHalConverter manifestHalConverter{};

const XmlTextConverter<KernelSepolicyVersion> kernelSepolicyVersionConverter{"kernel-sepolicy-version"};
const XmlTextConverter<VersionRange> sepolicyVersionConverter{"sepolicy-version"};

struct SepolicyConverter : public XmlNodeConverter<Sepolicy> {
    std::string elementName() const override { return "sepolicy"; }
    void mutateNode(const Sepolicy &object, NodeType *root, DocType *d) const override {
        appendChild(root, kernelSepolicyVersionConverter(object.kernelSepolicyVersion(), d));
        appendChildren(root, sepolicyVersionConverter, object.sepolicyVersions(), d);
    }
    bool buildObject(Sepolicy *object, NodeType *root) const override {
        if (!parseChild(root, kernelSepolicyVersionConverter, &object->mKernelSepolicyVersion) ||
            !parseChildren(root, sepolicyVersionConverter, &object->mSepolicyVersionRanges)) {
            return false;
        }
        return true;
    }
};
const SepolicyConverter sepolicyConverter{};

const XmlTextConverter<VndkVersionRange> vndkVersionRangeConverter{"version"};
const XmlTextConverter<std::string> vndkLibraryConverter{"library"};

struct VndkConverter : public XmlNodeConverter<Vndk> {
    std::string elementName() const override { return "vndk"; }
    void mutateNode(const Vndk &object, NodeType *root, DocType *d) const override {
        appendChild(root, vndkVersionRangeConverter(object.mVersionRange, d));
        appendChildren(root, vndkLibraryConverter, object.mLibraries, d);
    }
    bool buildObject(Vndk *object, NodeType *root) const override {
        if (!parseChild(root, vndkVersionRangeConverter, &object->mVersionRange) ||
            !parseChildren(root, vndkLibraryConverter, &object->mLibraries)) {
            return false;
        }
        return true;
    }
};

const VndkConverter vndkConverter{};

struct HalManifestSepolicyConverter : public XmlNodeConverter<Version> {
    std::string elementName() const override { return "sepolicy"; }
    void mutateNode(const Version &m, NodeType *root, DocType *d) const override {
        appendChild(root, versionConverter(m, d));
    }
    bool buildObject(Version *object, NodeType *root) const override {
        return parseChild(root, versionConverter, object);
    }
};
const HalManifestSepolicyConverter halManifestSepolicyConverter{};

struct HalManifestConverter : public XmlNodeConverter<HalManifest> {
    std::string elementName() const override { return "manifest"; }
    void mutateNode(const HalManifest &m, NodeType *root, DocType *d) const override {
        appendAttr(root, "version", HalManifest::kVersion);
        appendAttr(root, "type", m.mType);
        appendChildren(root, manifestHalConverter, m.getHals(), d);
        if (m.mType == SchemaType::DEVICE) {
            appendChild(root, halManifestSepolicyConverter(m.device.mSepolicyVersion, d));
        } else if (m.mType == SchemaType::FRAMEWORK) {
            appendChildren(root, vndkConverter, m.framework.mVndks, d);
        }
    }
    bool buildObject(HalManifest *object, NodeType *root) const override {
        Version version;
        std::vector<ManifestHal> hals;
        if (!parseAttr(root, "version", &version) ||
            !parseAttr(root, "type", &object->mType) ||
            !parseChildren(root, manifestHalConverter, &hals)) {
            return false;
        }
        if (version != HalManifest::kVersion) {
            this->mLastError = "Unrecognized manifest.version";
            return false;
        }
        if (object->mType == SchemaType::DEVICE) {
            // tags for device hal manifest only.
            // <sepolicy> can be missing because it can be determined at build time, not hard-coded
            // in the XML file.
            if (!parseOptionalChild(root, halManifestSepolicyConverter, {},
                    &object->device.mSepolicyVersion)) {
                return false;
            }
        } else if (object->mType == SchemaType::FRAMEWORK) {
            if (!parseChildren(root, vndkConverter, &object->framework.mVndks)) {
                return false;
            }
            for (const auto &vndk : object->framework.mVndks) {
                if (!vndk.mVersionRange.isSingleVersion()) {
                    this->mLastError = "vndk.version " + to_string(vndk.mVersionRange)
                            + " cannot be a range for manifests";
                    return false;
                }
            }
        }
        for (auto &&hal : hals) {
            std::string description{hal.name};
            if (!object->add(std::move(hal))) {
                this->mLastError = "Duplicated manifest.hal entry " + description;
                return false;
            }
        }
        return true;
    }
};

const HalManifestConverter halManifestConverter{};

const XmlTextConverter<Version> avbVersionConverter{"vbmeta-version"};
struct AvbConverter : public XmlNodeConverter<Version> {
    std::string elementName() const override { return "avb"; }
    void mutateNode(const Version &m, NodeType *root, DocType *d) const override {
        appendChild(root, avbVersionConverter(m, d));
    }
    bool buildObject(Version *object, NodeType *root) const override {
        return parseChild(root, avbVersionConverter, object);
    }
};
const AvbConverter avbConverter{};

struct CompatibilityMatrixConverter : public XmlNodeConverter<CompatibilityMatrix> {
    std::string elementName() const override { return "compatibility-matrix"; }
    void mutateNode(const CompatibilityMatrix &m, NodeType *root, DocType *d) const override {
        appendAttr(root, "version", CompatibilityMatrix::kVersion);
        appendAttr(root, "type", m.mType);
        appendChildren(root, matrixHalConverter, iterateValues(m.mHals), d);
        if (m.mType == SchemaType::FRAMEWORK) {
            appendChildren(root, matrixKernelConverter, m.framework.mKernels, d);
            appendChild(root, sepolicyConverter(m.framework.mSepolicy, d));
            appendChild(root, avbConverter(m.framework.mAvbMetaVersion, d));
        } else if (m.mType == SchemaType::DEVICE) {
            appendChild(root, vndkConverter(m.device.mVndk, d));
        }
    }
    bool buildObject(CompatibilityMatrix *object, NodeType *root) const override {
        Version version;
        std::vector<MatrixHal> hals;
        if (!parseAttr(root, "version", &version) ||
            !parseAttr(root, "type", &object->mType) ||
            !parseChildren(root, matrixHalConverter, &hals)) {
            return false;
        }

        if (object->mType == SchemaType::FRAMEWORK) {
            // <avb> and <sepolicy> can be missing because it can be determined at build time, not
            // hard-coded in the XML file.
            if (!parseChildren(root, matrixKernelConverter, &object->framework.mKernels) ||
                !parseOptionalChild(root, sepolicyConverter, {}, &object->framework.mSepolicy) ||
                !parseOptionalChild(root, avbConverter, {}, &object->framework.mAvbMetaVersion)) {
                return false;
            }
        } else if (object->mType == SchemaType::DEVICE) {
            // <vndk> can be missing because it can be determined at build time, not hard-coded
            // in the XML file.
            if (!parseOptionalChild(root, vndkConverter, {}, &object->device.mVndk)) {
                return false;
            }
        }

        if (version != CompatibilityMatrix::kVersion) {
            this->mLastError = "Unrecognized compatibility-matrix.version";
            return false;
        }
        for (auto &&hal : hals) {
            if (!object->add(std::move(hal))) {
                this->mLastError = "Duplicated compatibility-matrix.hal entry";
                return false;
            }
        }
        return true;
    }
};

const CompatibilityMatrixConverter compatibilityMatrixConverter{};

// Publicly available as in parse_xml.h
const XmlConverter<HalManifest> &gHalManifestConverter = halManifestConverter;
const XmlConverter<CompatibilityMatrix> &gCompatibilityMatrixConverter
        = compatibilityMatrixConverter;

// For testing in LibVintfTest
const XmlConverter<Version> &gVersionConverter = versionConverter;
const XmlConverter<KernelConfigTypedValue> &gKernelConfigTypedValueConverter
        = kernelConfigTypedValueConverter;
const XmlConverter<MatrixHal> &gMatrixHalConverter = matrixHalConverter;
const XmlConverter<ManifestHal> &gManifestHalConverter = manifestHalConverter;

} // namespace vintf
} // namespace android
