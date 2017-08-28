#!/usr/bin/env python
#
# Copyright (C) 2017 The Android Open Source Project
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

import os
import struct


class ElfError(Exception):
    """The exception raised by ElfParser."""
    pass


class ElfParser(object):
    """The class reads information from an ELF file.

    Attributes:
        _file: The ELF file object.
        _file_size: Size of the ELF.
        bitness: Bitness of the ELF.
        _address_size: Size of address or offset in the ELF.
        _offsets: Offset of each entry in the ELF.
        _seek_read_address: The function to read an address or offset entry
                            from the ELF.
        _sh_offset: Offset of section header table in the file.
        _sh_size: Size of section header table entry.
        _sh_count: Number of section header table entries.
        _section_headers: List of SectionHeader objects read from the ELF.
    """
    _MAGIC_OFFSET = 0
    _MAGIC_BYTES = b"\x7fELF"
    _BITNESS_OFFSET = 4
    _BITNESS_32 = 1
    _BITNESS_64 = 2
    # Section type
    _SHT_DYNAMIC = 6
    # Tag in dynamic section
    _DT_NULL = 0
    _DT_NEEDED = 1
    _DT_STRTAB = 5

    class ElfOffsets32(object):
        """Offset of each entry in 32-bit ELF"""
        # offset from ELF header
        SECTION_HEADER_OFFSET = 0x20
        SECTION_HEADER_SIZE = 0x2e
        SECTION_HEADER_COUNT = 0x30
        # offset from section header
        SECTION_TYPE = 0x04
        SECTION_ADDRESS = 0x0c
        SECTION_OFFSET = 0x10

    class ElfOffsets64(object):
        """Offset of each entry in 64-bit ELF"""
        # offset from ELF header
        SECTION_HEADER_OFFSET = 0x28
        SECTION_HEADER_SIZE = 0x3a
        SECTION_HEADER_COUNT = 0x3c
        # offset from section header
        SECTION_TYPE = 0x04
        SECTION_ADDRESS = 0x10
        SECTION_OFFSET = 0x18

    class SectionHeader(object):
        """Contains section header entries as attributes.

        Attributes:
            type: Type of the section.
            address: The virtual memory address where the section is loaded.
            offset: The offset of the section in the ELF file.
        """
        def __init__(self, type, address, offset):
            self.type = type
            self.address = address
            self.offset = offset

    def __init__(self, file_path):
        """Creates a parser to open and read an ELF file.

        Args:
            file_path: The path to the ELF.

        Raises:
            ElfError if the file is not a valid ELF.
        """
        try:
            self._file = open(file_path, "rb")
        except IOError as e:
            raise ElfError(e)
        try:
            self._loadElfHeader()
            self._section_headers = [
                    self._loadSectionHeader(self._sh_offset + i * self._sh_size)
                    for i in range(self._sh_count)]
        except:
            self._file.close()
            raise

    def __del__(self):
        """Closes the ELF file."""
        self.close()

    def close(self):
        """Closes the ELF file."""
        self._file.close()

    def _seekRead(self, offset, read_size):
        """Reads a byte string at specific offset in the file.

        Args:
            offset: An integer, the offset from the beginning of the file.
            read_size: An integer, number of bytes to read.

        Returns:
            A byte string which is the file content.

        Raises:
            ElfError if fails to seek and read.
        """
        if offset + read_size > self._file_size:
            raise ElfError("Read beyond end of file.")
        try:
            self._file.seek(offset)
            return self._file.read(read_size)
        except IOError as e:
            raise ElfError(e)

    def _seekRead8(self, offset):
        """Reads an 1-byte integer from file."""
        return struct.unpack("B", self._seekRead(offset, 1))[0]

    def _seekRead16(self, offset):
        """Reads a 2-byte integer from file."""
        return struct.unpack("H", self._seekRead(offset, 2))[0]

    def _seekRead32(self, offset):
        """Reads a 4-byte integer from file."""
        return struct.unpack("I", self._seekRead(offset, 4))[0]

    def _seekRead64(self, offset):
        """Reads an 8-byte integer from file."""
        return struct.unpack("Q", self._seekRead(offset, 8))[0]

    def _seekReadString(self, offset):
        """Reads a null-terminated string starting from specific offset.

        Args:
            offset: The offset from the beginning of the file.

        Returns:
            A byte string, excluding the null character.
        """
        ret = ""
        buf_size = 16
        self._file.seek(offset)
        while True:
            try:
                buf = self._file.read(buf_size)
            except IOError as e:
                raise ElfError(e)
            end_index = buf.find('\0')
            if end_index < 0:
                ret += buf
            else:
                ret += buf[:end_index]
                return ret
            if len(buf) != buf_size:
                raise ElfError("Null-terminated string reaches end of file.")

    def _loadElfHeader(self):
        """Loads ElfHeader and initializes attributes"""
        try:
            self._file_size = os.fstat(self._file.fileno()).st_size
        except OSError as e:
            raise ElfError(e)

        magic = self._seekRead(self._MAGIC_OFFSET, 4)
        if magic != self._MAGIC_BYTES:
            raise ElfError("Wrong magic bytes.")
        bitness = self._seekRead8(self._BITNESS_OFFSET)
        if bitness == self._BITNESS_32:
            self.bitness = 32
            self._address_size = 4
            self._offsets = self.ElfOffsets32
            self._seek_read_address = self._seekRead32
        elif bitness == self._BITNESS_64:
            self.bitness = 64
            self._address_size = 8
            self._offsets = self.ElfOffsets64
            self._seek_read_address = self._seekRead64
        else:
            raise ElfError("Wrong bitness value.")

        self._sh_offset = self._seek_read_address(
                self._offsets.SECTION_HEADER_OFFSET)
        self._sh_size = self._seekRead16(self._offsets.SECTION_HEADER_SIZE)
        self._sh_count = self._seekRead16(self._offsets.SECTION_HEADER_COUNT)
        return True

    def _loadSectionHeader(self, offset):
        """Loads a section header from ELF file.

        Args:
            offset: The starting offset of the section header.

        Returns:
            An instance of SectionHeader.
        """
        return self.SectionHeader(
                self._seekRead32(offset + self._offsets.SECTION_TYPE),
                self._seek_read_address(offset + self._offsets.SECTION_ADDRESS),
                self._seek_read_address(offset + self._offsets.SECTION_OFFSET))

    def _loadDtNeeded(self, offset):
        """Reads DT_NEEDED entries from dynamic section.

        Args:
            offset: The offset of the dynamic section from the beginning of
                    the file

        Returns:
            A list of strings, the names of libraries.
        """
        strtab_address = None
        name_offsets = []
        while True:
            tag = self._seek_read_address(offset)
            offset += self._address_size
            value = self._seek_read_address(offset)
            offset += self._address_size

            if tag == self._DT_NULL:
                break
            if tag == self._DT_NEEDED:
                name_offsets.append(value)
            if tag == self._DT_STRTAB:
                strtab_address = value

        if strtab_address is None:
            raise ElfError("Cannot find string table offset in dynamic section")

        try:
            strtab_offset = next(x.offset for x in self._section_headers
                                 if x.address == strtab_address)
        except StopIteration:
            raise ElfError("Cannot find dynamic string table.")

        names = [self._seekReadString(strtab_offset + x)
                 for x in name_offsets]
        return names

    def listDependencies(self):
        """Lists the shared libraries that the ELF depends on.

        Returns:
            A list of strings, the names of the depended libraries.
        """
        deps = []
        for sh in self._section_headers:
            if sh.type == self._SHT_DYNAMIC:
                deps.extend(self._loadDtNeeded(sh.offset))
        return deps
