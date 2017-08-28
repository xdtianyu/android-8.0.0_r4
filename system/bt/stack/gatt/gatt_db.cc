/******************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
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
 *  this file contains GATT database building and query functions
 *
 ******************************************************************************/

#include "bt_target.h"

#include "bt_trace.h"
#include "bt_utils.h"

#include <stdio.h>
#include <string.h>
#include "btm_int.h"
#include "gatt_int.h"
#include "l2c_api.h"
#include "osi/include/osi.h"

/*******************************************************************************
 *             L O C A L    F U N C T I O N     P R O T O T Y P E S            *
 ******************************************************************************/
static tGATT_ATTR& allocate_attr_in_db(tGATT_SVC_DB& db, const tBT_UUID& uuid,
                                       tGATT_PERM perm);
static tGATT_STATUS gatts_send_app_read_request(
    tGATT_TCB* p_tcb, uint8_t op_code, uint16_t handle, uint16_t offset,
    uint32_t trans_id, bt_gatt_db_attribute_type_t gatt_type);

/**
 * Initialize a memory space to be a service database.
 */
void gatts_init_service_db(tGATT_SVC_DB& db, tBT_UUID* p_service, bool is_pri,
                           uint16_t s_hdl, uint16_t num_handle) {
  db.attr_list.reserve(num_handle);

  GATT_TRACE_DEBUG("%s: s_hdl= %d num_handle= %d", __func__, s_hdl, num_handle);

  /* update service database information */
  db.next_handle = s_hdl;
  db.end_handle = s_hdl + num_handle;

  /* add service declration record */
  tBT_UUID uuid = {LEN_UUID_16, {0}};
  uuid.uu.uuid16 = is_pri ? GATT_UUID_PRI_SERVICE : GATT_UUID_SEC_SERVICE;
  tGATT_ATTR& attr = allocate_attr_in_db(db, uuid, GATT_PERM_READ);
  attr.p_value.reset((tGATT_ATTR_VALUE*)(new tBT_UUID));
  memcpy(&attr.p_value->uuid, p_service, sizeof(tBT_UUID));
}

tBT_UUID* gatts_get_service_uuid(tGATT_SVC_DB* p_db) {
  if (!p_db || p_db->attr_list.empty()) {
    GATT_TRACE_ERROR("service DB empty");
    return NULL;
  } else {
    return &p_db->attr_list[0].p_value->uuid;
  }
}

/** Check attribute readability. Returns status of operation. */
static tGATT_STATUS gatts_check_attr_readability(const tGATT_ATTR& attr,
                                                 UNUSED_ATTR uint16_t offset,
                                                 bool read_long,
                                                 tGATT_SEC_FLAG sec_flag,
                                                 uint8_t key_size) {
  uint16_t min_key_size;
  tGATT_PERM perm = attr.permission;

  min_key_size = (((perm & GATT_ENCRYPT_KEY_SIZE_MASK) >> 12));
  if (min_key_size != 0) {
    min_key_size += 6;
  }

  if (!(perm & GATT_READ_ALLOWED)) {
    GATT_TRACE_ERROR("%s: GATT_READ_NOT_PERMIT", __func__);
    return GATT_READ_NOT_PERMIT;
  }

  if ((perm & GATT_READ_AUTH_REQUIRED) &&
      !(sec_flag & GATT_SEC_FLAG_LKEY_UNAUTHED) &&
      !(sec_flag & BTM_SEC_FLAG_ENCRYPTED)) {
    GATT_TRACE_ERROR("%s: GATT_INSUF_AUTHENTICATION", __func__);
    return GATT_INSUF_AUTHENTICATION;
  }

  if ((perm & GATT_READ_MITM_REQUIRED) &&
      !(sec_flag & GATT_SEC_FLAG_LKEY_AUTHED)) {
    GATT_TRACE_ERROR("%s: GATT_INSUF_AUTHENTICATION: MITM Required", __func__);
    return GATT_INSUF_AUTHENTICATION;
  }

  if ((perm & GATT_READ_ENCRYPTED_REQUIRED) &&
      !(sec_flag & GATT_SEC_FLAG_ENCRYPTED)) {
    GATT_TRACE_ERROR("%s: GATT_INSUF_ENCRYPTION", __func__);
    return GATT_INSUF_ENCRYPTION;
  }

  if ((perm & GATT_READ_ENCRYPTED_REQUIRED) &&
      (sec_flag & GATT_SEC_FLAG_ENCRYPTED) && (key_size < min_key_size)) {
    GATT_TRACE_ERROR("%s: GATT_INSUF_KEY_SIZE", __func__);
    return GATT_INSUF_KEY_SIZE;
  }

  if (read_long && attr.uuid.len == LEN_UUID_16) {
    switch (attr.uuid.uu.uuid16) {
      case GATT_UUID_PRI_SERVICE:
      case GATT_UUID_SEC_SERVICE:
      case GATT_UUID_CHAR_DECLARE:
      case GATT_UUID_INCLUDE_SERVICE:
      case GATT_UUID_CHAR_EXT_PROP:
      case GATT_UUID_CHAR_CLIENT_CONFIG:
      case GATT_UUID_CHAR_SRVR_CONFIG:
      case GATT_UUID_CHAR_PRESENT_FORMAT:
        GATT_TRACE_ERROR("%s: GATT_NOT_LONG", __func__);
        return GATT_NOT_LONG;

      default:
        break;
    }
  }

  return GATT_SUCCESS;
}

