/******************************************************************************
 *
 *  Copyright (C) 2003-2013 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

/******************************************************************************
 *
 *  This is the public interface file for BTA GATT.
 *
 ******************************************************************************/

#ifndef BTA_GATT_API_H
#define BTA_GATT_API_H

#include "bta_api.h"
#include "gatt_api.h"
#include "osi/include/list.h"

#include <base/callback_forward.h>
#include <vector>

using std::vector;

#ifndef BTA_GATT_DEBUG
#define BTA_GATT_DEBUG false
#endif

/*****************************************************************************
 *  Constants and data types
 ****************************************************************************/
/**************************
 *  Common Definitions
 **************************/
/* GATT ID */
typedef struct {
  tBT_UUID uuid;   /* uuid of the attribute */
  uint8_t inst_id; /* instance ID */
} __attribute__((packed)) tBTA_GATT_ID;

/* Success code and error codes */
#define BTA_GATT_OK GATT_SUCCESS
#define BTA_GATT_INVALID_HANDLE GATT_INVALID_HANDLE             /* 0x0001 */
#define BTA_GATT_READ_NOT_PERMIT GATT_READ_NOT_PERMIT           /* 0x0002 */
#define BTA_GATT_WRITE_NOT_PERMIT GATT_WRITE_NOT_PERMIT         /* 0x0003 */
#define BTA_GATT_INVALID_PDU GATT_INVALID_PDU                   /* 0x0004 */
#define BTA_GATT_INSUF_AUTHENTICATION GATT_INSUF_AUTHENTICATION /* 0x0005 */
#define BTA_GATT_REQ_NOT_SUPPORTED GATT_REQ_NOT_SUPPORTED       /* 0x0006 */
#define BTA_GATT_INVALID_OFFSET GATT_INVALID_OFFSET             /* 0x0007 */
#define BTA_GATT_INSUF_AUTHORIZATION GATT_INSUF_AUTHORIZATION   /* 0x0008 */
#define BTA_GATT_PREPARE_Q_FULL GATT_PREPARE_Q_FULL             /* 0x0009 */
#define BTA_GATT_NOT_FOUND GATT_NOT_FOUND                       /* 0x000a */
#define BTA_GATT_NOT_LONG GATT_NOT_LONG                         /* 0x000b */
#define BTA_GATT_INSUF_KEY_SIZE GATT_INSUF_KEY_SIZE             /* 0x000c */
#define BTA_GATT_INVALID_ATTR_LEN GATT_INVALID_ATTR_LEN         /* 0x000d */
#define BTA_GATT_ERR_UNLIKELY GATT_ERR_UNLIKELY                 /* 0x000e */
#define BTA_GATT_INSUF_ENCRYPTION GATT_INSUF_ENCRYPTION         /* 0x000f */
#define BTA_GATT_UNSUPPORT_GRP_TYPE GATT_UNSUPPORT_GRP_TYPE     /* 0x0010 */
#define BTA_GATT_INSUF_RESOURCE GATT_INSUF_RESOURCE             /* 0x0011 */

#define BTA_GATT_NO_RESOURCES GATT_NO_RESOURCES           /* 0x80 */
#define BTA_GATT_INTERNAL_ERROR GATT_INTERNAL_ERROR       /* 0x81 */
#define BTA_GATT_WRONG_STATE GATT_WRONG_STATE             /* 0x82 */
#define BTA_GATT_DB_FULL GATT_DB_FULL                     /* 0x83 */
#define BTA_GATT_BUSY GATT_BUSY                           /* 0x84 */
#define BTA_GATT_ERROR GATT_ERROR                         /* 0x85 */
#define BTA_GATT_CMD_STARTED GATT_CMD_STARTED             /* 0x86 */
#define BTA_GATT_ILLEGAL_PARAMETER GATT_ILLEGAL_PARAMETER /* 0x87 */
#define BTA_GATT_PENDING GATT_PENDING                     /* 0x88 */
#define BTA_GATT_AUTH_FAIL GATT_AUTH_FAIL                 /* 0x89 */
#define BTA_GATT_MORE GATT_MORE                           /* 0x8a */
#define BTA_GATT_INVALID_CFG GATT_INVALID_CFG             /* 0x8b */
#define BTA_GATT_SERVICE_STARTED GATT_SERVICE_STARTED     /* 0x8c */
#define BTA_GATT_ENCRYPED_MITM GATT_ENCRYPED_MITM         /* GATT_SUCCESS */
#define BTA_GATT_ENCRYPED_NO_MITM GATT_ENCRYPED_NO_MITM   /* 0x8d */
#define BTA_GATT_NOT_ENCRYPTED GATT_NOT_ENCRYPTED         /* 0x8e */
#define BTA_GATT_CONGESTED GATT_CONGESTED                 /* 0x8f */

#define BTA_GATT_DUP_REG 0x90      /* 0x90 */
#define BTA_GATT_ALREADY_OPEN 0x91 /* 0x91 */
#define BTA_GATT_CANCEL 0x92       /* 0x92 */

/* 0xE0 ~ 0xFC reserved for future use */
#define BTA_GATT_CCC_CFG_ERR                                              \
  GATT_CCC_CFG_ERR /* 0xFD Client Characteristic Configuration Descriptor \
                      Improperly Configured */
#define BTA_GATT_PRC_IN_PROGRESS \
  GATT_PRC_IN_PROGRESS /* 0xFE Procedure Already in progress */
#define BTA_GATT_OUT_OF_RANGE \
  GATT_OUT_OF_RANGE /* 0xFFAttribute value out of range */

typedef uint8_t tBTA_GATT_STATUS;

#define BTA_GATT_INVALID_CONN_ID GATT_INVALID_CONN_ID

