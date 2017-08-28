/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "calibration/over_temp/over_temp_cal.h"

#include <float.h>
#include <math.h>
#include <string.h>

#include "calibration/util/cal_log.h"
#include "common/math/vec.h"
#include "util/nano_assert.h"

/////// DEFINITIONS AND MACROS ////////////////////////////////////////////////

// Rate-limits the search for the nearest offset estimate to every 2 seconds.
#define OVERTEMPCAL_NEAREST_NANOS (2000000000)

// Rate-limits the check of old data to every 2 hours.
#define OVERTEMPCAL_STALE_CHECK_TIME_NANOS (7200000000000)

// Value used to check whether OTC model parameters are near zero.
#define OTC_MODELDATA_NEAR_ZERO_TOL (1e-7f)  // [rad/sec]

#ifdef OVERTEMPCAL_DBG_ENABLED
// A debug version label to help with tracking results.
#define OVERTEMPCAL_DEBUG_VERSION_STRING "[Apr 05, 2017]"

// The time value used to throttle debug messaging.
#define OVERTEMPCAL_WAIT_TIME_NANOS (300000000)

// Debug log tag string used to identify debug report output data.
#define OVERTEMPCAL_REPORT_TAG "[OVER_TEMP_CAL:REPORT]"

// Converts units of radians to milli-degrees.
#define RAD_TO_MILLI_DEGREES (float)(1e3f * 180.0f / NANO_PI)

// Sensor axis label definition with index correspondence: 0=X, 1=Y, 2=Z.
static const char  kDebugAxisLabel[3] = "XYZ";
#endif  // OVERTEMPCAL_DBG_ENABLED

/////// FORWARD DECLARATIONS //////////////////////////////////////////////////

// Updates the model estimate data nearest to the sensor's temperature.
static void setNearestEstimate(struct OverTempCal *over_temp_cal,
                              const float *offset, float offset_temp_celsius,
                              uint64_t timestamp_nanos);

/*
 * Determines if a new over-temperature model fit should be performed, and then
 * updates the model as needed.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   timestamp_nanos:  Current timestamp for the model update.
 */
static void computeModelUpdate(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos);

/*
 * Searches 'model_data' for the sensor offset estimate closest to the current
 * temperature. Sets the 'nearest_offset' pointer to the result.
 */
static void findNearestEstimate(struct OverTempCal *over_temp_cal);

/*
 * Provides the current over-temperature compensated offset vector.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   timestamp_nanos:  The current system timestamp.
 * OUTPUTS:
 *   compensated_offset: Temperature compensated offset estimate array.
 *   compensated_offset_temperature_celsius: Compensated offset temperature.
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
static void getCalOffset(struct OverTempCal *over_temp_cal,
                         uint64_t timestamp_nanos,
                         float *compensated_offset_temperature_celsius,
                         float *compensated_offset);

/*
 * Removes the "old" offset estimates from 'model_data' (i.e., eliminates the
 * drift-compromised data). Returns 'true' if any data was removed.
 */
static bool removeStaleModelData(struct OverTempCal *over_temp_cal,
                                 uint64_t timestamp_nanos);

/*
 * Removes the offset estimates from 'model_data' at index, 'model_index'.
 * Returns 'true' if data was removed.
 */
static bool removeModelDataByIndex(struct OverTempCal *over_temp_cal,
                                   size_t model_index);

/*
 * Since it may take a while for an empty model to build up enough data to start
 * producing new model parameter updates, the model collection can be
 * jump-started by using the new model parameters to insert fake data in place
 * of actual sensor offset data.
 */
static bool jumpStartModelData(struct OverTempCal *over_temp_cal);

/*
 * Provides updated model parameters for the over-temperature model data.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 * OUTPUTS:
 *   temp_sensitivity: Updated modeled temperature sensitivity (array).
 *   sensor_intercept: Updated model intercept (array).
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 *
 * Reference: "Comparing two ways to fit a line to data", John D. Cook.
 * http://www.johndcook.com/blog/2008/10/20/comparing-two-ways-to-fit-a-line-to-data/
 */
static void updateModel(const struct OverTempCal *over_temp_cal,
                        float *temp_sensitivity, float *sensor_intercept);

/*
 * Checks new offset estimates to determine if they could be an outlier that
 * should be rejected. Operates on a per-axis basis determined by 'axis_index'.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   offset:           Offset array.
 *   axis_index:       Index of the axis to check (0=x, 1=y, 2=z).
 *
 * Returns 'true' if the deviation of the offset value from the linear model
 * exceeds 'max_error_limit'.
 */
static bool outlierCheck(struct OverTempCal *over_temp_cal, const float *offset,
                         size_t axis_index, float temperature_celsius);

// Sets the OTC model parameters to an "initialized" state.
static void resetOtcLinearModel(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Sets the temperature sensitivity model parameters to
  // OTC_INITIAL_SENSITIVITY to indicate that the model is in an "initial"
  // state.
  over_temp_cal->temp_sensitivity[0] = OTC_INITIAL_SENSITIVITY;
  over_temp_cal->temp_sensitivity[1] = OTC_INITIAL_SENSITIVITY;
  over_temp_cal->temp_sensitivity[2] = OTC_INITIAL_SENSITIVITY;
  memset(over_temp_cal->sensor_intercept, 0, 3 * sizeof(float));
}

// Checks that the input temperature value is within the valid range. If outside
// of range, then 'temperature_celsius' is coerced to within the limits.
static bool checkAndEnforceTemperatureRange(float *temperature_celsius) {
  if (*temperature_celsius > OVERTEMPCAL_TEMP_MAX_CELSIUS) {
    *temperature_celsius = OVERTEMPCAL_TEMP_MAX_CELSIUS;
    return false;
  }
  if (*temperature_celsius < OVERTEMPCAL_TEMP_MIN_CELSIUS) {
    *temperature_celsius = OVERTEMPCAL_TEMP_MIN_CELSIUS;
    return false;
  }
  return true;
}

// Returns "true" if the candidate linear model parameters are within the valid
// range, and not all zeros.
static bool isValidOtcLinearModel(const struct OverTempCal *over_temp_cal,
                   float temp_sensitivity, float sensor_intercept) {
  ASSERT_NOT_NULL(over_temp_cal);

  return NANO_ABS(temp_sensitivity) < over_temp_cal->temp_sensitivity_limit &&
         NANO_ABS(sensor_intercept) < over_temp_cal->sensor_intercept_limit &&
         NANO_ABS(temp_sensitivity) > OTC_MODELDATA_NEAR_ZERO_TOL &&
         NANO_ABS(sensor_intercept) > OTC_MODELDATA_NEAR_ZERO_TOL;
}