/*******************************************************************************
 *
 * Function         read_attr_value
 *
 * Description      Utility function to read an attribute value.
 *
 * Parameter        p_attr: pointer to the attribute to read.
 *                  offset: read offset.
 *                  p_value: output parameter to carry out the attribute value.
 *                  p_len: output parameter to carry out the attribute length.
 *                  read_long: this is a read blob request.
 *                  mtu: MTU
 *                  sec_flag: current link security status.
 *                  key_size: encryption key size.
 *
 * Returns          status of operation.
 *
 ******************************************************************************/
static tGATT_STATUS read_attr_value(tGATT_ATTR& attr16, uint16_t offset,
                                    uint8_t** p_data, bool read_long,
                                    uint16_t mtu, uint16_t* p_len,
                                    tGATT_SEC_FLAG sec_flag, uint8_t key_size) {
  uint16_t len = 0, uuid16 = 0;
  uint8_t* p = *p_data;

  GATT_TRACE_DEBUG(
      "%s: uuid=0x%04x perm=0x%02x sec_flag=0x%x offset=%d read_long=%d",
      __func__, attr16.uuid, attr16.permission, sec_flag, offset, read_long);

  tGATT_STATUS status = gatts_check_attr_readability(attr16, offset, read_long,
                                                     sec_flag, key_size);

  if (status != GATT_SUCCESS) return status;

  if (attr16.uuid.len == LEN_UUID_16) uuid16 = attr16.uuid.uu.uuid16;

  status = GATT_NO_RESOURCES;

  if (uuid16 == GATT_UUID_PRI_SERVICE || uuid16 == GATT_UUID_SEC_SERVICE) {
    len = attr16.p_value->uuid.len;
    if (mtu >= attr16.p_value->uuid.len) {
      gatt_build_uuid_to_stream(&p, attr16.p_value->uuid);
      status = GATT_SUCCESS;
    }
  } else if (uuid16 == GATT_UUID_CHAR_DECLARE) {
    tGATT_ATTR* val_attr = &attr16 + 1;
    len = (val_attr->uuid.len == LEN_UUID_16) ? 5 : 19;

    if (mtu >= len) {
      UINT8_TO_STREAM(p, attr16.p_value->char_decl.property);
      UINT16_TO_STREAM(p, attr16.p_value->char_decl.char_val_handle);

      if (val_attr->uuid.len == LEN_UUID_16) {
        UINT16_TO_STREAM(p, val_attr->uuid.uu.uuid16);
      }
      /* convert a 32bits UUID to 128 bits */
      else if (val_attr->uuid.len == LEN_UUID_32) {
        gatt_convert_uuid32_to_uuid128(p, val_attr->uuid.uu.uuid32);
        p += LEN_UUID_128;
      } else {
        ARRAY_TO_STREAM(p, val_attr->uuid.uu.uuid128, LEN_UUID_128);
      }
      status = GATT_SUCCESS;
    }

  } else if (uuid16 == GATT_UUID_INCLUDE_SERVICE) {
    if (attr16.p_value->incl_handle.service_type.len == LEN_UUID_16)
      len = 6;
    else
      len = 4;

    if (mtu >= len) {
      UINT16_TO_STREAM(p, attr16.p_value->incl_handle.s_handle);
      UINT16_TO_STREAM(p, attr16.p_value->incl_handle.e_handle);

      if (attr16.p_value->incl_handle.service_type.len == LEN_UUID_16) {
        UINT16_TO_STREAM(p, attr16.p_value->incl_handle.service_type.uu.uuid16);
      }
      status = GATT_SUCCESS;
    }
  } else /* characteristic description or characteristic value */
  {
    status = GATT_PENDING;
  }

  *p_len = len;
  *p_data = p;
  return status;
}

