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

/*
 * This module provides an online algorithm for compensating a 3-axis sensor's
 * offset over its operating temperature:
 *
 *   1) Estimates of sensor offset with associated temperature are consumed,
 *      {offset, offset_temperature}.
 *   2) A temperature dependence model is extracted from the collected set of
 *      data pairs.
 *   3) Until a "complete" model has been built and a model equation has been
 *      computed, the compensation will use the collected offset nearest in
 *      temperature. If a model is available, then the compensation will take
 *      the form of:
 *
 * Linear Compensation Model Equation:
 *   sensor_out = sensor_in - compensated_offset
 *   Where,
 *     compensated_offset = (temp_sensitivity * current_temp + sensor_intercept)
 *
 * NOTE - 'current_temp' is the current measured temperature. 'temp_sensitivity'
 *        is the modeled temperature sensitivity (i.e., linear slope).
 *        'sensor_intercept' is linear model intercept.
 *
 * Assumptions:
 *
 *   1) Sensor hysteresis is negligible.
 *   2) Sensor offset temperature dependence is sufficiently "linear".
 *   3) The impact of long-term offset drift/aging compared to the magnitude of
 *      deviation resulting from the thermal sensitivity of the offset is
 *      relatively small.
 *
 * Sensor Input and Units:
 *       - General 3-axis sensor data.
 *       - Temperature measurements [Celsius].
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 *
 * #define OVERTEMPCAL_DBG_ENABLED to enable debug printout statements.
 * #define OVERTEMPCAL_DBG_LOG_TEMP to periodically printout sensor temperature.
 */

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_OVER_TEMP_OVER_TEMP_CAL_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_OVER_TEMP_OVER_TEMP_CAL_H_

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Defines the maximum size of the 'model_data' array.
#define OVERTEMPCAL_MODEL_SIZE (40)

// A common sensor operating temperature at which to start producing the model
// jump-start data.
#define JUMPSTART_START_TEMP_CELSIUS (30.0f)

// The maximum number of successive outliers that may be rejected.
#define OVERTEMPCAL_MAX_OUTLIER_COUNT (3)

// The 'temp_sensitivity' parameters are set to this value to indicate that the
// model is in its initial state.
#define OTC_INITIAL_SENSITIVITY (1e6f)

// Minimum "significant" change of offset value.
#define SIGNIFICANT_OFFSET_CHANGE_RPS (5.23e-5f)  // 3mDPS

// Valid sensor temperature operating range.
#define OVERTEMPCAL_TEMP_MIN_CELSIUS (-40.0f)
#define OVERTEMPCAL_TEMP_MAX_CELSIUS (85.0f)

// Over-temperature sensor offset estimate structure.
struct OverTempCalDataPt {
  // Sensor offset estimate, temperature, and timestamp.
  float offset[3];
  float offset_temp_celsius;  // [Celsius]
  uint64_t timestamp_nanos;   // [nanoseconds]
};

#ifdef OVERTEMPCAL_DBG_ENABLED
// Debug printout state enumeration.
enum OverTempCalDebugState {
  OTC_IDLE = 0,
  OTC_WAIT_STATE,
  OTC_PRINT_OFFSET,
  OTC_PRINT_MODEL_PARAMETERS,
  OTC_PRINT_MODEL_ERROR,
  OTC_PRINT_MODEL_DATA
};

// OverTempCal debug information/data tracking structure.
struct DebugOverTempCal {
  uint64_t modelupdate_timestamp_nanos;

  // The offset estimate nearest the current sensor temperature.
  struct OverTempCalDataPt nearest_offset;

  // The maximum model error over all model_data points.
  float max_error[3];

  float temp_sensitivity[3];
  float sensor_intercept[3];
  float temperature_celsius;
  size_t num_model_pts;
};
#endif  // OVERTEMPCAL_DBG_ENABLED

// The following data structure contains all of the necessary components for
// modeling a sensor's temperature dependency and providing over-temperature
// offset corrections.
struct OverTempCal {
  // Storage for over-temperature model data.
  struct OverTempCalDataPt model_data[OVERTEMPCAL_MODEL_SIZE];