// Returns "true" if 'offset' and 'offset_temp_celsius' is valid.
static bool isValidOtcOffset(const float *offset, float offset_temp_celsius) {
  ASSERT_NOT_NULL(offset);

  // Simple check to ensure that:
  //   1. All of the input data is non "zero".
  //   2. The offset temperature is within the valid range.
  if (NANO_ABS(offset[0]) < OTC_MODELDATA_NEAR_ZERO_TOL &&
      NANO_ABS(offset[1]) < OTC_MODELDATA_NEAR_ZERO_TOL &&
      NANO_ABS(offset[2]) < OTC_MODELDATA_NEAR_ZERO_TOL &&
      NANO_ABS(offset_temp_celsius) < OTC_MODELDATA_NEAR_ZERO_TOL) {
    return false;
  }

  // Only returns the "check" result. Don't care about coercion.
  return checkAndEnforceTemperatureRange(&offset_temp_celsius);
}

#ifdef OVERTEMPCAL_DBG_ENABLED
// This helper function stores all of the debug tracking information necessary
// for printing log messages.
static void updateDebugData(struct OverTempCal* over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED

/////// FUNCTION DEFINITIONS //////////////////////////////////////////////////

void overTempCalInit(struct OverTempCal *over_temp_cal,
                     size_t min_num_model_pts,
                     uint64_t min_update_interval_nanos,
                     float delta_temp_per_bin, float max_error_limit,
                     uint64_t age_limit_nanos, float temp_sensitivity_limit,
                     float sensor_intercept_limit, bool over_temp_enable) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Clears OverTempCal memory.
  memset(over_temp_cal, 0, sizeof(struct OverTempCal));

  // Initializes the pointer to the most recent sensor offset estimate. Sets it
  // as the first element in 'model_data'.
  over_temp_cal->nearest_offset = &over_temp_cal->model_data[0];

  // Initializes the OTC linear model parameters.
  resetOtcLinearModel(over_temp_cal);

  // Initializes the model identification parameters.
  over_temp_cal->new_overtemp_model_available = false;
  over_temp_cal->min_num_model_pts = min_num_model_pts;
  over_temp_cal->min_update_interval_nanos = min_update_interval_nanos;
  over_temp_cal->delta_temp_per_bin = delta_temp_per_bin;
  over_temp_cal->max_error_limit = max_error_limit;
  over_temp_cal->age_limit_nanos = age_limit_nanos;
  over_temp_cal->temp_sensitivity_limit = temp_sensitivity_limit;
  over_temp_cal->sensor_intercept_limit = sensor_intercept_limit;
  over_temp_cal->over_temp_enable = over_temp_enable;

  // Initialize the sensor's temperature with a good initial operating point.
  over_temp_cal->temperature_celsius = JUMPSTART_START_TEMP_CELSIUS;

#ifdef OVERTEMPCAL_DBG_ENABLED
  CAL_DEBUG_LOG("[OVER_TEMP_CAL:MEMORY]", "sizeof(struct OverTempCal): %lu",
                (unsigned long int)sizeof(struct OverTempCal));

  if (over_temp_cal->over_temp_enable) {
    CAL_DEBUG_LOG("[OVER_TEMP_CAL:INIT]",
                  "Over-temperature compensation ENABLED.");
  } else {
    CAL_DEBUG_LOG("[OVER_TEMP_CAL:INIT]",
                  "Over-temperature compensation DISABLED.");
  }
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalSetModel(struct OverTempCal *over_temp_cal, const float *offset,
                         float offset_temp_celsius, uint64_t timestamp_nanos,
                         const float *temp_sensitivity,
                         const float *sensor_intercept, bool jump_start_model) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);

  // Initializes the OTC linear model parameters.
  resetOtcLinearModel(over_temp_cal);

  // Sets the model parameters if they are within the acceptable limits.
  // Includes a check to reject input model parameters that may have been passed
  // in as all zeros.
  size_t i;
  for (i = 0; i < 3; i++) {
    if (isValidOtcLinearModel(over_temp_cal, temp_sensitivity[i],
                              sensor_intercept[i])) {
      over_temp_cal->temp_sensitivity[i] = temp_sensitivity[i];
      over_temp_cal->sensor_intercept[i] = sensor_intercept[i];
    }
  }

  // Sets the model update time to the current timestamp.
  over_temp_cal->modelupdate_timestamp_nanos = timestamp_nanos;

  // Model "Jump-Start".
  const bool model_jump_started =
      (jump_start_model) ? jumpStartModelData(over_temp_cal) : false;

  if (!model_jump_started) {
    // Checks that the new offset data is valid.
    if (isValidOtcOffset(offset, offset_temp_celsius)) {
      // Sets the initial over-temp calibration estimate and model data.
      over_temp_cal->nearest_offset = &over_temp_cal->model_data[0];
      setNearestEstimate(over_temp_cal, offset, offset_temp_celsius,
                         timestamp_nanos);
      over_temp_cal->num_model_pts = 1;
    } else {
      // No valid offset data to load.
      over_temp_cal->num_model_pts = 0;
#ifdef OVERTEMPCAL_DBG_ENABLED
      CAL_DEBUG_LOG("[OVER_TEMP_CAL:RECALL]",
                    "No valid sensor offset vector to load.");
#endif  // OVERTEMPCAL_DBG_ENABLED
    }
  } else {
    // Finds the offset nearest the sensor's current temperature.
    findNearestEstimate(over_temp_cal);
  }

  // Updates the 'compensated_offset_previous' vector to prevent from
  // immediately triggering a new calibration update.
  float compensated_offset_temperature_celsius = 0.0f;
  getCalOffset(over_temp_cal, timestamp_nanos,
               &compensated_offset_temperature_celsius,
               over_temp_cal->compensated_offset_previous);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Prints the updated model data.
  CAL_DEBUG_LOG(
      "[OVER_TEMP_CAL:RECALL]",
      "Temperature|Offset|Sensitivity|Intercept [rps]: %s%d.%06d, | %s%d.%06d, "
      "%s%d.%06d, %s%d.%06d | %s%d.%06d, %s%d.%06d, %s%d.%06d | "
      "%s%d.%06d, "
      "%s%d.%06d, %s%d.%06d",
      CAL_ENCODE_FLOAT(offset_temp_celsius, 6),
      CAL_ENCODE_FLOAT(offset[0], 6),
      CAL_ENCODE_FLOAT(offset[1], 6),
      CAL_ENCODE_FLOAT(offset[2], 6),
      CAL_ENCODE_FLOAT(temp_sensitivity[0], 6),
      CAL_ENCODE_FLOAT(temp_sensitivity[1], 6),
      CAL_ENCODE_FLOAT(temp_sensitivity[2], 6),
      CAL_ENCODE_FLOAT(sensor_intercept[0], 6),
      CAL_ENCODE_FLOAT(sensor_intercept[1], 6),
      CAL_ENCODE_FLOAT(sensor_intercept[2], 6));

  // Resets the debug print machine to ensure that updateDebugData() can
  // produce a debug report and interupt any ongoing report.
  over_temp_cal->debug_state = OTC_IDLE;

  // Triggers a debug print out to view the new model parameters.
  updateDebugData(over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalGetModel(struct OverTempCal *over_temp_cal, float *offset,
                         float *offset_temp_celsius, uint64_t *timestamp_nanos,
                         float *temp_sensitivity, float *sensor_intercept) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(over_temp_cal->nearest_offset);
  ASSERT_NOT_NULL(offset);
  ASSERT_NOT_NULL(offset_temp_celsius);
  ASSERT_NOT_NULL(timestamp_nanos);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);

  // Gets the latest over-temp calibration model data.
  memcpy(temp_sensitivity, over_temp_cal->temp_sensitivity, 3 * sizeof(float));
  memcpy(sensor_intercept, over_temp_cal->sensor_intercept, 3 * sizeof(float));
  *timestamp_nanos = over_temp_cal->modelupdate_timestamp_nanos;

  // Gets the latest temperature compensated offset estimate.
  getCalOffset(over_temp_cal, *timestamp_nanos, offset_temp_celsius, offset);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Prints the updated model data.
  CAL_DEBUG_LOG(
      "[OVER_TEMP_CAL:STORED]",
      "Temperature|Offset|Sensitivity|Intercept [rps]: %s%d.%06d, | %s%d.%06d, "
      "%s%d.%06d, %s%d.%06d | %s%d.%06d, %s%d.%06d, %s%d.%06d | "
      "%s%d.%06d, "
      "%s%d.%06d, %s%d.%06d",
      CAL_ENCODE_FLOAT(*offset_temp_celsius, 6),
      CAL_ENCODE_FLOAT(offset[0], 6),
      CAL_ENCODE_FLOAT(offset[1], 6),
      CAL_ENCODE_FLOAT(offset[2], 6),
      CAL_ENCODE_FLOAT(temp_sensitivity[0], 6),
      CAL_ENCODE_FLOAT(temp_sensitivity[1], 6),
      CAL_ENCODE_FLOAT(temp_sensitivity[2], 6),
      CAL_ENCODE_FLOAT(sensor_intercept[0], 6),
      CAL_ENCODE_FLOAT(sensor_intercept[1], 6),
      CAL_ENCODE_FLOAT(sensor_intercept[2], 6));