/*******************************************************************************
 *
 * Function         gatts_db_read_attr_value_by_type
 *
 * Description      Query attribute value by attribute type.
 *
 * Parameter        p_db: pointer to the attribute database.
 *                  p_rsp: Read By type response data.
 *                  s_handle: starting handle of the range we are looking for.
 *                  e_handle: ending handle of the range we are looking for.
 *                  type: Attribute type.
 *                  mtu: MTU.
 *                  sec_flag: current link security status.
 *                  key_size: encryption key size.
 *
 * Returns          Status of the operation.
 *
 ******************************************************************************/
tGATT_STATUS gatts_db_read_attr_value_by_type(
    tGATT_TCB* p_tcb, tGATT_SVC_DB* p_db, uint8_t op_code, BT_HDR* p_rsp,
    uint16_t s_handle, uint16_t e_handle, tBT_UUID type, uint16_t* p_len,
    tGATT_SEC_FLAG sec_flag, uint8_t key_size, uint32_t trans_id,
    uint16_t* p_cur_handle) {
  tGATT_STATUS status = GATT_NOT_FOUND;
  uint16_t len = 0;
  uint8_t* p = (uint8_t*)(p_rsp + 1) + p_rsp->len + L2CAP_MIN_OFFSET;

  if (p_db) {
    for (tGATT_ATTR& attr : p_db->attr_list) {
      tBT_UUID attr_uuid = attr.uuid;

      if (attr.handle >= s_handle && gatt_uuid_compare(type, attr_uuid)) {
        if (*p_len <= 2) {
          status = GATT_NO_RESOURCES;
          break;
        }

        UINT16_TO_STREAM(p, attr.handle);

        status = read_attr_value(attr, 0, &p, false, (uint16_t)(*p_len - 2),
                                 &len, sec_flag, key_size);

        if (status == GATT_PENDING) {
          status = gatts_send_app_read_request(p_tcb, op_code, attr.handle, 0,
                                               trans_id, attr.gatt_type);

          /* one callback at a time */
          break;
        } else if (status == GATT_SUCCESS) {
          if (p_rsp->offset == 0) p_rsp->offset = len + 2;

          if (p_rsp->offset == len + 2) {
            p_rsp->len += (len + 2);
            *p_len -= (len + 2);
          } else {
            GATT_TRACE_ERROR("format mismatch");
            status = GATT_NO_RESOURCES;
            break;
          }
        } else {
          *p_cur_handle = attr.handle;
          break;
        }
      }
    }
  }

#if (BLE_DELAY_REQUEST_ENC == TRUE)
  uint8_t flag = 0;
  if (BTM_GetSecurityFlags(p_tcb->peer_bda, &flag)) {
    if ((p_tcb->att_lcid == L2CAP_ATT_CID) && (status == GATT_PENDING) &&
        (type.uu.uuid16 == GATT_UUID_GAP_DEVICE_NAME)) {
      if ((flag & (BTM_SEC_LINK_KEY_KNOWN | BTM_SEC_FLAG_ENCRYPTED)) ==
          BTM_SEC_LINK_KEY_KNOWN) {
        tACL_CONN* p = btm_bda_to_acl(p_tcb->peer_bda, BT_TRANSPORT_LE);
        if ((p != NULL) && (p->link_role == BTM_ROLE_MASTER))
          btm_ble_set_encryption(p_tcb->peer_bda, BTM_BLE_SEC_ENCRYPT,
                                 p->link_role);
      }
    }
  }
#endif
  return status;
}