/* Client callback function events */
#define BTA_GATTC_DEREG_EVT 1        /* GATT client deregistered event */
#define BTA_GATTC_OPEN_EVT 2         /* GATTC open request status  event */
#define BTA_GATTC_CLOSE_EVT 5        /* GATTC  close request status event */
#define BTA_GATTC_SEARCH_CMPL_EVT 6  /* GATT discovery complete event */
#define BTA_GATTC_SEARCH_RES_EVT 7   /* GATT discovery result event */
#define BTA_GATTC_NOTIF_EVT 10       /* GATT attribute notification event */
#define BTA_GATTC_EXEC_EVT 12        /* execute write complete event */
#define BTA_GATTC_ACL_EVT 13         /* ACL up event */
#define BTA_GATTC_CANCEL_OPEN_EVT 14 /* cancel open event */
#define BTA_GATTC_SRVC_CHG_EVT 15    /* service change event */
#define BTA_GATTC_ENC_CMPL_CB_EVT 17 /* encryption complete callback event */
#define BTA_GATTC_CFG_MTU_EVT 18     /* configure MTU complete event */
#define BTA_GATTC_CONGEST_EVT 24     /* Congestion event */
#define BTA_GATTC_PHY_UPDATE_EVT 25  /* PHY change event */
#define BTA_GATTC_CONN_UPDATE_EVT 26 /* Connection parameters update event */

typedef uint8_t tBTA_GATTC_EVT;

typedef tGATT_IF tBTA_GATTC_IF;

typedef struct {
  uint16_t unit;  /* as UUIUD defined by SIG */
  uint16_t descr; /* as UUID as defined by SIG */
  tGATT_FORMAT format;
  int8_t exp;
  uint8_t name_spc; /* The name space of the description */
} tBTA_GATT_CHAR_PRES;

#define BTA_GATT_CLT_CONFIG_NONE GATT_CLT_CONFIG_NONE /* 0x0000    */
#define BTA_GATT_CLT_CONFIG_NOTIFICATION \
  GATT_CLT_CONFIG_NOTIFICATION                                    /* 0x0001 */
#define BTA_GATT_CLT_CONFIG_INDICATION GATT_CLT_CONFIG_INDICATION /* 0x0002 */
typedef uint16_t tBTA_GATT_CLT_CHAR_CONFIG;

/* characteristic descriptor: server configuration value
*/
#define BTA_GATT_SVR_CONFIG_NONE GATT_SVR_CONFIG_NONE           /* 0x0000 */
#define BTA_GATT_SVR_CONFIG_BROADCAST GATT_SVR_CONFIG_BROADCAST /*  0x0001 */
typedef uint16_t tBTA_GATT_SVR_CHAR_CONFIG;

/* Characteristic Aggregate Format attribute value
*/
#define BTA_GATT_AGGR_HANDLE_NUM_MAX 10
typedef struct {
  uint8_t num_handle;
  uint16_t handle_list[BTA_GATT_AGGR_HANDLE_NUM_MAX];
} tBTA_GATT_CHAR_AGGRE;
typedef tGATT_VALID_RANGE tBTA_GATT_VALID_RANGE;

typedef struct {
  uint16_t len;
  uint8_t* p_value;
} tBTA_GATT_UNFMT;

#define BTA_GATT_MAX_ATTR_LEN GATT_MAX_ATTR_LEN

#define BTA_GATTC_TYPE_WRITE GATT_WRITE
#define BTA_GATTC_TYPE_WRITE_NO_RSP GATT_WRITE_NO_RSP
typedef uint8_t tBTA_GATTC_WRITE_TYPE;

#define BTA_GATT_CONN_UNKNOWN 0
#define BTA_GATT_CONN_L2C_FAILURE \
  GATT_CONN_L2C_FAILURE /* general l2cap resource failure */
#define BTA_GATT_CONN_TIMEOUT GATT_CONN_TIMEOUT /* 0x08 connection timeout  */
#define BTA_GATT_CONN_TERMINATE_PEER_USER \
  GATT_CONN_TERMINATE_PEER_USER /* 0x13 connection terminate by peer user  */
#define BTA_GATT_CONN_TERMINATE_LOCAL_HOST \
  GATT_CONN_TERMINATE_LOCAL_HOST /* 0x16 connectionterminated by local host */
#define BTA_GATT_CONN_FAIL_ESTABLISH \
  GATT_CONN_FAIL_ESTABLISH /* 0x03E connection fail to establish  */
#define BTA_GATT_CONN_LMP_TIMEOUT \
  GATT_CONN_LMP_TIMEOUT /* 0x22 connection fail for LMP response tout */
#define BTA_GATT_CONN_CANCEL \
  GATT_CONN_CANCEL                /* 0x0100 L2CAP connection cancelled  */
#define BTA_GATT_CONN_NONE 0x0101 /* 0x0101 no connection to cancel  */
typedef uint16_t tBTA_GATT_REASON;

#define BTA_GATTC_MULTI_MAX GATT_MAX_READ_MULTI_HANDLES

typedef struct {
  uint8_t num_attr;
  uint16_t handles[BTA_GATTC_MULTI_MAX];
} tBTA_GATTC_MULTI;

#define BTA_GATT_AUTH_REQ_NONE GATT_AUTH_REQ_NONE
#define BTA_GATT_AUTH_REQ_NO_MITM \
  GATT_AUTH_REQ_NO_MITM /* unauthenticated encryption */
#define BTA_GATT_AUTH_REQ_MITM                   \
  GATT_AUTH_REQ_MITM /* authenticated encryption \
                        */
#define BTA_GATT_AUTH_REQ_SIGNED_NO_MITM GATT_AUTH_REQ_SIGNED_NO_MITM
#define BTA_GATT_AUTH_REQ_SIGNED_MITM GATT_AUTH_REQ_SIGNED_MITM