#endif  // OVERTEMPCAL_DBG_ENABLED
}

void overTempCalSetModelData(struct OverTempCal *over_temp_cal,
                             size_t data_length,
                             const struct OverTempCalDataPt *model_data) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(model_data);

  // Load only "good" data from the input 'model_data'.
  over_temp_cal->num_model_pts = NANO_MIN(data_length, OVERTEMPCAL_MODEL_SIZE);
  size_t i;
  size_t valid_data_count = 0;
  for (i = 0; i < over_temp_cal->num_model_pts; i++) {
    if (isValidOtcOffset(model_data[i].offset,
                         model_data[i].offset_temp_celsius)) {
      memcpy(&over_temp_cal->model_data[i], &model_data[i],
             sizeof(struct OverTempCalDataPt));
      valid_data_count++;
    }
  }
  over_temp_cal->num_model_pts = valid_data_count;

  // Initializes the OTC linear model parameters.
  resetOtcLinearModel(over_temp_cal);

  // Finds the offset nearest the sensor's current temperature.
  findNearestEstimate(over_temp_cal);

  // Updates the 'compensated_offset_previous' vector to prevent from
  // immediately triggering a new calibration update.
  float compensated_offset_temperature_celsius = 0.0f;
  getCalOffset(over_temp_cal, /*timestamp_nanos=*/0,
               &compensated_offset_temperature_celsius,
               over_temp_cal->compensated_offset_previous);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Prints the updated model data.
  CAL_DEBUG_LOG("[OVER_TEMP_CAL:RECALL]",
                "Over-temperature full model data set recalled.");
  // Resets the debug print machine to ensure that computeModelUpdate() can
  // produce a debug report and interupt any ongoing report.
  over_temp_cal->debug_state = OTC_IDLE;
#endif  // OVERTEMPCAL_DBG_ENABLED

  // Ensures that minimum number of points required for a model fit has been
  // satisfied and recomputes the OTC model parameters.
  if (over_temp_cal->num_model_pts > over_temp_cal->min_num_model_pts) {
    // Computes and replaces the model parameters. If successful, this will
    // trigger a "new calibration" update.
    computeModelUpdate(over_temp_cal,
                       over_temp_cal->modelupdate_timestamp_nanos);
  }
}

void overTempCalGetModelData(struct OverTempCal *over_temp_cal,
                             size_t *data_length,
                             struct OverTempCalDataPt *model_data) {
  ASSERT_NOT_NULL(over_temp_cal);
  *data_length = over_temp_cal->num_model_pts;
  memcpy(model_data, over_temp_cal->model_data,
         over_temp_cal->num_model_pts * sizeof(struct OverTempCalDataPt));
}

bool overTempCalGetOffset(struct OverTempCal *over_temp_cal,
                          uint64_t timestamp_nanos,
                          float *compensated_offset_temperature_celsius,
                          float *compensated_offset) {
  // Gets the temperature compensated sensor offset estimate.
  getCalOffset(over_temp_cal, timestamp_nanos,
               compensated_offset_temperature_celsius, compensated_offset);

  // If the compensated_offset value has changed significantly then return
  // 'true' status.
  bool offset_has_changed = false;
  int i;
  for (i = 0; i < 3; i++) {
    if (NANO_ABS(over_temp_cal->compensated_offset_previous[i] -
                 compensated_offset[i]) >= SIGNIFICANT_OFFSET_CHANGE_RPS) {
      offset_has_changed = true;

      // Update the 'compensated_offset_previous' vector.
      memcpy(over_temp_cal->compensated_offset_previous, compensated_offset,
             3 * sizeof(float));
      break;
    }
  }

  return offset_has_changed;
}

