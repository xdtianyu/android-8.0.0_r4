#/usr/bin/env python3.4
#
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

from acts.test_utils.bt.GattEnum import CharacteristicValueFormat
from acts.test_utils.bt.GattEnum import GattCharacteristic
from acts.test_utils.bt.GattEnum import GattDescriptor
from acts.test_utils.bt.GattEnum import GattService
from acts.test_utils.bt.GattEnum import GattCharTypes
from acts.test_utils.bt.GattEnum import GattCharDesc

STRING_512BYTES = '''
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
11111222223333344444555556666677777888889999900000
111112222233
'''
STRING_50BYTES = '''
11111222223333344444555556666677777888889999900000
'''
STRING_25BYTES = '''
1111122222333334444455555
'''

INVALID_SMALL_DATABASE = {
    'services': [{
        'uuid':
        '00001800-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [{
            'uuid':
            GattCharTypes.GATT_CHARAC_DEVICE_NAME.value,
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x0003,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'Test Database'
        }, {
            'uuid':
            GattCharTypes.GATT_CHARAC_APPEARANCE.value,
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x0005,
            'value_type':
            CharacteristicValueFormat.FORMAT_SINT32.value,
            'offset':
            0,
            'value':
            17
        }, {
            'uuid':
            GattCharTypes.GATT_CHARAC_PERIPHERAL_PREF_CONN.value,
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x0007
        }]
    }, {
        'uuid':
        '00001801-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [{
            'uuid':
            GattCharTypes.GATT_CHARAC_SERVICE_CHANGED.value,
            'properties':
            GattCharacteristic.PROPERTY_INDICATE.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value |
            GattCharacteristic.PERMISSION_WRITE.value,
            'instance_id':
            0x0012,
            'value_type':
            CharacteristicValueFormat.BYTE.value,
            'value': [0x0000],
            'descriptors': [{
                'uuid':
                GattCharDesc.GATT_CLIENT_CHARAC_CFG_UUID.value,
                'permissions':
                GattDescriptor.PERMISSION_READ.value |
                GattDescriptor.PERMISSION_WRITE.value,
            }]
        }, {
            'uuid':
            '0000b004-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x0015,
            'value_type':
            CharacteristicValueFormat.BYTE.value,
            'value': [0x04]
        }]
    }]
}