  // Total number of model data points collected.
  size_t num_model_pts;

  // Modeled temperature sensitivity, dOffset/dTemp [sensor_units/Celsius].
  float temp_sensitivity[3];

  // Sensor model equation intercept [sensor_units].
  float sensor_intercept[3];

  // Timestamp of the last model update.
  uint64_t modelupdate_timestamp_nanos;  // [nanoseconds]

  // The temperature at which the offset compensation is performed.
  float temperature_celsius;

  // The stored value of the temperature compensated sensor offset.
  float compensated_offset_previous[3];

  // Pointer to the offset estimate closest to the current sensor temperature.
  struct OverTempCalDataPt *nearest_offset;

  ///// Online Model Identification Parameters ////////////////////////////////
  //
  // The rules for determining whether a new model fit is computed and the
  // resulting fit parameters are accepted are:
  //    1) A minimum number of data points must have been collected:
  //          num_model_pts >= min_num_model_pts
  //       NOTE: Collecting 'num_model_pts' and given that only one point is
  //       kept per temperature bin (spanning a thermal range specified by
  //       'delta_temp_per_bin'), implies that model data covers at least,
  //          model_temp_span >= 'num_model_pts' * delta_temp_per_bin
  //    2) New model updates will not occur for intervals less than:
  //          (current_timestamp_nanos - modelupdate_timestamp_nanos) <
  //            min_update_interval_nanos
  //    3) A new set of model parameters are accepted if:
  //         i.  The model fit error is less than, 'max_error_limit'. See
  //             overTempGetModelError() for error metric description.
  //         ii. The model fit parameters must be within certain absolute
  //             bounds:
  //               a. ABS(temp_sensitivity) < temp_sensitivity_limit
  //               b. ABS(sensor_intercept) < sensor_intercept_limit
  size_t min_num_model_pts;
  uint64_t min_update_interval_nanos;  // [nanoseconds]
  float max_error_limit;               // [sensor units]
  float temp_sensitivity_limit;        // [sensor units/Celsius]
  float sensor_intercept_limit;        // [sensor units]

  // The number of successive outliers rejected in a row. This is used to
  // prevent the possibility of a bad state where an initial bad fit causes
  // good data to be continually rejected.
  size_t num_outliers;

  // The rules for accepting new offset estimates into the 'model_data'
  // collection:
  //    1) The temperature domain is divided into bins each spanning
  //       'delta_temp_per_bin'.
  //    2) Find and replace the i'th 'model_data' estimate data if:
  //          Let, bin_num = floor(current_temp / delta_temp_per_bin)
  //          temp_lo_check = bin_num * delta_temp_per_bin
  //          temp_hi_check = (bin_num + 1) * delta_temp_per_bin
  //          Check condition:
  //          temp_lo_check <= model_data[i].offset_temp_celsius < temp_hi_check
  //    3) If nothing was replaced, and the 'model_data' buffer is not full then
  //       add the sensor offset estimate to the array.
  //    4) Otherwise (nothing was replaced and buffer is full), replace the
  //       oldest data with the incoming one.
  // This approach ensures a uniform spread of collected data, keeps the most
  // recent estimates in cases where they arrive frequently near a given
  // temperature, and prevents model oversampling (i.e., dominance of estimates
  // concentrated at a given set of temperatures).
  float delta_temp_per_bin;        // [Celsius/bin]

  // Timer used to limit the rate at which a search for the nearest offset
  // estimate is performed.
  uint64_t nearest_search_timer;   // [nanoseconds]

  // Timer used to limit the rate at which old estimates are removed from
  // the 'model_data' collection.
  uint64_t stale_data_timer;       // [nanoseconds]

  // Duration beyond which data will be removed to avoid corrupting the model
  // with drift-compromised data.
  uint64_t age_limit_nanos;        // [nanoseconds]

  // Flag set by user to control whether over-temp compensation is used.
  bool over_temp_enable;