void overTempCalRemoveOffset(struct OverTempCal *over_temp_cal,
                             uint64_t timestamp_nanos, float xi, float yi,
                             float zi, float *xo, float *yo, float *zo) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(xo);
  ASSERT_NOT_NULL(yo);
  ASSERT_NOT_NULL(zo);

  // Determines whether over-temp compensation will be applied.
  if (over_temp_cal->over_temp_enable) {
    // Gets the temperature compensated sensor offset estimate.
    float compensated_offset[3] = {0.0f, 0.0f, 0.0f};
    float compensated_offset_temperature_celsius = 0.0f;
    getCalOffset(over_temp_cal, timestamp_nanos,
                 &compensated_offset_temperature_celsius, compensated_offset);

    // Removes the over-temperature compensated offset from the input sensor
    // data.
    *xo = xi - compensated_offset[0];
    *yo = yi - compensated_offset[1];
    *zo = zi - compensated_offset[2];
  } else {
    *xo = xi;
    *yo = yi;
    *zo = zi;
  }
}

bool overTempCalNewModelUpdateAvailable(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);
  const bool update_available = over_temp_cal->new_overtemp_model_available &&
                                over_temp_cal->over_temp_enable;

  // The 'new_overtemp_model_available' flag is reset when it is read here.
  over_temp_cal->new_overtemp_model_available = false;

  return update_available;
}

void overTempCalUpdateSensorEstimate(struct OverTempCal *over_temp_cal,
                                     uint64_t timestamp_nanos,
                                     const float *offset,
                                     float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(over_temp_cal->nearest_offset);
  ASSERT_NOT_NULL(offset);
  ASSERT(over_temp_cal->delta_temp_per_bin > 0);

  // Checks that the new offset data is valid, returns if bad.
  if (!isValidOtcOffset(offset, temperature_celsius)) {
    return;
  }

  // Prevent a divide by zero below.
  if (over_temp_cal->delta_temp_per_bin <= 0) {
    return;
  }

  // Checks whether this offset estimate is a likely outlier. A limit is placed
  // on 'num_outliers', the previous number of successive rejects, to prevent
  // too many back-to-back rejections.
  if (over_temp_cal->num_outliers < OVERTEMPCAL_MAX_OUTLIER_COUNT) {
    if (outlierCheck(over_temp_cal, offset, 0, temperature_celsius) ||
        outlierCheck(over_temp_cal, offset, 1, temperature_celsius) ||
        outlierCheck(over_temp_cal, offset, 2, temperature_celsius)) {
      // Increments the count of rejected outliers.
      over_temp_cal->num_outliers++;

#ifdef OVERTEMPCAL_DBG_ENABLED
      CAL_DEBUG_LOG("[OVER_TEMP_CAL:OUTLIER]",
                    "Offset|Temperature|Time [mdps|Celcius|nsec] = "
                    "%s%d.%06d, %s%d.%06d, %s%d.%06d, %s%d.%03d, %llu",
                    CAL_ENCODE_FLOAT(offset[0] * RAD_TO_MILLI_DEGREES, 6),
                    CAL_ENCODE_FLOAT(offset[1] * RAD_TO_MILLI_DEGREES, 6),
                    CAL_ENCODE_FLOAT(offset[2] * RAD_TO_MILLI_DEGREES, 6),
                    CAL_ENCODE_FLOAT(temperature_celsius, 3),
                    (unsigned long long int)timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED

      return;  // Outlier detected: skips adding this offset to the model.
    } else {
      // Resets the count of rejected outliers.
      over_temp_cal->num_outliers = 0;
    }
  } else {
    // Resets the count of rejected outliers.
    over_temp_cal->num_outliers = 0;
  }

  // Computes the temperature bin range data.
  const int32_t bin_num =
      CAL_FLOOR(temperature_celsius / over_temp_cal->delta_temp_per_bin);
  const float temp_lo_check = bin_num * over_temp_cal->delta_temp_per_bin;
  const float temp_hi_check = (bin_num + 1) * over_temp_cal->delta_temp_per_bin;

  // The rules for accepting new offset estimates into the 'model_data'
  // collection:
  //    1) The temperature domain is divided into bins each spanning
  //       'delta_temp_per_bin'.
  //    2) Find and replace the i'th 'model_data' estimate data if:
  //          Let, bin_num = floor(temperature_celsius / delta_temp_per_bin)
  //          temp_lo_check = bin_num * delta_temp_per_bin
  //          temp_hi_check = (bin_num + 1) * delta_temp_per_bin
  //          Check condition:
  //          temp_lo_check <= model_data[i].offset_temp_celsius < temp_hi_check
  bool replaced_one = false;
  size_t i = 0;
  for (i = 0; i < over_temp_cal->num_model_pts; i++) {
    if (over_temp_cal->model_data[i].offset_temp_celsius < temp_hi_check &&
        over_temp_cal->model_data[i].offset_temp_celsius >= temp_lo_check) {
      // NOTE - the pointer to the new model data point is set here; the offset
      // data is set below in the call to 'setNearestEstimate'.
      over_temp_cal->nearest_offset = &over_temp_cal->model_data[i];
      replaced_one = true;
      break;
    }
  }

  // NOTE - the pointer to the new model data point is set here; the offset
  // data is set below in the call to 'setNearestEstimate'.
  if (!replaced_one && over_temp_cal->num_model_pts < OVERTEMPCAL_MODEL_SIZE) {
    if (over_temp_cal->num_model_pts < OVERTEMPCAL_MODEL_SIZE) {
      // 3) If nothing was replaced, and the 'model_data' buffer is not full
      //    then add the estimate data to the array.
      over_temp_cal->nearest_offset =
          &over_temp_cal->model_data[over_temp_cal->num_model_pts];
      over_temp_cal->num_model_pts++;
    } else {
      // 4) Otherwise (nothing was replaced and buffer is full), replace the
      //    oldest data with the incoming one.
      over_temp_cal->nearest_offset = &over_temp_cal->model_data[0];
      for (i = 1; i < over_temp_cal->num_model_pts; i++) {
        if (over_temp_cal->nearest_offset->timestamp_nanos <
            over_temp_cal->model_data[i].timestamp_nanos) {
          over_temp_cal->nearest_offset = &over_temp_cal->model_data[i];
        }
      }
    }
  }

  // Updates the model estimate data nearest to the sensor's temperature.
  setNearestEstimate(over_temp_cal, offset, temperature_celsius,
                     timestamp_nanos);

#ifdef OVERTEMPCAL_DBG_ENABLED
  // Updates the total number of received sensor offset estimates.
  over_temp_cal->debug_num_estimates++;
#endif  // OVERTEMPCAL_DBG_ENABLED

  // The rules for determining whether a new model fit is computed are:
  //    1) A minimum number of data points must have been collected:
  //          num_model_pts >= min_num_model_pts
  //       NOTE: Collecting 'num_model_pts' and given that only one point is
  //       kept per temperature bin (spanning a thermal range specified by
  //       'delta_temp_per_bin'), implies that model data covers at least,
  //          model_temperature_span >= 'num_model_pts' * delta_temp_per_bin
  //    2) New model updates will not occur for intervals less than:
  //          (current_timestamp_nanos - modelupdate_timestamp_nanos) <
  //            min_update_interval_nanos
  if (over_temp_cal->num_model_pts < over_temp_cal->min_num_model_pts ||
      timestamp_nanos < over_temp_cal->min_update_interval_nanos +
                            over_temp_cal->modelupdate_timestamp_nanos) {
#ifdef OVERTEMPCAL_DBG_ENABLED
    // Triggers a log printout to show the updated sensor offset estimate.
    updateDebugData(over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED
  } else {
    // The conditions satisfy performing a new model update.
    computeModelUpdate(over_temp_cal, timestamp_nanos);
  }
}

void overTempCalSetTemperature(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos,
                               float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);

#ifdef OVERTEMPCAL_DBG_ENABLED
#ifdef OVERTEMPCAL_DBG_LOG_TEMP
  static uint64_t wait_timer = 0;
  // Prints the sensor temperature trajectory for debugging purposes.
  // This throttles the print statements.
  if (timestamp_nanos >= 1000000000 + wait_timer) {
    wait_timer = timestamp_nanos;  // Starts the wait timer.

    // Prints out temperature and the current timestamp.
    CAL_DEBUG_LOG("[OVER_TEMP_CAL:TEMP]",
                  "Temperature|Time [C|nsec] = %s%d.%06d, %llu",
                  CAL_ENCODE_FLOAT(temperature_celsius, 6),
                  (unsigned long long int)timestamp_nanos);
  }
#endif  // OVERTEMPCAL_DBG_LOG_TEMP
#endif  // OVERTEMPCAL_DBG_ENABLED

  // Checks that the offset temperature is within a valid range, saturates if
  // outside.
  checkAndEnforceTemperatureRange(&temperature_celsius);

  // Updates the sensor temperature.
  over_temp_cal->temperature_celsius = temperature_celsius;

  // Searches for the sensor offset estimate closest to the current
  // temperature. A timer is used to limit the rate at which this search is
  // performed.
  if (over_temp_cal->num_model_pts > 0 &&
      timestamp_nanos >=
          OVERTEMPCAL_NEAREST_NANOS + over_temp_cal->nearest_search_timer) {
    findNearestEstimate(over_temp_cal);
    over_temp_cal->nearest_search_timer = timestamp_nanos;  // Reset timer.
  }
}

void overTempGetModelError(const struct OverTempCal *over_temp_cal,
                   const float *temp_sensitivity, const float *sensor_intercept,
                   float *max_error) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);
  ASSERT_NOT_NULL(max_error);

  size_t i;
  size_t j;
  float max_error_test;
  memset(max_error, 0, 3 * sizeof(float));

  for (i = 0; i < over_temp_cal->num_model_pts; i++) {
    for (j = 0; j < 3; j++) {
      max_error_test =
          NANO_ABS(over_temp_cal->model_data[i].offset[j] -
                   (temp_sensitivity[j] *
                        over_temp_cal->model_data[i].offset_temp_celsius +
                    sensor_intercept[j]));
      if (max_error_test > max_error[j]) {
        max_error[j] = max_error_test;
      }
    }
  }
}

