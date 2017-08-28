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

// Mechanism to instantiate classes by name.
//
// This mechanism is useful if the concrete classes to be instantiated are not
// statically known (e.g., if their names are read from a dynamically-provided
// config).
//
// In that case, the first step is to define the API implemented by the
// instantiated classes.  E.g.,
//
//  // In a header file function.h:
//
//  // Abstract function that takes a double and returns a double.
//  class Function : public RegisterableClass<Function> {
//   public:
//    virtual ~Function() {}
//    virtual double Evaluate(double x) = 0;
//  };
//
//  // Should be inside namespace libtextclassifier::nlp_core.
//  TC_DECLARE_CLASS_REGISTRY_NAME(Function);
//
// Notice the inheritance from RegisterableClass<Function>.  RegisterableClass
// is defined by this file (registry.h).  Under the hood, this inheritanace
// defines a "registry" that maps names (zero-terminated arrays of chars) to
// factory methods that create Functions.  You should give a human-readable name
// to this registry.  To do that, use the following macro in a .cc file (it has
// to be a .cc file, as it defines some static data):
//
//  // Inside function.cc
//  // Should be inside namespace libtextclassifier::nlp_core.
//  TC_DEFINE_CLASS_REGISTRY_NAME("function", Function);
//
// Now, let's define a few concrete Functions: e.g.,
//
//   class Cos : public Function {
//    public:
//     double Evaluate(double x) override { return cos(x); }
//     TC_DEFINE_REGISTRATION_METHOD("cos", Cos);
//   };
//
//   class Exp : public Function {
//    public:
//     double Evaluate(double x) override { return exp(x); }
//     TC_DEFINE_REGISTRATION_METHOD("sin", Sin);
//   };
//
// Each concrete Function implementation should have (in the public section) the
// macro
//
//   TC_DEFINE_REGISTRATION_METHOD("name", implementation_class);
//
// This defines a RegisterClass static method that, when invoked, associates
// "name" with a factory method that creates instances of implementation_class.
//
// Before instantiating Functions by name, we need to tell our system which
// Functions we may be interested in.  This is done by calling the
// Foo::RegisterClass() for each relevant Foo implementation of Function.  It is
// ok to call Foo::RegisterClass() multiple times (even in parallel): only the
// first call will perform something, the others will return immediately.
//
//   Cos::RegisterClass();
//   Exp::RegisterClass();
//
// Now, let's instantiate a Function based on its name.  This get a lot more
// interesting if the Function name is not statically known (i.e.,
// read from an input proto:
//
//   std::unique_ptr<Function> f(Function::Create("cos"));
//   double result = f->Evaluate(arg);
//
// NOTE: the same binary can use this mechanism for different APIs.  E.g., one
// can also have (in the binary with Function, Sin, Cos, etc):
//
// class IntFunction : public RegisterableClass<IntFunction> {
//  public:
//   virtual ~IntFunction() {}
//   virtual int Evaluate(int k) = 0;
// };
//
// TC_DECLARE_CLASS_REGISTRY_NAME(IntFunction);
//
// TC_DEFINE_CLASS_REGISTRY_NAME("int function", IntFunction);
//
// class Inc : public IntFunction {
//  public:
//   int Evaluate(int k) override { return k + 1; }
//   TC_DEFINE_REGISTRATION_METHOD("inc", Inc);
// };
//
// RegisterableClass<Function> and RegisterableClass<IntFunction> define their
// own registries: each maps string names to implementation of the corresponding
// API.

#ifndef LIBTEXTCLASSIFIER_COMMON_REGISTRY_H_
#define LIBTEXTCLASSIFIER_COMMON_REGISTRY_H_

#include <stdlib.h>
#include <string.h>

#include <string>

#include "util/base/logging.h"

namespace libtextclassifier {
namespace nlp_core {

namespace internal {
// Registry that associates keys (zero-terminated array of chars) with values.
// Values are pointers to type T (the template parameter).  This is used to
// store the association between component names and factory methods that
// produce those components; the error messages are focused on that case.
//
// Internally, this registry uses a linked list of (key, value) pairs.  We do
// not use an STL map, list, etc because we aim for small code size.
template <class T>
class ComponentRegistry {
 public:
  explicit ComponentRegistry(const char *name) : name_(name), head_(nullptr) {}