typedef tGATT_AUTH_REQ tBTA_GATT_AUTH_REQ;

enum {
  BTA_GATTC_ATTR_TYPE_INCL_SRVC,
  BTA_GATTC_ATTR_TYPE_CHAR,
  BTA_GATTC_ATTR_TYPE_CHAR_DESCR,
  BTA_GATTC_ATTR_TYPE_SRVC
};
typedef uint8_t tBTA_GATTC_ATTR_TYPE;

typedef struct {
  tBT_UUID uuid;
  uint16_t s_handle;
  uint16_t e_handle; /* used for service only */
  uint8_t attr_type;
  uint8_t id;
  uint8_t prop;              /* used when attribute type is characteristic */
  bool is_primary;           /* used when attribute type is service */
  uint16_t incl_srvc_handle; /* used when attribute type is included service */
} tBTA_GATTC_NV_ATTR;

/* callback data structure */
typedef struct {
  tBTA_GATT_STATUS status;
  tBTA_GATTC_IF client_if;
  tBT_UUID app_uuid;
} tBTA_GATTC_REG;

typedef struct {
  uint16_t conn_id;
  tBTA_GATT_STATUS status;
  uint16_t handle;
  uint16_t len;
  uint8_t value[BTA_GATT_MAX_ATTR_LEN];
} tBTA_GATTC_READ;

typedef struct {
  uint16_t conn_id;
  tBTA_GATT_STATUS status;
  uint16_t handle;
} tBTA_GATTC_WRITE;

typedef struct {
  uint16_t conn_id;
  tBTA_GATT_STATUS status;
} tBTA_GATTC_EXEC_CMPL;

typedef struct {
  uint16_t conn_id;
  tBTA_GATT_STATUS status;
} tBTA_GATTC_SEARCH_CMPL;

typedef struct {
  uint16_t conn_id;
  tBTA_GATT_ID service_uuid;
} tBTA_GATTC_SRVC_RES;

typedef struct {
  uint16_t conn_id;
  tBTA_GATT_STATUS status;
  uint16_t mtu;
} tBTA_GATTC_CFG_MTU;

typedef struct {
  tBTA_GATT_STATUS status;
  uint16_t conn_id;
  tBTA_GATTC_IF client_if;
  BD_ADDR remote_bda;
  tBTA_TRANSPORT transport;
  uint16_t mtu;
} tBTA_GATTC_OPEN;

typedef struct {
  tBTA_GATT_STATUS status;
  uint16_t conn_id;
  tBTA_GATTC_IF client_if;
  BD_ADDR remote_bda;
  tBTA_GATT_REASON reason; /* disconnect reason code, not useful when connect
                              event is reported */
} tBTA_GATTC_CLOSE;

typedef struct {
  uint16_t conn_id;
  BD_ADDR bda;
  uint16_t handle;
  uint16_t len;
  uint8_t value[BTA_GATT_MAX_ATTR_LEN];
  bool is_notify;
} tBTA_GATTC_NOTIFY;

typedef struct {
  uint16_t conn_id;
  bool congested; /* congestion indicator */
} tBTA_GATTC_CONGEST;

typedef struct {
  tBTA_GATT_STATUS status;
  tBTA_GATTC_IF client_if;
  uint16_t conn_id;
  BD_ADDR remote_bda;
} tBTA_GATTC_OPEN_CLOSE;

typedef struct {
  tBTA_GATTC_IF client_if;
  BD_ADDR remote_bda;
} tBTA_GATTC_ENC_CMPL_CB;

typedef struct {
  tBTA_GATTC_IF server_if;
  uint16_t conn_id;
  uint8_t tx_phy;
  uint8_t rx_phy;
  tBTA_GATT_STATUS status;
} tBTA_GATTC_PHY_UPDATE;

typedef struct {
  tBTA_GATTC_IF server_if;
  uint16_t conn_id;
  uint16_t interval;
  uint16_t latency;
  uint16_t timeout;
  tBTA_GATT_STATUS status;
} tBTA_GATTC_CONN_UPDATE;

typedef union {
  tBTA_GATT_STATUS status;

  tBTA_GATTC_SEARCH_CMPL search_cmpl; /* discovery complete */
  tBTA_GATTC_SRVC_RES srvc_res;       /* discovery result */
  tBTA_GATTC_REG reg_oper;            /* registration data */
  tBTA_GATTC_OPEN open;
  tBTA_GATTC_CLOSE close;
  tBTA_GATTC_READ read;           /* read attribute/descriptor data */
  tBTA_GATTC_WRITE write;         /* write complete data */
  tBTA_GATTC_EXEC_CMPL exec_cmpl; /*  execute complete */
  tBTA_GATTC_NOTIFY notify;       /* notification/indication event data */
  tBTA_GATTC_ENC_CMPL_CB enc_cmpl;
  BD_ADDR remote_bda;         /* service change event */
  tBTA_GATTC_CFG_MTU cfg_mtu; /* configure MTU operation */
  tBTA_GATTC_CONGEST congest;
  tBTA_GATTC_PHY_UPDATE phy_update;
  tBTA_GATTC_CONN_UPDATE conn_update;
} tBTA_GATTC;

/* GATTC enable callback function */
typedef void(tBTA_GATTC_ENB_CBACK)(tBTA_GATT_STATUS status);

/* Client callback function */
typedef void(tBTA_GATTC_CBACK)(tBTA_GATTC_EVT event, tBTA_GATTC* p_data);

/* GATT Server Data Structure */
/* Server callback function events */
#define BTA_GATTS_REG_EVT 0
#define BTA_GATTS_READ_CHARACTERISTIC_EVT \
  GATTS_REQ_TYPE_READ_CHARACTERISTIC                                 /* 1 */