# Corresponds to the PTS defined LARGE_DB_1
LARGE_DB_1 = {
    'services': [
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            7,
            'characteristics': [{
                'uuid':
                '0000b008-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value |
                GattCharacteristic.PROPERTY_EXTENDED_PROPS.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x08],
                'descriptors': [{
                    'uuid':
                    '0000b015-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                }, {
                    'uuid':
                    '0000b016-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                }, {
                    'uuid':
                    '0000b017-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value |
                    GattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM.value,
                }]
            }]
        },
        {
            'uuid':
            '0000a00d-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_SECONDARY.value,
            'handles':
            6,
            'characteristics': [{
                'uuid':
                '0000b00c-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_EXTENDED_PROPS.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x0C],
            }, {
                'uuid':
                '0000b00b-0000-0000-0123-456789abcdef',
                'properties':
                GattCharacteristic.PROPERTY_EXTENDED_PROPS.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x0B],
            }]
        },
        {
            'uuid':
            '0000a00a-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            10,
            'characteristics': [{
                'uuid':
                '0000b001-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x01],
            }, {
                'uuid':
                '0000b002-0000-0000-0123-456789abcdef',
                'properties':
                GattCharacteristic.PROPERTY_EXTENDED_PROPS.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                STRING_512BYTES,
            }, {
                'uuid':
                '0000b004-0000-0000-0123-456789abcdef',
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                STRING_512BYTES,
            }, {
                'uuid':
                '0000b002-0000-0000-0123-456789abcdef',
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '11111222223333344444555556666677777888889999900000',
            }, {
                'uuid':
                '0000b003-0000-0000-0123-456789abcdef',
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x03],
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            3,
            'characteristics': [{
                'uuid':
                '0000b007-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x07],
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            3,
            'characteristics': [{
                'uuid':
                '0000b006-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value |
                GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE.value |
                GattCharacteristic.PROPERTY_NOTIFY.value |
                GattCharacteristic.PROPERTY_INDICATE.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value |
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x06],
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            12,
            'characteristics': [
                {
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value |
                    GattCharacteristic.PROPERTY_WRITE.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_WRITE.value |
                    GattCharacteristic.PERMISSION_READ.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04],
                },
                {
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value |
                    GattCharacteristic.PROPERTY_WRITE.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_WRITE.value |
                    GattCharacteristic.PERMISSION_READ.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04],
                    'descriptors': [{
                        'uuid':
                        GattCharDesc.GATT_SERVER_CHARAC_CFG_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value |
                        GattDescriptor.PERMISSION_WRITE.value,
                        'value':
                        GattDescriptor.DISABLE_NOTIFICATION_VALUE.value
                    }]
                },
                {
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'properties':
                    0x0,
                    'permissions':
                    0x0,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04],
                    'descriptors': [{
                        'uuid':
                        '0000b012-0000-1000-8000-00805f9b34fb',
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value |
                        GattDescriptor.PERMISSION_WRITE.value,
                        'value': [
                            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                            0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                            0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44,
                            0x55, 0x66, 0x77, 0x88, 0x99, 0x00, 0x11, 0x22,
                            0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                            0x11, 0x22, 0x33
                        ]
                    }]
                },
                {
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04],
                    'descriptors': [{
                        'uuid':
                        '0000b012-0000-1000-8000-00805f9b34fb',
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value,
                        'value': [
                            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                            0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                            0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44,
                            0x55, 0x66, 0x77, 0x88, 0x99, 0x00, 0x11, 0x22,
                            0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                            0x11, 0x22, 0x33
                        ]
                    }]
                },
            ]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            7,
            'characteristics': [{
                'uuid':
                '0000b005-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value |
                GattCharacteristic.PROPERTY_EXTENDED_PROPS.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value |
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x05],
                'descriptors': [{
                    'uuid':
                    GattCharDesc.GATT_CHARAC_EXT_PROPER_UUID.value,
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value,
                    'value': [0x03, 0x00]
                }, {
                    'uuid':
                    GattCharDesc.GATT_CHARAC_USER_DESC_UUID.value,
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x65, 0x66, 0x67, 0x68, 0x69, 0x70, 0x71, 0x72, 0x73,
                        0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x80, 0x81, 0x82,
                        0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x90
                    ]
                }, {
                    'uuid':
                    GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                    'permissions':
                    GattDescriptor.PERMISSION_READ_ENCRYPTED_MITM.value,
                    'value': [0x00, 0x01, 0x30, 0x01, 0x11, 0x31]
                }, {
                    'uuid':
                    '0000d5d4-0000-0000-0123-456789abcdef',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value,
                    'value': [0x44]
                }]
            }]
        },
        {
            'uuid':
            '0000a00c-0000-0000-0123-456789abcdef',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            7,
            'characteristics': [{
                'uuid':
                '0000b009-0000-0000-0123-456789abcdef',
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value |
                GattCharacteristic.PROPERTY_EXTENDED_PROPS.value |
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value |
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x09],
                'descriptors': [{
                    'uuid':
                    GattCharDesc.GATT_CHARAC_EXT_PROPER_UUID.value,
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value,
                    'value':
                    GattDescriptor.ENABLE_NOTIFICATION_VALUE.value
                }, {
                    'uuid':
                    '0000d9d2-0000-0000-0123-456789abcdef',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [0x22]
                }, {
                    'uuid':
                    '0000d9d3-0000-0000-0123-456789abcdef',
                    'permissions':
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [0x33]
                }]
            }]
        },
        {
            'uuid':
            '0000a00f-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            18,
            'characteristics': [
                {
                    'uuid':
                    '0000b00e-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    "Length is ",
                    'descriptors': [{
                        'uuid':
                        GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value,
                        'value': [0x19, 0x00, 0x00, 0x30, 0x01, 0x00, 0x00]
                    }]
                },
                {
                    'uuid':
                    '0000b00f-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value |
                    GattCharacteristic.PROPERTY_WRITE.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x65],
                    'descriptors': [{
                        'uuid':
                        GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value,
                        'value': [0x04, 0x00, 0x01, 0x27, 0x01, 0x01, 0x00]
                    }]
                },
                {
                    'uuid':
                    '0000b006-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value |
                    GattCharacteristic.PROPERTY_WRITE.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x34, 0x12],
                    'descriptors': [{
                        'uuid':
                        GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value,
                        'value': [0x06, 0x00, 0x10, 0x27, 0x01, 0x02, 0x00]
                    }]
                },
                {
                    'uuid':
                    '0000b007-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value |
                    GattCharacteristic.PROPERTY_WRITE.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04, 0x03, 0x02, 0x01],
                    'descriptors': [{
                        'uuid':
                        GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value,
                        'value': [0x08, 0x00, 0x17, 0x27, 0x01, 0x03, 0x00]
                    }]
                },
                {
                    'uuid':
                    '0000b010-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x65, 0x34, 0x12, 0x04, 0x03, 0x02, 0x01],
                    'descriptors': [{
                        'uuid':
                        GattCharDesc.GATT_CHARAC_AGREG_FMT_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value,
                        'value': [0xa6, 0x00, 0xa9, 0x00, 0xac, 0x00]
                    }]
                },
                {
                    'uuid':
                    '0000b011-0000-1000-8000-00805f9b34fb',
                    'properties':
                    GattCharacteristic.WRITE_TYPE_SIGNED.value
                    |  #for some reason 0x40 is not working...
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x12]
                }
            ]
        },
        {
            'uuid':
            '0000a00c-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            30,
            'characteristics': [{
                'uuid':
                '0000b00a-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x0a],
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                "111112222233333444445",
                'descriptors': [{
                    'uuid':
                    '0000b012-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x11
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                "2222233333444445555566",
                'descriptors': [{
                    'uuid':
                    '0000b013-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x11, 0x22
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                "33333444445555566666777",
                'descriptors': [{
                    'uuid':
                    '0000b014-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x11, 0x22, 0x33
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33
                ],
                'descriptors': [{
                    'uuid':
                    '0000b012-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x56,
                        0x78, 0x90, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34,
                        0x56, 0x78, 0x90, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                        0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44
                ],
                'descriptors': [{
                    'uuid':
                    '0000b013-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x56,
                        0x78, 0x90, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34,
                        0x56, 0x78, 0x90, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                        0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x11, 0x22, 0x33, 0x44, 0x55
                ],
                'descriptors': [{
                    'uuid':
                    '0000b014-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x56,
                        0x78, 0x90, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34,
                        0x56, 0x78, 0x90, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                        0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                "1111122222333334444455555666667777788888999",
                'descriptors': [{
                    'uuid':
                    '0000b012-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x56,
                        0x78, 0x90, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34,
                        0x56, 0x78, 0x90, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                        0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                "22222333334444455555666667777788888999990000",
                'descriptors': [{
                    'uuid':
                    '0000b013-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x56,
                        0x78, 0x90, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34,
                        0x56, 0x78, 0x90, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                        0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44
                    ]
                }]
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'properties':
                GattCharacteristic.PROPERTY_READ.value |
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                "333334444455555666667777788888999990000011111",
                'descriptors': [{
                    'uuid':
                    '0000b014-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [
                        0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99,
                        0x00, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34, 0x56,
                        0x78, 0x90, 0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x34,
                        0x56, 0x78, 0x90, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66,
                        0x77, 0x88, 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55
                    ]
                }]
            }]
        },
    ]
}

# Corresponds to the PTS defined LARGE_DB_2
LARGE_DB_2 = {
    'services': [
        {
            'uuid':
            '0000a00c-0000-0000-0123-456789abdcef',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b00a-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0003,
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x04],
            }, {
                'uuid':
                '0000b0002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0005,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '111112222233333444445',
            }, {
                'uuid':
                '0000b0002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0007,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '2222233333444445555566',
            }, {
                'uuid':
                '0000b0002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0009,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '33333444445555566666777',
            }, {
                'uuid':
                '0000b0002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x000b,
                'properties':
                0x0a0,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '1111122222333334444455555666667777788888999',
            }, {
                'uuid':
                '0000b0002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x000d,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '22222333334444455555666667777788888999990000',
            }, {
                'uuid':
                '0000b0002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x000f,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '333334444455555666667777788888999990000011111',
            }]
        },
        {
            'uuid':
            '0000a00c-0000-0000-0123-456789abcdef',
            'handles':
            5,
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b009-0000-0000-0123-456789abcdef',
                'instance_id':
                0x0023,
                'properties':
                0x8a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x09],
                'descriptors': [{
                    'uuid':
                    '0000d9d2-0000-0000-0123-456789abcdef',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [0x22]
                }, {
                    'uuid':
                    '0000d9d3-0000-0000-0123-456789abcdef',
                    'permissions':
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [0x33]
                }, {
                    'uuid':
                    GattCharDesc.GATT_CHARAC_EXT_PROPER_UUID.value,
                    'permissions':
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value':
                    GattDescriptor.ENABLE_NOTIFICATION_VALUE.value
                }]
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b007-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0012,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x04],
            }]
        },
    ]
}

