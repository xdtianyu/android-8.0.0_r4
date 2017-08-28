//
// Copyright 2015 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#define LOG_TAG "dual_mode_controller"

#include "dual_mode_controller.h"

#include <memory>

#include <base/logging.h>
#include "base/files/file_util.h"
#include "base/json/json_reader.h"
#include "base/values.h"
#include "event_packet.h"

#include "osi/include/log.h"
#include "osi/include/osi.h"
#include "stack/include/hcidefs.h"

using std::vector;

namespace {

// Included in certain events to indicate success (specific to the event
// context).
const uint8_t kSuccessStatus = 0;

const uint8_t kUnknownHciCommand = 1;

// The location of the config file loaded to populate controller attributes.
const std::string kControllerPropertiesFile =
    "/etc/bluetooth/controller_properties.json";

// Inquiry modes for specifiying inquiry result formats.
const uint8_t kStandardInquiry = 0x00;
const uint8_t kRssiInquiry = 0x01;
const uint8_t kExtendedOrRssiInquiry = 0x02;

// The bd address of another (fake) device.
const vector<uint8_t> kOtherDeviceBdAddress = {6, 5, 4, 3, 2, 1};

void LogCommand(const char* command) {
  LOG_INFO(LOG_TAG, "Controller performing command: %s", command);
}

// Functions used by JSONValueConverter to read stringified JSON into Properties
// object.
bool ParseUint8t(const base::StringPiece& value, uint8_t* field) {
  *field = std::stoi(value.as_string());
  return true;
}

bool ParseUint16t(const base::StringPiece& value, uint16_t* field) {
  *field = std::stoi(value.as_string());
  return true;
}

}  // namespace