#define BTA_GATTS_READ_DESCRIPTOR_EVT GATTS_REQ_TYPE_READ_DESCRIPTOR /* 2 */
#define BTA_GATTS_WRITE_CHARACTERISTIC_EVT \
  GATTS_REQ_TYPE_WRITE_CHARACTERISTIC                                  /* 3 */
#define BTA_GATTS_WRITE_DESCRIPTOR_EVT GATTS_REQ_TYPE_WRITE_DESCRIPTOR /* 4 */
#define BTA_GATTS_EXEC_WRITE_EVT GATTS_REQ_TYPE_WRITE_EXEC             /* 5 */
#define BTA_GATTS_MTU_EVT GATTS_REQ_TYPE_MTU                           /* 6 */
#define BTA_GATTS_CONF_EVT GATTS_REQ_TYPE_CONF                         /* 7 */
#define BTA_GATTS_DEREG_EVT 8
#define BTA_GATTS_DELELTE_EVT 11
#define BTA_GATTS_STOP_EVT 13
#define BTA_GATTS_CONNECT_EVT 14
#define BTA_GATTS_DISCONNECT_EVT 15
#define BTA_GATTS_OPEN_EVT 16
#define BTA_GATTS_CANCEL_OPEN_EVT 17
#define BTA_GATTS_CLOSE_EVT 18
#define BTA_GATTS_CONGEST_EVT 20
#define BTA_GATTS_PHY_UPDATE_EVT 21
#define BTA_GATTS_CONN_UPDATE_EVT 22

typedef uint8_t tBTA_GATTS_EVT;
typedef tGATT_IF tBTA_GATTS_IF;

/* Attribute permissions
*/
#define BTA_GATT_PERM_READ GATT_PERM_READ /* bit 0 -  0x0001 */
#define BTA_GATT_PERM_READ_ENCRYPTED \
  GATT_PERM_READ_ENCRYPTED /* bit 1 -  0x0002 */
#define BTA_GATT_PERM_READ_ENC_MITM \
  GATT_PERM_READ_ENC_MITM                   /* bit 2 -  0x0004 */
#define BTA_GATT_PERM_WRITE GATT_PERM_WRITE /* bit 4 -  0x0010 */
#define BTA_GATT_PERM_WRITE_ENCRYPTED \
  GATT_PERM_WRITE_ENCRYPTED /* bit 5 -  0x0020 */
#define BTA_GATT_PERM_WRITE_ENC_MITM \
  GATT_PERM_WRITE_ENC_MITM /* bit 6 -  0x0040 */
#define BTA_GATT_PERM_WRITE_SIGNED          \
  GATT_PERM_WRITE_SIGNED /* bit 7 -  0x0080 \
                            */
#define BTA_GATT_PERM_WRITE_SIGNED_MITM \
  GATT_PERM_WRITE_SIGNED_MITM /* bit 8 -  0x0100 */
typedef uint16_t tBTA_GATT_PERM;

#define BTA_GATTS_INVALID_APP 0xff

#define BTA_GATTS_INVALID_IF 0

/* definition of characteristic properties */
#define BTA_GATT_CHAR_PROP_BIT_BROADCAST                                    \
  GATT_CHAR_PROP_BIT_BROADCAST                                      /* 0x01 \
                                                                       */
#define BTA_GATT_CHAR_PROP_BIT_READ GATT_CHAR_PROP_BIT_READ         /* 0x02 */
#define BTA_GATT_CHAR_PROP_BIT_WRITE_NR GATT_CHAR_PROP_BIT_WRITE_NR /* 0x04 */
#define BTA_GATT_CHAR_PROP_BIT_WRITE GATT_CHAR_PROP_BIT_WRITE       /* 0x08 */
#define BTA_GATT_CHAR_PROP_BIT_NOTIFY GATT_CHAR_PROP_BIT_NOTIFY     /* 0x10 */
#define BTA_GATT_CHAR_PROP_BIT_INDICATE GATT_CHAR_PROP_BIT_INDICATE /* 0x20 */
#define BTA_GATT_CHAR_PROP_BIT_AUTH GATT_CHAR_PROP_BIT_AUTH         /* 0x40 */
#define BTA_GATT_CHAR_PROP_BIT_EXT_PROP GATT_CHAR_PROP_BIT_EXT_PROP /* 0x80 */
typedef uint8_t tBTA_GATT_CHAR_PROP;

#ifndef BTA_GATTC_CHAR_DESCR_MAX
#define BTA_GATTC_CHAR_DESCR_MAX 7
#endif

/***********************  NV callback Data Definitions   **********************
*/
typedef struct {
  tBT_UUID app_uuid128;
  tBT_UUID svc_uuid;
  uint16_t svc_inst;
  uint16_t s_handle;
  uint16_t e_handle;
  bool is_primary; /* primary service or secondary */
} tBTA_GATTS_HNDL_RANGE;

#define BTA_GATTS_SRV_CHG_CMD_ADD_CLIENT GATTS_SRV_CHG_CMD_ADD_CLIENT
#define BTA_GATTS_SRV_CHG_CMD_UPDATE_CLIENT GATTS_SRV_CHG_CMD_UPDATE_CLIENT
#define BTA_GATTS_SRV_CHG_CMD_REMOVE_CLIENT GATTS_SRV_CHG_CMD_REMOVE_CLIENT
#define BTA_GATTS_SRV_CHG_CMD_READ_NUM_CLENTS GATTS_SRV_CHG_CMD_READ_NUM_CLENTS
#define BTA_GATTS_SRV_CHG_CMD_READ_CLENT GATTS_SRV_CHG_CMD_READ_CLENT
typedef tGATTS_SRV_CHG_CMD tBTA_GATTS_SRV_CHG_CMD;