/**
 * This function adds an included service into a database.
 *
 * Parameter        db: database pointer.
 *                  inc_srvc_type: included service type.
 *
 * Returns          Status of the operation.
 *
 */
uint16_t gatts_add_included_service(tGATT_SVC_DB& db, uint16_t s_handle,
                                    uint16_t e_handle, tBT_UUID service) {
  tBT_UUID uuid = {LEN_UUID_16, {GATT_UUID_INCLUDE_SERVICE}};

  GATT_TRACE_DEBUG("%s: s_hdl = 0x%04x e_hdl = 0x%04x uuid = 0x%04x", __func__,
                   s_handle, e_handle, service.uu.uuid16);

  if (service.len == 0 || s_handle == 0 || e_handle == 0) {
    GATT_TRACE_ERROR("%s: Illegal Params.", __func__);
    return 0;
  }

  tGATT_ATTR& attr = allocate_attr_in_db(db, uuid, GATT_PERM_READ);

  attr.p_value.reset((tGATT_ATTR_VALUE*)(new tGATT_INCL_SRVC));
  attr.p_value->incl_handle.s_handle = s_handle;
  attr.p_value->incl_handle.e_handle = e_handle;
  memcpy(&attr.p_value->incl_handle.service_type, &service, sizeof(tBT_UUID));

  return attr.handle;
}

/*******************************************************************************
 *
 * Function         gatts_add_characteristic
 *
 * Description      This function add a characteristics and its descriptor into
 *                  a servce identified by the service database pointer.
 *
 * Parameter        db: database.
 *                  perm: permission (authentication and key size requirements)
 *                  property: property of the characteristic.
 *                  p_char: characteristic value information.
 *
 * Returns          Status of te operation.
 *
 ******************************************************************************/
uint16_t gatts_add_characteristic(tGATT_SVC_DB& db, tGATT_PERM perm,
                                  tGATT_CHAR_PROP property,
                                  tBT_UUID& char_uuid) {
  tBT_UUID uuid = {LEN_UUID_16, {GATT_UUID_CHAR_DECLARE}};

  GATT_TRACE_DEBUG("%s: perm=0x%0x property=0x%0x", __func__, perm, property);

  tGATT_ATTR& char_decl = allocate_attr_in_db(db, uuid, GATT_PERM_READ);
  tGATT_ATTR& char_val = allocate_attr_in_db(db, char_uuid, perm);

  char_decl.p_value.reset((tGATT_ATTR_VALUE*)(new tGATT_CHAR_DECL));
  char_decl.p_value->char_decl.property = property;
  char_decl.p_value->char_decl.char_val_handle = char_val.handle;
  char_val.gatt_type = BTGATT_DB_CHARACTERISTIC;
  return char_val.handle;
}

/*******************************************************************************
 *
 * Function         gatt_convertchar_descr_type
 *
 * Description      Convert a char descript UUID into descriptor type.
 *
 * Returns          descriptor type.
 *
 ******************************************************************************/
uint8_t gatt_convertchar_descr_type(tBT_UUID* p_descr_uuid) {
  tBT_UUID std_descr = {LEN_UUID_16, {GATT_UUID_CHAR_EXT_PROP}};

  if (gatt_uuid_compare(std_descr, *p_descr_uuid))
    return GATT_DESCR_EXT_DSCPTOR;

  std_descr.uu.uuid16++;
  if (gatt_uuid_compare(std_descr, *p_descr_uuid))
    return GATT_DESCR_USER_DSCPTOR;

  std_descr.uu.uuid16++;
  if (gatt_uuid_compare(std_descr, *p_descr_uuid)) return GATT_DESCR_CLT_CONFIG;

  std_descr.uu.uuid16++;
  if (gatt_uuid_compare(std_descr, *p_descr_uuid)) return GATT_DESCR_SVR_CONFIG;

  std_descr.uu.uuid16++;
  if (gatt_uuid_compare(std_descr, *p_descr_uuid))
    return GATT_DESCR_PRES_FORMAT;

  std_descr.uu.uuid16++;
  if (gatt_uuid_compare(std_descr, *p_descr_uuid))
    return GATT_DESCR_AGGR_FORMAT;

  std_descr.uu.uuid16++;
  if (gatt_uuid_compare(std_descr, *p_descr_uuid))
    return GATT_DESCR_VALID_RANGE;

  return GATT_DESCR_UNKNOWN;
}