DB_TEST = {
    'services': [{
        'uuid':
        '0000a00b-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [
            {
                'uuid':
                '0000b004-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x01],
                'descriptors': [{
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'permissions':
                    GattDescriptor.PERMISSION_READ.value |
                    GattDescriptor.PERMISSION_WRITE.value,
                    'value': [0x01] * 30
                }]
            },
        ]
    }]
}

PTS_TEST2 = {
    'services': [{
        'uuid':
        '0000a00b-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [
            {
                'uuid': '000018ba-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000060aa-0000-0000-0123-456789abcdef',
                'properties': 0x02,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '00000af2-0000-1000-8000-00805f9b34fb',
                'properties': 0x20,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000af2-0000-1000-8000-00805f9b34fb',
                'properties': 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000004d5e-0000-1000-8000-00805f9b34fb',
                'properties': 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000001b44-0000-1000-8000-00805f9b34fb',
                'properties': 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000006b98-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08 | 0x10 | 0x04,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '00000247f-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '00000247f-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '00000247f-0000-1000-8000-00805f9b34fb',
                'properties': 0x00,
                'permissions': 0x00,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '00000247f-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000d62-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08 | 0x80,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000002e85-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000004a64-0000-0000-0123-456789abcdef',
                'properties': 0x02 | 0x08 | 0x80,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000005b4a-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000001c81-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000006b98-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000001b44-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000c55-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '0000014dd-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000c55-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000c55-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000c55-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000000c55-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '00000008f-0000-1000-8000-00805f9b34fb',
                'properties': 0x02,
                'permissions': 0x10,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid':
                '000000af2-0000-1000-8000-00805f9b34fb',
                'properties':
                0x02 | 0x08,
                'permissions':
                0x10 | 0x01,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [
                    0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0x00,
                    0x12, 0x34, 0x56, 0x78, 0x90, 0x12, 0x32
                ],
            },
            {
                'uuid': '000002aad-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000002ab0-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
            {
                'uuid': '000002ab3-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_512BYTES,
            },
        ]
    }]
}