typedef tGATTS_SRV_CHG tBTA_GATTS_SRV_CHG;
typedef tGATTS_SRV_CHG_REQ tBTA_GATTS_SRV_CHG_REQ;
typedef tGATTS_SRV_CHG_RSP tBTA_GATTS_SRV_CHG_RSP;

#define BTA_GATT_TRANSPORT_LE GATT_TRANSPORT_LE
#define BTA_GATT_TRANSPORT_BR_EDR GATT_TRANSPORT_BR_EDR
#define BTA_GATT_TRANSPORT_LE_BR_EDR GATT_TRANSPORT_LE_BR_EDR
typedef uint8_t tBTA_GATT_TRANSPORT;

/* attribute value */
typedef tGATT_VALUE tBTA_GATT_VALUE;

/* attribute response data */
typedef tGATTS_RSP tBTA_GATTS_RSP;

/* attribute request data from the client */
#define BTA_GATT_PREP_WRITE_CANCEL 0x00
#define BTA_GATT_PREP_WRITE_EXEC 0x01
typedef tGATT_EXEC_FLAG tBTA_GATT_EXEC_FLAG;

/* read request always based on UUID */
typedef tGATT_READ_REQ tTA_GBATT_READ_REQ;

/* write request data */
typedef tGATT_WRITE_REQ tBTA_GATT_WRITE_REQ;

/* callback data for server access request from client */
typedef tGATTS_DATA tBTA_GATTS_REQ_DATA;

typedef struct {
  tBTA_GATT_STATUS status;
  BD_ADDR remote_bda;
  uint32_t trans_id;
  uint16_t conn_id;
  tBTA_GATTS_REQ_DATA* p_data;
} tBTA_GATTS_REQ;

typedef struct {
  tBTA_GATTS_IF server_if;
  tBTA_GATT_STATUS status;
  tBT_UUID uuid;
} tBTA_GATTS_REG_OPER;

typedef struct {
  tBTA_GATTS_IF server_if;
  uint16_t service_id;
  uint16_t svc_instance;
  bool is_primary;
  tBTA_GATT_STATUS status;
  tBT_UUID uuid;
} tBTA_GATTS_CREATE;

typedef struct {
  tBTA_GATTS_IF server_if;
  uint16_t service_id;
  tBTA_GATT_STATUS status;
} tBTA_GATTS_SRVC_OPER;

typedef struct {
  tBTA_GATTS_IF server_if;
  BD_ADDR remote_bda;
  uint16_t conn_id;
  tBTA_GATT_REASON reason; /* report disconnect reason */
  tBTA_GATT_TRANSPORT transport;
} tBTA_GATTS_CONN;

typedef struct {
  uint16_t conn_id;
  bool congested; /* report channel congestion indicator */
} tBTA_GATTS_CONGEST;

typedef struct {
  uint16_t conn_id;        /* connection ID */
  tBTA_GATT_STATUS status; /* notification/indication status */
} tBTA_GATTS_CONF;

typedef struct {
  tBTA_GATTS_IF server_if;
  uint16_t conn_id;
  uint8_t tx_phy;
  uint8_t rx_phy;
  tBTA_GATT_STATUS status;
} tBTA_GATTS_PHY_UPDATE;

typedef struct {
  tBTA_GATTS_IF server_if;
  uint16_t conn_id;
  uint16_t interval;
  uint16_t latency;
  uint16_t timeout;
  tBTA_GATT_STATUS status;
} tBTA_GATTS_CONN_UPDATE;

/* GATTS callback data */
typedef union {
  tBTA_GATTS_REG_OPER reg_oper;
  tBTA_GATTS_CREATE create;
  tBTA_GATTS_SRVC_OPER srvc_oper;
  tBTA_GATT_STATUS status; /* BTA_GATTS_LISTEN_EVT */
  tBTA_GATTS_REQ req_data;
  tBTA_GATTS_CONN conn;       /* BTA_GATTS_CONN_EVT */
  tBTA_GATTS_CONGEST congest; /* BTA_GATTS_CONGEST_EVT callback data */
  tBTA_GATTS_CONF confirm;    /* BTA_GATTS_CONF_EVT callback data */
  tBTA_GATTS_PHY_UPDATE phy_update; /* BTA_GATTS_PHY_UPDATE_EVT callback data */
  tBTA_GATTS_CONN_UPDATE
      conn_update; /* BTA_GATTS_CONN_UPDATE_EVT callback data */
} tBTA_GATTS;

/* GATTS enable callback function */
typedef void(tBTA_GATTS_ENB_CBACK)(tBTA_GATT_STATUS status);

/* Server callback function */
typedef void(tBTA_GATTS_CBACK)(tBTA_GATTS_EVT event, tBTA_GATTS* p_data);

typedef struct {
  tBT_UUID uuid;
  bool is_primary;
  uint16_t handle;
  uint16_t s_handle;
  uint16_t e_handle;
  list_t* characteristics; /* list of tBTA_GATTC_CHARACTERISTIC */
  list_t* included_svc;    /* list of tBTA_GATTC_INCLUDED_SVC */
} __attribute__((packed, aligned(alignof(tBT_UUID)))) tBTA_GATTC_SERVICE;

typedef struct {
  tBT_UUID uuid;
  uint16_t handle;
  tBTA_GATT_CHAR_PROP properties;
  tBTA_GATTC_SERVICE* service; /* owning service*/
  list_t* descriptors;         /* list of tBTA_GATTC_DESCRIPTOR */
} __attribute__((packed, aligned(alignof(tBT_UUID)))) tBTA_GATTC_CHARACTERISTIC;