/*******************************************************************************
 *
 * Function         gatts_add_char_descr
 *
 * Description      This function add a characteristics descriptor.
 *
 * Parameter        p_db: database pointer.
 *                  perm: characteristic descriptor permission type.
 *                  char_dscp_tpye: the characteristic descriptor masks.
 *                  p_dscp_params: characteristic descriptors values.
 *
 * Returns          Status of the operation.
 *
 ******************************************************************************/
uint16_t gatts_add_char_descr(tGATT_SVC_DB& db, tGATT_PERM perm,
                              tBT_UUID& descr_uuid) {
  GATT_TRACE_DEBUG("gatts_add_char_descr uuid=0x%04x", descr_uuid.uu.uuid16);

  /* Add characteristic descriptors */
  tGATT_ATTR& char_dscptr = allocate_attr_in_db(db, descr_uuid, perm);
  char_dscptr.gatt_type = BTGATT_DB_DESCRIPTOR;
  return char_dscptr.handle;
}

/******************************************************************************/
/* Service Attribute Database Query Utility Functions */
/******************************************************************************/
tGATT_ATTR* find_attr_by_handle(tGATT_SVC_DB* p_db, uint16_t handle) {
  if (!p_db) return nullptr;

  for (auto& attr : p_db->attr_list) {
    if (attr.handle == handle) return &attr;
    if (attr.handle > handle) return nullptr;
  }

  return nullptr;
}

/*******************************************************************************
 *
 * Function         gatts_read_attr_value_by_handle
 *
 * Description      Query attribute value by attribute handle.
 *
 * Parameter        p_db: pointer to the attribute database.
 *                  handle: Attribute handle to read.
 *                  offset: Read offset.
 *                  p_value: output parameter to carry out the attribute value.
 *                  p_len: output parameter as attribute length read.
 *                  read_long: this is a read blob request.
 *                  mtu: MTU.
 *                  sec_flag: current link security status.
 *                  key_size: encryption key size
 *
 * Returns          Status of operation.
 *
 ******************************************************************************/
tGATT_STATUS gatts_read_attr_value_by_handle(
    tGATT_TCB* p_tcb, tGATT_SVC_DB* p_db, uint8_t op_code, uint16_t handle,
    uint16_t offset, uint8_t* p_value, uint16_t* p_len, uint16_t mtu,
    tGATT_SEC_FLAG sec_flag, uint8_t key_size, uint32_t trans_id) {
  tGATT_ATTR* p_attr = find_attr_by_handle(p_db, handle);
  if (!p_attr) return GATT_NOT_FOUND;

  uint8_t* pp = p_value;
  tGATT_STATUS status = read_attr_value(*p_attr, offset, &pp,
                                        (bool)(op_code == GATT_REQ_READ_BLOB),
                                        mtu, p_len, sec_flag, key_size);

  if (status == GATT_PENDING) {
    status = gatts_send_app_read_request(p_tcb, op_code, p_attr->handle, offset,
                                         trans_id, p_attr->gatt_type);
  }
  return status;
}

/*******************************************************************************
 *
 * Function         gatts_read_attr_perm_check
 *
 * Description      Check attribute readability.
 *
 * Parameter        p_db: pointer to the attribute database.
 *                  handle: Attribute handle to read.
 *                  offset: Read offset.
 *                  p_value: output parameter to carry out the attribute value.
 *                  p_len: output parameter as attribute length read.
 *                  read_long: this is a read blob request.
 *                  mtu: MTU.
 *                  sec_flag: current link security status.
 *                  key_size: encryption key size
 *
 * Returns          Status of operation.
 *
 ******************************************************************************/
tGATT_STATUS gatts_read_attr_perm_check(tGATT_SVC_DB* p_db, bool is_long,
                                        uint16_t handle,
                                        tGATT_SEC_FLAG sec_flag,
                                        uint8_t key_size) {
  tGATT_ATTR* p_attr = find_attr_by_handle(p_db, handle);
  if (!p_attr) return GATT_NOT_FOUND;

  return gatts_check_attr_readability(*p_attr, 0, is_long, sec_flag, key_size);
}