/////// LOCAL HELPER FUNCTION DEFINITIONS /////////////////////////////////////

void getCalOffset(struct OverTempCal *over_temp_cal, uint64_t timestamp_nanos,
                  float *compensated_offset_temperature_celsius,
                  float *compensated_offset) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(over_temp_cal->nearest_offset);
  ASSERT_NOT_NULL(compensated_offset);
  ASSERT_NOT_NULL(compensated_offset_temperature_celsius);

  // Sets the sensor temperature associated with the compensated offset.
  *compensated_offset_temperature_celsius = over_temp_cal->temperature_celsius;

  // Removes very old data from the collected model estimates (eliminates
  // drift-compromised data). Only does this when there is more than one
  // estimate in the model (i.e., don't want to remove all data, even if it is
  // very old [something is likely better than nothing]).
  if ((timestamp_nanos >=
       OVERTEMPCAL_STALE_CHECK_TIME_NANOS + over_temp_cal->stale_data_timer) &&
      over_temp_cal->num_model_pts > 1) {
    over_temp_cal->stale_data_timer = timestamp_nanos;  // Resets timer.

    if (removeStaleModelData(over_temp_cal, timestamp_nanos)) {
      // If anything was removed, then this attempts to recompute the model.
      if (over_temp_cal->num_model_pts >= over_temp_cal->min_num_model_pts) {
        computeModelUpdate(over_temp_cal, timestamp_nanos);
      }
    }
  }

  size_t index;
  for (index = 0; index < 3; index++) {
    if (over_temp_cal->temp_sensitivity[index] >= OTC_INITIAL_SENSITIVITY ||
        NANO_ABS(over_temp_cal->temperature_celsius -
                 over_temp_cal->nearest_offset->offset_temp_celsius) <
            over_temp_cal->delta_temp_per_bin) {
      // Use the nearest estimate to perform the compensation if either of the
      // following is true:
      //    1) This axis model is in its initial state.
      //    2) The sensor's temperature is within a small neighborhood of the
      //       'nearest_offset'.
      // compensated_offset = nearest_offset
      //
      // If either of the above conditions applies and 'nearest_offset' is not
      // defined, then the offset returned is zero.
      compensated_offset[index] =
          (over_temp_cal->num_model_pts > 0)
              ? over_temp_cal->nearest_offset->offset[index]
              : 0.0f;
    } else {
      // Offset computed from the linear model:
      //  compensated_offset = (temp_sensitivity * temperature +
      //  sensor_intercept)
      compensated_offset[index] = (over_temp_cal->temp_sensitivity[index] *
                                       over_temp_cal->temperature_celsius +
                                   over_temp_cal->sensor_intercept[index]);
    }
  }
}

void setNearestEstimate(struct OverTempCal *over_temp_cal, const float *offset,
                       float offset_temp_celsius, uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);
  ASSERT_NOT_NULL(over_temp_cal->nearest_offset);

  // Sets the latest over-temp calibration estimate.
  memcpy(over_temp_cal->nearest_offset->offset, offset, 3 * sizeof(float));
  over_temp_cal->nearest_offset->offset_temp_celsius = offset_temp_celsius;
  over_temp_cal->nearest_offset->timestamp_nanos = timestamp_nanos;
}