  // Adds a the (key, value) pair to this registry (if the key does not already
  // exists in this registry) and returns true.  If the registry already has a
  // mapping for key, returns false and does not modify the registry.  NOTE: the
  // error (false) case happens even if the existing value for key is equal with
  // the new one.
  //
  // This method does not take ownership of key, nor of value.
  bool Add(const char *key, T *value) {
    const Cell *old_cell = FindCell(key);
    if (old_cell != nullptr) {
      TC_LOG(ERROR) << "Duplicate component: " << key;
      return false;
    }
    Cell *new_cell = new Cell(key, value, head_);
    head_ = new_cell;
    return true;
  }

  // Returns the value attached to a key in this registry.  Returns nullptr on
  // error (e.g., unknown key).
  T *Lookup(const char *key) const {
    const Cell *cell = FindCell(key);
    if (cell == nullptr) {
      TC_LOG(ERROR) << "Unknown " << name() << " component: " << key;
    }
    return (cell == nullptr) ? nullptr : cell->value();
  }

  T *Lookup(const std::string &key) const { return Lookup(key.c_str()); }

  // Returns name of this ComponentRegistry.
  const char *name() const { return name_; }

 private:
  // Cell for the singly-linked list underlying this ComponentRegistry.  Each
  // cell contains a key, the value for that key, as well as a pointer to the
  // next Cell from the list.
  class Cell {
   public:
    // Constructs a new Cell.
    Cell(const char *key, T *value, Cell *next)
        : key_(key), value_(value), next_(next) {}

    const char *key() const { return key_; }
    T *value() const { return value_; }
    Cell *next() const { return next_; }

   private:
    const char *const key_;
    T *const value_;
    Cell *const next_;
  };

  // Finds Cell for indicated key in the singly-linked list pointed to by head_.
  // Returns pointer to that first Cell with that key, or nullptr if no such
  // Cell (i.e., unknown key).
  //
  // Caller does NOT own the returned pointer.
  const Cell *FindCell(const char *key) const {
    Cell *c = head_;
    while (c != nullptr && strcmp(key, c->key()) != 0) {
      c = c->next();
    }
    return c;
  }

  // Human-readable description for this ComponentRegistry.  For debug purposes.
  const char *const name_;

  // Pointer to the first Cell from the underlying list of (key, value) pairs.
  Cell *head_;
};
}  // namespace internal

// Base class for registerable classes.
template <class T>
class RegisterableClass {
 public:
  // Factory function type.
  typedef T *(Factory)();

  // Registry type.
  typedef internal::ComponentRegistry<Factory> Registry;

  // Creates a new instance of T.  Returns pointer to new instance or nullptr in
  // case of errors (e.g., unknown component).
  //
  // Passes ownership of the returned pointer to the caller.
  static T *Create(const std::string &name) {  // NOLINT
    auto *factory = registry()->Lookup(name);
    if (factory == nullptr) {
      TC_LOG(ERROR) << "Unknown RegisterableClass " << name;
      return nullptr;
    }
    return factory();
  }

  // Returns registry for class.
  static Registry *registry() {
    static Registry *registry_for_type_t = new Registry(kRegistryName);
    return registry_for_type_t;
  }

 protected:
  // Factory method for subclass ComponentClass.  Used internally by the static
  // method RegisterClass() defined by TC_DEFINE_REGISTRATION_METHOD.
  template <class ComponentClass>
  static T *_internal_component_factory() {
    return new ComponentClass();
  }

 private:
  // Human-readable name for the registry for this class.
  static const char kRegistryName[];
};

// Defines the static method component_class::RegisterClass() that should be
// called before trying to instantiate component_class by name.  Should be used
// inside the public section of the declaration of component_class.  See
// comments at the top-level of this file.
#define TC_DEFINE_REGISTRATION_METHOD(component_name, component_class)  \
  static void RegisterClass() {                                         \
    static bool once = registry()->Add(                                 \
        component_name, &_internal_component_factory<component_class>); \
    if (!once) {                                                        \
      TC_LOG(ERROR) << "Problem registering " << component_name;        \
    }                                                                   \
    TC_DCHECK(once);                                                    \
  }

// Defines the human-readable name of the registry associated with base_class.
#define TC_DECLARE_CLASS_REGISTRY_NAME(base_class)             \
  template <>                                                  \
  const char ::libtextclassifier::nlp_core::RegisterableClass< \
      base_class>::kRegistryName[]

// Defines the human-readable name of the registry associated with base_class.
#define TC_DEFINE_CLASS_REGISTRY_NAME(registry_name, base_class) \
  template <>                                                    \
  const char ::libtextclassifier::nlp_core::RegisterableClass<   \
      base_class>::kRegistryName[] = registry_name

}  // namespace nlp_core
}  // namespace libtextclassifier

#endif  // LIBTEXTCLASSIFIER_COMMON_REGISTRY_H_