  // True when new compensation model values have been computed; and reset when
  // overTempCalNewModelUpdateAvailable() is called. This variable indicates
  // that the following should be stored/updated in persistent system memory:
  //    1) 'temp_sensitivity' and 'sensor_intercept'.
  //    2) The sensor offset data pointed to by 'nearest_offset'
  //       (saving timestamp information is not required).
  bool new_overtemp_model_available;

#ifdef OVERTEMPCAL_DBG_ENABLED
  struct DebugOverTempCal debug_overtempcal;  // Debug data structure.
  enum OverTempCalDebugState debug_state;     // Debug printout state machine.
  size_t debug_num_model_updates;  // Total number of model updates.
  size_t debug_num_estimates;      // Total number of offset estimates.
  bool debug_print_trigger;        // Flag used to trigger data printout.
#endif  // OVERTEMPCAL_DBG_ENABLED
};

/////// FUNCTION PROTOTYPES ///////////////////////////////////////////////////

/*
 * Initializes the over-temp calibration model identification parameters.
 *
 * INPUTS:
 *   over_temp_cal:             Over-temp main data structure.
 *   min_num_model_pts:         Minimum number of model points per model
 *                              calculation update.
 *   min_update_interval_nanos: Minimum model update interval.
 *   delta_temp_per_bin:        Temperature span that defines the spacing of
 *                              collected model estimates.
 *   max_error_limit:           Model acceptance fit error tolerance.
 *   age_limit_nanos:           Sets the age limit beyond which a offset
 *                              estimate is removed from 'model_data'.
 *   temp_sensitivity_limit:    Values that define the upper limits for the
 *   sensor_intercept_limit:    model parameters. The acceptance of new model
 *                              parameters must satisfy:
 *                          i.  ABS(temp_sensitivity) < temp_sensitivity_limit
 *                          ii. ABS(sensor_intercept) < sensor_intercept_limit
 *   over_temp_enable:          Flag that determines whether over-temp sensor
 *                              offset compensation is applied.
 */
void overTempCalInit(struct OverTempCal *over_temp_cal,
                     size_t min_num_model_pts,
                     uint64_t min_update_interval_nanos,
                     float delta_temp_per_bin, float max_error_limit,
                     uint64_t age_limit_nanos, float temp_sensitivity_limit,
                     float sensor_intercept_limit, bool over_temp_enable);

/*
 * Sets the over-temp calibration model parameters.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp main data structure.
 *   offset:           Update values for the latest offset estimate (array).
 *   offset_temp_celsius: Measured temperature for the offset estimate.
 *   timestamp_nanos:  Timestamp for the offset estimate [nanoseconds].
 *   temp_sensitivity: Modeled temperature sensitivity (array).
 *   sensor_intercept: Linear model intercept for the over-temp model (array).
 *   jump_start_model: When 'true' populates an empty 'model_data' array using
 *                     valid input model parameters.
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalSetModel(struct OverTempCal *over_temp_cal, const float *offset,
                         float offset_temp_celsius, uint64_t timestamp_nanos,
                         const float *temp_sensitivity,
                         const float *sensor_intercept, bool jump_start_model);

/*
 * Gets the over-temp calibration model parameters.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 * OUTPUTS:
 *   offset:           Offset values for the latest offset estimate (array).
 *   offset_temp_celsius: Measured temperature for the offset estimate.
 *   timestamp_nanos:  Timestamp for the offset estimate [nanoseconds].
 *   temp_sensitivity: Modeled temperature sensitivity (array).
 *   sensor_intercept: Linear model intercept for the over-temp model (array).
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalGetModel(struct OverTempCal *over_temp_cal, float *offset,
                         float *offset_temp_celsius, uint64_t *timestamp_nanos,
                         float *temp_sensitivity, float *sensor_intercept);

/*
 * Sets the over-temp compensation model data set, and computes new model
 * parameters provided that 'min_num_model_pts' is satisfied.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp main data structure.
 *   model_data:       Array of the new model data set.
 *   data_length:      Number of model data entries in 'model_data'.
 *
 * NOTE: Max array length for 'model_data' is OVERTEMPCAL_MODEL_SIZE.
 */
void overTempCalSetModelData(struct OverTempCal *over_temp_cal,
                             size_t data_length,
                             const struct OverTempCalDataPt *model_data);