void computeModelUpdate(struct OverTempCal *over_temp_cal,
                        uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Updates the linear model fit.
  float temp_sensitivity[3];
  float sensor_intercept[3];
  updateModel(over_temp_cal, temp_sensitivity, sensor_intercept);

  // Computes the maximum error over all of the model data.
  float max_error[3];
  overTempGetModelError(over_temp_cal, temp_sensitivity, sensor_intercept,
                        max_error);

  //    3) A new set of model parameters are accepted if:
  //         i.  The model fit error is less than, 'max_error_limit'. See
  //             overTempGetModelError() for error metric description.
  //         ii. The model fit parameters must be within certain absolute
  //             bounds:
  //               a. NANO_ABS(temp_sensitivity) < temp_sensitivity_limit
  //               b. NANO_ABS(sensor_intercept) < sensor_intercept_limit
  size_t i;
  bool updated_one = false;
  for (i = 0; i < 3; i++) {
    if (max_error[i] < over_temp_cal->max_error_limit &&
        isValidOtcLinearModel(over_temp_cal, temp_sensitivity[i],
                              sensor_intercept[i])) {
      over_temp_cal->temp_sensitivity[i] = temp_sensitivity[i];
      over_temp_cal->sensor_intercept[i] = sensor_intercept[i];
      updated_one = true;
    } else {
#ifdef OVERTEMPCAL_DBG_ENABLED
      CAL_DEBUG_LOG(
          "[OVER_TEMP_CAL:REJECT]",
          "%c-Axis Parameters|Max Error|Time [mdps/C|mdps|mdps|nsec] = "
          "%s%d.%06d, %s%d.%06d, %s%d.%06d, %llu",
          kDebugAxisLabel[i],
          CAL_ENCODE_FLOAT(temp_sensitivity[i] * RAD_TO_MILLI_DEGREES, 6),
          CAL_ENCODE_FLOAT(sensor_intercept[i] * RAD_TO_MILLI_DEGREES, 6),
          CAL_ENCODE_FLOAT(max_error[i] * RAD_TO_MILLI_DEGREES, 6),
          (unsigned long long int)timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED
    }
  }

  // If at least one of the axes updated then consider this a valid model
  // update.
  if (updated_one) {
    // Resets the timer and sets the update flag.
    over_temp_cal->modelupdate_timestamp_nanos = timestamp_nanos;
    over_temp_cal->new_overtemp_model_available = true;

#ifdef OVERTEMPCAL_DBG_ENABLED
    // Updates the total number of model updates, the debug data package, and
    // triggers a log printout.
    over_temp_cal->debug_num_model_updates++;
    updateDebugData(over_temp_cal);
#endif  // OVERTEMPCAL_DBG_ENABLED
  }
}

void findNearestEstimate(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);

  // Performs a brute force search for the estimate nearest the current sensor
  // temperature.
  size_t i = 0;
  float dtemp_new = 0.0f;
  float dtemp_old = FLT_MAX;
  over_temp_cal->nearest_offset = &over_temp_cal->model_data[0];
  for (i = 0; i < over_temp_cal->num_model_pts; i++) {
    dtemp_new = NANO_ABS(over_temp_cal->model_data[i].offset_temp_celsius -
                    over_temp_cal->temperature_celsius);
    if (dtemp_new < dtemp_old) {
      over_temp_cal->nearest_offset = &over_temp_cal->model_data[i];
      dtemp_old = dtemp_new;
    }
  }
}

bool removeStaleModelData(struct OverTempCal *over_temp_cal,
                          uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);

  size_t i;
  bool removed_one = false;
  for (i = 0; i < over_temp_cal->num_model_pts; i++) {
    if (timestamp_nanos > over_temp_cal->model_data[i].timestamp_nanos &&
        timestamp_nanos > over_temp_cal->age_limit_nanos +
                              over_temp_cal->model_data[i].timestamp_nanos) {
      removed_one |= removeModelDataByIndex(over_temp_cal, i);
    }
  }

  // Updates the latest offset so that it is the one nearest to the current
  // temperature.
  findNearestEstimate(over_temp_cal);

  return removed_one;
}