typedef struct {
  tBT_UUID uuid;
  uint16_t handle;
  tBTA_GATTC_CHARACTERISTIC* characteristic; /* owning characteristic */
} __attribute__((packed)) tBTA_GATTC_DESCRIPTOR;

typedef struct {
  tBT_UUID uuid;
  uint16_t handle;
  tBTA_GATTC_SERVICE* owning_service; /* owning service*/
  tBTA_GATTC_SERVICE* included_service;
} __attribute__((packed)) tBTA_GATTC_INCLUDED_SVC;

/*****************************************************************************
 *  External Function Declarations
 ****************************************************************************/

/**************************
 *  Client Functions
 **************************/

/*******************************************************************************
 *
 * Function         BTA_GATTC_Disable
 *
 * Description      This function is called to disable the GATTC module
 *
 * Parameters       None.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_Disable(void);

using BtaAppRegisterCallback =
    base::Callback<void(uint8_t /* app_id */, uint8_t /* status */)>;

/**
 * This function is called to register application callbacks with BTA GATTC
 *module.
 * p_client_cb - pointer to the application callback function.
 **/
extern void BTA_GATTC_AppRegister(tBTA_GATTC_CBACK* p_client_cb,
                                  BtaAppRegisterCallback cb);

/*******************************************************************************
 *
 * Function         BTA_GATTC_AppDeregister
 *
 * Description      This function is called to deregister an application
 *                  from BTA GATTC module.
 *
 * Parameters       client_if - client interface identifier.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_AppDeregister(tBTA_GATTC_IF client_if);

/*******************************************************************************
 *
 * Function         BTA_GATTC_Open
 *
 * Description      Open a direct connection or add a background auto connection
 *                  bd address
 *
 * Parameters       client_if: server interface.
 *                  remote_bda: remote device BD address.
 *                  is_direct: direct connection or background auto connection
 *                  initiating_phys: LE PHY to use, optional
 *
 ******************************************************************************/
extern void BTA_GATTC_Open(tBTA_GATTC_IF client_if, BD_ADDR remote_bda,
                           bool is_direct, tBTA_GATT_TRANSPORT transport,
                           bool opportunistic);
extern void BTA_GATTC_Open(tBTA_GATTC_IF client_if, BD_ADDR remote_bda,
                           bool is_direct, tBTA_GATT_TRANSPORT transport,
                           bool opportunistic, uint8_t initiating_phys);

/*******************************************************************************
 *
 * Function         BTA_GATTC_CancelOpen
 *
 * Description      Open a direct connection or add a background auto connection
 *                  bd address
 *
 * Parameters       client_if: server interface.
 *                  remote_bda: remote device BD address.
 *                  is_direct: direct connection or background auto connection
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTC_CancelOpen(tBTA_GATTC_IF client_if, BD_ADDR remote_bda,
                                 bool is_direct);

/*******************************************************************************
 *
 * Function         BTA_GATTC_Close
 *
 * Description      Close a connection to a GATT server.
 *
 * Parameters       conn_id: connectino ID to be closed.
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTC_Close(uint16_t conn_id);

/*******************************************************************************
 *
 * Function         BTA_GATTC_ServiceSearchRequest
 *
 * Description      This function is called to request a GATT service discovery
 *                  on a GATT server. This function report service search result
 *                  by a callback event, and followed by a service search
 *                  complete event.
 *
 * Parameters       conn_id: connection ID.
 *                  p_srvc_uuid: a UUID of the service application is interested
 *                               in. If Null, discover for all services.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_ServiceSearchRequest(uint16_t conn_id,
                                           tBT_UUID* p_srvc_uuid);

/**
 * This function is called to send "Find service by UUID" request. Used only for
 * PTS tests.
 */
extern void BTA_GATTC_DiscoverServiceByUuid(uint16_t conn_id,
                                            tBT_UUID* p_srvc_uuid);

/*******************************************************************************
 *
 * Function         BTA_GATTC_GetServices
 *
 * Description      This function is called to find the services on the given
 *                  server.
 *
 * Parameters       conn_id: connection ID which identify the server.
 *
 * Returns          returns list_t of tBTA_GATTC_SERVICE or NULL.
 *
 ******************************************************************************/
extern const list_t* BTA_GATTC_GetServices(uint16_t conn_id);

/*******************************************************************************
 *
 * Function         BTA_GATTC_GetCharacteristic
 *
 * Description      This function is called to find the characteristic on the
 *                  given server.
 *
 * Parameters       conn_id: connection ID which identify the server.
 *                  handle: characteristic handle
 *
 * Returns          returns pointer to tBTA_GATTC_CHARACTERISTIC or NULL.
 *
 ******************************************************************************/
extern const tBTA_GATTC_CHARACTERISTIC* BTA_GATTC_GetCharacteristic(
    uint16_t conn_id, uint16_t handle);

/*******************************************************************************
 *
 * Function         BTA_GATTC_GetDescriptor
 *
 * Description      This function is called to find the characteristic on the
 *                  given server.
 *
 * Parameters       conn_id: connection ID which identify the server.
 *                  handle: descriptor handle
 *
 * Returns          returns pointer to tBTA_GATTC_DESCRIPTOR or NULL.
 *
 ******************************************************************************/
extern const tBTA_GATTC_DESCRIPTOR* BTA_GATTC_GetDescriptor(uint16_t conn_id,
                                                            uint16_t handle);

/*******************************************************************************
 *
 * Function         BTA_GATTC_GetGattDb
 *
 * Description      This function is called to get gatt db.
 *
 * Parameters       conn_id: connection ID which identify the server.
 *                  db: output parameter which will contain gatt db copy.
 *                      Caller is responsible for freeing it.
 *                  count: number of elements in db.
 *
 ******************************************************************************/
extern void BTA_GATTC_GetGattDb(uint16_t conn_id, uint16_t start_handle,
                                uint16_t end_handle, btgatt_db_element_t** db,
                                int* count);