/*******************************************************************************
 *
 * Function         gatts_write_attr_perm_check
 *
 * Description      Write attribute value into database.
 *
 * Parameter        p_db: pointer to the attribute database.
 *                  op_code:op code of this write.
 *                  handle: handle of the attribute to write.
 *                  offset: Write offset if write op code is write blob.
 *                  p_data: Attribute value to write.
 *                  len: attribute data length.
 *                  sec_flag: current link security status.
 *                  key_size: encryption key size
 *
 * Returns          Status of the operation.
 *
 ******************************************************************************/
tGATT_STATUS gatts_write_attr_perm_check(tGATT_SVC_DB* p_db, uint8_t op_code,
                                         uint16_t handle, uint16_t offset,
                                         uint8_t* p_data, uint16_t len,
                                         tGATT_SEC_FLAG sec_flag,
                                         uint8_t key_size) {
  GATT_TRACE_DEBUG(
      "%s: op_code=0x%0x handle=0x%04x offset=%d len=%d sec_flag=0x%0x "
      "key_size=%d",
      __func__, op_code, handle, offset, len, sec_flag, key_size);

  tGATT_ATTR* p_attr = find_attr_by_handle(p_db, handle);
  if (!p_attr) return GATT_NOT_FOUND;

  tGATT_PERM perm = p_attr->permission;
  uint16_t min_key_size = (((perm & GATT_ENCRYPT_KEY_SIZE_MASK) >> 12));
  if (min_key_size != 0) {
    min_key_size += 6;
  }
  GATT_TRACE_DEBUG("%s: p_attr->permission =0x%04x min_key_size==0x%04x",
                   __func__, p_attr->permission, min_key_size);

  if ((op_code == GATT_CMD_WRITE || op_code == GATT_REQ_WRITE) &&
      (perm & GATT_WRITE_SIGNED_PERM)) {
    /* use the rules for the mixed security see section 10.2.3*/
    /* use security mode 1 level 2 when the following condition follows */
    /* LE security mode 2 level 1 and LE security mode 1 level 2 */
    if ((perm & GATT_PERM_WRITE_SIGNED) && (perm & GATT_PERM_WRITE_ENCRYPTED)) {
      perm = GATT_PERM_WRITE_ENCRYPTED;
    }
    /* use security mode 1 level 3 when the following condition follows */
    /* LE security mode 2 level 2 and security mode 1 and LE */
    else if (((perm & GATT_PERM_WRITE_SIGNED_MITM) &&
              (perm & GATT_PERM_WRITE_ENCRYPTED)) ||
             /* LE security mode 2 and security mode 1 level 3 */
             ((perm & GATT_WRITE_SIGNED_PERM) &&
              (perm & GATT_PERM_WRITE_ENC_MITM))) {
      perm = GATT_PERM_WRITE_ENC_MITM;
    }
  }

  tGATT_STATUS status = GATT_NOT_FOUND;
  if ((op_code == GATT_SIGN_CMD_WRITE) && !(perm & GATT_WRITE_SIGNED_PERM)) {
    status = GATT_WRITE_NOT_PERMIT;
    GATT_TRACE_DEBUG("%s: sign cmd write not allowed", __func__);
  }
  if ((op_code == GATT_SIGN_CMD_WRITE) &&
      (sec_flag & GATT_SEC_FLAG_ENCRYPTED)) {
    status = GATT_INVALID_PDU;
    GATT_TRACE_ERROR("%s: Error!! sign cmd write sent on a encypted link",
                     __func__);
  } else if (!(perm & GATT_WRITE_ALLOWED)) {
    status = GATT_WRITE_NOT_PERMIT;
    GATT_TRACE_ERROR("%s: GATT_WRITE_NOT_PERMIT", __func__);
  }
  /* require authentication, but not been authenticated */
  else if ((perm & GATT_WRITE_AUTH_REQUIRED) &&
           !(sec_flag & GATT_SEC_FLAG_LKEY_UNAUTHED)) {
    status = GATT_INSUF_AUTHENTICATION;
    GATT_TRACE_ERROR("%s: GATT_INSUF_AUTHENTICATION", __func__);
  } else if ((perm & GATT_WRITE_MITM_REQUIRED) &&
             !(sec_flag & GATT_SEC_FLAG_LKEY_AUTHED)) {
    status = GATT_INSUF_AUTHENTICATION;
    GATT_TRACE_ERROR("%s: GATT_INSUF_AUTHENTICATION: MITM required", __func__);
  } else if ((perm & GATT_WRITE_ENCRYPTED_PERM) &&
             !(sec_flag & GATT_SEC_FLAG_ENCRYPTED)) {
    status = GATT_INSUF_ENCRYPTION;
    GATT_TRACE_ERROR("%s: GATT_INSUF_ENCRYPTION", __func__);
  } else if ((perm & GATT_WRITE_ENCRYPTED_PERM) &&
             (sec_flag & GATT_SEC_FLAG_ENCRYPTED) &&
             (key_size < min_key_size)) {
    status = GATT_INSUF_KEY_SIZE;
    GATT_TRACE_ERROR("%s: GATT_INSUF_KEY_SIZE", __func__);
  }
  /* LE security mode 2 attribute  */
  else if (perm & GATT_WRITE_SIGNED_PERM && op_code != GATT_SIGN_CMD_WRITE &&
           !(sec_flag & GATT_SEC_FLAG_ENCRYPTED) &&
           (perm & GATT_WRITE_ALLOWED) == 0) {
    status = GATT_INSUF_AUTHENTICATION;
    GATT_TRACE_ERROR(
        "%s: GATT_INSUF_AUTHENTICATION: LE security mode 2 required", __func__);
  } else /* writable: must be char value declaration or char descritpors
            */
  {
    uint16_t max_size = 0;

    if (p_attr->uuid.len == LEN_UUID_16) {
      switch (p_attr->uuid.uu.uuid16) {
        case GATT_UUID_CHAR_PRESENT_FORMAT: /* should be readable only */
        case GATT_UUID_CHAR_EXT_PROP:       /* should be readable only */
        case GATT_UUID_CHAR_AGG_FORMAT:     /* should be readable only */
        case GATT_UUID_CHAR_VALID_RANGE:
          status = GATT_WRITE_NOT_PERMIT;
          break;

        case GATT_UUID_CHAR_CLIENT_CONFIG:
        /* fall through */
        case GATT_UUID_CHAR_SRVR_CONFIG:
          max_size = 2;
        /* fall through */
        case GATT_UUID_CHAR_DESCRIPTION:
        default: /* any other must be character value declaration */
          status = GATT_SUCCESS;
          break;
      }
    } else if (p_attr->uuid.len == LEN_UUID_128 ||
               p_attr->uuid.len == LEN_UUID_32) {
      status = GATT_SUCCESS;
    } else {
      status = GATT_INVALID_PDU;
    }

    if (p_data == NULL && len > 0) {
      status = GATT_INVALID_PDU;
    }
    /* these attribute does not allow write blob */
    else if ((p_attr->uuid.len == LEN_UUID_16) &&
             (p_attr->uuid.uu.uuid16 == GATT_UUID_CHAR_CLIENT_CONFIG ||
              p_attr->uuid.uu.uuid16 == GATT_UUID_CHAR_SRVR_CONFIG)) {
      if (op_code == GATT_REQ_PREPARE_WRITE &&
          offset != 0) /* does not allow write blob */
      {
        status = GATT_NOT_LONG;
        GATT_TRACE_ERROR("%s: GATT_NOT_LONG", __func__);
      } else if (len != max_size) /* data does not match the required format */
      {
        status = GATT_INVALID_ATTR_LEN;
        GATT_TRACE_ERROR("%s: GATT_INVALID_PDU", __func__);
      } else {
        status = GATT_SUCCESS;
      }
    }
  }

  return status;
}