bool removeModelDataByIndex(struct OverTempCal *over_temp_cal,
                            size_t model_index) {
  ASSERT_NOT_NULL(over_temp_cal);

  // This function will not remove all of the model data. At least one model
  // sample will be left.
  if (over_temp_cal->num_model_pts <= 1) {
    return false;
  }

#ifdef OVERTEMPCAL_DBG_ENABLED
  CAL_DEBUG_LOG(
      "[OVER_TEMP_CAL:REMOVE]",
      "Offset|Temp|Time [mdps|C|nsec] = %s%d.%06d, %s%d.%06d, %s%d.%06d, "
      "%s%d.%03d, %llu",
      CAL_ENCODE_FLOAT(over_temp_cal->model_data[model_index].offset[0] *
                           RAD_TO_MILLI_DEGREES,
                       6),
      CAL_ENCODE_FLOAT(over_temp_cal->model_data[model_index].offset[1] *
                           RAD_TO_MILLI_DEGREES,
                       6),
      CAL_ENCODE_FLOAT(over_temp_cal->model_data[model_index].offset[1] *
                           RAD_TO_MILLI_DEGREES,
                       6),
      CAL_ENCODE_FLOAT(
          over_temp_cal->model_data[model_index].offset_temp_celsius, 3),
      (unsigned long long int)over_temp_cal->model_data[model_index]
          .timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED

  // Remove the model data at 'model_index'.
  size_t i;
  for (i = model_index; i < over_temp_cal->num_model_pts - 1; i++) {
    memcpy(&over_temp_cal->model_data[i], &over_temp_cal->model_data[i + 1],
           sizeof(struct OverTempCalDataPt));
  }
  over_temp_cal->num_model_pts--;

  return true;
}

bool jumpStartModelData(struct OverTempCal *over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT(over_temp_cal->delta_temp_per_bin > 0);

  // Prevent a divide by zero below.
  if (over_temp_cal->delta_temp_per_bin <= 0) {
    return false;
  }

  // In normal operation the offset estimates enter into the 'model_data' array
  // complete (i.e., x, y, z values are all provided). Therefore, the jumpstart
  // data produced here requires that the model parameters have all been fully
  // defined and are all within the valid range.
  size_t i;
  for (i = 0; i < 3; i++) {
    if (!isValidOtcLinearModel(over_temp_cal,
                               over_temp_cal->temp_sensitivity[i],
                               over_temp_cal->sensor_intercept[i])) {
      return false;
    }
  }

  // Any pre-existing model data points will be overwritten.
  over_temp_cal->num_model_pts = 0;

  // This defines the minimum contiguous set of points to allow a model update
  // when the next offset estimate is received. They are placed at a common
  // temperature range that is likely to get replaced with actual data soon.
  const int32_t start_bin_num = CAL_FLOOR(JUMPSTART_START_TEMP_CELSIUS /
                                          over_temp_cal->delta_temp_per_bin);
  float offset_temp_celsius =
      (start_bin_num + 0.5f) * over_temp_cal->delta_temp_per_bin;

  size_t j;
  for (i = 0; i < over_temp_cal->min_num_model_pts; i++) {
    float offset[3];
    const uint64_t timestamp_nanos = over_temp_cal->modelupdate_timestamp_nanos;
    for (j = 0; j < 3; j++) {
      offset[j] = over_temp_cal->temp_sensitivity[j] * offset_temp_celsius +
                  over_temp_cal->sensor_intercept[j];
    }
    over_temp_cal->nearest_offset = &over_temp_cal->model_data[i];
    setNearestEstimate(over_temp_cal, offset, offset_temp_celsius,
                      timestamp_nanos);
    offset_temp_celsius += over_temp_cal->delta_temp_per_bin;
    over_temp_cal->num_model_pts++;
  }

#ifdef OVERTEMPCAL_DBG_ENABLED
  if (over_temp_cal->num_model_pts > 0) {
    CAL_DEBUG_LOG("[OVER_TEMP_CAL:INIT]", "Model Jump-Start:  #Points = %lu.",
                  (unsigned long int)over_temp_cal->num_model_pts);
  }
#endif  // OVERTEMPCAL_DBG_ENABLED

  return (over_temp_cal->num_model_pts > 0);
}

void updateModel(const struct OverTempCal *over_temp_cal,
                 float *temp_sensitivity, float *sensor_intercept) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(temp_sensitivity);
  ASSERT_NOT_NULL(sensor_intercept);
  ASSERT(over_temp_cal->num_model_pts > 0);

  float st = 0.0f, stt = 0.0f;
  float sx = 0.0f, stsx = 0.0f;
  float sy = 0.0f, stsy = 0.0f;
  float sz = 0.0f, stsz = 0.0f;
  const size_t n = over_temp_cal->num_model_pts;
  size_t i = 0;

  // First pass computes the mean values.
  for (i = 0; i < n; ++i) {
    st += over_temp_cal->model_data[i].offset_temp_celsius;
    sx += over_temp_cal->model_data[i].offset[0];
    sy += over_temp_cal->model_data[i].offset[1];
    sz += over_temp_cal->model_data[i].offset[2];
  }

  // Second pass computes the mean corrected second moment values.
  const float inv_n = 1.0f / n;
  for (i = 0; i < n; ++i) {
    const float t =
        over_temp_cal->model_data[i].offset_temp_celsius - st * inv_n;
    stt += t * t;
    stsx += t * over_temp_cal->model_data[i].offset[0];
    stsy += t * over_temp_cal->model_data[i].offset[1];
    stsz += t * over_temp_cal->model_data[i].offset[2];
  }

  // Calculates the linear model fit parameters.
  ASSERT(stt > 0);
  const float inv_stt = 1.0f / stt;
  temp_sensitivity[0] = stsx * inv_stt;
  sensor_intercept[0] = (sx - st * temp_sensitivity[0]) * inv_n;
  temp_sensitivity[1] = stsy * inv_stt;
  sensor_intercept[1] = (sy - st * temp_sensitivity[1]) * inv_n;
  temp_sensitivity[2] = stsz * inv_stt;
  sensor_intercept[2] = (sz - st * temp_sensitivity[2]) * inv_n;
}

bool outlierCheck(struct OverTempCal *over_temp_cal, const float *offset,
                  size_t axis_index, float temperature_celsius) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(offset);

  // If a model has been defined, then check to see if this offset could be a
  // potential outlier:
  if (over_temp_cal->temp_sensitivity[axis_index] < OTC_INITIAL_SENSITIVITY) {
    const float max_error_test = NANO_ABS(
        offset[axis_index] -
        (over_temp_cal->temp_sensitivity[axis_index] * temperature_celsius +
         over_temp_cal->sensor_intercept[axis_index]));

    if (max_error_test > over_temp_cal->max_error_limit) {
      return true;
    }
  }

  return false;
}

/////// DEBUG FUNCTION DEFINITIONS ////////////////////////////////////////////

#ifdef OVERTEMPCAL_DBG_ENABLED
void updateDebugData(struct OverTempCal* over_temp_cal) {
  ASSERT_NOT_NULL(over_temp_cal);
  ASSERT_NOT_NULL(over_temp_cal->nearest_offset);

  // Only update this data if debug printing is not currently in progress
  // (i.e., don't want to risk overwriting debug information that is actively
  // being reported).
  if (over_temp_cal->debug_state != OTC_IDLE) {
    return;
  }

  // Triggers a debug log printout.
  over_temp_cal->debug_print_trigger = true;

  // Initializes the debug data structure.
  memset(&over_temp_cal->debug_overtempcal, 0, sizeof(struct DebugOverTempCal));

  // Copies over the relevant data.
  memcpy(over_temp_cal->debug_overtempcal.temp_sensitivity,
         over_temp_cal->temp_sensitivity, 3 * sizeof(float));
  memcpy(over_temp_cal->debug_overtempcal.sensor_intercept,
         over_temp_cal->sensor_intercept, 3 * sizeof(float));
  memcpy(&over_temp_cal->debug_overtempcal.nearest_offset,
         over_temp_cal->nearest_offset, sizeof(struct OverTempCalDataPt));

  over_temp_cal->debug_overtempcal.num_model_pts = over_temp_cal->num_model_pts;
  over_temp_cal->debug_overtempcal.modelupdate_timestamp_nanos =
      over_temp_cal->modelupdate_timestamp_nanos;
  over_temp_cal->debug_overtempcal.temperature_celsius =
      over_temp_cal->temperature_celsius;

  // Computes the maximum error over all of the model data.
  overTempGetModelError(over_temp_cal,
                over_temp_cal->debug_overtempcal.temp_sensitivity,
                over_temp_cal->debug_overtempcal.sensor_intercept,
                over_temp_cal->debug_overtempcal.max_error);
}