typedef void (*GATT_READ_OP_CB)(uint16_t conn_id, tGATT_STATUS status,
                                uint16_t handle, uint16_t len, uint8_t* value,
                                void* data);
typedef void (*GATT_WRITE_OP_CB)(uint16_t conn_id, tGATT_STATUS status,
                                 uint16_t handle, void* data);

/*******************************************************************************
 *
 * Function         BTA_GATTC_ReadCharacteristic
 *
 * Description      This function is called to read a characteristics value
 *
 * Parameters       conn_id - connectino ID.
 *                  handle - characteritic handle to read.
 *
 * Returns          None
 *
 ******************************************************************************/
void BTA_GATTC_ReadCharacteristic(uint16_t conn_id, uint16_t handle,
                                  tBTA_GATT_AUTH_REQ auth_req,
                                  GATT_READ_OP_CB callback, void* cb_data);

/**
 * This function is called to read a value of characteristic with uuid equal to
 * |uuid|
 */
void BTA_GATTC_ReadUsingCharUuid(uint16_t conn_id, tBT_UUID uuid,
                                 uint16_t s_handle, uint16_t e_handle,
                                 tBTA_GATT_AUTH_REQ auth_req,
                                 GATT_READ_OP_CB callback, void* cb_data);

/*******************************************************************************
 *
 * Function         BTA_GATTC_ReadCharDescr
 *
 * Description      This function is called to read a descriptor value.
 *
 * Parameters       conn_id - connection ID.
 *                  handle - descriptor handle to read.
 *
 * Returns          None
 *
 ******************************************************************************/
void BTA_GATTC_ReadCharDescr(uint16_t conn_id, uint16_t handle,
                             tBTA_GATT_AUTH_REQ auth_req,
                             GATT_READ_OP_CB callback, void* cb_data);

/*******************************************************************************
 *
 * Function         BTA_GATTC_WriteCharValue
 *
 * Description      This function is called to write characteristic value.
 *
 * Parameters       conn_id - connection ID.
 *                  handle - characteristic handle to write.
 *                  write_type - type of write.
 *                  value - the value to be written.
 *
 * Returns          None
 *
 ******************************************************************************/
void BTA_GATTC_WriteCharValue(uint16_t conn_id, uint16_t handle,
                              tBTA_GATTC_WRITE_TYPE write_type,
                              vector<uint8_t> value,
                              tBTA_GATT_AUTH_REQ auth_req,
                              GATT_WRITE_OP_CB callback, void* cb_data);

/*******************************************************************************
 *
 * Function         BTA_GATTC_WriteCharDescr
 *
 * Description      This function is called to write descriptor value.
 *
 * Parameters       conn_id - connection ID
 *                  handle - descriptor handle to write.
 *                  value - the value to be written.
 *
 * Returns          None
 *
 ******************************************************************************/
void BTA_GATTC_WriteCharDescr(uint16_t conn_id, uint16_t handle,
                              vector<uint8_t> value,
                              tBTA_GATT_AUTH_REQ auth_req,
                              GATT_WRITE_OP_CB callback, void* cb_data);

/*******************************************************************************
 *
 * Function         BTA_GATTC_SendIndConfirm
 *
 * Description      This function is called to send handle value confirmation.
 *
 * Parameters       conn_id - connection ID.
 *                  handle - characteristic handle to confirm.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_SendIndConfirm(uint16_t conn_id, uint16_t handle);

/*******************************************************************************
 *
 * Function         BTA_GATTC_RegisterForNotifications
 *
 * Description      This function is called to register for notification of a
 *                  service.
 *
 * Parameters       client_if - client interface.
 *                  remote_bda - target GATT server.
 *                  handle - GATT characteristic handle.
 *
 * Returns          OK if registration succeed, otherwise failed.
 *
 ******************************************************************************/
extern tBTA_GATT_STATUS BTA_GATTC_RegisterForNotifications(
    tBTA_GATTC_IF client_if, const BD_ADDR remote_bda, uint16_t handle);

/*******************************************************************************
 *
 * Function         BTA_GATTC_DeregisterForNotifications
 *
 * Description      This function is called to de-register for notification of a
 *                  service.
 *
 * Parameters       client_if - client interface.
 *                  remote_bda - target GATT server.
 *                  handle - GATT characteristic handle.
 *
 * Returns          OK if deregistration succeed, otherwise failed.
 *
 ******************************************************************************/
extern tBTA_GATT_STATUS BTA_GATTC_DeregisterForNotifications(
    tBTA_GATTC_IF client_if, const BD_ADDR remote_bda, uint16_t handle);

/*******************************************************************************
 *
 * Function         BTA_GATTC_PrepareWrite
 *
 * Description      This function is called to prepare write a characteristic
 *                  value.
 *
 * Parameters       conn_id - connection ID.
 *                  handle - GATT characteritic handle.
 *                  offset - offset of the write value.
 *                  value - the value to be written.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_PrepareWrite(uint16_t conn_id, uint16_t handle,
                                   uint16_t offset, vector<uint8_t> value,
                                   tBTA_GATT_AUTH_REQ auth_req,
                                   GATT_WRITE_OP_CB callback, void* cb_data);

/*******************************************************************************
 *
 * Function         BTA_GATTC_ExecuteWrite
 *
 * Description      This function is called to execute write a prepare write
 *                  sequence.
 *
 * Parameters       conn_id - connection ID.
 *                    is_execute - execute or cancel.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_ExecuteWrite(uint16_t conn_id, bool is_execute);

/*******************************************************************************
 *
 * Function         BTA_GATTC_ReadMultiple
 *
 * Description      This function is called to read multiple characteristic or
 *                  characteristic descriptors.
 *
 * Parameters       conn_id - connectino ID.
 *                    p_read_multi - read multiple parameters.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTC_ReadMultiple(uint16_t conn_id,
                                   tBTA_GATTC_MULTI* p_read_multi,
                                   tBTA_GATT_AUTH_REQ auth_req);

/*******************************************************************************
 *
 * Function         BTA_GATTC_Refresh
 *
 * Description      Refresh the server cache of the remote device
 *
 * Parameters       remote_bda: remote device BD address.
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTC_Refresh(const BD_ADDR remote_bda);

/*******************************************************************************
 *
 * Function         BTA_GATTC_ConfigureMTU
 *
 * Description      Configure the MTU size in the GATT channel. This can be done
 *                  only once per connection.
 *
 * Parameters       conn_id: connection ID.
 *                  mtu: desired MTU size to use.
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTC_ConfigureMTU(uint16_t conn_id, uint16_t mtu);

/*******************************************************************************
 *  BTA GATT Server API
 ******************************************************************************/