PTS_TEST = {
    'services': [{
        'uuid':
        '0000a00b-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [
            {
                'uuid': '000018ba-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_25BYTES,
            },
            {
                'uuid': '000060aa-0000-1000-8000-00805f9b34fb',
                'properties': 0x02 | 0x08,
                'permissions': 0x10 | 0x01,
                'value_type': CharacteristicValueFormat.STRING.value,
                'value': STRING_25BYTES,
            },
        ]
    }]
}

# Corresponds to the PTS defined LARGE_DB_3
LARGE_DB_3 = {
    'services': [
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [
                {
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x0003,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04],
                },
                {
                    'uuid':
                    '0000b004-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x0013,
                    'properties':
                    0x10,
                    'permissions':
                    0x17,
                    'value_type':
                    CharacteristicValueFormat.BYTE.value,
                    'value': [0x04],
                    'descriptors': [
                        {
                            'uuid':
                            GattCharDesc.GATT_CHARAC_EXT_PROPER_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x09]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_CHARAC_USER_DESC_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_CLIENT_CHARAC_CFG_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x01, 0x00]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_SERVER_CHARAC_CFG_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_CHARAC_AGREG_FMT_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_CHARAC_VALID_RANGE_UUID.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_EXTERNAL_REPORT_REFERENCE.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                        {
                            'uuid':
                            GattCharDesc.GATT_REPORT_REFERENCE.value,
                            'permissions':
                            GattDescriptor.PERMISSION_READ.value |
                            GattDescriptor.PERMISSION_WRITE.value,
                            'value': [0x22]
                        },
                    ]
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_SERVICE_CHANGED.value,
                    'instance_id':
                    0x0023,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_APPEARANCE.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_PERIPHERAL_PRIV_FLAG.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_RECONNECTION_ADDRESS.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_SYSTEM_ID.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_MODEL_NUMBER_STRING.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_SERIAL_NUMBER_STRING.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_FIRMWARE_REVISION_STRING.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_HARDWARE_REVISION_STRING.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_SOFTWARE_REVISION_STRING.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_MANUFACTURER_NAME_STRING.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
                {
                    'uuid':
                    GattCharTypes.GATT_CHARAC_PNP_ID.value,
                    'properties':
                    GattCharacteristic.PROPERTY_READ.value,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
            ]
        },
        {
            'uuid':
            '0000a00d-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_SECONDARY.value,
            'handles':
            5,
            'characteristics': [{
                'uuid':
                '0000b00c-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0023,
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x0c],
            }, {
                'uuid':
                '0000b00b-0000-0000-0123-456789abcdef',
                'instance_id':
                0x0025,
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x0b],
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b008-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0032,
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x08],
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b007-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0042,
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x07],
            }]
        },
        {
            'uuid':
            '0000a00b-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b006-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0052,
                'properties':
                0x3e,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value |
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x06],
            }]
        },
        {
            'uuid':
            '0000a00a-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            10,
            'characteristics': [{
                'uuid':
                '0000b001-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0074,
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x01],
            }, {
                'uuid':
                '0000b002-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0076,
                'properties':
                0x0a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.STRING.value,
                'value':
                '11111222223333344444555556666677777888889999900000',
            }, {
                'uuid':
                '0000b003-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0x0078,
                'properties':
                GattCharacteristic.PROPERTY_WRITE.value,
                'permissions':
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x03],
            }]
        },
        {
            'uuid':
            '0000a00c-0000-0000-0123-456789abcdef',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'handles':
            10,
            'characteristics': [{
                'uuid':
                '0000b009-0000-0000-0123-456789abcdef',
                'instance_id':
                0x0082,
                'properties':
                0x8a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x09],
                'descriptors': [
                    {
                        'uuid':
                        '0000b009-0000-0000-0123-456789abcdef',
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value |
                        GattDescriptor.PERMISSION_WRITE.value,
                        'value': [0x09]
                    },
                    {
                        'uuid':
                        '0000d9d2-0000-0000-0123-456789abcdef',
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value |
                        GattDescriptor.PERMISSION_WRITE.value,
                        'value': [0x22]
                    },
                    {
                        'uuid': GattCharDesc.GATT_CHARAC_EXT_PROPER_UUID.value,
                        'permissions': GattDescriptor.PERMISSION_READ.value,
                        'value': [0x01, 0x00]
                    },
                    {
                        'uuid': '0000d9d3-0000-0000-0123-456789abcdef',
                        'permissions': GattDescriptor.PERMISSION_WRITE.value,
                        'value': [0x22]
                    },
                ]
            }]
        },
        {
            'uuid':
            '0000a00b-0000-0000-0123-456789abcdef',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b009-0000-0000-0123-456789abcdef',
                'instance_id':
                0x0092,
                'properties':
                0x8a,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value |
                GattCharacteristic.PERMISSION_WRITE.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x05],
                'descriptors': [
                    {
                        'uuid':
                        GattCharDesc.GATT_CHARAC_USER_DESC_UUID.value,
                        'permissions':
                        GattDescriptor.PERMISSION_READ.value |
                        GattDescriptor.PERMISSION_WRITE.value,
                        'value': [0] * 26
                    },
                    {
                        'uuid': GattCharDesc.GATT_CHARAC_EXT_PROPER_UUID.value,
                        'permissions': GattDescriptor.PERMISSION_READ.value,
                        'value': [0x03, 0x00]
                    },
                    {
                        'uuid': '0000d5d4-0000-0000-0123-456789abcdef',
                        'permissions': GattDescriptor.PERMISSION_READ.value,
                        'value': [0x44]
                    },
                    {
                        'uuid': GattCharDesc.GATT_CHARAC_FMT_UUID.value,
                        'permissions': GattDescriptor.PERMISSION_READ.value,
                        'value': [0x04, 0x00, 0x01, 0x30, 0x01, 0x11, 0x31]
                    },
                ]
            }]
        },
        {
            'uuid':
            '0000a00c-0000-0000-0123-456789abcdef',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [
                {
                    'uuid': '0000b00a-0000-1000-8000-00805f9b34fb',
                    'instance_id': 0x00a2,
                    'properties': GattCharacteristic.PROPERTY_READ.value,
                    'permissions': GattCharacteristic.PERMISSION_READ.value,
                    'value_type': CharacteristicValueFormat.BYTE.value,
                    'value': [0x0a],
                },
                {
                    'uuid':
                    '0000b002-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x00a4,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '111112222233333444445',
                },
                {
                    'uuid':
                    '0000b002-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x00a6,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '2222233333444445555566',
                },
                {
                    'uuid':
                    '0000b002-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x00a8,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '33333444445555566666777',
                },
                {
                    'uuid':
                    '0000b002-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x00aa,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '1111122222333334444455555666667777788888999',
                },
                {
                    'uuid':
                    '0000b002-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x00ac,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '22222333334444455555666667777788888999990000',
                },
                {
                    'uuid':
                    '0000b002-0000-1000-8000-00805f9b34fb',
                    'instance_id':
                    0x00ae,
                    'properties':
                    0x0a,
                    'permissions':
                    GattCharacteristic.PERMISSION_READ.value |
                    GattCharacteristic.PERMISSION_WRITE.value,
                    'value_type':
                    CharacteristicValueFormat.STRING.value,
                    'value':
                    '333334444455555666667777788888999990000011111',
                },
            ]
        },
        {
            'uuid':
            '0000a00e-0000-1000-8000-00805f9b34fb',
            'type':
            GattService.SERVICE_TYPE_PRIMARY.value,
            'characteristics': [{
                'uuid':
                '0000b00d-0000-1000-8000-00805f9b34fb',
                'instance_id':
                0xffff,
                'properties':
                GattCharacteristic.PROPERTY_READ.value,
                'permissions':
                GattCharacteristic.PERMISSION_READ.value,
                'value_type':
                CharacteristicValueFormat.BYTE.value,
                'value': [0x0d],
            }]
        },
    ]
}