void overTempCalDebugPrint(struct OverTempCal *over_temp_cal,
                           uint64_t timestamp_nanos) {
  ASSERT_NOT_NULL(over_temp_cal);

  static enum OverTempCalDebugState next_state = 0;
  static uint64_t wait_timer = 0;
  static size_t i = 0;  // Counter.

  // This is a state machine that controls the reporting out of debug data.
  switch (over_temp_cal->debug_state) {
    case OTC_IDLE:
      // Wait for a trigger and start the debug printout sequence.
      if (over_temp_cal->debug_print_trigger) {
        CAL_DEBUG_LOG(OVERTEMPCAL_REPORT_TAG, "");
        CAL_DEBUG_LOG(OVERTEMPCAL_REPORT_TAG, "Debug Version: %s",
                      OVERTEMPCAL_DEBUG_VERSION_STRING);
        over_temp_cal->debug_print_trigger = false;  // Resets trigger.
        over_temp_cal->debug_state = OTC_PRINT_OFFSET;
      } else {
        over_temp_cal->debug_state = OTC_IDLE;
      }
      break;

    case OTC_WAIT_STATE:
      // This helps throttle the print statements.
      if (timestamp_nanos >= OVERTEMPCAL_WAIT_TIME_NANOS + wait_timer) {
        over_temp_cal->debug_state = next_state;
      }
      break;

    case OTC_PRINT_OFFSET:
      // Prints out the latest GyroCal offset estimate (input data).
      CAL_DEBUG_LOG(
          OVERTEMPCAL_REPORT_TAG,
          "Cal#|Offset|Temp|Time [mdps|C|nsec]: %lu, %s%d.%06d, "
          "%s%d.%06d, %s%d.%06d, %s%d.%03d, %llu",
          (unsigned long int)over_temp_cal->debug_num_estimates,
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.nearest_offset.offset[0] *
                  RAD_TO_MILLI_DEGREES,
              6),
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.nearest_offset.offset[1] *
                  RAD_TO_MILLI_DEGREES,
              6),
          CAL_ENCODE_FLOAT(
              over_temp_cal->debug_overtempcal.nearest_offset.offset[2] *
                  RAD_TO_MILLI_DEGREES,
              6),
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.nearest_offset
                               .offset_temp_celsius,
                           6),
          (unsigned long long int)
              over_temp_cal->debug_overtempcal.nearest_offset.timestamp_nanos);

      wait_timer = timestamp_nanos;                 // Starts the wait timer.
      next_state = OTC_PRINT_MODEL_PARAMETERS;      // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
      break;

    case OTC_PRINT_MODEL_PARAMETERS:
      // Prints out the model parameters.
      CAL_DEBUG_LOG(OVERTEMPCAL_REPORT_TAG,
                    "Cal#|Sensitivity|Intercept [mdps/C|mdps]: %lu, %s%d.%06d, "
                    "%s%d.%06d, %s%d.%06d, %s%d.%06d, %s%d.%06d, %s%d.%06d",
                    (unsigned long int)over_temp_cal->debug_num_estimates,
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.temp_sensitivity[0] *
                            RAD_TO_MILLI_DEGREES,
                        6),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.temp_sensitivity[1] *
                            RAD_TO_MILLI_DEGREES,
                        6),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.temp_sensitivity[2] *
                            RAD_TO_MILLI_DEGREES,
                        6),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.sensor_intercept[0] *
                            RAD_TO_MILLI_DEGREES,
                        6),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.sensor_intercept[1] *
                            RAD_TO_MILLI_DEGREES,
                        6),
                    CAL_ENCODE_FLOAT(
                        over_temp_cal->debug_overtempcal.sensor_intercept[2] *
                            RAD_TO_MILLI_DEGREES,
                        6));

      wait_timer = timestamp_nanos;                 // Starts the wait timer.
      next_state = OTC_PRINT_MODEL_ERROR;           // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
      break;

    case OTC_PRINT_MODEL_ERROR:
      // Computes the maximum error over all of the model data.
      CAL_DEBUG_LOG(
          OVERTEMPCAL_REPORT_TAG,
          "Cal#|#Updates|#ModelPts|Model Error|Update Time [mdps|nsec]: %lu, "
          "%lu, %lu, %s%d.%06d, %s%d.%06d, %s%d.%06d, %llu",
          (unsigned long int)over_temp_cal->debug_num_estimates,
          (unsigned long int)over_temp_cal->debug_num_model_updates,
          (unsigned long int)over_temp_cal->debug_overtempcal.num_model_pts,
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.max_error[0] *
                               RAD_TO_MILLI_DEGREES,
                           6),
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.max_error[1] *
                               RAD_TO_MILLI_DEGREES,
                           6),
          CAL_ENCODE_FLOAT(over_temp_cal->debug_overtempcal.max_error[2] *
                               RAD_TO_MILLI_DEGREES,
                           6),
          (unsigned long long int)
              over_temp_cal->debug_overtempcal.modelupdate_timestamp_nanos);

      i = 0;                          // Resets the model data printer counter.
      wait_timer = timestamp_nanos;       // Starts the wait timer.
      next_state = OTC_PRINT_MODEL_DATA;  // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
      break;

    case OTC_PRINT_MODEL_DATA:
      // Prints out all of the model data.
      if (i < over_temp_cal->num_model_pts) {
        CAL_DEBUG_LOG(
            OVERTEMPCAL_REPORT_TAG,
            "  Model[%lu] [mdps|C|nsec] = %s%d.%06d, %s%d.%06d, %s%d.%06d, "
            "%s%d.%03d, %llu",
            (unsigned long int)i,
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[i].offset[0] * RAD_TO_MILLI_DEGREES,
                6),
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[i].offset[1] * RAD_TO_MILLI_DEGREES,
                6),
            CAL_ENCODE_FLOAT(
                over_temp_cal->model_data[i].offset[2] * RAD_TO_MILLI_DEGREES,
                6),
            CAL_ENCODE_FLOAT(over_temp_cal->model_data[i].offset_temp_celsius,
                             3),
            (unsigned long long int)over_temp_cal->model_data[i]
                .timestamp_nanos);

        i++;
        wait_timer = timestamp_nanos;       // Starts the wait timer.
        next_state = OTC_PRINT_MODEL_DATA;  // Sets the next state.
        over_temp_cal->debug_state =
            OTC_WAIT_STATE;                 // First, go to wait state.
      } else {
        // Sends this state machine to its idle state.
        wait_timer = timestamp_nanos;  // Starts the wait timer.
        next_state = OTC_IDLE;         // Sets the next state.
        over_temp_cal->debug_state =
            OTC_WAIT_STATE;            // First, go to wait state.
      }
      break;

    default:
      // Sends this state machine to its idle state.
      wait_timer = timestamp_nanos;                 // Starts the wait timer.
      next_state = OTC_IDLE;                        // Sets the next state.
      over_temp_cal->debug_state = OTC_WAIT_STATE;  // First, go to wait state.
  }
}
#endif  // OVERTEMPCAL_DBG_ENABLED