static void uuid_to_str(const tBT_UUID bt_uuid, char* str_buf, size_t buf_len) {
  if (bt_uuid.len == LEN_UUID_16) {
    snprintf(str_buf, buf_len, "0x%04x", bt_uuid.uu.uuid16);
  } else if (bt_uuid.len == LEN_UUID_32) {
    snprintf(str_buf, buf_len, "0x%08x", bt_uuid.uu.uuid32);
  } else if (bt_uuid.len == LEN_UUID_128) {
    int x = snprintf(str_buf, buf_len, "%02x%02x%02x%02x-%02x%02x-%02x%02x-",
                     bt_uuid.uu.uuid128[15], bt_uuid.uu.uuid128[14],
                     bt_uuid.uu.uuid128[13], bt_uuid.uu.uuid128[12],
                     bt_uuid.uu.uuid128[11], bt_uuid.uu.uuid128[10],
                     bt_uuid.uu.uuid128[9], bt_uuid.uu.uuid128[8]);
    snprintf(&str_buf[x], buf_len - x, "%02x%02x-%02x%02x%02x%02x%02x%02x",
             bt_uuid.uu.uuid128[7], bt_uuid.uu.uuid128[6],
             bt_uuid.uu.uuid128[5], bt_uuid.uu.uuid128[4],
             bt_uuid.uu.uuid128[3], bt_uuid.uu.uuid128[2],
             bt_uuid.uu.uuid128[1], bt_uuid.uu.uuid128[0]);
  } else
    snprintf(str_buf, buf_len, "Unknown (len=%d)", bt_uuid.len);
}