TEST_DB_1 = {
    'services': [{
        'uuid':
        '0000180d-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'handles':
        4,
        'characteristics': [{
            'uuid':
            '00002a29-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value |
            GattCharacteristic.PROPERTY_WRITE.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value |
            GattCharacteristic.PERMISSION_WRITE.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'test',
            'instance_id':
            0x002a,
            'descriptors': [{
                'uuid':
                GattCharDesc.GATT_CHARAC_USER_DESC_UUID.value,
                'permissions':
                GattDescriptor.PERMISSION_READ.value,
                'value': [0x01]
            }]
        }]
    }]
}

TEST_DB_2 = {
    'services': [{
        'uuid':
        '0000180d-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'handles':
        4,
        'characteristics': [{
            'uuid':
            '00002a29-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'test',
            'instance_id':
            0x002a,
        }, {
            'uuid':
            '00002a30-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'test',
            'instance_id':
            0x002b,
        }]
    }]
}

TEST_DB_3 = {
    'services': [{
        'uuid':
        '0000180d-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'handles':
        4,
        'characteristics': [{
            'uuid':
            '00002a29-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value |
            GattCharacteristic.PROPERTY_WRITE.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value |
            GattCharacteristic.PERMISSION_WRITE.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'test',
            'instance_id':
            0x002a,
            'descriptors': [{
                'uuid':
                GattCharDesc.GATT_CHARAC_USER_DESC_UUID.value,
                'permissions':
                GattDescriptor.PERMISSION_READ.value,
                'value': [0x01]
            }, {
                'uuid':
                '00002a20-0000-1000-8000-00805f9b34fb',
                'permissions':
                GattDescriptor.PERMISSION_READ.value |
                GattDescriptor.PERMISSION_WRITE.value,
                'instance_id':
                0x002c,
                'value': [0x01]
            }]
        }, {
            'uuid':
            '00002a30-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value |
            GattCharacteristic.PROPERTY_WRITE.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value |
            GattCharacteristic.PERMISSION_WRITE.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'test',
            'instance_id':
            0x002b,
        }]
    }]
}