namespace test_vendor_lib {

void DualModeController::AddControllerEvent(std::chrono::milliseconds delay,
                                            const TaskCallback& task) {
  controller_events_.push_back(schedule_task_(delay, task));
}

void DualModeController::SendCommandCompleteSuccess(
    uint16_t command_opcode) const {
  send_event_(EventPacket::CreateCommandCompleteOnlyStatusEvent(
      command_opcode, kSuccessStatus));
}

void DualModeController::SendCommandCompleteOnlyStatus(uint16_t command_opcode,
                                                       uint8_t status) const {
  send_event_(EventPacket::CreateCommandCompleteOnlyStatusEvent(command_opcode,
                                                                status));
}

void DualModeController::SendCommandStatus(uint8_t status,
                                           uint16_t command_opcode) const {
  send_event_(EventPacket::CreateCommandStatusEvent(status, command_opcode));
}

void DualModeController::SendCommandStatusSuccess(
    uint16_t command_opcode) const {
  SendCommandStatus(kSuccessStatus, command_opcode);
}

DualModeController::DualModeController()
    : state_(kStandby),
      properties_(kControllerPropertiesFile),
      test_channel_state_(kNone) {
#define SET_HANDLER(opcode, method)                                     \
  active_hci_commands_[opcode] = [this](const vector<uint8_t>& param) { \
    method(param);                                                      \
  };
  SET_HANDLER(HCI_RESET, HciReset);
  SET_HANDLER(HCI_READ_BUFFER_SIZE, HciReadBufferSize);
  SET_HANDLER(HCI_HOST_BUFFER_SIZE, HciHostBufferSize);
  SET_HANDLER(HCI_READ_LOCAL_VERSION_INFO, HciReadLocalVersionInformation);
  SET_HANDLER(HCI_READ_BD_ADDR, HciReadBdAddr);
  SET_HANDLER(HCI_READ_LOCAL_SUPPORTED_CMDS, HciReadLocalSupportedCommands);
  SET_HANDLER(HCI_READ_LOCAL_SUPPORTED_CODECS, HciReadLocalSupportedCodecs);
  SET_HANDLER(HCI_READ_LOCAL_EXT_FEATURES, HciReadLocalExtendedFeatures);
  SET_HANDLER(HCI_WRITE_SIMPLE_PAIRING_MODE, HciWriteSimplePairingMode);
  SET_HANDLER(HCI_WRITE_LE_HOST_SUPPORT, HciWriteLeHostSupport);
  SET_HANDLER(HCI_SET_EVENT_MASK, HciSetEventMask);
  SET_HANDLER(HCI_WRITE_INQUIRY_MODE, HciWriteInquiryMode);
  SET_HANDLER(HCI_WRITE_PAGESCAN_TYPE, HciWritePageScanType);
  SET_HANDLER(HCI_WRITE_INQSCAN_TYPE, HciWriteInquiryScanType);
  SET_HANDLER(HCI_WRITE_CLASS_OF_DEVICE, HciWriteClassOfDevice);
  SET_HANDLER(HCI_WRITE_PAGE_TOUT, HciWritePageTimeout);
  SET_HANDLER(HCI_WRITE_DEF_POLICY_SETTINGS, HciWriteDefaultLinkPolicySettings);
  SET_HANDLER(HCI_READ_LOCAL_NAME, HciReadLocalName);
  SET_HANDLER(HCI_CHANGE_LOCAL_NAME, HciWriteLocalName);
  SET_HANDLER(HCI_WRITE_EXT_INQ_RESPONSE, HciWriteExtendedInquiryResponse);
  SET_HANDLER(HCI_WRITE_VOICE_SETTINGS, HciWriteVoiceSetting);
  SET_HANDLER(HCI_WRITE_CURRENT_IAC_LAP, HciWriteCurrentIacLap);
  SET_HANDLER(HCI_WRITE_INQUIRYSCAN_CFG, HciWriteInquiryScanActivity);
  SET_HANDLER(HCI_WRITE_SCAN_ENABLE, HciWriteScanEnable);
  SET_HANDLER(HCI_SET_EVENT_FILTER, HciSetEventFilter);
  SET_HANDLER(HCI_INQUIRY, HciInquiry);
  SET_HANDLER(HCI_INQUIRY_CANCEL, HciInquiryCancel);
  SET_HANDLER(HCI_DELETE_STORED_LINK_KEY, HciDeleteStoredLinkKey);
  SET_HANDLER(HCI_RMT_NAME_REQUEST, HciRemoteNameRequest);
  SET_HANDLER(HCI_BLE_SET_EVENT_MASK, HciLeSetEventMask);
  SET_HANDLER(HCI_BLE_READ_BUFFER_SIZE, HciLeReadBufferSize);
  SET_HANDLER(HCI_BLE_READ_LOCAL_SPT_FEAT, HciLeReadLocalSupportedFeatures);
  SET_HANDLER(HCI_BLE_WRITE_RANDOM_ADDR, HciLeSetRandomAddress);
  SET_HANDLER(HCI_BLE_WRITE_ADV_DATA, HciLeSetAdvertisingData);
  SET_HANDLER(HCI_BLE_WRITE_ADV_PARAMS, HciLeSetAdvertisingParameters);
  SET_HANDLER(HCI_BLE_WRITE_SCAN_PARAMS, HciLeSetScanParameters);
  SET_HANDLER(HCI_BLE_WRITE_SCAN_ENABLE, HciLeSetScanEnable);
  SET_HANDLER(HCI_BLE_READ_WHITE_LIST_SIZE, HciLeReadWhiteListSize);
  SET_HANDLER(HCI_BLE_RAND, HciLeRand);
  SET_HANDLER(HCI_BLE_READ_SUPPORTED_STATES, HciLeReadSupportedStates);
  SET_HANDLER((HCI_GRP_VENDOR_SPECIFIC | 0x27), HciBleVendorSleepMode);
  SET_HANDLER(HCI_BLE_VENDOR_CAP_OCF, HciBleVendorCap);
  SET_HANDLER(HCI_BLE_MULTI_ADV_OCF, HciBleVendorMultiAdv);
  SET_HANDLER((HCI_GRP_VENDOR_SPECIFIC | 0x155), HciBleVendor155);
  SET_HANDLER((HCI_GRP_VENDOR_SPECIFIC | 0x157), HciBleVendor157);
  SET_HANDLER(HCI_BLE_ENERGY_INFO_OCF, HciBleEnergyInfo);
  SET_HANDLER(HCI_BLE_EXTENDED_SCAN_PARAMS_OCF, HciBleExtendedScanParams);
#undef SET_HANDLER

#define SET_TEST_HANDLER(command_name, method)  \
  active_test_channel_commands_[command_name] = \
      [this](const vector<std::string>& param) { method(param); };
  SET_TEST_HANDLER("CLEAR", TestChannelClear);
  SET_TEST_HANDLER("CLEAR_EVENT_DELAY", TestChannelClearEventDelay);
  SET_TEST_HANDLER("DISCOVER", TestChannelDiscover);
  SET_TEST_HANDLER("SET_EVENT_DELAY", TestChannelSetEventDelay);
  SET_TEST_HANDLER("TIMEOUT_ALL", TestChannelTimeoutAll);
#undef SET_TEST_HANDLER
}

void DualModeController::RegisterTaskScheduler(
    std::function<AsyncTaskId(std::chrono::milliseconds, const TaskCallback&)>
        oneshotScheduler) {
  schedule_task_ = oneshotScheduler;
}

void DualModeController::RegisterPeriodicTaskScheduler(
    std::function<AsyncTaskId(std::chrono::milliseconds,
                              std::chrono::milliseconds, const TaskCallback&)>
        periodicScheduler) {
  schedule_periodic_task_ = periodicScheduler;
}

void DualModeController::RegisterTaskCancel(
    std::function<void(AsyncTaskId)> task_cancel) {
  cancel_task_ = task_cancel;
}

void DualModeController::HandleTestChannelCommand(
    const std::string& name, const vector<std::string>& args) {
  if (active_test_channel_commands_.count(name) == 0) return;
  active_test_channel_commands_[name](args);
}

void DualModeController::HandleCommand(
    std::unique_ptr<CommandPacket> command_packet) {
  uint16_t opcode = command_packet->GetOpcode();
  LOG_INFO(LOG_TAG, "Command opcode: 0x%04X, OGF: 0x%04X, OCF: 0x%04X", opcode,
           command_packet->GetOGF(), command_packet->GetOCF());

  // The command hasn't been registered with the handler yet. There is nothing
  // to do.
  if (active_hci_commands_.count(opcode) == 0)
    return;
  else if (test_channel_state_ == kTimeoutAll)
    return;
  active_hci_commands_[opcode](command_packet->GetPayload());
}

void DualModeController::RegisterEventChannel(
    const std::function<void(std::unique_ptr<EventPacket>)>& callback) {
  send_event_ = callback;
}

void DualModeController::HandleTimerTick() {
  // PageScan();
  if (le_scan_enable_) LOG_ERROR(LOG_TAG, "LE scan");
  // LeScan();
}

void DualModeController::SetTimerPeriod(std::chrono::milliseconds new_period) {
  timer_period_ = new_period;

  if (timer_tick_task_ == kInvalidTaskId) return;

  // Restart the timer with the new period
  StopTimer();
  StartTimer();
}

void DualModeController::StartTimer() {
  LOG_ERROR(LOG_TAG, "StartTimer");
  timer_tick_task_ = schedule_periodic_task_(
      std::chrono::milliseconds(0), timer_period_,
      [this]() { DualModeController::HandleTimerTick(); });
}

void DualModeController::StopTimer() {
  LOG_ERROR(LOG_TAG, "StopTimer");
  cancel_task_(timer_tick_task_);
  timer_tick_task_ = kInvalidTaskId;
}

void DualModeController::TestChannelClear(
    UNUSED_ATTR const vector<std::string>& args) {
  LogCommand("TestChannel Clear");
  test_channel_state_ = kNone;
}

void DualModeController::TestChannelDiscover(
    UNUSED_ATTR const vector<std::string>& args) {
  LogCommand("TestChannel Discover");
  /* TODO: Replace with adding devices */
  /*

    for (size_t i = 0; i < args.size() - 1; i += 2)
      SendExtendedInquiryResult(args[i], args[i + 1]);
  */
}

void DualModeController::TestChannelTimeoutAll(
    UNUSED_ATTR const vector<std::string>& args) {
  LogCommand("TestChannel Timeout All");
  test_channel_state_ = kTimeoutAll;
}

void DualModeController::TestChannelSetEventDelay(
    const vector<std::string>& args UNUSED_ATTR) {
  LogCommand("TestChannel Set Event Delay");
  test_channel_state_ = kDelayedResponse;
}

void DualModeController::TestChannelClearEventDelay(
    UNUSED_ATTR const vector<std::string>& args) {
  LogCommand("TestChannel Clear Event Delay");
  test_channel_state_ = kNone;
}

void DualModeController::HciReset(UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Reset");
  state_ = kStandby;
  if (timer_tick_task_ != kInvalidTaskId) {
    LOG_INFO(LOG_TAG, "The timer was already running!");
    StopTimer();
  }
  LOG_INFO(LOG_TAG, "Starting timer.");
  StartTimer();

  SendCommandCompleteSuccess(HCI_RESET);
}

void DualModeController::HciReadBufferSize(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Read Buffer Size");
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadBufferSize(
          kSuccessStatus, properties_.GetAclDataPacketSize(),
          properties_.GetSynchronousDataPacketSize(),
          properties_.GetTotalNumAclDataPackets(),
          properties_.GetTotalNumSynchronousDataPackets());

  send_event_(std::move(command_complete));
}

void DualModeController::HciHostBufferSize(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Host Buffer Size");
  SendCommandCompleteSuccess(HCI_HOST_BUFFER_SIZE);
}

void DualModeController::HciReadLocalVersionInformation(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Read Local Version Information");
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadLocalVersionInformation(
          kSuccessStatus, properties_.GetVersion(), properties_.GetRevision(),
          properties_.GetLmpPalVersion(), properties_.GetManufacturerName(),
          properties_.GetLmpPalSubversion());
  send_event_(std::move(command_complete));
}

void DualModeController::HciReadBdAddr(
    UNUSED_ATTR const vector<uint8_t>& args) {
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadBdAddr(kSuccessStatus,
                                                   properties_.GetAddress());
  send_event_(std::move(command_complete));
}

void DualModeController::HciReadLocalSupportedCommands(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Read Local Supported Commands");
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadLocalSupportedCommands(
          kSuccessStatus, properties_.GetLocalSupportedCommands());
  send_event_(std::move(command_complete));
}

void DualModeController::HciReadLocalSupportedCodecs(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Read Local Supported Codecs");
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadLocalSupportedCodecs(
          kSuccessStatus, properties_.GetSupportedCodecs(),
          properties_.GetVendorSpecificCodecs());
  send_event_(std::move(command_complete));
}

void DualModeController::HciReadLocalExtendedFeatures(
    const vector<uint8_t>& args) {
  LogCommand("Read Local Extended Features");
  CHECK(args.size() == 2);
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadLocalExtendedFeatures(
          kSuccessStatus, args[1],
          properties_.GetLocalExtendedFeaturesMaximumPageNumber(),
          properties_.GetLocalExtendedFeatures(args[1]));
  send_event_(std::move(command_complete));
}

void DualModeController::HciWriteSimplePairingMode(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Simple Pairing Mode");
  SendCommandCompleteSuccess(HCI_WRITE_SIMPLE_PAIRING_MODE);
}

void DualModeController::HciWriteLeHostSupport(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Le Host Support");
  SendCommandCompleteSuccess(HCI_WRITE_LE_HOST_SUPPORT);
}

void DualModeController::HciSetEventMask(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Set Event Mask");
  SendCommandCompleteSuccess(HCI_SET_EVENT_MASK);
}

void DualModeController::HciWriteInquiryMode(const vector<uint8_t>& args) {
  LogCommand("Write Inquiry Mode");
  CHECK(args.size() == 2);
  inquiry_mode_ = args[1];
  SendCommandCompleteSuccess(HCI_WRITE_INQUIRY_MODE);
}

void DualModeController::HciWritePageScanType(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Page Scan Type");
  SendCommandCompleteSuccess(HCI_WRITE_PAGESCAN_TYPE);
}

void DualModeController::HciWriteInquiryScanType(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Inquiry Scan Type");
  SendCommandCompleteSuccess(HCI_WRITE_INQSCAN_TYPE);
}

void DualModeController::HciWriteClassOfDevice(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Class Of Device");
  SendCommandCompleteSuccess(HCI_WRITE_CLASS_OF_DEVICE);
}

void DualModeController::HciWritePageTimeout(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Page Timeout");
  SendCommandCompleteSuccess(HCI_WRITE_PAGE_TOUT);
}

void DualModeController::HciWriteDefaultLinkPolicySettings(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Default Link Policy Settings");
  SendCommandCompleteSuccess(HCI_WRITE_DEF_POLICY_SETTINGS);
}

void DualModeController::HciReadLocalName(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Get Local Name");
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteReadLocalName(
          kSuccessStatus, properties_.GetLocalName());
  send_event_(std::move(command_complete));
}

void DualModeController::HciWriteLocalName(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Local Name");
  SendCommandCompleteSuccess(HCI_CHANGE_LOCAL_NAME);
}

void DualModeController::HciWriteExtendedInquiryResponse(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Extended Inquiry Response");
  SendCommandCompleteSuccess(HCI_WRITE_EXT_INQ_RESPONSE);
}

void DualModeController::HciWriteVoiceSetting(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Voice Setting");
  SendCommandCompleteSuccess(HCI_WRITE_VOICE_SETTINGS);
}

void DualModeController::HciWriteCurrentIacLap(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Current IAC LAP");
  SendCommandCompleteSuccess(HCI_WRITE_CURRENT_IAC_LAP);
}

void DualModeController::HciWriteInquiryScanActivity(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Inquiry Scan Activity");
  SendCommandCompleteSuccess(HCI_WRITE_INQUIRYSCAN_CFG);
}

void DualModeController::HciWriteScanEnable(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Write Scan Enable");
  SendCommandCompleteSuccess(HCI_WRITE_SCAN_ENABLE);
}

void DualModeController::HciSetEventFilter(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Set Event Filter");
  SendCommandCompleteSuccess(HCI_SET_EVENT_FILTER);
}

void DualModeController::HciInquiry(const vector<uint8_t>& args) {
  // Fake inquiry response for a fake device.
  const EventPacket::PageScanRepetitionMode kPageScanRepetitionMode =
      EventPacket::kR0;
  const uint32_t kClassOfDevice = 0x030201;
  const uint16_t kClockOffset = 513;
  BtAddress other_addr;
  other_addr.FromVector(kOtherDeviceBdAddress);

  LogCommand("Inquiry");
  state_ = kInquiry;
  SendCommandStatusSuccess(HCI_INQUIRY);
  switch (inquiry_mode_) {
    case (kStandardInquiry): {
      std::unique_ptr<EventPacket> inquiry_result_evt =
          EventPacket::CreateInquiryResultEvent(other_addr,
                                                kPageScanRepetitionMode,
                                                kClassOfDevice, kClockOffset);
      send_event_(std::move(inquiry_result_evt));
      /* TODO: Return responses from modeled devices */
    } break;

    case (kRssiInquiry):
      LOG_INFO(LOG_TAG, "RSSI Inquiry Mode currently not supported.");
      break;

    case (kExtendedOrRssiInquiry): {
      const std::string name = "Foobar";
      vector<uint8_t> extended_inquiry_data = {
          static_cast<uint8_t>(name.length() + 1), 0x09};

      for (size_t i = 0; i < name.length(); i++)
        extended_inquiry_data.push_back(name[i]);
      extended_inquiry_data.push_back('\0');

      uint8_t rssi = static_cast<uint8_t>(-20);
      send_event_(EventPacket::CreateExtendedInquiryResultEvent(
          other_addr, kPageScanRepetitionMode, kClassOfDevice, kClockOffset,
          rssi, extended_inquiry_data));
      /* TODO: Return responses from modeled devices */
    } break;
  }
  AddControllerEvent(std::chrono::milliseconds(args[4] * 1280),
                     [this]() { DualModeController::InquiryTimeout(); });
}

void DualModeController::HciInquiryCancel(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Inquiry Cancel");
  CHECK(state_ == kInquiry);
  state_ = kStandby;
  SendCommandCompleteSuccess(HCI_INQUIRY_CANCEL);
}

void DualModeController::InquiryTimeout() {
  LOG_INFO(LOG_TAG, "InquiryTimer fired");
  if (state_ == kInquiry) {
    state_ = kStandby;
    send_event_(EventPacket::CreateInquiryCompleteEvent(kSuccessStatus));
  }
}

void DualModeController::HciDeleteStoredLinkKey(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Delete Stored Link Key");
  /* Check the last octect in |args|. If it is 0, delete only the link key for
   * the given BD_ADDR. If is is 1, delete all stored link keys. */
  SendCommandCompleteOnlyStatus(HCI_INQUIRY_CANCEL, kUnknownHciCommand);
}

void DualModeController::HciRemoteNameRequest(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("Remote Name Request");
  SendCommandStatusSuccess(HCI_RMT_NAME_REQUEST);
}

void DualModeController::HciLeSetEventMask(const vector<uint8_t>& args) {
  LogCommand("LE SetEventMask");
  le_event_mask_ = args;
  SendCommandCompleteSuccess(HCI_BLE_SET_EVENT_MASK);
}

void DualModeController::HciLeReadBufferSize(
    UNUSED_ATTR const vector<uint8_t>& args) {
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteLeReadBufferSize(
          kSuccessStatus, properties_.GetLeDataPacketLength(),
          properties_.GetTotalNumLeDataPackets());
  send_event_(std::move(command_complete));
}

void DualModeController::HciLeReadLocalSupportedFeatures(
    UNUSED_ATTR const vector<uint8_t>& args) {
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteLeReadLocalSupportedFeatures(
          kSuccessStatus, properties_.GetLeLocalSupportedFeatures());
  send_event_(std::move(command_complete));
}

void DualModeController::HciLeSetRandomAddress(const vector<uint8_t>& args) {
  LogCommand("LE SetRandomAddress");
  CHECK(args.size() == 7);
  vector<uint8_t> new_addr = {args[1], args[2], args[3],
                              args[4], args[5], args[6]};
  CHECK(le_random_address_.FromVector(new_addr));
  SendCommandCompleteSuccess(HCI_BLE_WRITE_RANDOM_ADDR);
}

void DualModeController::HciLeSetAdvertisingParameters(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("LE SetAdvertisingParameters");
  SendCommandCompleteSuccess(HCI_BLE_WRITE_ADV_PARAMS);
}

void DualModeController::HciLeSetAdvertisingData(
    UNUSED_ATTR const vector<uint8_t>& args) {
  LogCommand("LE SetAdvertisingData");
  SendCommandCompleteSuccess(HCI_BLE_WRITE_ADV_DATA);
}

void DualModeController::HciLeSetScanParameters(const vector<uint8_t>& args) {
  LogCommand("LE SetScanParameters");
  CHECK(args.size() == 8);
  le_scan_type_ = args[1];
  le_scan_interval_ = args[2] | (args[3] << 8);
  le_scan_window_ = args[4] | (args[5] << 8);
  own_address_type_ = args[6];
  scanning_filter_policy_ = args[7];
  SendCommandCompleteSuccess(HCI_BLE_WRITE_SCAN_PARAMS);
}

void DualModeController::HciLeSetScanEnable(const vector<uint8_t>& args) {
  LogCommand("LE SetScanEnable");
  CHECK(args.size() == 3);
  CHECK(args[0] == 2);
  le_scan_enable_ = args[1];
  filter_duplicates_ = args[2];
  SendCommandCompleteSuccess(HCI_BLE_WRITE_SCAN_ENABLE);
}

void DualModeController::HciLeReadWhiteListSize(
    UNUSED_ATTR const vector<uint8_t>& args) {
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteLeReadWhiteListSize(
          kSuccessStatus, properties_.GetLeWhiteListSize());
  send_event_(std::move(command_complete));
}

void DualModeController::HciLeRand(UNUSED_ATTR const vector<uint8_t>& args) {
  uint64_t random_val = rand();
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteLeRand(kSuccessStatus, random_val);
  send_event_(std::move(command_complete));
}

void DualModeController::HciLeReadSupportedStates(
    UNUSED_ATTR const vector<uint8_t>& args) {
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteLeReadSupportedStates(
          kSuccessStatus, properties_.GetLeSupportedStates());
  send_event_(std::move(command_complete));
}

void DualModeController::HciBleVendorSleepMode(
    UNUSED_ATTR const vector<uint8_t>& args) {
  SendCommandCompleteOnlyStatus(HCI_GRP_VENDOR_SPECIFIC | 0x27,
                                kUnknownHciCommand);
}

void DualModeController::HciBleVendorCap(
    UNUSED_ATTR const vector<uint8_t>& args) {
  std::unique_ptr<EventPacket> command_complete =
      EventPacket::CreateCommandCompleteLeVendorCap(
          kSuccessStatus, properties_.GetLeVendorCap());
  send_event_(std::move(command_complete));
}

void DualModeController::HciBleVendorMultiAdv(
    UNUSED_ATTR const vector<uint8_t>& args) {
  SendCommandCompleteOnlyStatus(HCI_BLE_MULTI_ADV_OCF, kUnknownHciCommand);
}

void DualModeController::HciBleVendor155(
    UNUSED_ATTR const vector<uint8_t>& args) {
  SendCommandCompleteOnlyStatus(HCI_GRP_VENDOR_SPECIFIC | 0x155,
                                kUnknownHciCommand);
}

void DualModeController::HciBleVendor157(
    UNUSED_ATTR const vector<uint8_t>& args) {
  SendCommandCompleteOnlyStatus(HCI_GRP_VENDOR_SPECIFIC | 0x157,
                                kUnknownHciCommand);
}

void DualModeController::HciBleEnergyInfo(
    UNUSED_ATTR const vector<uint8_t>& args) {
  SendCommandCompleteOnlyStatus(HCI_BLE_ENERGY_INFO_OCF, kUnknownHciCommand);
}

void DualModeController::HciBleExtendedScanParams(
    UNUSED_ATTR const vector<uint8_t>& args) {
  SendCommandCompleteOnlyStatus(HCI_BLE_EXTENDED_SCAN_PARAMS_OCF,
                                kUnknownHciCommand);
}

DualModeController::Properties::Properties(const std::string& file_name)
    : acl_data_packet_size_(1024),
      sco_data_packet_size_(255),
      num_acl_data_packets_(10),
      num_sco_data_packets_(10),
      version_(4),
      revision_(1),
      lmp_pal_version_(0),
      manufacturer_name_(0),
      lmp_pal_subversion_(0),
      le_data_packet_length_(27),
      num_le_data_packets_(15),
      le_white_list_size_(15) {
  std::string properties_raw;

  local_extended_features_ = {0xffffffffffffffff, 0x7};

  CHECK(address_.FromString("01:02:03:04:05:06"));
  local_name_ = "DefaultName";

  supported_codecs_ = {1};
  vendor_specific_codecs_ = {};

  for (int i = 0; i < 64; i++) local_supported_commands_.push_back(0xff);

  le_supported_features_ = 0x1f;
  le_supported_states_ = 0x3ffffffffff;
  le_vendor_cap_ = {0x05, 0x01, 0x00, 0x04, 0x80, 0x01, 0x10,
                    0x01, 0x60, 0x00, 0x0a, 0x00, 0x01, 0x01};

  LOG_INFO(LOG_TAG, "Reading controller properties from %s.",
           file_name.c_str());
  if (!base::ReadFileToString(base::FilePath(file_name), &properties_raw)) {
    LOG_ERROR(LOG_TAG, "Error reading controller properties from file.");
    return;
  }

  std::unique_ptr<base::Value> properties_value_ptr =
      base::JSONReader::Read(properties_raw);
  if (properties_value_ptr.get() == nullptr)
    LOG_INFO(LOG_TAG,
             "Error controller properties may consist of ill-formed JSON.");

  // Get the underlying base::Value object, which is of type
  // base::Value::TYPE_DICTIONARY, and read it into member variables.
  base::Value& properties_dictionary = *(properties_value_ptr.get());
  base::JSONValueConverter<DualModeController::Properties> converter;

  if (!converter.Convert(properties_dictionary, this))
    LOG_INFO(LOG_TAG,
             "Error converting JSON properties into Properties object.");
}

// static
void DualModeController::Properties::RegisterJSONConverter(
    base::JSONValueConverter<DualModeController::Properties>* converter) {
// TODO(dennischeng): Use RegisterIntField() here?
#define REGISTER_UINT8_T(field_name, field) \
  converter->RegisterCustomField<uint8_t>(  \
      field_name, &DualModeController::Properties::field, &ParseUint8t);
#define REGISTER_UINT16_T(field_name, field) \
  converter->RegisterCustomField<uint16_t>(  \
      field_name, &DualModeController::Properties::field, &ParseUint16t);
  REGISTER_UINT16_T("AclDataPacketSize", acl_data_packet_size_);
  REGISTER_UINT8_T("ScoDataPacketSize", sco_data_packet_size_);
  REGISTER_UINT16_T("NumAclDataPackets", num_acl_data_packets_);
  REGISTER_UINT16_T("NumScoDataPackets", num_sco_data_packets_);
  REGISTER_UINT8_T("Version", version_);
  REGISTER_UINT16_T("Revision", revision_);
  REGISTER_UINT8_T("LmpPalVersion", lmp_pal_version_);
  REGISTER_UINT16_T("ManufacturerName", manufacturer_name_);
  REGISTER_UINT16_T("LmpPalSubversion", lmp_pal_subversion_);
#undef REGISTER_UINT8_T
#undef REGISTER_UINT16_T
}

}  // namespace test_vendor_lib