/*******************************************************************************
 *
 * Function         BTA_GATTS_Init
 *
 * Description      This function is called to initalize GATTS module
 *
 * Parameters       None
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTS_Init();

/*******************************************************************************
 *
 * Function         BTA_GATTS_Disable
 *
 * Description      This function is called to disable GATTS module
 *
 * Parameters       None.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTS_Disable(void);

/*******************************************************************************
 *
 * Function         BTA_GATTS_AppRegister
 *
 * Description      This function is called to register application callbacks
 *                    with BTA GATTS module.
 *
 * Parameters       p_app_uuid - applicaiton UUID
 *                  p_cback - pointer to the application callback function.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTS_AppRegister(tBT_UUID* p_app_uuid,
                                  tBTA_GATTS_CBACK* p_cback);

/*******************************************************************************
 *
 * Function         BTA_GATTS_AppDeregister
 *
 * Description      De-register with BTA GATT Server.
 *
 * Parameters       server_if: server interface
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTS_AppDeregister(tBTA_GATTS_IF server_if);

/*******************************************************************************
 *
 * Function         BTA_GATTS_AddService
 *
 * Description      Add the given |service| and all included elements to the
 *                  GATT database. a |BTA_GATTS_ADD_SRVC_EVT| is triggered to
 *                  report the status and attribute handles.
 *
 * Parameters       server_if: server interface.
 *                  service: pointer to vector describing service.
 *
 * Returns          Returns |BTA_GATT_OK| on success or |BTA_GATT_ERROR| if the
 *                  service cannot be added.
 *
 ******************************************************************************/
extern uint16_t BTA_GATTS_AddService(tBTA_GATTS_IF server_if,
                                     vector<btgatt_db_element_t>& service);

/*******************************************************************************
 *
 * Function         BTA_GATTS_DeleteService
 *
 * Description      This function is called to delete a service. When this is
 *                  done, a callback event BTA_GATTS_DELETE_EVT is report with
 *                  the status.
 *
 * Parameters       service_id: service_id to be deleted.
 *
 * Returns          returns none.
 *
 ******************************************************************************/
extern void BTA_GATTS_DeleteService(uint16_t service_id);

/*******************************************************************************
 *
 * Function         BTA_GATTS_StopService
 *
 * Description      This function is called to stop a service.
 *
 * Parameters       service_id - service to be topped.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTS_StopService(uint16_t service_id);

/*******************************************************************************
 *
 * Function         BTA_GATTS_HandleValueIndication
 *
 * Description      This function is called to read a characteristics
 *                  descriptor.
 *
 * Parameters       conn_id - connection identifier.
 *                  attr_id - attribute ID to indicate.
 *                  value - data to indicate.
 *                  need_confirm - if this indication expects a confirmation or
 *                                 not.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTS_HandleValueIndication(uint16_t conn_id, uint16_t attr_id,
                                            vector<uint8_t> value,
                                            bool need_confirm);

/*******************************************************************************
 *
 * Function         BTA_GATTS_SendRsp
 *
 * Description      This function is called to send a response to a request.
 *
 * Parameters       conn_id - connection identifier.
 *                  trans_id - transaction ID.
 *                  status - response status
 *                  p_msg - response data.
 *
 * Returns          None
 *
 ******************************************************************************/
extern void BTA_GATTS_SendRsp(uint16_t conn_id, uint32_t trans_id,
                              tBTA_GATT_STATUS status, tBTA_GATTS_RSP* p_msg);

/*******************************************************************************
 *
 * Function         BTA_GATTS_Open
 *
 * Description      Open a direct open connection or add a background auto
 *                  connection bd address
 *
 * Parameters       server_if: server interface.
 *                  remote_bda: remote device BD address.
 *                  is_direct: direct connection or background auto connection
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTS_Open(tBTA_GATTS_IF server_if, BD_ADDR remote_bda,
                           bool is_direct, tBTA_GATT_TRANSPORT transport);

/*******************************************************************************
 *
 * Function         BTA_GATTS_CancelOpen
 *
 * Description      Cancel a direct open connection or remove a background auto
 *                  connection bd address
 *
 * Parameters       server_if: server interface.
 *                  remote_bda: remote device BD address.
 *                  is_direct: direct connection or background auto connection
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTS_CancelOpen(tBTA_GATTS_IF server_if, BD_ADDR remote_bda,
                                 bool is_direct);

/*******************************************************************************
 *
 * Function         BTA_GATTS_Close
 *
 * Description      Close a connection  a remote device.
 *
 * Parameters       conn_id: connectino ID to be closed.
 *
 * Returns          void
 *
 ******************************************************************************/
extern void BTA_GATTS_Close(uint16_t conn_id);

#endif /* BTA_GATT_API_H */
