#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import copy
import logging
import random
import sys

from vts.utils.python.fuzzer import FuzzerUtils
from vts.utils.python.mirror import mirror_object_for_types
from vts.proto import ComponentSpecificationMessage_pb2 as CompSpecMsg
from google.protobuf import text_format

# a dict containing the IDs of the registered function pointers.
_function_pointer_id_dict = {}

INTERFACE = "interface"
API = "api"

class MirrorObjectError(Exception):
    """Raised when there is a general error in manipulating a mirror object."""
    pass


class MirrorObject(object):
    """The class that mirrors objects on the native side.

    This class exists on the host and can be used to communicate to a
    particular HAL in the HAL agent on the target side.

    Attributes:
        _client: the TCP client instance.
        _if_spec_msg: the interface specification message of a host object to
                      mirror.
        _callback_server: the instance of a callback server.
        _parent_path: the name of a sub struct this object mirrors.
        _last_raw_code_coverage_data: NativeCodeCoverageRawDataMessage,
                                      last seen raw code coverage data.
        __caller_uid: string, the caller's UID if not None.
    """

    def __init__(self, client, msg, callback_server, parent_path=None):
        self._client = client
        self._if_spec_msg = msg
        self._callback_server = callback_server
        self._parent_path = parent_path
        self._last_raw_code_coverage_data = None
        self.__caller_uid = None

    def GetFunctionPointerID(self, function_pointer):
        """Returns the function pointer ID for the given one."""
        max_num = 0
        for key in _function_pointer_id_dict:
            if _function_pointer_id_dict[key] == function_pointer:
                return str(key)
            if not max_num or key > max_num:
                max_num = key
        _function_pointer_id_dict[max_num + 1] = function_pointer
        id = str(max_num + 1)
        if self._callback_server:
            self._callback_server.RegisterCallback(id, function_pointer)
        return id

    def OpenConventionalHal(self, module_name=None):
        """Opens the target conventional HAL component.

        This is only needed for conventional HAL.

        Args:
            module_name: string, the name of a module to load.
        """
        func_msg = CompSpecMsg.FunctionSpecificationMessage()
        func_msg.name = "#Open"
        logging.debug("remote call %s", func_msg.name)
        if module_name:
            arg = func_msg.arg.add()
            arg.type = CompSpecMsg.TYPE_STRING
            arg.string_value.message = module_name

            func_msg.return_type.type == CompSpecMsg.TYPE_SCALAR
            func_msg.return_type.scalar_type = "int32_t"
        logging.debug("final msg %s", func_msg)

        result = self._client.CallApi(text_format.MessageToString(func_msg),
                                      self.__caller_uid)
        logging.debug(result)
        return result

    def SetCallerUid(self, uid):
        """Sets target-side caller's UID.

        Args:
            uid: string, the caller's UID.
        """
        self.__caller_uid = uid

    def GetAttributeValue(self, attribute_name):
        """Retrieves the value of an attribute from a target.

        Args:
            attribute_name: string, the name of an attribute.

        Returns:
            FunctionSpecificationMessage which contains the value.
        """
        def RemoteCallToGetAttribute(*args, **kwargs):
            """Makes a remote call and retrieves an attribute."""
            func_msg = self.GetAttribute(attribute_name)
            if not func_msg:
                raise MirrorObjectError("attribute %s unknown", func_msg)

            logging.debug("remote call %s.%s", self._parent_path, attribute_name)
            logging.info("remote call %s%s", attribute_name, args)
            if self._parent_path:
                func_msg.parent_path = self._parent_path
            try:
                if isinstance(self._if_spec_msg,
                              CompSpecMsg.ComponentSpecificationMessage):
                    logging.info("component_class %s",
                                 self._if_spec_msg.component_class)
                    if (self._if_spec_msg.component_class
                        == CompSpecMsg.HAL_CONVENTIONAL_SUBMODULE):
                        submodule_name = self._if_spec_msg.original_data_structure_name
                        if submodule_name.endswith("*"):
                            submodule_name = submodule_name[:-1]
                        func_msg.submodule_name = submodule_name
                elif isinstance(self._if_spec_msg,
                                CompSpecMsg.StructSpecificationMessage):
                    pass
                else:
                    logging.error("unknown type %s", type(self._if_spec_msg))
                    sys.exit(1)
            except AttributeError as e:
                logging.exception("%s" % e)
                pass
            result = self._client.GetAttribute(
                text_format.MessageToString(func_msg))
            logging.debug(result)
            return result

        var_msg = self.GetAttribute(attribute_name)
        if var_msg:
            logging.debug("attribute: %s", var_msg)
            return RemoteCallToGetAttribute()
        raise MirrorObjectError("unknown attribute name %s" % attribute_name)

    def GetHidlCallbackInterface(self, interface_name, **kwargs):
        var_msg = CompSpecMsg.VariableSpecificationMessage()
        var_msg.name = interface_name
        var_msg.type = CompSpecMsg.TYPE_FUNCTION_POINTER
        var_msg.is_callback = True

        msg = self._if_spec_msg
        specification = self._client.ReadSpecification(
            interface_name,
            msg.component_class,
            msg.component_type,
            msg.component_type_version,
            msg.package)
        logging.info("specification: %s", specification)
        interface = getattr(specification, INTERFACE, None)
        apis = getattr(interface, API, [])
        for api in apis:
            function_pointer = None
            if api.name in kwargs:
                function_pointer = kwargs[api.name]
            else:
                def dummy(*args):
                    """Dummy implementation for any callback function."""
                    logging.info("Entering dummy implementation"
                                 " for callback function: %s", api.name)
                    for arg_index in range(len(args)):
                        logging.info("arg%s: %s", arg_index, args[arg_index])
                function_pointer = dummy
            func_pt_msg = var_msg.function_pointer.add()
            func_pt_msg.function_name = api.name
            func_pt_msg.id = self.GetFunctionPointerID(function_pointer)

        return var_msg

    def GetHidlTypeInterface(self, interface_name,
                             target_class=None, target_type=None,
                             version=None, package=None):
        """Gets HIDL type interface's host-side mirror.

        Args:
            interface_name: string, the name of a target interface to read.
            target_class: integer, optional used to override the loaded HAL's
                          component_class.
            target_type: integer, optional used to override the loaded HAL's
                         component_type.
            version: integer, optional used to override the loaded HAL's
                     component_type_version.
            package: integer, optional used to override the loaded HAL's
                              package.

        Returns:
            a host-side mirror of a HIDL interface
        """
        msg = self._if_spec_msg
        result = self._client.ReadSpecification(
            interface_name,
            msg.component_class if target_class is None else target_class,
            msg.component_type if target_type is None else target_type,
            msg.component_type_version if version is None else version,
            msg.package if package is None else package,
            recursive=True)

        logging.info("result %s", result)
        return mirror_object_for_types.MirrorObjectForTypes(result)

    def CleanUp(self):
        self._client.Disconnect()

    def GetApi(self, api_name):
        """Returns the Function Specification Message.

        Args:
            api_name: string, the name of the target function API.

        Returns:
            FunctionSpecificationMessage or StructSpecificationMessage if found,
            None otherwise
        """
        logging.debug("GetAPI %s for %s", api_name, self._if_spec_msg)
        # handle reserved methods first.
        if api_name == "notifySyspropsChanged":
            func_msg = CompSpecMsg.FunctionSpecificationMessage()
            func_msg.name =  api_name
            return func_msg
        if isinstance(self._if_spec_msg, CompSpecMsg.ComponentSpecificationMessage):
            if len(self._if_spec_msg.interface.api) > 0:
                for api in self._if_spec_msg.interface.api:
                    if api.name == api_name:
                        return copy.copy(api)
        elif isinstance(self._if_spec_msg, CompSpecMsg.StructSpecificationMessage):
            if len(self._if_spec_msg.api) > 0:
                for api in self._if_spec_msg.api:
                    logging.info("api %s", api)
                    if api.name == api_name:
                        return copy.copy(api)
            if len(self._if_spec_msg.sub_struct) > 0:
                for sub_struct in self._if_spec_msg.sub_struct:
                    if len(sub_struct.api) > 0:
                        for api in sub_struct.api:
                            logging.info("api %s", api)
                            if api.name == api_name:
                                return copy.copy(api)
        else:
            logging.error("unknown spec type %s", type(self._if_spec_msg))
            sys.exit(1)
        return None

    def GetAttribute(self, attribute_name):
        """Returns the Message.
        """
        logging.debug("GetAttribute %s for %s",
                      attribute_name, self._if_spec_msg)
        if self._if_spec_msg.attribute:
            for attribute in self._if_spec_msg.attribute:
                if attribute.name == attribute_name:
                    func_msg = CompSpecMsg.FunctionSpecificationMessage()
                    func_msg.name = attribute.name
                    func_msg.return_type.type = attribute.type
                    if func_msg.return_type.type == CompSpecMsg.TYPE_SCALAR:
                        func_msg.return_type.scalar_type = attribute.scalar_type
                    else:
                        func_msg.return_type.predefined_type = attribute.predefined_type
                    logging.info("GetAttribute attribute: %s", attribute)
                    logging.info("GetAttribute request: %s", func_msg)
                    return copy.copy(func_msg)
        if (self._if_spec_msg.interface and
            self._if_spec_msg.interface.attribute):
            for attribute in self._if_spec_msg.interface.attribute:
                if attribute.name == attribute_name:
                    func_msg = CompSpecMsg.FunctionSpecificationMessage()
                    func_msg.name = attribute.name
                    func_msg.return_type.type = attribute.type
                    if func_msg.return_type.type == CompSpecMsg.TYPE_SCALAR:
                        func_msg.return_type.scalar_type = attribute.scalar_type
                    else:
                        func_msg.return_type.predefined_type = attribute.predefined_type
                    logging.info("GetAttribute attribute: %s", attribute)
                    logging.info("GetAttribute request: %s", func_msg)
                    return copy.copy(func_msg)
        return None

    def GetSubStruct(self, sub_struct_name):
        """Returns the Struct Specification Message.

        Args:
            sub_struct_name: string, the name of the target sub struct attribute.

        Returns:
            StructSpecificationMessage if found, None otherwise
        """
        if isinstance(self._if_spec_msg, CompSpecMsg.ComponentSpecificationMessage):
            if (self._if_spec_msg.interface and
                self._if_spec_msg.interface.sub_struct):
                for sub_struct in self._if_spec_msg.interface.sub_struct:
                    if sub_struct.name == sub_struct_name:
                        return copy.copy(sub_struct)
        elif isinstance(self._if_spec_msg, CompSpecMsg.StructSpecificationMessage):
            if len(self._if_spec_msg.sub_struct) > 0:
                for sub_struct in self._if_spec_msg.sub_struct:
                    if sub_struct.name == sub_struct_name:
                        return copy.copy(sub_struct)
        return None

    def GetCustomAggregateType(self, type_name):
        """Returns the Argument Specification Message.

        Args:
            type_name: string, the name of the target data type.

        Returns:
            VariableSpecificationMessage if found, None otherwise
        """
        try:
            if self._if_spec_msg.attribute:
                for attribute in self._if_spec_msg.attribute:
                    if not attribute.is_const and attribute.name == type_name:
                        return copy.copy(attribute)
            if (self._if_spec_msg.interface and
                self._if_spec_msg.interface.attribute):
                for attribute in self._if_spec_msg.interface.attribute:
                    if not attribute.is_const and attribute.name == type_name:
                        return copy.copy(attribute)
            return None
        except AttributeError as e:
            # TODO: check in advance whether self._if_spec_msg Interface
            # SpecificationMessage.
            return None

    def GetConstType(self, type_name):
        """Returns the Argument Specification Message.

        Args:
            type_name: string, the name of the target const data variable.

        Returns:
            VariableSpecificationMessage if found, None otherwise
        """
        try:
            if self._if_spec_msg.attribute:
                for attribute in self._if_spec_msg.attribute:
                    if attribute.is_const and attribute.name == type_name:
                        return copy.copy(attribute)
                    elif attribute.type == CompSpecMsg.TYPE_ENUM:
                      for enumerator in attribute.enum_value.enumerator:
                            if enumerator == type_name:
                                return copy.copy(attribute)
            if self._if_spec_msg.interface and self._if_spec_msg.interface.attribute:
                for attribute in self._if_spec_msg.interface.attribute:
                    if attribute.is_const and attribute.name == type_name:
                        return copy.copy(attribute)
                    elif attribute.type == CompSpecMsg.TYPE_ENUM:
                        for enumerator in attribute.enum_value.enumerator:
                            if enumerator == type_name:
                                return copy.copy(attribute)
            return None
        except AttributeError as e:
            # TODO: check in advance whether self._if_spec_msg Interface
            # SpecificationMessage.
            return None

    def ArgToPb(self, arg_msg, value_msg):
        """Converts args to a ProtoBuf message.

        Args:
            arg_msg: the ProtoBuf message where the result will be stored in.
            value_msg: value given as an argument. it can be either Python
                       data structure, a ProtoBuf message, or both.

        Raises:
            AttributeError: when unexpected type is seen.
        """
        if isinstance(value_msg, CompSpecMsg.VariableSpecificationMessage):
            arg_msg.CopyFrom(value_msg)
        elif isinstance(value_msg, int):
            arg_msg.type = CompSpecMsg.TYPE_SCALAR
            if not arg_msg.scalar_type:
                arg_msg.scalar_type = "int32_t"
            setattr(arg_msg.scalar_value, arg_msg.scalar_type, value_msg)
        elif isinstance(value_msg, float):
            arg_msg.type = CompSpecMsg.TYPE_SCALAR
            arg_msg.scalar_value.float_t = value_msg
            arg_msg.scalar_type = "float_t"
        elif isinstance(value_msg, str):
            if ((arg_msg.type == CompSpecMsg.TYPE_SCALAR and
                 (arg_msg.scalar_type == "char_pointer" or
                  arg_msg.scalar_type == "uchar_pointer")) or
                arg_msg.type == CompSpecMsg.TYPE_STRING):
                arg_msg.string_value.message = value_msg
                arg_msg.string_value.length = len(value_msg)
            else:
                raise MirrorObjectError(
                    "unsupported type %s for str" % arg_msg)
        elif isinstance(value_msg, list):
            if (arg_msg.type == CompSpecMsg.TYPE_VECTOR or
                arg_msg.type == CompSpecMsg.TYPE_ARRAY):
                first = True
                for list_element in value_msg:
                    if first:
                        self.ArgToPb(arg_msg.vector_value[0], list_element)
                        first = False
                    else:
                        self.ArgToPb(arg_msg.vector_value.add(), list_element)
                arg_msg.vector_size = len(value_msg)
            else:
                raise MirrorObjectError(
                    "unsupported arg_msg type %s for list" % arg_msg.type)
        else:
            raise MirrorObjectError(
                "unsupported value type %s" % type(value_msg))

    # TODO: Guard against calls to this function after self.CleanUp is called.
    def __getattr__(self, api_name, *args, **kwargs):
        """Calls a target component's API.

        Args:
            api_name: string, the name of an API function to call.
            *args: a list of arguments
            **kwargs: a dict for the arg name and value pairs
        """
        def RemoteCall(*args, **kwargs):
            """Dynamically calls a remote API."""
            func_msg = self.GetApi(api_name)
            if not func_msg:
                raise MirrorObjectError("api %s unknown", func_msg)

            logging.debug("remote call %s.%s", self._parent_path, api_name)
            logging.info("remote call %s%s", api_name, args)
            if args:
                for arg_msg, value_msg in zip(func_msg.arg, args):
                    logging.debug("arg msg %s", arg_msg)
                    logging.debug("value %s", value_msg)
                    if value_msg is not None:
                        self.ArgToPb(arg_msg, value_msg)

                logging.debug("final msg %s", func_msg)
            else:
                # TODO: use kwargs
                for arg in func_msg.arg:
                    # TODO: handle other
                    if (arg.type == CompSpecMsg.TYPE_SCALAR
                        and arg.scalar_type == "pointer"):
                        arg.scalar_value.pointer = 0
                logging.debug(func_msg)

            if self._parent_path:
                func_msg.parent_path = self._parent_path

            if isinstance(self._if_spec_msg, CompSpecMsg.ComponentSpecificationMessage):
                if self._if_spec_msg.component_class:
                    logging.info("component_class %s",
                                 self._if_spec_msg.component_class)
                    if self._if_spec_msg.component_class == CompSpecMsg.HAL_CONVENTIONAL_SUBMODULE:
                        submodule_name = self._if_spec_msg.original_data_structure_name
                        if submodule_name.endswith("*"):
                            submodule_name = submodule_name[:-1]
                        func_msg.submodule_name = submodule_name
            result = self._client.CallApi(text_format.MessageToString(func_msg),
                                          self.__caller_uid)
            logging.debug(result)
            if (isinstance(result, tuple) and len(result) == 2 and
                isinstance(result[1], dict) and "coverage" in result[1]):
                self._last_raw_code_coverage_data = result[1]["coverage"]
                return result[0]
            return result

        def MessageGenerator(*args, **kwargs):
            """Dynamically generates a custom message instance."""
            arg_msg = self.GetCustomAggregateType(api_name)
            if not arg_msg:
                raise MirrorObjectError("arg %s unknown" % arg_msg)
            logging.info("MessageGenerator %s %s", api_name, arg_msg)
            logging.debug("MESSAGE %s", api_name)
            if arg_msg.type == CompSpecMsg.TYPE_STRUCT:
                for struct_value in arg_msg.struct_value:
                    logging.debug("for %s %s",
                                  struct_value.name, struct_value.scalar_type)
                    for given_name, given_value in kwargs.iteritems():
                        logging.debug("check %s %s", struct_value.name, given_name)
                        if given_name == struct_value.name:
                            logging.debug("match type=%s", struct_value.scalar_type)
                            if struct_value.type == CompSpecMsg.TYPE_SCALAR:
                                if struct_value.scalar_type == "uint32_t":
                                    struct_value.scalar_value.uint32_t = given_value
                                elif struct_value.scalar_type == "int32_t":
                                    struct_value.scalar_value.int32_t = given_value
                                else:
                                    raise MirrorObjectError(
                                        "support %s" % struct_value.scalar_type)
                            continue
            elif arg_msg.type == CompSpecMsg.TYPE_FUNCTION_POINTER:
                for fp_value in arg_msg.function_pointer:
                    logging.debug("for %s", fp_value.function_name)
                    for given_name, given_value in kwargs.iteritems():
                          logging.debug("check %s %s", fp_value.function_name, given_name)
                          if given_name == fp_value.function_name:
                              fp_value.id = self.GetFunctionPointerID(given_value)
                              break

            if arg_msg.type == CompSpecMsg.TYPE_STRUCT:
                for struct_value, given_value in zip(arg_msg.struct_value, args):
                    logging.debug("arg match type=%s", struct_value.scalar_type)
                    if struct_value.type == CompSpecMsg.TYPE_SCALAR:
                        if struct_value.scalar_type == "uint32_t":
                            struct_value.scalar_value.uint32_t = given_value
                        elif struct_value.scalar_type == "int32_t":
                            struct_value.scalar_value.int32_t = given_value
                        else:
                            raise MirrorObjectError("support %s" % p_type)
            elif arg_msg.type == CompSpecMsg.TYPE_FUNCTION_POINTER:
                for fp_value, given_value in zip(arg_msg.function_pointer, args):
                    logging.debug("for %s", fp_value.function_name)
                    fp_value.id = self.GetFunctionPointerID(given_value)
                    logging.debug("fp %s", fp_value)
            logging.debug("generated %s", arg_msg)
            return arg_msg

        def MessageFuzzer(arg_msg):
            """Fuzz a custom message instance."""
            if not self.GetCustomAggregateType(api_name):
                raise MirrorObjectError("fuzz arg %s unknown" % arg_msg)

            if arg_msg.type == CompSpecMsg.TYPE_STRUCT:
                index = random.randint(0, len(arg_msg.struct_value))
                count = 0
                for struct_value in arg_msg.struct_value:
                    if count == index:
                        if struct_value.scalar_type == "uint32_t":
                            struct_value.scalar_value.uint32_t ^= FuzzerUtils.mask_uint32_t()
                        elif struct_value.scalar_type == "int32_t":
                            mask = FuzzerUtils.mask_int32_t()
                            if mask == (1 << 31):
                                struct_value.scalar_value.int32_t *= -1
                                struct_value.scalar_value.int32_t += 1
                            else:
                                struct_value.scalar_value.int32_t ^= mask
                        else:
                            raise MirrorObjectError(
                                "support %s" % struct_value.scalar_type)
                        break
                    count += 1
                logging.debug("fuzzed %s", arg_msg)
            else:
                raise MirrorObjectError(
                    "unsupported fuzz message type %s." % arg_msg.type)
            return arg_msg

        def ConstGenerator():
            """Dynamically generates a const variable's value."""
            arg_msg = self.GetConstType(api_name)
            if not arg_msg:
                raise MirrorObjectError("const %s unknown" % arg_msg)
            logging.debug("check %s", api_name)
            if arg_msg.type == CompSpecMsg.TYPE_SCALAR:
                ret_v = getattr(arg_msg.scalar_value, arg_msg.scalar_type, None)
                if ret_v is None:
                    raise MirrorObjectError(
                        "No value found for type %s in %s." %
                        (arg_msg.scalar_type, api_name))
                return ret_v
            elif arg_msg.type == CompSpecMsg.TYPE_STRING:
                return arg_msg.string_value.message
            elif arg_msg.type == CompSpecMsg.TYPE_ENUM:
                for enumerator, scalar_value in zip(
                        arg_msg.enum_value.enumerator,
                        arg_msg.enum_value.scalar_value):
                    if enumerator == api_name:
                      return getattr(scalar_value,
                                     arg_msg.enum_value.scalar_type)
            raise MirrorObjectError("const %s not found" % api_name)

        # handle APIs.
        func_msg = self.GetApi(api_name)
        if func_msg:
            logging.debug("api %s", func_msg)
            return RemoteCall

        struct_msg = self.GetSubStruct(api_name)
        if struct_msg:
            logging.debug("sub_struct %s", struct_msg)
            if self._parent_path:
                parent_name = "%s.%s" % (self._parent_path, api_name)
            else:
                parent_name = api_name
            return MirrorObject(self._client, struct_msg,
                                self._callback_server,
                                parent_path=parent_name)

        # handle attributes.
        fuzz = False
        if api_name.endswith("_fuzz"):
            fuzz = True
            api_name = api_name[:-5]
        arg_msg = self.GetCustomAggregateType(api_name)
        if arg_msg:
            logging.debug("arg %s", arg_msg)
            if not fuzz:
                return MessageGenerator
            else:
                return MessageFuzzer

        arg_msg = self.GetConstType(api_name)
        if arg_msg:
            logging.debug("const %s *\n%s", api_name, arg_msg)
            return ConstGenerator()
        raise MirrorObjectError("unknown api name %s" % api_name)

    def GetRawCodeCoverage(self):
        """Returns any measured raw code coverage data."""
        return self._last_raw_code_coverage_data

    def __str__(self):
        """Prints all the attributes and methods."""
        result = ""
        if self._if_spec_msg:
            if self._if_spec_msg.attribute:
                for attribute in self._if_spec_msg.attribute:
                    result += "attribute %s\n" % attribute.name
            if self._if_spec_msg.api:
                for api in self._if_spec_msg.api:
                    result += "api %s\n" % api.name
        return result