/*
 * Gets the over-temp compensation model data set.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp main data structure.
 * OUTPUTS:
 *   model_data:       Array containing the model data set.
 *   data_length:      Number of model data entries in 'model_data'.
 *
 * NOTE: Max array length for 'model_data' is OVERTEMPCAL_MODEL_SIZE.
 */
void overTempCalGetModelData(struct OverTempCal *over_temp_cal,
                             size_t *data_length,
                             struct OverTempCalDataPt *model_data);

/*
 * Returns 'true' if the estimated offset has changed by
 * 'SIGNIFICANT_OFFSET_CHANGE_RPS' and provides the current over-temperature
 * compensated offset vector. This function is useful for detecting changes in
 * the offset vector.
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
bool overTempCalGetOffset(struct OverTempCal *over_temp_cal,
                          uint64_t timestamp_nanos,
                          float *compensated_offset_temperature_celsius,
                          float *compensated_offset);

/*
 * Removes the over-temp compensated offset from the input sensor data.
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   timestamp_nanos:  Timestamp of the sensor estimate update.
 *   xi, yi, zi:       3-axis sensor data to be compensated.
 * OUTPUTS:
 *   xo, yo, zo:       3-axis sensor data that has been compensated.
 */
void overTempCalRemoveOffset(struct OverTempCal *over_temp_cal,
                             uint64_t timestamp_nanos, float xi, float yi,
                             float zi, float *xo, float *yo, float *zo);

// Returns true when a new over-temp model update is available; and the
// 'new_overtemp_model_available' flag is reset.
bool overTempCalNewModelUpdateAvailable(struct OverTempCal *over_temp_cal);

/*
 * Updates the sensor's offset estimate and conditionally assimilates it into
 * the over-temp model data set, 'model_data'.
 *
 * INPUTS:
 *   over_temp_cal:       Over-temp data structure.
 *   timestamp_nanos:     Timestamp of the sensor estimate update.
 *   offset:              3-axis sensor data to be compensated (array).
 *   temperature_celsius: Measured temperature for the new sensor estimate.
 *
 * NOTE: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 */
void overTempCalUpdateSensorEstimate(struct OverTempCal *over_temp_cal,
                                     uint64_t timestamp_nanos,
                                     const float *offset,
                                     float temperature_celsius);

// Updates the temperature at which the offset compensation is performed (i.e.,
// the current measured temperature value). This function is provided mainly for
// flexibility since temperature updates may come in from a source other than
// the sensor itself, and at a different rate.
void overTempCalSetTemperature(struct OverTempCal *over_temp_cal,
                               uint64_t timestamp_nanos,
                               float temperature_celsius);

/*
 * Computes the maximum absolute error between the 'model_data' estimates and
 * the estimate determined by the input model parameters.
 *   max_error (over all i)
 *     |model_data[i]->offset_xyz -
 *       getCompensatedOffset(model_data[i]->offset_temp_celsius,
 *         temp_sensitivity, sensor_intercept)|
 *
 * INPUTS:
 *   over_temp_cal:    Over-temp data structure.
 *   temp_sensitivity: Model temperature sensitivity to test (array).
 *   sensor_intercept: Model intercept to test (array).
 * OUTPUTS:
 *   max_error:        Maximum absolute error for the candidate model (array).
 *
 * NOTE 1: Arrays are all 3-dimensional with indices: 0=x, 1=y, 2=z.
 * NOTE 2: This function is provided for testing purposes.
 */
void overTempGetModelError(const struct OverTempCal *over_temp_cal,
                           const float *temp_sensitivity,
                           const float *sensor_intercept, float *max_error);

#ifdef OVERTEMPCAL_DBG_ENABLED
// This debug printout function assumes the input sensor data is a gyroscope
// [rad/sec].
void overTempCalDebugPrint(struct OverTempCal *over_temp_cal,
                           uint64_t timestamp_nanos);
#endif  // OVERTEMPCAL_DBG_ENABLED

#ifdef __cplusplus
}
#endif

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_CALIBRATION_OVER_TEMP_OVER_TEMP_CAL_H_