TEST_DB_4 = {
    'services': [{
        'uuid':
        '0000180d-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'handles':
        4,
        'characteristics': [{
            'uuid':
            '00002a29-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            "test",
            'instance_id':
            0x002a,
            'descriptors': [{
                'uuid':
                GattCharDesc.GATT_CHARAC_USER_DESC_UUID.value,
                'permissions':
                GattDescriptor.PERMISSION_READ_ENCRYPTED_MITM.value,
                'value': [0] * 512
            }]
        }]
    }]
}

TEST_DB_5 = {
    'services': [{
        'uuid':
        '0000180d-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [{
            'uuid':
            'b2c83efa-34ca-11e6-ac61-9e71128cae77',
            'properties':
            GattCharacteristic.PROPERTY_WRITE.value |
            GattCharacteristic.PROPERTY_READ.value |
            GattCharacteristic.PROPERTY_NOTIFY.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value |
            GattCharacteristic.PERMISSION_WRITE.value,
            'value_type':
            CharacteristicValueFormat.BYTE.value,
            'value': [0x1],
            'instance_id':
            0x002c,
            'descriptors': [{
                'uuid':
                '00002902-0000-1000-8000-00805f9b34fb',
                'permissions':
                GattDescriptor.PERMISSION_READ.value |
                GattDescriptor.PERMISSION_WRITE.value,
            }]
        }]
    }]
}