/**
 * Description      Allocate a memory space for a new attribute, and link this
 *                  attribute into the database attribute list.
 *
 *
 * Parameter        p_db    : database pointer.
 *                  uuid:     attribute UUID
 *
 * Returns          pointer to the newly allocated attribute.
 *
 */
static tGATT_ATTR& allocate_attr_in_db(tGATT_SVC_DB& db, const tBT_UUID& uuid,
                                       tGATT_PERM perm) {
  if (db.next_handle >= db.end_handle) {
    LOG(FATAL) << __func__
               << " wrong number of handles! handle_max = " << +db.end_handle
               << ", next_handle = " << +db.next_handle;
  }

  db.attr_list.emplace_back();
  tGATT_ATTR& attr = db.attr_list.back();
  attr.handle = db.next_handle++;
  attr.uuid = uuid;
  attr.permission = perm;

  char uuid_str[37];
  uuid_to_str(attr.uuid, uuid_str, sizeof(uuid_str));

  return attr;
}

/*******************************************************************************
 *
 * Function         gatts_send_app_read_request
 *
 * Description      Send application read request callback
 *
 * Returns          status of operation.
 *
 ******************************************************************************/
static tGATT_STATUS gatts_send_app_read_request(
    tGATT_TCB* p_tcb, uint8_t op_code, uint16_t handle, uint16_t offset,
    uint32_t trans_id, bt_gatt_db_attribute_type_t gatt_type) {
  tGATT_SRV_LIST_ELEM& el = *gatt_sr_find_i_rcb_by_handle(handle);
  uint16_t conn_id = GATT_CREATE_CONN_ID(p_tcb->tcb_idx, el.gatt_if);

  if (trans_id == 0) {
    trans_id = gatt_sr_enqueue_cmd(p_tcb, op_code, handle);
    gatt_sr_update_cback_cnt(p_tcb, el.gatt_if, true, true);
  }

  if (trans_id != 0) {
    tGATTS_DATA sr_data;
    memset(&sr_data, 0, sizeof(tGATTS_DATA));

    sr_data.read_req.handle = handle;
    sr_data.read_req.is_long = (bool)(op_code == GATT_REQ_READ_BLOB);
    sr_data.read_req.offset = offset;

    uint8_t opcode;
    if (gatt_type == BTGATT_DB_DESCRIPTOR) {
      opcode = GATTS_REQ_TYPE_READ_DESCRIPTOR;
    } else if (gatt_type == BTGATT_DB_CHARACTERISTIC) {
      opcode = GATTS_REQ_TYPE_READ_CHARACTERISTIC;
    } else {
      GATT_TRACE_ERROR(
          "%s: Attempt to read attribute that's not tied with"
          " characteristic or descriptor value.",
          __func__);
      return GATT_ERROR;
    }

    gatt_sr_send_req_callback(conn_id, trans_id, opcode, &sr_data);
    return (tGATT_STATUS)GATT_PENDING;
  } else
    return (tGATT_STATUS)GATT_BUSY; /* max pending command, application error */
}