TEST_DB_6 = {
    'services': [{
        'uuid':
        '0000180d-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'handles':
        4,
        'characteristics': [{
            'uuid':
            '00002a29-0000-1000-8000-00805f9b34fb',
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'test',
            'instance_id':
            0x002a,
            'descriptors': [{
                'uuid':
                '00002a19-0000-1000-8000-00805f9b34fb',
                'permissions':
                GattDescriptor.PERMISSION_READ.value,
                'value': [0x01] * 30
            }]
        }]
    }]
}

SIMPLE_READ_DESCRIPTOR = {
    'services': [{
        'uuid':
        '0000a00a-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [{
            'uuid':
            'aa7edd5a-4d1d-4f0e-883a-d145616a1630',
            'properties':
            GattCharacteristic.PROPERTY_READ.value,
            'permissions':
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x002a,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'Test Database',
            'descriptors': [{
                'uuid':
                GattCharDesc.GATT_CLIENT_CHARAC_CFG_UUID.value,
                'permissions':
                GattDescriptor.PERMISSION_READ.value,
            }]
        }]
    }]
}

CHARACTERISTIC_PROPERTY_WRITE_NO_RESPONSE = {
    'services': [{
        'uuid':
        '0000a00a-0000-1000-8000-00805f9b34fb',
        'type':
        GattService.SERVICE_TYPE_PRIMARY.value,
        'characteristics': [{
            'uuid':
            'aa7edd5a-4d1d-4f0e-883a-d145616a1630',
            'properties':
            GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE.value,
            'permissions':
            GattCharacteristic.PERMISSION_WRITE.value |
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x0042,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'Test Database'
        }, {
            'uuid':
            'aa7edd6a-4d1d-4f0e-883a-d145616a1630',
            'properties':
            GattCharacteristic.PROPERTY_WRITE_NO_RESPONSE.value,
            'permissions':
            GattCharacteristic.PERMISSION_WRITE.value |
            GattCharacteristic.PERMISSION_READ.value,
            'instance_id':
            0x004d,
            'value_type':
            CharacteristicValueFormat.STRING.value,
            'value':
            'Test Database'
        }]
    }]
}

GATT_SERVER_DB_MAPPING = {
    'LARGE_DB_1':
    LARGE_DB_1,
    'LARGE_DB_3':
    LARGE_DB_3,
    'INVALID_SMALL_DATABASE':
    INVALID_SMALL_DATABASE,
    'SIMPLE_READ_DESCRIPTOR':
    SIMPLE_READ_DESCRIPTOR,
    'CHARACTERISTIC_PROPERTY_WRITE_NO_RESPONSE':
    CHARACTERISTIC_PROPERTY_WRITE_NO_RESPONSE,
    'TEST_DB_1':
    TEST_DB_1,
    'TEST_DB_2':
    TEST_DB_2,
    'TEST_DB_3':
    TEST_DB_3,
    'TEST_DB_4':
    TEST_DB_4,
    'TEST_DB_5':
    TEST_DB_5,
    'LARGE_DB_3_PLUS':
    LARGE_DB_3,
    'DB_TEST':
    DB_TEST,
    'PTS_TEST':
    PTS_TEST,
    'PTS_TEST2':
    PTS_TEST2,
    'TEST_DB_6':
    TEST_DB_6,
}
