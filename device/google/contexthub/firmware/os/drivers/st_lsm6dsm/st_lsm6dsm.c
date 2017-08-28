/*
 * Copyright (C) 2016 STMicroelectronics
 *
 * Author: Denis Ciocca <denis.ciocca@st.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdlib.h>
#include <string.h>
#include <sensors.h>
#include <slab.h>
#include <spi.h>
#include <gpio.h>
#include <atomic.h>
#include <timer.h>
#include <printf.h>
#include <isr.h>
#include <hostIntf.h>
#include <nanohubPacket.h>
#include <cpu/cpuMath.h>
#include <variant/sensType.h>
#include <plat/gpio.h>
#include <plat/syscfg.h>
#include <plat/exti.h>
#include <plat/rtc.h>
#include <algos/accel_cal.h>
#include <algos/gyro_cal.h>
#include <algos/mag_cal.h>

#include "st_lsm6dsm_lis3mdl_slave.h"
#include "st_lsm6dsm_lsm303agr_slave.h"
#include "st_lsm6dsm_ak09916_slave.h"
#include "st_lsm6dsm_lps22hb_slave.h"

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) || defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#define LSM6DSM_I2C_MASTER_ENABLED                    1
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#if defined(LSM6DSM_MAGN_CALIB_ENABLED) && !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED)
#pragma message("LSM6DSM_MAGN_CALIB_ENABLED can not be used if no magnetometer sensors are enabled on I2C master. Disabling it!")
#undef LSM6DSM_MAGN_CALIB_ENABLED
#endif /* LSM6DSM_MAGN_CALIB_ENABLED, LSM6DSM_I2C_MASTER_ENABLED */

#if defined(LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP) && !defined(LSM6DSM_I2C_MASTER_ENABLED)
#pragma message("LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP has no meaning if no sensors are enabled on I2C master. Discarding!")
#endif /* LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP, LSM6DSM_I2C_MASTER_ENABLED */

#define LSM6DSM_APP_ID                                APP_ID_MAKE(NANOHUB_VENDOR_STMICRO, 0)

#define LSM6DSM_WAI_VALUE                             (0x6a)
#define LSM6DSM_RETRY_CNT_WAI                         5
#define LSM6DSM_ACCEL_KSCALE                          0.00239364f    /* Accel scale @8g in (m/s^2)/LSB */
#define LSM6DSM_GYRO_KSCALE                           0.00122173f    /* Gyro scale @2000dps in (rad/sec)/LSB */
#define LSM6DSM_ONE_SAMPLE_BYTE                       6              /* One sample on triaxial sensor generate 6 byte */
#define LSM6DSM_TEMP_SAMPLE_BYTE                      2              /* One sample on temperature sensor generate 2 byte */
#define LSM6DSM_TEMP_OFFSET                           (25.0f)

/* Sensors orientation */
#define LSM6DSM_ROT_MATRIX                            1, 0, 0, 0, 1, 0, 0, 0, 1
#define LSM6DSM_MAGN_ROT_MATRIX                       1, 0, 0, 0, 1, 0, 0, 0, 1

/* SPI slave connection */
#define LSM6DSM_SPI_SLAVE_BUS_ID                      1
#define LSM6DSM_SPI_SLAVE_FREQUENCY_HZ                10000000
#define LSM6DSM_SPI_SLAVE_CS_GPIO                     GPIO_PB(12)

/* LSM6DSM status check registers */
#define LSM6DSM_STATUS_REG_XLDA                       (0x01)
#define LSM6DSM_STATUS_REG_GDA                        (0x02)
#define LSM6DSM_STATUS_REG_TDA                        (0x04)
#define LSM6DSM_FUNC_SRC_STEP_DETECTED                (0x10)
#define LSM6DSM_FUNC_SRC_STEP_COUNT_DELTA_IA          (0x80)
#define LSM6DSM_FUNC_SRC_SIGN_MOTION                  (0x40)
#define LSM6DSM_FUNC_SRC_SENSOR_HUB_END_OP            (0x01)

/* LSM6DSM ODR related */
#define LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON            80000
#define LSM6DSM_ODR_12HZ_ACCEL_STD                    1
#define LSM6DSM_ODR_26HZ_ACCEL_STD                    1
#define LSM6DSM_ODR_52HZ_ACCEL_STD                    1
#define LSM6DSM_ODR_104HZ_ACCEL_STD                   1
#define LSM6DSM_ODR_208HZ_ACCEL_STD                   1
#define LSM6DSM_ODR_416HZ_ACCEL_STD                   1
#define LSM6DSM_ODR_12HZ_GYRO_STD                     2
#define LSM6DSM_ODR_26HZ_GYRO_STD                     3
#define LSM6DSM_ODR_52HZ_GYRO_STD                     3
#define LSM6DSM_ODR_104HZ_GYRO_STD                    3
#define LSM6DSM_ODR_208HZ_GYRO_STD                    3
#define LSM6DSM_ODR_416HZ_GYRO_STD                    3

#define LSM6DSM_ODR_12HZ_REG_VALUE                    (0x10)
#define LSM6DSM_ODR_26HZ_REG_VALUE                    (0x20)
#define LSM6DSM_ODR_52HZ_REG_VALUE                    (0x30)
#define LSM6DSM_ODR_104HZ_REG_VALUE                   (0x40)
#define LSM6DSM_ODR_208HZ_REG_VALUE                   (0x50)
#define LSM6DSM_ODR_416HZ_REG_VALUE                   (0x60)

/* Interrupts */
#define LSM6DSM_INT_IRQ                               EXTI9_5_IRQn
#define LSM6DSM_INT1_GPIO                             GPIO_PB(6)
#define LSM6DSM_INT1_INDEX                            0
#define LSM6DSM_INT2_INDEX                            1
#define LSM6DSM_INT_NUM                               2

#define LSM6DSM_INT_ACCEL_ENABLE_REG_VALUE            (0x01)
#define LSM6DSM_INT_GYRO_ENABLE_REG_VALUE             (0x02)
#define LSM6DSM_INT_STEP_DETECTOR_ENABLE_REG_VALUE    (0x80)
#define LSM6DSM_INT_STEP_COUNTER_ENABLE_REG_VALUE     (0x80)
#define LSM6DSM_INT_SIGN_MOTION_ENABLE_REG_VALUE      (0x40)

/* LSM6DSM registers */
#define LSM6DSM_FUNC_CFG_ACCESS_ADDR                  (0x01)
#define LSM6DSM_DRDY_PULSE_CFG_ADDR                   (0x0b)
#define LSM6DSM_INT1_CTRL_ADDR                        (0x0d)
#define LSM6DSM_INT2_CTRL_ADDR                        (0x0e)
#define LSM6DSM_WAI_ADDR                              (0x0f)
#define LSM6DSM_CTRL1_XL_ADDR                         (0x10)
#define LSM6DSM_CTRL2_G_ADDR                          (0x11)
#define LSM6DSM_CTRL3_C_ADDR                          (0x12)
#define LSM6DSM_CTRL4_C_ADDR                          (0x13)
#define LSM6DSM_EBD_STEP_COUNT_DELTA_ADDR             (0x15)
#define LSM6DSM_CTRL10_C_ADDR                         (0x19)
#define LSM6DSM_MASTER_CONFIG_ADDR                    (0x1a)
#define LSM6DSM_STATUS_REG_ADDR                       (0x1e)
#define LSM6DSM_OUTX_L_G_ADDR                         (0x22)
#define LSM6DSM_OUTX_L_XL_ADDR                        (0x28)
#define LSM6DSM_OUT_TEMP_L_ADDR                       (0x20)
#define LSM6DSM_SENSORHUB1_REG_ADDR                   (0x2e)
#define LSM6DSM_STEP_COUNTER_L_ADDR                   (0x4b)
#define LSM6DSM_FUNC_SRC_ADDR                         (0x53)

#define LSM6DSM_SW_RESET                              (0x01)
#define LSM6DSM_RESET_PEDOMETER                       (0x02)
#define LSM6DSM_ENABLE_FUNC_CFG_ACCESS                (0x80)
#define LSM6DSM_ENABLE_DIGITAL_FUNC                   (0x04)
#define LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC         (0x10)
#define LSM6DSM_ENABLE_SIGN_MOTION_DIGITAL_FUNC       (0x01)
#define LSM6DSM_ENABLE_TIMER_DIGITAL_FUNC             (0x20)
#define LSM6DSM_MASTER_CONFIG_PULL_UP_EN              (0x08)
#define LSM6DSM_MASTER_CONFIG_MASTER_ON               (0x01)
#define LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1            (0x80)

/* LSM6DSM embedded registers */
#define LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR               (0x02)
#define LSM6DSM_EMBEDDED_SLV0_SUBADDR_ADDR            (0x03)
#define LSM6DSM_EMBEDDED_SLV0_CONFIG_ADDR             (0x04)
#define LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR               (0x05)
#define LSM6DSM_EMBEDDED_SLV1_SUBADDR_ADDR            (0x06)
#define LSM6DSM_EMBEDDED_SLV1_CONFIG_ADDR             (0x07)
#define LSM6DSM_EMBEDDED_SLV2_ADDR_ADDR               (0x08)
#define LSM6DSM_EMBEDDED_SLV2_SUBADDR_ADDR            (0x09)
#define LSM6DSM_EMBEDDED_SLV2_CONFIG_ADDR             (0x0a)
#define LSM6DSM_EMBEDDED_SLV3_ADDR_ADDR               (0x0b)
#define LSM6DSM_EMBEDDED_SLV3_SUBADDR_ADDR            (0x0c)
#define LSM6DSM_EMBEDDED_SLV3_CONFIG_ADDR             (0x0d)
#define LSM6DSM_EMBEDDED_DATAWRITE_SLV0_ADDR          (0x0e)
#define LSM6DSM_EMBEDDED_STEP_COUNT_DELTA_ADDR        (0x15)

#define LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB           (0x01)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONLY_WRITE   (0x00)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONE_SENSOR   (0x10)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_TWO_SENSOR   (0x20)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_THREE_SENSOR (0x30)
#define LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE       (0x20)
#define LSM6DSM_EMBEDDED_SLV0_WRITE_ADDR_SLEEP        (0x07)

/* LSM6DSM I2C master - slave devices */
#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
#define LSM6DSM_MAGN_KSCALE                           LIS3MDL_KSCALE
#define LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT       LIS3MDL_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_MAGN_DUMMY_REG_ADDR      LIS3MDL_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR          LIS3MDL_CTRL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE         LIS3MDL_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR          LIS3MDL_CTRL3_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE          LIS3MDL_CTRL3_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE      LIS3MDL_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE     LIS3MDL_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR            LIS3MDL_CTRL1_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE            LIS3MDL_CTRL1_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR        LIS3MDL_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN         LIS3MDL_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i)  LIS3MDLMagnRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

#ifdef LSM6DSM_I2C_MASTER_AK09916
#define LSM6DSM_MAGN_KSCALE                           AK09916_KSCALE
#define LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT       AK09916_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_MAGN_DUMMY_REG_ADDR      AK09916_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR          AK09916_CNTL3_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE         AK09916_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR          AK09916_CNTL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE          AK09916_CNTL2_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE      AK09916_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE     AK09916_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR            AK09916_CNTL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE            AK09916_CNTL2_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR        AK09916_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN         AK09916_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i)  AK09916MagnRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
#define LSM6DSM_MAGN_KSCALE                           LSM303AGR_KSCALE
#define LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT       LSM303AGR_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_MAGN_DUMMY_REG_ADDR      LSM303AGR_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR          LSM303AGR_CFG_REG_A_M_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE         LSM303AGR_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR          LSM303AGR_CFG_REG_A_M_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE          LSM303AGR_CFG_REG_A_M_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE      LSM303AGR_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE     LSM303AGR_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR            LSM303AGR_CFG_REG_A_M_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE            LSM303AGR_CFG_REG_A_M_BASE
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR        LSM303AGR_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN         LSM303AGR_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i)  LSM303AGRMagnRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

#ifdef LSM6DSM_I2C_MASTER_LPS22HB
#define LSM6DSM_PRESS_KSCALE                          LPS22HB_PRESS_KSCALE
#define LSM6DSM_TEMP_KSCALE                           LPS22HB_TEMP_KSCALE
#define LSM6DSM_PRESS_OUTDATA_LEN                     LPS22HB_OUTDATA_PRESS_BYTE
#define LSM6DSM_TEMP_OUTDATA_LEN                      LPS22HB_OUTDATA_TEMP_BYTE
#define LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT       LPS22HB_I2C_ADDRESS
#define LSM6DSM_SENSOR_SLAVE_BARO_DUMMY_REG_ADDR      LPS22HB_WAI_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_RESET_ADDR          LPS22HB_CTRL2_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_RESET_VALUE         LPS22HB_SW_RESET
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_ADDR          LPS22HB_CTRL1_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_BASE          LPS22HB_CTRL1_BASE
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_ON_VALUE      LPS22HB_POWER_ON_VALUE
#define LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE     LPS22HB_POWER_OFF_VALUE
#define LSM6DSM_SENSOR_SLAVE_BARO_ODR_ADDR            LPS22HB_CTRL1_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_ODR_BASE            LPS22HB_CTRL1_BASE
#define LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_ADDR        LPS22HB_OUTDATA_ADDR
#define LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN         LPS22HB_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i)  LPS22HBBaroRatesRegValue[i]
#endif /* LSM6DSM_I2C_MASTER_LPS22HB */

#ifndef LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN         0
#endif /* LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN */
#ifndef LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN
#define LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN         0
#endif /* LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN */

#define LSM6DSM_SH_READ_BYTE_NUM                      (LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN + \
                                                      LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN)

/* SPI buffers */
#define LSM6DSM_SPI_PACKET_SIZE                       70
#define LSM6DSM_OUTPUT_DATA_READ_SIZE                 ((2 * LSM6DSM_ONE_SAMPLE_BYTE) + LSM6DSM_SH_READ_BYTE_NUM + 2)
#define LSM6DSM_BUF_MARGIN                            120
#define SPI_BUF_SIZE                                  (LSM6DSM_OUTPUT_DATA_READ_SIZE + LSM6DSM_BUF_MARGIN)

/* Magn & Baro both enabled */
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#ifdef LSM6DSM_I2C_MASTER_AK09916
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE         LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_THREE_SENSOR
#else /* LSM6DSM_I2C_MASTER_AK09916 */
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE         LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_TWO_SENSOR
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

/* Magn only enabled */
#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && !defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#ifdef LSM6DSM_I2C_MASTER_AK09916
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE         LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_TWO_SENSOR
#else /* LSM6DSM_I2C_MASTER_AK09916 */
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE         LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONE_SENSOR
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

/* Baro only enabled */
#if !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)
#define LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE         LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONE_SENSOR
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

/* LSM6DSM default base registers status */
/* LSM6DSM_FUNC_CFG_ACCESS_BASE: enable embedded functions register */
#define LSM6DSM_FUNC_CFG_ACCESS_BASE                  (0x00)

/* LSM6DSM_DRDY_PULSE_CFG_BASE: enable pulsed interrupt register */
#define LSM6DSM_DRDY_PULSE_CFG_BASE                   (0x80)

/* LSM6DSM_INT1_CTRL_BASE: interrupt 1 control register default settings */
#define LSM6DSM_INT1_CTRL_BASE                       ((0 << 7) |    /* INT1_STEP_DETECTOR */ \
                                                      (0 << 6) |    /* INT1_SIGN_MOT */ \
                                                      (0 << 5) |    /* INT1_FULL_FLAG */ \
                                                      (0 << 4) |    /* INT1_FIFO_OVR */ \
                                                      (0 << 3) |    /* INT1_FTH */ \
                                                      (0 << 2) |    /* INT1_BOOT */ \
                                                      (0 << 1) |    /* INT1_DRDY_G */ \
                                                      (0 << 0))     /* INT1_DRDY_XL */

/* LSM6DSM_INT2_CTRL_BASE: interrupt 2 control register default settings */
#define LSM6DSM_INT2_CTRL_BASE                       ((0 << 7) |    /* INT2_STEP_DELTA */ \
                                                      (0 << 6) |    /* INT2_STEP_OV */ \
                                                      (0 << 5) |    /* INT2_FULL_FLAG */ \
                                                      (0 << 4) |    /* INT2_FIFO_OVR */ \
                                                      (0 << 3) |    /* INT2_FTH */ \
                                                      (0 << 2) |    /* INT2_DRDY_TEMP */ \
                                                      (0 << 1) |    /* INT2_DRDY_G */ \
                                                      (0 << 0))     /* INT2_DRDY_XL */

/* LSM6DSM_CTRL1_XL_BASE: accelerometer sensor register default settings */
#define LSM6DSM_CTRL1_XL_BASE                        ((0 << 7) |    /* ODR_XL3 */ \
                                                      (0 << 6) |    /* ODR_XL2 */ \
                                                      (0 << 5) |    /* ODR_XL1 */ \
                                                      (0 << 4) |    /* ODR_XL0 */ \
                                                      (1 << 3) |    /* FS_XL1 */ \
                                                      (1 << 2) |    /* FS_XL0 */ \
                                                      (0 << 1) |    /* LPF1_BW_SEL */ \
                                                      (0 << 0))     /* (0) */

/* LSM6DSM_CTRL2_G_BASE: gyroscope sensor register default settings */
#define LSM6DSM_CTRL2_G_BASE                         ((0 << 7) |    /* ODR_G3 */ \
                                                      (0 << 6) |    /* ODR_G2 */ \
                                                      (0 << 5) |    /* ODR_G1 */ \
                                                      (0 << 4) |    /* ODR_G0 */ \
                                                      (1 << 3) |    /* FS_G1 */ \
                                                      (1 << 2) |    /* FS_G0 */ \
                                                      (0 << 1) |    /* FS_125 */ \
                                                      (0 << 0))     /* (0) */

/* LSM6DSM_CTRL3_C_BASE: control register 3 default settings */
#define LSM6DSM_CTRL3_C_BASE                         ((0 << 7) |    /* BOOT */ \
                                                      (1 << 6) |    /* BDU */ \
                                                      (0 << 5) |    /* H_LACTIVE */ \
                                                      (0 << 4) |    /* PP_OD */ \
                                                      (0 << 3) |    /* SIM */ \
                                                      (1 << 2) |    /* IF_INC */ \
                                                      (0 << 1) |    /* BLE */ \
                                                      (0 << 0))     /* SW_RESET */

/* LSM6DSM_CTRL4_C_BASE: control register 4 default settings */
#define LSM6DSM_CTRL4_C_BASE                         ((0 << 7) |    /* DEN_XL_EN */ \
                                                      (0 << 6) |    /* SLEEP */ \
                                                      (1 << 5) |    /* INT2_on_INT1 */ \
                                                      (0 << 4) |    /* DEN_DRDY_MASK */ \
                                                      (0 << 3) |    /* DRDY_MASK */ \
                                                      (1 << 2) |    /* I2C_disable */ \
                                                      (0 << 1) |    /* LPF1_SEL_G */ \
                                                      (0 << 0))     /* (0) */

/* LSM6DSM_CTRL10_C_BASE: control register 10 default settings */
#define LSM6DSM_CTRL10_C_BASE                        ((0 << 7) |    /* (0) */ \
                                                      (0 << 6) |    /* (0) */ \
                                                      (0 << 5) |    /* TIMER_EN */ \
                                                      (0 << 4) |    /* PEDO_EN */ \
                                                      (0 << 3) |    /* TILT_EN */ \
                                                      (0 << 2) |    /* FUNC_EN */ \
                                                      (0 << 1) |    /* PEDO_RST_STEP */ \
                                                      (0 << 0))     /* SIGN_MOTION_EN */

/* LSM6DSM_MASTER_CONFIG_BASE: I2C master configuration register default value */
#ifdef LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP
#define LSM6DSM_MASTER_CONFIG_BASE                    (LSM6DSM_MASTER_CONFIG_PULL_UP_EN)
#else /* LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP */
#define LSM6DSM_MASTER_CONFIG_BASE                    (0x00)
#endif /* LSM6DSM_I2C_MASTER_USE_INTERNAL_PULLUP */

#define LSM6DSM_X_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                      ((r11 == 1 ? x : (r11 == -1 ? -x : 0)) + \
                                                      (r21 == 1 ? y : (r21 == -1 ? -y : 0)) + \
                                                      (r31 == 1 ? z : (r31 == -1 ? -z : 0)))

#define LSM6DSM_Y_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                      ((r12 == 1 ? x : (r12 == -1 ? -x : 0)) + \
                                                      (r22 == 1 ? y : (r22 == -1 ? -y : 0)) + \
                                                      (r32 == 1 ? z : (r32 == -1 ? -z : 0)))

#define LSM6DSM_Z_MAP(x, y, z, r11, r12, r13, r21, r22, r23, r31, r32, r33) \
                                                      ((r13 == 1 ? x : (r13 == -1 ? -x : 0)) + \
                                                      (r23 == 1 ? y : (r23 == -1 ? -y : 0)) + \
                                                      (r33 == 1 ? z : (r33 == -1 ? -z : 0)))

#define LSM6DSM_REMAP_X_DATA(...)                     LSM6DSM_X_MAP(__VA_ARGS__)
#define LSM6DSM_REMAP_Y_DATA(...)                     LSM6DSM_Y_MAP(__VA_ARGS__)
#define LSM6DSM_REMAP_Z_DATA(...)                     LSM6DSM_Z_MAP(__VA_ARGS__)

enum SensorIndex {
    ACCEL = 0,
    GYRO,
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    MAGN,
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    PRESS,
    TEMP,
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    STEP_DETECTOR,
    STEP_COUNTER,
    SIGN_MOTION,
    NUM_SENSORS
};

enum InitState {
    RESET_LSM6DSM = 0,
    INIT_LSM6DSM,
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    INIT_I2C_MASTER_REGS_CONF,
    INIT_I2C_MASTER_SENSOR_RESET,
    INIT_I2C_MASTER_MAGN_SENSOR,
    INIT_I2C_MASTER_BARO_SENSOR,
    INIT_I2C_MASTER_SENSOR_END,
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
    INIT_DONE,
};

enum SensorEvents {
    NO_EVT = -1,
    EVT_SPI_DONE = EVT_APP_START + 1,
    EVT_SENSOR_INTERRUPT_1
};

enum SensorState {
    SENSOR_BOOT = 0,
    SENSOR_VERIFY_WAI,
    SENSOR_INITIALIZATION,
    SENSOR_IDLE,
    SENSOR_POWERING_UP,
    SENSOR_POWERING_DOWN,
    SENSOR_CONFIG_CHANGING,
    SENSOR_INT1_STATUS_REG_HANDLING,
    SENSOR_INT1_OUTPUT_DATA_HANDLING
};

static void lsm6dsm_spiQueueRead(uint8_t addr, size_t size, uint8_t **buf, uint32_t delay);
static void lsm6dsm_spiQueueWrite(uint8_t addr, uint8_t data, uint32_t delay);
static void lsm6dsm_spiQueueMultiwrite(uint8_t addr, uint8_t *data, size_t size, uint32_t delay);

#define SPI_MULTIWRITE_0(addr, data, size)                         lsm6dsm_spiQueueMultiwrite(addr, data, size, 2)
#define SPI_MULTIWRITE_1(addr, data, size, delay)                  lsm6dsm_spiQueueMultiwrite(addr, data, size, delay)
#define GET_SPI_MULTIWRITE_MACRO(_1, _2, _3, _4, NAME, ...)        NAME
#define SPI_MULTIWRITE(...)                                        GET_SPI_MULTIWRITE_MACRO(__VA_ARGS__, SPI_MULTIWRITE_1, SPI_MULTIWRITE_0)(__VA_ARGS__)

#define SPI_WRITE_0(addr, data)                                    lsm6dsm_spiQueueWrite(addr, data, 2)
#define SPI_WRITE_1(addr, data, delay)                             lsm6dsm_spiQueueWrite(addr, data, delay)
#define GET_SPI_WRITE_MACRO(_1, _2, _3, NAME, ...)                 NAME
#define SPI_WRITE(...)                                             GET_SPI_WRITE_MACRO(__VA_ARGS__, SPI_WRITE_1, SPI_WRITE_0)(__VA_ARGS__)

#define SPI_READ_0(addr, size, buf)                                lsm6dsm_spiQueueRead(addr, size, buf, 0)
#define SPI_READ_1(addr, size, buf, delay)                         lsm6dsm_spiQueueRead(addr, size, buf, delay)
#define GET_SPI_READ_MACRO(_1, _2, _3, _4, NAME, ...)              NAME
#define SPI_READ(...)                                              GET_SPI_READ_MACRO(__VA_ARGS__, SPI_READ_1, SPI_READ_0)(__VA_ARGS__)

#ifdef LSM6DSM_I2C_MASTER_ENABLED
static void lsm6dsm_writeSlaveRegister(uint8_t addr, uint8_t value, uint32_t accelRate, uint32_t delay, enum SensorIndex si);

#define SPI_WRITE_SS_REGISTER_0(addr, value, accelRate, si)        lsm6dsm_writeSlaveRegister(addr, value, accelRate, 0, si)
#define SPI_WRITE_SS_REGISTER_1(addr, value, accelRate, si, delay) lsm6dsm_writeSlaveRegister(addr, value, accelRate, delay, si)
#define GET_SPI_WRITE_SS_MACRO(_1, _2, _3, _4, _5, NAME, ...)      NAME
#define SPI_WRITE_SLAVE_SENSOR_REGISTER(...)                       GET_SPI_WRITE_SS_MACRO(__VA_ARGS__, SPI_WRITE_SS_REGISTER_1, \
                                                                       SPI_WRITE_SS_REGISTER_0)(__VA_ARGS__)
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

#define INFO_PRINT(fmt, ...) \
    do { \
        osLog(LOG_INFO, "%s " fmt, "[LSM6DSM]", ##__VA_ARGS__); \
    } while (0);

#define DEBUG_PRINT(fmt, ...) \
    do { \
        if (LSM6DSM_DBG_ENABLED) { \
            osLog(LOG_DEBUG, "%s " fmt, "[LSM6DSM]", ##__VA_ARGS__); \
        } \
    } while (0);

#define ERROR_PRINT(fmt, ...) \
    do { \
        osLog(LOG_ERROR, "%s " fmt, "[LSM6DSM]", ##__VA_ARGS__); \
    } while (0);

/* DO NOT MODIFY, just to avoid compiler error if not defined using FLAGS */
#ifndef LSM6DSM_DBG_ENABLED
#define LSM6DSM_DBG_ENABLED                           0
#endif /* LSM6DSM_DBG_ENABLED */


/* struct LSM6DSMSPISlaveInterface: SPI slave data interface
 * @packets: spi packets to perform read/write operations.
 * @txrxBuffer: spi data buffer.
 * @spiDev: spi device pointer.
 * @mode: spi mode (frequency, polarity, etc).
 * @cs: chip select used by SPI slave.
 * @mWbufCnt: counter of total data in spi buffer.
 * @statusRegBuffer: pointer to txrxBuffer to access status register data.
 * @funcSrcBuffer: pointer to txrxBuffer to access func source register data.
 * @tmpDataBuffer: pointer to txrxBuffer to access sporadic read.
 * @accelDataBuffer: pointer to txrxBuffer to access accelerometer data.
 * @gyroDataBuffer: pointer to txrxBuffer to access gyroscope data.
 * @SHDataBuffer: pointer to txrxBuffer to access magnetometer data.
 * @stepCounterDataBuffer: pointer to txrxBuffer to access step counter data.
 * @tempCounterDataBuffer: pointer to txrxBuffer to access temperature data.
 * @mRegCnt: spi packet num counter.
 * @spiInUse: flag used to check if SPI is currently busy.
 */
struct LSM6DSMSPISlaveInterface {
    struct SpiPacket packets[LSM6DSM_SPI_PACKET_SIZE];
    uint8_t txrxBuffer[SPI_BUF_SIZE];
    struct SpiDevice *spiDev;
    struct SpiMode mode;
    spi_cs_t cs;
    uint16_t mWbufCnt;
    uint8_t *statusRegBuffer;
    uint8_t *funcSrcBuffer;
    uint8_t *tmpDataBuffer;
    uint8_t *accelDataBuffer;
    uint8_t *gyroDataBuffer;
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    uint8_t *SHDataBuffer;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
    uint8_t *stepCounterDataBuffer;
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    uint8_t *tempDataBuffer;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
    uint8_t mRegCnt;
    bool spiInUse;
};

/*
 * struct LSM6DSMConfigStatus: temporary data of pending events
 * @latency: value to be used in next setRate operation [ns].
 * @rate: value to be used in next setRate operation [Hz * 1024].
 * @enable: value to be used in next setEnable.
 */
struct LSM6DSMConfigStatus {
    uint64_t latency;
    uint32_t rate;
    bool enable;
};

/* struct LSM6DSMSensor: sensor status data
 * @pConfig: temporary data of pending events.
 * @tADataEvt: store three axis sensor data to send to nanohub.
 * @sADataEvt: store one axis sensor data to send to nanohub.
 * @latency: current value of latency [n].
 * @handle: sensor handle obtained by sensorRegister.
 * @rate: current value of rate [Hz * 1024].
 * @hwRate: current value of physical rate [Hz * 1024].
 * @idx: enum SensorIndex.
 * @samplesToDiscard: samples to discard after enable or odr switch.
 * @samplesDecimator: decimator factor to achieve lower odr not available in hw.
 * @samplesCounter: samples counter by decimation operation.
 * enabled: current status of sensor.
 */
struct LSM6DSMSensor {
    struct LSM6DSMConfigStatus pConfig;
    struct TripleAxisDataEvent *tADataEvt;
    struct SingleAxisDataEvent *sADataEvt;
    uint64_t latency;
    uint32_t handle;
    uint32_t rate;
    uint32_t hwRate;
    enum SensorIndex idx;
    uint8_t samplesToDiscard;
    uint8_t samplesDecimator;
    uint8_t samplesCounter;
    bool enabled;
};

/* struct LSM6DSMTask: task data
 * @sensors: sensor status data list.
 * @slaveConn: slave interface / communication data.
 * @accelCal: accelerometer calibration algo data.
 * @gyroCal: gyroscope calibration algo data.
 * @gmagnCal: magnetometer calibration algo data.
 * @int1: int1 gpio data.
 * @isr1: isr1 data.
 * @mDataSlabThreeAxis: sensors data memory three axis sensors.
 * @mDataSlabOneAxis: sensors data memory one axis sensors.
 * @currentTemperature: sensors temperature data value used by gyroscope/accelerometer bias calibration libs.
 * @timestampInt: timestamp value from isr.
 * @tid: task id.
 * @totalNumSteps: total number of steps of step counter sensor.
 * @triggerRate: max ODR value between accel or gyro.
 * @initState: initialization id done in several steps (enum InitState).
 * @state: task state, driver manage operations using a state machine (enum SensorState).
 * @mRetryLeft: counter used to retry operations #n times before return a failure.
 * @statusRegisterDA: acceleromter/gyroscope status register (data available).
 * @statusRegisterTDA: temperature status register (data available).
 * @statusRegisterSH: sensor-hub status register (slave data available).
 * @accelSensorDependencies: dependencies mask of sensors that are using accelerometer.
 * @embeddedFunctionsDependencies: dependencies mask of sensors that are using embedded functions.
 * @int1Register: interrupt 1 register status content (addr: 0x0d).
 * @int2Register: interrupt 2 register status content (addr: 0x0e).
 * @embeddedFunctionsRegister: embedded register status content (addr: 0x19).
 * @masterConfigRegister: i2c master register status content (addr: 0x1a).
 * @newMagnCalibData: this flag indicate if new magnetometer calibration data are available.
 * @readSteps: flag used to indicate if interrupt task need to read number of steps.
 * @pendingEnableConfig: pending setEnable operations to be executed.
 * @pendingRateConfig: pending setRate operations to be executed.
 * @pendingInt: pending interrupt task to be executed.
 */
typedef struct LSM6DSMTask {
    struct LSM6DSMSensor sensors[NUM_SENSORS];
    struct LSM6DSMSPISlaveInterface slaveConn;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    struct accelCal_t accelCal;
    struct TripleAxisDataEvent *accelBiasDataEvt;
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    struct gyroCal_t gyroCal;
    struct TripleAxisDataEvent *gyroBiasDataEvt;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    struct MagCal magnCal;
    struct TripleAxisDataEvent *magnCalDataEvt;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    struct Gpio *int1;
    struct ChainedIsr isr1;
    struct SlabAllocator *mDataSlabThreeAxis;
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    struct SlabAllocator *mDataSlabOneAxis;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    float currentTemperature;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */

    uint64_t timestampInt[LSM6DSM_INT_NUM];

    uint32_t tid;
    uint32_t totalNumSteps;
    uint32_t triggerRate;

    enum InitState initState;
    volatile uint8_t state;

    uint8_t mRetryLeft;
    uint8_t statusRegisterDA;
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    uint8_t statusRegisterTDA;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    uint8_t statusRegisterSH;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */
    uint8_t accelSensorDependencies;
    uint8_t embeddedFunctionsDependencies;
    uint8_t int1Register;
    uint8_t int2Register;
    uint8_t embeddedFunctionsRegister;

#ifdef LSM6DSM_I2C_MASTER_ENABLED
    uint8_t masterConfigRegister;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    bool newMagnCalibData;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    bool readSteps;
    bool pendingEnableConfig[NUM_SENSORS];
    bool pendingRateConfig[NUM_SENSORS];
    bool pendingInt[LSM6DSM_INT_NUM];
} LSM6DSMTask;

static LSM6DSMTask mTask;

#define TASK                                          LSM6DSMTask* const _task
#define TDECL()                                       TASK = &mTask; (void)_task
#define T(v)                                          (_task->v)
#define T_SLAVE_INTERFACE(v)                          (_task->slaveConn.v)

#define BIT(x)                                        (0x01 << x)
#define SENSOR_HZ_RATE_TO_US(x)                       (1024000000UL / x)
#define NS_TO_US(ns)                                  cpuMathU64DivByU16(ns, 1000)

/* Atomic get state */
#define GET_STATE()                                   (atomicReadByte(&(_task->state)))

/* Atomic set state, this set the state to arbitrary value, use with caution */
#define SET_STATE(s) \
    do { \
        atomicWriteByte(&(_task->state), (s)); \
    } while (0)

static bool trySwitchState_(TASK, enum SensorState newState)
{
    return atomicCmpXchgByte(&T(state), SENSOR_IDLE, newState);
}
#define trySwitchState(s) trySwitchState_(_task, (s))

static void lsm6dsm_readStatusReg_(TASK, bool isInterruptContext);
#define lsm6dsm_readStatusReg(a)                      lsm6dsm_readStatusReg_(_task, (a))

#define DEC_INFO(name, type, axis, inter, samples) \
    .sensorName = name, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples

#define DEC_INFO_RATE(name, rates, type, axis, inter, samples) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates

#define DEC_INFO_RATE_BIAS(name, rates, type, axis, inter, samples, bias) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

#define DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale) \
    DEC_INFO(name, type, axis, inter, samples), \
    .supportedRates = rates, \
    .flags1 = SENSOR_INFO_FLAGS1_RAW, \
    .rawType = raw, \
    .rawScale = scale

#define DEC_INFO_RATE_RAW_BIAS(name, rates, type, axis, inter, samples, raw, scale, bias) \
    DEC_INFO_RATE_RAW(name, rates, type, axis, inter, samples, raw, scale), \
    .flags1 = SENSOR_INFO_FLAGS1_RAW | SENSOR_INFO_FLAGS1_BIAS, \
    .biasType = bias

/* LSM6DSMImuRates: supported frequencies by accelerometer and gyroscope sensors
 * LSM6DSMImuRatesRegValue, LSM6DSMRatesSamplesToDiscardGyroPowerOn, LSM6DSMAccelRatesSamplesToDiscard,
 *     LSM6DSMGyroRatesSamplesToDiscard must have same length.
 */
static uint32_t LSM6DSMImuRates[] = {
    SENSOR_HZ(26.0f / 8.0f),      /* 3.25Hz */
    SENSOR_HZ(26.0f / 4.0f),      /* 6.5Hz */
    SENSOR_HZ(26.0f / 2.0f),      /* 12.5Hz */
    SENSOR_HZ(26.0f),             /* 26Hz */
    SENSOR_HZ(52.0f),             /* 52Hz */
    SENSOR_HZ(104.0f),            /* 104Hz */
    SENSOR_HZ(208.0f),            /* 208Hz */
    SENSOR_HZ(416.0f),            /* 416Hz */
    0,
};

static uint8_t LSM6DSMImuRatesRegValue[] = {
    LSM6DSM_ODR_12HZ_REG_VALUE,   /* 3.25Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_REG_VALUE,   /* 6.5Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_REG_VALUE,   /* 12.5Hz */
    LSM6DSM_ODR_26HZ_REG_VALUE,   /* 26Hz */
    LSM6DSM_ODR_52HZ_REG_VALUE,   /* 52Hz */
    LSM6DSM_ODR_104HZ_REG_VALUE,  /* 104Hz */
    LSM6DSM_ODR_208HZ_REG_VALUE,  /* 208Hz */
    LSM6DSM_ODR_416HZ_REG_VALUE,  /* 416Hz */
};

/* When sensors switch status from power-down, constant boottime must be considered, some samples should be discarded */
static uint8_t LSM6DSMRatesSamplesToDiscardGyroPowerOn[] = {
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 3.25Hz - do not exist, use 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 6.5Hz - do not exist, use 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 80000, /* 12.5Hz = 80000us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 38461, /* 26Hz = 38461us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 19230, /* 52Hz = 19230s */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 9615,  /* 104Hz = 9615us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 4807,  /* 208Hz = 4807us */
    LSM6DSM_ODR_DELAY_US_GYRO_POWER_ON / 2403,  /* 416Hz = 2403us */
};

/* When accelerometer change odr but sensor is already on, few samples should be discarded */
static uint8_t LSM6DSMAccelRatesSamplesToDiscard[] = {
    LSM6DSM_ODR_12HZ_ACCEL_STD,   /* 3.25Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_ACCEL_STD,   /* 6.5Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_ACCEL_STD,   /* 12.5Hz */
    LSM6DSM_ODR_26HZ_ACCEL_STD,   /* 26Hz */
    LSM6DSM_ODR_52HZ_ACCEL_STD,   /* 52Hz */
    LSM6DSM_ODR_104HZ_ACCEL_STD,  /* 104Hz */
    LSM6DSM_ODR_208HZ_ACCEL_STD,  /* 208Hz */
    LSM6DSM_ODR_416HZ_ACCEL_STD,  /* 416Hz */
};

/* When gyroscope change odr but sensor is already on, few samples should be discarded */
static uint8_t LSM6DSMGyroRatesSamplesToDiscard[] = {
    LSM6DSM_ODR_12HZ_GYRO_STD,    /* 3.25Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_GYRO_STD,    /* 6.5Hz - do not exist, use 12.5Hz */
    LSM6DSM_ODR_12HZ_GYRO_STD,    /* 12.5Hz */
    LSM6DSM_ODR_26HZ_GYRO_STD,    /* 26Hz */
    LSM6DSM_ODR_52HZ_GYRO_STD,    /* 52Hz */
    LSM6DSM_ODR_104HZ_GYRO_STD,   /* 104Hz */
    LSM6DSM_ODR_208HZ_GYRO_STD,   /* 208Hz */
    LSM6DSM_ODR_416HZ_GYRO_STD,   /* 416Hz */
};

#ifdef LSM6DSM_I2C_MASTER_ENABLED
static uint32_t LSM6DSMSHRates[] = {
    SENSOR_HZ(26.0f / 8.0f),      /* 3.25Hz */
    SENSOR_HZ(26.0f / 4.0f),      /* 6.5Hz */
    SENSOR_HZ(26.0f / 2.0f),      /* 12.5Hz */
    SENSOR_HZ(26.0f),             /* 26Hz */
    SENSOR_HZ(52.0f),             /* 52Hz */
    SENSOR_HZ(104.0f),            /* 104Hz */
    0,
};
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

#define LSM6DSM_SC_DELTA_TIME_PERIOD_SEC              (1.6384f)

static uint32_t LSM6DSMStepCounterRates[] = {
    SENSOR_HZ(1.0f / (128 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)), /* 209.715 sec */
    SENSOR_HZ(1.0f / (64 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),  /* 104.857 sec */
    SENSOR_HZ(1.0f / (32 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),  /* 52.4288 sec */
    SENSOR_HZ(1.0f / (16 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),  /* 26.1574 sec */
    SENSOR_HZ(1.0f / (8 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 13.0787 sec */
    SENSOR_HZ(1.0f / (4 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 6.53936 sec */
    SENSOR_HZ(1.0f / (2 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 3.26968 sec */
    SENSOR_HZ(1.0f / (1 * LSM6DSM_SC_DELTA_TIME_PERIOD_SEC)),   /* 1.63840 sec */
    SENSOR_RATE_ONCHANGE,
    0,
};

static const struct SensorInfo LSM6DSMSensorInfo[NUM_SENSORS] = {
    {
#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
        DEC_INFO_RATE_RAW_BIAS("Accelerometer", LSM6DSMImuRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 1, SENS_TYPE_ACCEL_RAW, 1.0f / LSM6DSM_ACCEL_KSCALE, SENS_TYPE_ACCEL_BIAS)
#else /* LSM6DSM_ACCEL_CALIB_ENABLED */
        DEC_INFO_RATE_RAW("Accelerometer", LSM6DSMImuRates, SENS_TYPE_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 1, SENS_TYPE_ACCEL_RAW, 1.0f / LSM6DSM_ACCEL_KSCALE)
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
    },
    {
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
        DEC_INFO_RATE_BIAS("Gyroscope", LSM6DSMImuRates, SENS_TYPE_GYRO, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 1, SENS_TYPE_GYRO_BIAS)
#else /* LSM6DSM_GYRO_CALIB_ENABLED */
        DEC_INFO_RATE("Gyroscope", LSM6DSMImuRates, SENS_TYPE_GYRO, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 1)
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
    },
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    {
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
        DEC_INFO_RATE_BIAS("Magnetometer", LSM6DSMSHRates, SENS_TYPE_MAG, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 1, SENS_TYPE_MAG_BIAS)
#else /* LSM6DSM_MAGN_CALIB_ENABLED */
        DEC_INFO_RATE("Magnetometer", LSM6DSMSHRates, SENS_TYPE_MAG, NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 1)
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
    },
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    {
        DEC_INFO_RATE("Pressure", LSM6DSMSHRates, SENS_TYPE_BARO, NUM_AXIS_ONE, NANOHUB_INT_NONWAKEUP, 1)
    },
    {
        DEC_INFO_RATE("Temperature", LSM6DSMSHRates, SENS_TYPE_TEMP, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 1)
    },
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    {
        DEC_INFO("Step Detector", SENS_TYPE_STEP_DETECT, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 1)
    },
    {
        DEC_INFO_RATE("Step Counter", LSM6DSMStepCounterRates, SENS_TYPE_STEP_COUNT, NUM_AXIS_EMBEDDED, NANOHUB_INT_NONWAKEUP, 1)
    },
    {
        DEC_INFO("Significant Motion", SENS_TYPE_SIG_MOTION, NUM_AXIS_EMBEDDED, NANOHUB_INT_WAKEUP, 1)
    },
};

#define DEC_OPS(power, firmware, rate, flush) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush

#define DEC_OPS_SEND(power, firmware, rate, flush, send) \
    .sensorPower = power, \
    .sensorFirmwareUpload = firmware, \
    .sensorSetRate = rate, \
    .sensorFlush = flush, \
    .sensorSendOneDirectEvt = send

static bool lsm6dsm_setAccelPower(bool on, void *cookie);
static bool lsm6dsm_setGyroPower(bool on, void *cookie);
static bool lsm6dsm_setStepDetectorPower(bool on, void *cookie);
static bool lsm6dsm_setStepCounterPower(bool on, void *cookie);
static bool lsm6dsm_setSignMotionPower(bool on, void *cookie);
static bool lsm6dsm_accelFirmwareUpload(void *cookie);
static bool lsm6dsm_gyroFirmwareUpload(void *cookie);
static bool lsm6dsm_stepDetectorFirmwareUpload(void *cookie);
static bool lsm6dsm_stepCounterFirmwareUpload(void *cookie);
static bool lsm6dsm_signMotionFirmwareUpload(void *cookie);
static bool lsm6dsm_setAccelRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setGyroRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setStepDetectorRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setStepCounterRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_setSignMotionRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_accelFlush(void *cookie);
static bool lsm6dsm_gyroFlush(void *cookie);
static bool lsm6dsm_stepDetectorFlush(void *cookie);
static bool lsm6dsm_stepCounterFlush(void *cookie);
static bool lsm6dsm_signMotionFlush(void *cookie);
static bool lsm6dsm_stepCounterSendLastData(void *cookie, uint32_t tid);

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
static bool lsm6dsm_setMagnPower(bool on, void *cookie);
static bool lsm6dsm_magnFirmwareUpload(void *cookie);
static bool lsm6dsm_setMagnRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_magnFlush(void *cookie);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
static bool lsm6dsm_setPressPower(bool on, void *cookie);
static bool lsm6dsm_pressFirmwareUpload(void *cookie);
static bool lsm6dsm_setPressRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_pressFlush(void *cookie);
static bool lsm6dsm_setTempPower(bool on, void *cookie);
static bool lsm6dsm_tempFirmwareUpload(void *cookie);
static bool lsm6dsm_setTempRate(uint32_t rate, uint64_t latency, void *cookie);
static bool lsm6dsm_tempFlush(void *cookie);
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

static const struct SensorOps LSM6DSMSensorOps[NUM_SENSORS] = {
    { DEC_OPS(lsm6dsm_setAccelPower, lsm6dsm_accelFirmwareUpload, lsm6dsm_setAccelRate, lsm6dsm_accelFlush) },
    { DEC_OPS(lsm6dsm_setGyroPower, lsm6dsm_gyroFirmwareUpload, lsm6dsm_setGyroRate, lsm6dsm_gyroFlush) },
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    { DEC_OPS(lsm6dsm_setMagnPower, lsm6dsm_magnFirmwareUpload, lsm6dsm_setMagnRate, lsm6dsm_magnFlush) },
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    { DEC_OPS(lsm6dsm_setPressPower, lsm6dsm_pressFirmwareUpload, lsm6dsm_setPressRate, lsm6dsm_pressFlush) },
    { DEC_OPS(lsm6dsm_setTempPower, lsm6dsm_tempFirmwareUpload, lsm6dsm_setTempRate, lsm6dsm_tempFlush) },
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    { DEC_OPS(lsm6dsm_setStepDetectorPower, lsm6dsm_stepDetectorFirmwareUpload, lsm6dsm_setStepDetectorRate, lsm6dsm_stepDetectorFlush) },
    { DEC_OPS_SEND(lsm6dsm_setStepCounterPower, lsm6dsm_stepCounterFirmwareUpload, lsm6dsm_setStepCounterRate, lsm6dsm_stepCounterFlush, lsm6dsm_stepCounterSendLastData) },
    { DEC_OPS(lsm6dsm_setSignMotionPower, lsm6dsm_signMotionFirmwareUpload, lsm6dsm_setSignMotionRate, lsm6dsm_signMotionFlush) },
};

static void lsm6dsm_spiQueueRead(uint8_t addr, size_t size, uint8_t **buf, uint32_t delay)
{
    TDECL();

    if (T_SLAVE_INTERFACE(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue read (addr=%d len=%d)\n", (int)addr, (int)size);
        return;
    }

    *buf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);

    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).size = size + 1;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).txBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).rxBuf = *buf;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).delay = delay * 1000;

    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = addr | 0x80;
    T_SLAVE_INTERFACE(mWbufCnt) += size;
    T_SLAVE_INTERFACE(mRegCnt)++;
}

static void lsm6dsm_spiQueueWrite(uint8_t addr, uint8_t data, uint32_t delay)
{
    TDECL();

    if (T_SLAVE_INTERFACE(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue write (addr=%d data=%d)\n", (int)addr, (int)data);
        return;
    }

    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).size = 2;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).txBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).rxBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).delay = delay * 1000;

    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = addr;
    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = data;
    T_SLAVE_INTERFACE(mRegCnt)++;
}

static void lsm6dsm_spiQueueMultiwrite(uint8_t addr, uint8_t *data, size_t size, uint32_t delay)
{
    TDECL();
    uint8_t i;

    if (T_SLAVE_INTERFACE(spiInUse)) {
        ERROR_PRINT("SPI in use, cannot queue multiwrite (addr=%d size=%d)\n", (int)addr, (int)size);
        return;
    }

    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).size = 1 + size;
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).txBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).rxBuf = &T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)]);
    T_SLAVE_INTERFACE(packets[T_SLAVE_INTERFACE(mRegCnt)]).delay = delay * 1000;

    T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = addr;

    for (i = 0; i < size; i++)
        T_SLAVE_INTERFACE(txrxBuffer[T_SLAVE_INTERFACE(mWbufCnt)++]) = data[i];

    T_SLAVE_INTERFACE(mRegCnt)++;
}

static void lsm6dsm_spiBatchTxRx(struct SpiMode *mode,
            SpiCbkF callback, void *cookie, const char *src)
{
    TDECL();
    uint8_t regCount;

    if (T_SLAVE_INTERFACE(mWbufCnt) > SPI_BUF_SIZE) {
        ERROR_PRINT("No enough SPI buffer space, dropping transaction\n");
        return;
    }

    if (T_SLAVE_INTERFACE(mRegCnt) > LSM6DSM_SPI_PACKET_SIZE) {
        ERROR_PRINT("spiBatchTxRx too many packets!\n");
        return;
    }

    /* Reset variables before issuing SPI transaction.
       SPI may finish before spiMasterRxTx finish */
    regCount = T_SLAVE_INTERFACE(mRegCnt);
    T_SLAVE_INTERFACE(spiInUse) = true;
    T_SLAVE_INTERFACE(mRegCnt) = 0;
    T_SLAVE_INTERFACE(mWbufCnt) = 0;

    if (spiMasterRxTx(T_SLAVE_INTERFACE(spiDev), T_SLAVE_INTERFACE(cs), T_SLAVE_INTERFACE(packets), regCount, mode, callback, cookie)) {
        ERROR_PRINT("spiBatchTxRx failed!\n");
    }
}

static void lsm6dsm_timerCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_SPI_DONE, data, NULL, mTask.tid);
}

static void lsm6dsm_spiCallback(void *cookie, int err)
{
    TDECL();

    T_SLAVE_INTERFACE(spiInUse) = false;
    osEnqueuePrivateEvt(EVT_SPI_DONE, cookie, NULL, mTask.tid);
}

static void lsm6dsm_readStatusReg_(TASK, bool isInterruptContext)
{
    if (trySwitchState(SENSOR_INT1_STATUS_REG_HANDLING)) {
        SPI_READ(LSM6DSM_STATUS_REG_ADDR, 1, &T_SLAVE_INTERFACE(statusRegBuffer));
        SPI_READ(LSM6DSM_FUNC_SRC_ADDR, 1, &T_SLAVE_INTERFACE(funcSrcBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
    } else {
        if (isInterruptContext)
            osEnqueuePrivateEvt(EVT_SENSOR_INTERRUPT_1, _task, NULL, T(tid));
        else
            T(pendingInt[LSM6DSM_INT1_INDEX]) = true;
    }
}

/*
 * lsm6dsm_isr1: INT-1 line service routine
 */
static bool lsm6dsm_isr1(struct ChainedIsr *isr)
{
    TDECL();

    if (!extiIsPendingGpio(T(int1)))
        return false;

    T(timestampInt[LSM6DSM_INT1_INDEX]) = rtcGetTime();
    lsm6dsm_readStatusReg(true);

    extiClearPendingGpio(T(int1));

    return true;
}

/*
 * lsm6dsm_enableInterrupt: enable driver interrupt capability
 */
static void lsm6dsm_enableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    gpioConfigInput(pin, GPIO_SPEED_LOW, GPIO_PULL_NONE);
    syscfgSetExtiPort(pin);
    extiEnableIntGpio(pin, EXTI_TRIGGER_RISING);
    extiChainIsr(LSM6DSM_INT_IRQ, isr);
}

/*
 * lsm6dsm_disableInterrupt: disable driver interrupt capability
 */
static void lsm6dsm_disableInterrupt(struct Gpio *pin, struct ChainedIsr *isr)
{
    extiUnchainIsr(LSM6DSM_INT_IRQ, isr);
    extiDisableIntGpio(pin);
}

/*
 * lsm6dsm_writeEmbeddedRegister: write embedded register of sensor
 */
static void lsm6dsm_writeEmbeddedRegister(uint8_t addr, uint8_t value)
{
    TDECL();

    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister) & ~LSM6DSM_ENABLE_DIGITAL_FUNC, 3000);
    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

    SPI_WRITE(addr, value);

    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
}

#ifdef LSM6DSM_I2C_MASTER_ENABLED
/*
 * lsm6dsm_writeSlaveRegister: write I2C slave register using sensor-hub feature
 */
static void lsm6dsm_writeSlaveRegister(uint8_t addr, uint8_t value, uint32_t accelRate, uint32_t delay, enum SensorIndex si)
{
    TDECL();
    uint8_t slave_addr, buffer[3];
    uint32_t sh_op_complete_time;

    switch (si) {
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    case MAGN:
        slave_addr = LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT;
        break;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    case PRESS:
    case TEMP:
        slave_addr = LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT;
        break;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    default:
        return;
    }

    if (accelRate > SENSOR_HZ(104.0f))
        sh_op_complete_time = SENSOR_HZ_RATE_TO_US(SENSOR_HZ(104.0f));
    else
        sh_op_complete_time = SENSOR_HZ_RATE_TO_US(accelRate);

    /* Perform write to slave sensor and wait write is done (1 accel ODR) */
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister) & ~LSM6DSM_ENABLE_DIGITAL_FUNC, 3000);
    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

    buffer[0] = slave_addr << 1;                                     /* LSM6DSM_EMBEDDED_SLV0_ADDR */
    buffer[1] = addr;                                                /* LSM6DSM_EMBEDDED_SLV0_SUBADDR */
    buffer[2] = LSM6DSM_EMBEDDED_SENSOR_HUB_HAVE_ONLY_WRITE;         /* LSM6DSM_EMBEDDED_SLV0_CONFIG */
    SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR, buffer, 3);
    SPI_WRITE(LSM6DSM_EMBEDDED_DATAWRITE_SLV0_ADDR, value);

    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister), (3 * sh_op_complete_time) / 2);

    /* After write is completed slave 0 must be set to sleep mode */
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister) & ~LSM6DSM_ENABLE_DIGITAL_FUNC, 3000);
    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

    buffer[0] = LSM6DSM_EMBEDDED_SLV0_WRITE_ADDR_SLEEP;              /* LSM6DSM_EMBEDDED_SLV0_ADDR */
    buffer[1] = addr;                                                /* LSM6DSM_EMBEDDED_SLV0_SUBADDR */
    buffer[2] = LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE;               /* LSM6DSM_EMBEDDED_SLV0_CONFIG */
    SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR, buffer, 3);

    SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
    SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
}
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

/*
 * lsm6dsm_computeOdr: get index of LSM6DSMImuRates array based on selected rate
 */
static uint8_t lsm6dsm_computeOdr(uint32_t rate)
{
    int i;

    for (i = 0; i < (ARRAY_SIZE(LSM6DSMImuRates) - 1); i++) {
        if (LSM6DSMImuRates[i] == rate)
            break;
    }
    if (i == (ARRAY_SIZE(LSM6DSMImuRates) -1 )) {
        ERROR_PRINT("ODR not valid! Selected smallest ODR available\n");
        i = 0;
    }

    return i;
}

/*
 * lsm6dsm_getAccelHwMinOdr: verify minimum odr needed by accel in order to satisfy dependencies
 */
static uint8_t lsm6dsm_getAccelHwMinOdr()
{
    TDECL();
    uint32_t minRate = SENSOR_HZ(26.0f / 2.0f);

    if (T(accelSensorDependencies) & BIT(ACCEL)) {
        if (minRate < T(sensors[ACCEL]).rate)
            minRate = T(sensors[ACCEL]).rate;
    }

    /* Embedded functions are enabled, min odr required is 26Hz */
    if (T(embeddedFunctionsDependencies)) {
        if (minRate < SENSOR_HZ(26.0f))
            minRate = SENSOR_HZ(26.0f);
    }

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    if (T(accelSensorDependencies) & BIT(GYRO)) {
        if (minRate < T(sensors[GYRO].rate))
            minRate = T(sensors[GYRO].rate);
    }
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    if (T(accelSensorDependencies) & BIT(MAGN)) {
        if (minRate < T(sensors[MAGN].rate))
            minRate = T(sensors[MAGN].rate);
    }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    if (T(accelSensorDependencies) & BIT(PRESS)) {
        if (minRate < T(sensors[PRESS].rate))
            minRate = T(sensors[PRESS].rate);
    }

    if (T(accelSensorDependencies) & BIT(TEMP)) {
        if (minRate < T(sensors[TEMP].rate))
            minRate = T(sensors[TEMP].rate);
    }
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

    /* This call return index of LSM6DSMImuRates struct element */
    return lsm6dsm_computeOdr(minRate);
}

/*
 * lsm6dsm_setTriggerRate: detect between accel & gyro fastest odr
 */
static void lsm6dsm_setTriggerRate()
{
    TDECL();
    uint8_t i;

    i = lsm6dsm_getAccelHwMinOdr();

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    T(triggerRate) = SENSOR_HZ_RATE_TO_US(LSM6DSMImuRates[i]);
#else /* LSM6DSM_GYRO_CALIB_ENABLED */
    uint32_t maxRate = LSM6DSMImuRates[i];

    if (maxRate < T(sensors[GYRO]).hwRate)
        maxRate = T(sensors[GYRO]).hwRate;

    T(triggerRate) = SENSOR_HZ_RATE_TO_US(maxRate);
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
}

/*
 * lsm6dsm_updateAccelOdr: update accel odr based on enabled dependencies
 */
static bool lsm6dsm_updateAccelOdr()
{
    TDECL();
    uint8_t i;

    /* If no one is using accel disable it. If dependencies are using accel try to reduce ODR */
    if (T(accelSensorDependencies) == 0) {
        DEBUG_PRINT("updateAccelOdr: no one is using accel, disabling it\n");
        T(sensors[ACCEL]).hwRate = 0;
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);
        lsm6dsm_setTriggerRate();
    } else {
        i = lsm6dsm_getAccelHwMinOdr();

        T(sensors[ACCEL]).samplesDecimator = LSM6DSMImuRates[i] / T(sensors[ACCEL]).rate;
        T(sensors[ACCEL]).samplesCounter = T(sensors[ACCEL]).samplesDecimator - 1;
        T(sensors[ACCEL]).samplesToDiscard = LSM6DSMAccelRatesSamplesToDiscard[i];
        T(sensors[ACCEL]).hwRate = LSM6DSMImuRates[i];

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
        if (T(accelSensorDependencies) & BIT(MAGN)) {
            if (T(sensors[ACCEL]).hwRate > SENSOR_HZ(104.0f))
                T(sensors[MAGN]).samplesDecimator = SENSOR_HZ(104.0f) / T(sensors[MAGN]).rate;
            else
                T(sensors[MAGN]).samplesDecimator = T(sensors[ACCEL]).hwRate / T(sensors[MAGN]).rate;

            T(sensors[MAGN]).samplesCounter = T(sensors[MAGN]).samplesDecimator - 1;
            T(sensors[MAGN]).samplesToDiscard = 1;
        }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        if (T(accelSensorDependencies) & BIT(PRESS)) {
            if (T(sensors[ACCEL]).hwRate > SENSOR_HZ(104.0f))
                T(sensors[PRESS]).samplesDecimator = SENSOR_HZ(104.0f) / T(sensors[PRESS]).rate;
            else
                T(sensors[PRESS]).samplesDecimator = T(sensors[ACCEL]).hwRate / T(sensors[PRESS]).rate;

            T(sensors[PRESS]).samplesCounter = T(sensors[PRESS]).samplesDecimator - 1;
            T(sensors[PRESS]).samplesToDiscard = 1;
        }

        if (T(accelSensorDependencies) & BIT(TEMP)) {
            if (T(sensors[ACCEL]).hwRate > SENSOR_HZ(104.0f))
                T(sensors[TEMP]).samplesDecimator = SENSOR_HZ(104.0f) / T(sensors[TEMP]).rate;
            else
                T(sensors[TEMP]).samplesDecimator = T(sensors[ACCEL]).hwRate / T(sensors[TEMP]).rate;

            T(sensors[TEMP]).samplesCounter = T(sensors[TEMP]).samplesDecimator - 1;
            T(sensors[TEMP]).samplesToDiscard = 1;
        }
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

        lsm6dsm_setTriggerRate();

        DEBUG_PRINT("updateAccelOdr: accel in use, updating odr to %dHz\n", (int)(T(sensors[ACCEL]).hwRate / 1024));

        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSMImuRatesRegValue[i]);
    }

    return true;
}

/*
 * lsm6dsm_setAccelPower: enable/disable accelerometer sensor
 */
static bool lsm6dsm_setAccelPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setAccelPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(accelSensorDependencies) |= BIT(ACCEL);
            T(int1Register) |= LSM6DSM_INT_ACCEL_ENABLE_REG_VALUE;

            /* Discard same samples if interrupts are generated during INT enable */
            T(sensors[ACCEL]).samplesToDiscard = 255;

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        } else {
            T(accelSensorDependencies) &= ~BIT(ACCEL);
            T(int1Register) &= ~LSM6DSM_INT_ACCEL_ENABLE_REG_VALUE;

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        }

        /* If enable, set only INT bit enable (sensor will be switched on by setRate function). If disable, it depends on accelSensorDependencies */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[ACCEL]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[ACCEL]) = true;
        T(sensors[ACCEL]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setGyroPower: enable/disable gyroscope sensor
 */
static bool lsm6dsm_setGyroPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setGyroPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(int1Register) |= LSM6DSM_INT_GYRO_ENABLE_REG_VALUE;

            /* Discard same samples if interrupts are generated during INT enable */
            T(sensors[GYRO]).samplesToDiscard = 255;

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        } else {
            T(int1Register) &= ~LSM6DSM_INT_GYRO_ENABLE_REG_VALUE;

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
            T(accelSensorDependencies) &= ~BIT(GYRO);

            if (!T(sensors[ACCEL].enabled))
                T(int1Register) &= ~LSM6DSM_INT_ACCEL_ENABLE_REG_VALUE;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

            T(sensors[GYRO]).hwRate = 0;

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
            SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE);

            lsm6dsm_updateAccelOdr();
        }

        /* If enable, set only INT bit enable (sensor will be switched on by setRate function). If disable, disable INT bit and disable ODR (power-off sensor) */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[GYRO]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[GYRO]) = true;
        T(sensors[GYRO]).pConfig.enable = on;
    }

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_setMagnPower: enable/disable magnetometer sensor
 */
static bool lsm6dsm_setMagnPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setMagnPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

            /* Discard same samples if interrupts are generated during INT enable before switch on sensor */
            T(sensors[MAGN]).samplesToDiscard = 255;

            SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
        } else {
            T(accelSensorDependencies) &= ~BIT(MAGN);
            T(embeddedFunctionsDependencies) &= ~BIT(MAGN);

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR,
                    LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE,
                    T(sensors[ACCEL]).hwRate, MAGN);

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
            if (!(T(sensors[PRESS].enabled) || T(sensors[TEMP].enabled))) {
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

            if (T(embeddedFunctionsDependencies) == 0) {
                DEBUG_PRINT("setMagnPower: no embedded sensors on, disabling digital functions\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_DIGITAL_FUNC;
                SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            }

            T(sensors[MAGN]).hwRate = 0;

            lsm6dsm_updateAccelOdr();
        }

        /* If enable, set only INT bit enable (sensor will be switched on by setRate function).
           If disable, disable INT bit, disable sensor-hub and disable ODR (power-off sensor) */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[MAGN]) = true;
        T(sensors[MAGN]).pConfig.enable = on;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_setPressPower: enable/disable pressure sensor
 */
static bool lsm6dsm_setPressPower(bool on, void *cookie)
{
    TDECL();
    uint8_t i, reg_value = LSM6DSM_SENSOR_SLAVE_BARO_POWER_BASE;

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setPressPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

            /* Discard same samples if interrupts are generated during INT enable before switch on sensor */
            T(sensors[PRESS]).samplesToDiscard = 255;

            SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
        } else {
            T(accelSensorDependencies) &= ~BIT(PRESS);
            T(embeddedFunctionsDependencies) &= ~BIT(PRESS);

            if (T(sensors[TEMP]).enabled) {
                i = lsm6dsm_computeOdr(T(sensors[TEMP]).rate);
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i);
            } else
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE;

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_POWER_ADDR, reg_value,
                    T(sensors[PRESS]).hwRate, PRESS);

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
            if (!(T(sensors[MAGN].enabled) || T(sensors[TEMP].enabled))) {
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
            if (!T(sensors[TEMP].enabled)) {
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

            if (T(embeddedFunctionsDependencies) == 0) {
                DEBUG_PRINT("setPressPower: no embedded sensors on, disabling digital functions\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_DIGITAL_FUNC;
                SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            }

            T(sensors[PRESS]).hwRate = 0;

            lsm6dsm_updateAccelOdr();
        }

        /* If enable, set only INT bit enable (sensor will be switched on by setRate function).
           If disable, disable INT bit, disable sensor-hub and disable ODR (power-off sensor) */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[PRESS]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[PRESS]) = true;
        T(sensors[PRESS]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setTempPower: enable/disable temperature sensor
 */
static bool lsm6dsm_setTempPower(bool on, void *cookie)
{
    TDECL();
    uint8_t i, reg_value = LSM6DSM_SENSOR_SLAVE_BARO_POWER_BASE;

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setTempPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

            /* Discard same samples if interrupts are generated during INT enable before switch on sensor */
            T(sensors[TEMP]).samplesToDiscard = 255;

            SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
        } else {
            T(accelSensorDependencies) &= ~BIT(TEMP);
            T(embeddedFunctionsDependencies) &= ~BIT(TEMP);

            if (T(sensors[PRESS]).enabled) {
                i = lsm6dsm_computeOdr(T(sensors[PRESS]).rate);
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i);
            } else
                reg_value |= LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE;

            SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_POWER_ADDR, reg_value,
                    T(sensors[TEMP]).hwRate, PRESS);

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
            if (!(T(sensors[MAGN].enabled) || T(sensors[PRESS].enabled))) {
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
            if (!T(sensors[PRESS].enabled)) {
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_MASTER_ON;
                T(masterConfigRegister) &= ~LSM6DSM_MASTER_CONFIG_DRDY_ON_INT1;

                SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, T(masterConfigRegister));
            }
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

            if (T(embeddedFunctionsDependencies) == 0) {
                DEBUG_PRINT("setTempPower: no embedded sensors on, disabling digital functions\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_DIGITAL_FUNC;
                SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            }

            T(sensors[TEMP]).hwRate = 0;

            lsm6dsm_updateAccelOdr();
        }

        /* If enable, set only INT bit enable (sensor will be switched on by setRate function).
           If disable, disable INT bit, disable sensor-hub and disable ODR (power-off sensor) */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[TEMP]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[TEMP]) = true;
        T(sensors[TEMP]).pConfig.enable = on;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_setStepDetectorPower: enable/disable step detector sensor
 */
static bool lsm6dsm_setStepDetectorPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setStepDetectorPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(accelSensorDependencies) |= BIT(STEP_DETECTOR);
            T(embeddedFunctionsDependencies) |= BIT(STEP_DETECTOR);
            T(embeddedFunctionsRegister) |= (LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC | LSM6DSM_ENABLE_DIGITAL_FUNC);
            T(int1Register) |= LSM6DSM_INT_STEP_DETECTOR_ENABLE_REG_VALUE;

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        } else {
            T(accelSensorDependencies) &= ~BIT(STEP_DETECTOR);
            T(embeddedFunctionsDependencies) &= ~BIT(STEP_DETECTOR);
            T(int1Register) &= ~LSM6DSM_INT_STEP_DETECTOR_ENABLE_REG_VALUE;

            if ((T(embeddedFunctionsDependencies) & (BIT(STEP_COUNTER) | BIT(SIGN_MOTION))) == 0) {
                DEBUG_PRINT("setStepDetectorPower: no more need pedometer algo, disabling it\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC;
            }

            if (T(embeddedFunctionsDependencies) == 0) {
                DEBUG_PRINT("setStepDetectorPower: no embedded sensors on, disabling digital functions\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_DIGITAL_FUNC;
            }

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
        }

        /* If enable, set INT bit enable and enable accelerometer sensor @26Hz if disabled. If disable, disable INT bit and disable accelerometer if no one need it */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[STEP_DETECTOR]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[STEP_DETECTOR]) = true;
        T(sensors[STEP_DETECTOR]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setStepCounterPower: enable/disable step counter sensor
 */
static bool lsm6dsm_setStepCounterPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setStepCounterPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(readSteps) = false;
            T(accelSensorDependencies) |= BIT(STEP_COUNTER);
            T(embeddedFunctionsDependencies) |= BIT(STEP_COUNTER);
            T(embeddedFunctionsRegister) |= (LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC | LSM6DSM_ENABLE_DIGITAL_FUNC);
            T(int2Register) |= LSM6DSM_INT_STEP_COUNTER_ENABLE_REG_VALUE;

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            SPI_WRITE(LSM6DSM_INT2_CTRL_ADDR, T(int2Register));
        } else {
            T(accelSensorDependencies) &= ~BIT(STEP_COUNTER);
            T(embeddedFunctionsDependencies) &= ~BIT(STEP_COUNTER);
            T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_TIMER_DIGITAL_FUNC;
            T(int2Register) &= ~LSM6DSM_INT_STEP_COUNTER_ENABLE_REG_VALUE;

            if ((T(embeddedFunctionsDependencies) & (BIT(STEP_DETECTOR) | BIT(SIGN_MOTION))) == 0) {
                DEBUG_PRINT("setStepCounterPower: no more need pedometer algo, disabling it\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC;
            }

            if (T(embeddedFunctionsDependencies) == 0) {
                DEBUG_PRINT("setStepCounterPower: no embedded sensors on, disabling digital functions\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_DIGITAL_FUNC;
            }

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_INT2_CTRL_ADDR, T(int2Register));
            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
        }

        /* If enable, set INT bit enable and enable accelerometer sensor @26Hz if disabled. If disable, disable INT bit and disable accelerometer if no one need it */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[STEP_COUNTER]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[STEP_COUNTER]) = true;
        T(sensors[STEP_COUNTER]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_setSignMotionPower: enable/disable significant motion sensor
 */
static bool lsm6dsm_setSignMotionPower(bool on, void *cookie)
{
    TDECL();

    /* If current status is SENSOR_IDLE set state to SENSOR_POWERING_* and execute command directly.
        If current status is NOT SENSOR_IDLE add pending config that will be managed before go back to SENSOR_IDLE. */
    if (trySwitchState(on ? SENSOR_POWERING_UP : SENSOR_POWERING_DOWN)) {
        INFO_PRINT("setSignMotionPower: %s\n", on ? "enable" : "disable");

        if (on) {
            T(accelSensorDependencies) |= BIT(SIGN_MOTION);
            T(embeddedFunctionsDependencies) |= BIT(SIGN_MOTION);
            T(embeddedFunctionsRegister) |= (LSM6DSM_ENABLE_SIGN_MOTION_DIGITAL_FUNC | LSM6DSM_ENABLE_PEDOMETER_DIGITAL_FUNC | LSM6DSM_ENABLE_DIGITAL_FUNC);
            T(int1Register) |= LSM6DSM_INT_SIGN_MOTION_ENABLE_REG_VALUE;

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
        } else {
            T(accelSensorDependencies) &= ~BIT(SIGN_MOTION);
            T(embeddedFunctionsDependencies) &= ~BIT(SIGN_MOTION);
            T(int1Register) &= ~LSM6DSM_INT_SIGN_MOTION_ENABLE_REG_VALUE;

            if ((T(embeddedFunctionsDependencies) & (BIT(STEP_DETECTOR) | BIT(STEP_COUNTER))) == 0) {
                DEBUG_PRINT("setSignMotionPower: no more need pedometer algo, disabling it\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_SIGN_MOTION_DIGITAL_FUNC;
            }

            if (T(embeddedFunctionsDependencies) == 0) {
                DEBUG_PRINT("setSignMotionPower: no embedded sensors on, disabling digital functions\n");
                T(embeddedFunctionsRegister) &= ~LSM6DSM_ENABLE_DIGITAL_FUNC;
            }

            lsm6dsm_updateAccelOdr();

            SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register), 50000);
            SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));
        }

        /* If enable, set INT bit enable and enable accelerometer sensor @26Hz if disabled. If disable, disable INT bit and disable accelerometer if no one need it */
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[SIGN_MOTION]), __FUNCTION__);
    } else {
        T(pendingEnableConfig[SIGN_MOTION]) = true;
        T(sensors[SIGN_MOTION]).pConfig.enable = on;
    }

    return true;
}

/*
 * lsm6dsm_accelFirmwareUpload: upload accelerometer firmware
 */
static bool lsm6dsm_accelFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[ACCEL]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_gyroFirmwareUpload: upload gyroscope firmware
 */
static bool lsm6dsm_gyroFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[GYRO]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_magnFirmwareUpload: upload magnetometer firmware
 */
static bool lsm6dsm_magnFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[MAGN]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_pressFirmwareUpload: upload pressure firmware
 */
static bool lsm6dsm_pressFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[PRESS]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_tempFirmwareUpload: upload pressure firmware
 */
static bool lsm6dsm_tempFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[TEMP]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_stepDetectorFirmwareUpload: upload step detector firmware
 */
static bool lsm6dsm_stepDetectorFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[STEP_DETECTOR]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_stepCounterFirmwareUpload: upload step counter firmware
 */
static bool lsm6dsm_stepCounterFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[STEP_COUNTER]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_signMotionFirmwareUpload: upload significant motion firmware
 */
static bool lsm6dsm_signMotionFirmwareUpload(void *cookie)
{
    TDECL();

    sensorSignalInternalEvt(T(sensors[SIGN_MOTION]).handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);

    return true;
}

/*
 * lsm6dsm_setAccelRate: set accelerometer ODR and report latency (FIFO watermark related)
 */
static bool lsm6dsm_setAccelRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setAccelRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(sensors[ACCEL]).rate = rate;
        T(sensors[ACCEL]).latency = latency;

        lsm6dsm_updateAccelOdr();

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[ACCEL]), __FUNCTION__);
    } else {
        T(pendingRateConfig[ACCEL]) = true;
        T(sensors[ACCEL].pConfig.rate) = rate;
        T(sensors[ACCEL]).pConfig.latency = latency;
    }

    return true;
}

/*
 * lsm6dsm_setGyroRate: set gyroscope ODR and report latency (FIFO watermark related)
 */
static bool lsm6dsm_setGyroRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i;

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setGyroRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);

        T(sensors[GYRO]).rate = rate;
        T(sensors[GYRO]).latency = latency;
        T(sensors[GYRO]).samplesToDiscard = LSM6DSMGyroRatesSamplesToDiscard[i];

        if (T(sensors[GYRO]).hwRate == 0)
            T(sensors[GYRO]).samplesToDiscard += LSM6DSMRatesSamplesToDiscardGyroPowerOn[i];

        T(sensors[GYRO]).hwRate = rate < (SENSOR_HZ(26.0f) / 2) ? (SENSOR_HZ(26.0f) / 2) : rate;
        T(sensors[GYRO]).samplesDecimator = T(sensors[GYRO]).hwRate / rate;
        T(sensors[GYRO]).samplesCounter = T(sensors[GYRO]).samplesDecimator - 1;

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
        /* Gyroscope bias calibration library requires accelerometer data, enabling it @26Hz if not enabled */
        T(accelSensorDependencies) |= BIT(GYRO);
        T(int1Register) |= LSM6DSM_INT_ACCEL_ENABLE_REG_VALUE;

        lsm6dsm_updateAccelOdr();

        SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, T(int1Register));
#else /* LSM6DSM_GYRO_CALIB_ENABLED */
        lsm6dsm_setTriggerRate();
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

        SPI_WRITE(LSM6DSM_CTRL2_G_ADDR, LSM6DSM_CTRL2_G_BASE | LSM6DSMImuRatesRegValue[i]);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[GYRO]), __FUNCTION__);
    } else {
        T(pendingRateConfig[GYRO]) = true;
        T(sensors[GYRO]).pConfig.rate = rate;
        T(sensors[GYRO]).pConfig.latency = latency;
    }

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_setMagnRate: set magnetometer ODR and report latency (FIFO watermark related)
 */
static bool lsm6dsm_setMagnRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i, buffer[2];

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setMagnRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(embeddedFunctionsDependencies) |= BIT(MAGN);
        T(embeddedFunctionsRegister) |= LSM6DSM_ENABLE_DIGITAL_FUNC;
        T(accelSensorDependencies) |= BIT(MAGN);

        T(sensors[MAGN]).rate = rate;
        T(sensors[MAGN]).latency = latency;

        lsm6dsm_updateAccelOdr();

        T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

        buffer[0] = T(embeddedFunctionsRegister); /* LSM6DSM_CTRL10_C */
        buffer[1] = T(masterConfigRegister);      /* LSM6DSM_MASTER_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_CTRL10_C_ADDR, buffer, 2);

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);
        T(sensors[MAGN]).hwRate = LSM6DSMSHRates[i];

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE | LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, MAGN);
#else /* LSM6DSM_I2C_MASTER_LSM303AGR */
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ADDR,
                LSM6DSM_SENSOR_SLAVE_MAGN_POWER_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_ON_VALUE,
                T(sensors[ACCEL]).hwRate, MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_MAGN_ODR_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, MAGN);
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[MAGN]), __FUNCTION__);
    } else {
        T(pendingRateConfig[MAGN]) = true;
        T(sensors[MAGN]).pConfig.rate = rate;
        T(sensors[MAGN]).pConfig.latency = latency;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_setPressRate: set pressure ODR and report latency (FIFO watermark related)
 */
static bool lsm6dsm_setPressRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i, buffer[2];

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setPressRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(embeddedFunctionsDependencies) |= BIT(PRESS);
        T(embeddedFunctionsRegister) |= LSM6DSM_ENABLE_DIGITAL_FUNC;
        T(accelSensorDependencies) |= BIT(PRESS);

        T(sensors[PRESS]).rate = rate;
        T(sensors[PRESS]).latency = latency;

        lsm6dsm_updateAccelOdr();

        T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

        buffer[0] = T(embeddedFunctionsRegister); /* LSM6DSM_CTRL10_C */
        buffer[1] = T(masterConfigRegister);      /* LSM6DSM_MASTER_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_CTRL10_C_ADDR, buffer, 2);

        if (T(sensors[TEMP]).enabled) {
            if (rate < T(sensors[TEMP]).rate)
                rate = T(sensors[TEMP]).rate;
        }

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);
        T(sensors[PRESS]).hwRate = LSM6DSMSHRates[i];

        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_BARO_ODR_BASE | LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, PRESS);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[PRESS]), __FUNCTION__);
    } else {
        T(pendingRateConfig[PRESS]) = true;
        T(sensors[PRESS]).pConfig.rate = rate;
        T(sensors[PRESS]).pConfig.latency = latency;
    }

    return true;
}

/*
 * lsm6dsm_setTempRate: set temperature ODR and report latency (FIFO watermark related)
 */
static bool lsm6dsm_setTempRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i, buffer[2];

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        INFO_PRINT("setTempRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

        T(embeddedFunctionsDependencies) |= BIT(TEMP);
        T(embeddedFunctionsRegister) |= LSM6DSM_ENABLE_DIGITAL_FUNC;
        T(accelSensorDependencies) |= BIT(TEMP);

        T(sensors[TEMP]).rate = rate;
        T(sensors[TEMP]).hwRate = rate;
        T(sensors[TEMP]).latency = latency;

        lsm6dsm_updateAccelOdr();

        T(masterConfigRegister) |= LSM6DSM_MASTER_CONFIG_MASTER_ON;

        buffer[0] = T(embeddedFunctionsRegister); /* LSM6DSM_CTRL10_C */
        buffer[1] = T(masterConfigRegister);      /* LSM6DSM_MASTER_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_CTRL10_C_ADDR, buffer, 2);

        if (T(sensors[PRESS]).enabled) {
            if (rate < T(sensors[PRESS]).rate)
                rate = T(sensors[PRESS]).rate;
        }

        /* This call return index of LSM6DSMImuRates struct element */
        i = lsm6dsm_computeOdr(rate);
        T(sensors[TEMP]).hwRate = LSM6DSMSHRates[i];

        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_ODR_ADDR,
                LSM6DSM_SENSOR_SLAVE_BARO_ODR_BASE | LSM6DSM_SENSOR_SLAVE_BARO_RATES_REG_VALUE(i),
                T(sensors[ACCEL]).hwRate, TEMP);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[TEMP]), __FUNCTION__);
    } else {
        T(pendingRateConfig[TEMP]) = true;
        T(sensors[TEMP]).pConfig.rate = rate;
        T(sensors[TEMP]).pConfig.latency = latency;
    }

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_setStepDetectorRate: set step detector report latency
 */
static bool lsm6dsm_setStepDetectorRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    INFO_PRINT("setStepDetectorRate: latency=%lldns\n", latency);

    T(sensors[STEP_DETECTOR]).rate = rate;
    T(sensors[STEP_DETECTOR]).latency = latency;

    sensorSignalInternalEvt(T(sensors[STEP_DETECTOR]).handle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

/*
 * lsm6dsm_setStepCounterRate: set step counter report latency
 */
static bool lsm6dsm_setStepCounterRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();
    uint8_t i, step_delta_reg;

    if (rate == SENSOR_RATE_ONCHANGE) {
        INFO_PRINT("setStepCounterRate: delivery-rate=on_change, latency=%lldns\n", latency);
    } else
        INFO_PRINT("setStepCounterRate: delivery_rate=%dms, latency=%lldns\n", (int)((1024.0f / rate) * 1000.0f), latency);

    if (trySwitchState(SENSOR_CONFIG_CHANGING)) {
        T(sensors[STEP_COUNTER]).rate = rate;
        T(sensors[STEP_COUNTER]).latency = latency;

        for (i = 0; i < ARRAY_SIZE(LSM6DSMStepCounterRates); i++) {
            if (rate == LSM6DSMStepCounterRates[i])
                break;
        }
        if (i >= (ARRAY_SIZE(LSM6DSMStepCounterRates) - 2))
            step_delta_reg = 0;
        else
            step_delta_reg = (128 >> i);

        T(embeddedFunctionsRegister) |= LSM6DSM_ENABLE_TIMER_DIGITAL_FUNC;
        SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, T(embeddedFunctionsRegister));

        lsm6dsm_writeEmbeddedRegister(LSM6DSM_EMBEDDED_STEP_COUNT_DELTA_ADDR, step_delta_reg);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &T(sensors[GYRO]), __FUNCTION__);
    } else {
        T(pendingRateConfig[STEP_COUNTER]) = true;
        T(sensors[STEP_COUNTER]).pConfig.rate = rate;
        T(sensors[STEP_COUNTER]).pConfig.latency = latency;
    }

    return true;
}

/*
 * lsm6dsm_setSignMotionRate: set significant motion report latency
 */
static bool lsm6dsm_setSignMotionRate(uint32_t rate, uint64_t latency, void *cookie)
{
    TDECL();

    DEBUG_PRINT("setSignMotionRate: rate=%dHz, latency=%lldns\n", (int)(rate / 1024), latency);

    T(sensors[SIGN_MOTION]).rate = rate;
    T(sensors[SIGN_MOTION]).latency = latency;

    sensorSignalInternalEvt(T(sensors[SIGN_MOTION]).handle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

/*
 * lsm6dsm_accelFlush: send accelerometer flush event
 */
static bool lsm6dsm_accelFlush(void *cookie)
{
    INFO_PRINT("accelFlush\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ACCEL), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_gyroFlush: send gyroscope flush event
 */
static bool lsm6dsm_gyroFlush(void *cookie)
{
    INFO_PRINT("gyroFlush\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_GYRO), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
/*
 * lsm6dsm_magnFlush: send magnetometer flush event
 */
static bool lsm6dsm_magnFlush(void *cookie)
{
    INFO_PRINT("magnFlush\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_MAG), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_pressFlush: send pressure flush event
 */
static bool lsm6dsm_pressFlush(void *cookie)
{
    return true;
}

/*
 * lsm6dsm_tempFlush: send temperature flush event
 */
static bool lsm6dsm_tempFlush(void *cookie)
{
    return true;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_stepDetectorFlush: send step detector flush event
 */
static bool lsm6dsm_stepDetectorFlush(void *cookie)
{
    INFO_PRINT("stepDetectorFlush\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_DETECT), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_stepCounterFlush: send step counter flush event
 */
static bool lsm6dsm_stepCounterFlush(void *cookie)
{
    INFO_PRINT("stepCounterFlush\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_signMotionFlush: send significant motion flush event
 */
static bool lsm6dsm_signMotionFlush(void *cookie)
{
    INFO_PRINT("signMotionFlush\n");

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_SIG_MOTION), SENSOR_DATA_EVENT_FLUSH, NULL);

    return true;
}

/*
 * lsm6dsm_stepCounterSendLastData: send last number of steps
 */
static bool lsm6dsm_stepCounterSendLastData(void *cookie, uint32_t tid)
{
    TDECL();

    INFO_PRINT("stepCounterSendLastData: %lu steps\n", T(totalNumSteps));

    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), &T(totalNumSteps), NULL);

    return true;
}

/*
 * lsm6dsm_sensorInit: initial sensor configuration
 */
static void lsm6dsm_sensorInit(void)
{
    TDECL();
    uint8_t buffer[4];

    switch (T(initState)) {
    case RESET_LSM6DSM:
        INFO_PRINT("Performing soft-reset\n");

        T(initState) = INIT_LSM6DSM;

        /* Sensor SW-reset */
        SPI_WRITE(LSM6DSM_CTRL3_C_ADDR, LSM6DSM_SW_RESET, 20000);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_LSM6DSM:
        INFO_PRINT("Initial registers configuration\n");

        /* During init, reset all configurable registers to default values */
        SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);
        SPI_WRITE(LSM6DSM_DRDY_PULSE_CFG_ADDR, LSM6DSM_DRDY_PULSE_CFG_BASE);

        buffer[0] = LSM6DSM_CTRL1_XL_BASE;                           /* LSM6DSM_CTRL1_XL */
        buffer[1] = LSM6DSM_CTRL2_G_BASE;                            /* LSM6DSM_CTRL2_G */
        buffer[2] = LSM6DSM_CTRL3_C_BASE;                            /* LSM6DSM_CTRL3_C */
        buffer[3] = LSM6DSM_CTRL4_C_BASE;                            /* LSM6DSM_CTRL4_C */
        SPI_MULTIWRITE(LSM6DSM_CTRL1_XL_ADDR, buffer, 4);

        buffer[0] = LSM6DSM_CTRL10_C_BASE | LSM6DSM_RESET_PEDOMETER; /* LSM6DSM_CTRL10_C */
        buffer[1] = LSM6DSM_MASTER_CONFIG_BASE;                      /* LSM6DSM_MASTER_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_CTRL10_C_ADDR, buffer, 2);

        SPI_WRITE(LSM6DSM_INT1_CTRL_ADDR, LSM6DSM_INT1_CTRL_BASE);

#ifdef LSM6DSM_I2C_MASTER_ENABLED
        T(initState) = INIT_I2C_MASTER_REGS_CONF;
#else /* LSM6DSM_I2C_MASTER_ENABLED */
        INFO_PRINT("Initialization completed successfully!\n");
        T(initState) = INIT_DONE;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

#ifdef LSM6DSM_I2C_MASTER_ENABLED
    case INIT_I2C_MASTER_REGS_CONF:
        INFO_PRINT("Initial I2C master registers configuration\n");

        /* Enable access for embedded registers */
        SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE | LSM6DSM_ENABLE_FUNC_CFG_ACCESS, 50);

        /* I2C-0 configuration */
        buffer[0] = LSM6DSM_EMBEDDED_SLV0_WRITE_ADDR_SLEEP;                                               /* LSM6DSM_EMBEDDED_SLV0_ADDR */
        buffer[1] = 0x00;                                                                                 /* LSM6DSM_EMBEDDED_SLV0_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SENSOR_HUB_NUM_SLAVE;                                                /* LSM6DSM_EMBEDDED_SLV0_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV0_ADDR_ADDR, buffer, 3);

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)     /* Magn & Baro both enabled */
        /* I2C-1 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV1_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV1_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE | LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN;      /* LSM6DSM_EMBEDDED_SLV1_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR, buffer, 3);

        /* I2C-2 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV2_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV2_SUBADDR */
        buffer[2] = LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN;                                                /* LSM6DSM_EMBEDDED_SLV2_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV2_ADDR_ADDR, buffer, 3);

#ifdef LSM6DSM_I2C_MASTER_AK09916
        /* I2C-3 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV3_ADDR */
        buffer[1] = AK09916_STATUS_DATA_ADDR;                                                             /* LSM6DSM_EMBEDDED_SLV3_SUBADDR */
        buffer[2] = 1;                                                                                    /* LSM6DSM_EMBEDDED_SLV3_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV3_ADDR_ADDR, buffer, 3);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

#if defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && !defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)    /* Magn only enabled */
        /* I2C-1 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV1_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV1_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE | LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN;      /* LSM6DSM_EMBEDDED_SLV1_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR, buffer, 3);

#ifdef LSM6DSM_I2C_MASTER_AK09916
        /* I2C-2 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_MAGN_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV2_ADDR */
        buffer[1] = AK09916_STATUS_DATA_ADDR;                                                             /* LSM6DSM_EMBEDDED_SLV2_SUBADDR */
        buffer[2] = 1;                                                                                    /* LSM6DSM_EMBEDDED_SLV2_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV2_ADDR_ADDR, buffer, 3);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

#if !defined(LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED) && defined(LSM6DSM_I2C_MASTER_BAROMETER_ENABLED)    /* Baro only enabled */
        /* I2C-1 configuration */
        buffer[0] = (LSM6DSM_SENSOR_SLAVE_BARO_I2C_ADDR_8BIT << 1) | LSM6DSM_EMBEDDED_READ_OP_SENSOR_HUB; /* LSM6DSM_EMBEDDED_SLV1_ADDR */
        buffer[1] = LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_ADDR;                                               /* LSM6DSM_EMBEDDED_SLV1_SUBADDR */
        buffer[2] = LSM6DSM_EMBEDDED_SLV1_CONFIG_WRITE_ONCE | LSM6DSM_SENSOR_SLAVE_BARO_OUTDATA_LEN;      /* LSM6DSM_EMBEDDED_SLV1_CONFIG */
        SPI_MULTIWRITE(LSM6DSM_EMBEDDED_SLV1_ADDR_ADDR, buffer, 3);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED, LSM6DSM_I2C_MASTER_BAROMETER_ENABLED) */

        /* Disable access for embedded registers */
        SPI_WRITE(LSM6DSM_FUNC_CFG_ACCESS_ADDR, LSM6DSM_FUNC_CFG_ACCESS_BASE, 50);

        T(initState) = INIT_I2C_MASTER_SENSOR_RESET;

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_SENSOR_RESET:
        INFO_PRINT("Performing soft-reset slave sensors\n");
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
        T(initState) = INIT_I2C_MASTER_MAGN_SENSOR;
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
        T(initState) = INIT_I2C_MASTER_BARO_SENSOR;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

        /* Enable accelerometer and sensor-hub to initialize slave sensor */
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE | LSM6DSM_ODR_104HZ_REG_VALUE);
        SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, LSM6DSM_CTRL10_C_BASE | LSM6DSM_ENABLE_DIGITAL_FUNC);
        SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE | LSM6DSM_MASTER_CONFIG_MASTER_ON);

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_MAGN_RESET_ADDR, LSM6DSM_SENSOR_SLAVE_MAGN_RESET_VALUE, SENSOR_HZ(104.0f), MAGN, 20000);
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM6DSM_SENSOR_SLAVE_BARO_RESET_ADDR, LSM6DSM_SENSOR_SLAVE_BARO_RESET_VALUE, SENSOR_HZ(104.0f), PRESS, 20000);
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_MAGN_SENSOR:
        INFO_PRINT("Initial slave magnetometer sensor registers configuration\n");
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        T(initState) = INIT_I2C_MASTER_BARO_SENSOR;
#else /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
        T(initState) = INIT_I2C_MASTER_SENSOR_END;
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_LIS3MDL
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL1_ADDR, LIS3MDL_CTRL1_BASE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL2_ADDR, LIS3MDL_CTRL2_BASE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL3_ADDR, LIS3MDL_CTRL3_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL4_ADDR, LIS3MDL_CTRL4_BASE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LIS3MDL_CTRL5_ADDR, LIS3MDL_CTRL5_BASE, SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_LIS3MDL */

#ifdef LSM6DSM_I2C_MASTER_LSM303AGR
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_A_M_ADDR, LSM303AGR_CFG_REG_A_M_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LSM303AGR_CFG_REG_C_M_ADDR, LSM303AGR_CFG_REG_C_M_BASE, SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_LSM303AGR */

#ifdef LSM6DSM_I2C_MASTER_AK09916
        SPI_WRITE_SLAVE_SENSOR_REGISTER(AK09916_CNTL2_ADDR, AK09916_CNTL2_BASE | LSM6DSM_SENSOR_SLAVE_MAGN_POWER_OFF_VALUE, SENSOR_HZ(104.0f), MAGN);
#endif /* LSM6DSM_I2C_MASTER_AK09916 */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_BARO_SENSOR:
        INFO_PRINT("Initial slave barometer sensor registers configuration\n");
        T(initState) = INIT_I2C_MASTER_SENSOR_END;

#ifdef LSM6DSM_I2C_MASTER_LPS22HB
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LPS22HB_CTRL1_ADDR, LPS22HB_CTRL1_BASE | LSM6DSM_SENSOR_SLAVE_BARO_POWER_OFF_VALUE, SENSOR_HZ(104.0f), PRESS);
        SPI_WRITE_SLAVE_SENSOR_REGISTER(LPS22HB_CTRL2_ADDR, LPS22HB_CTRL2_BASE, SENSOR_HZ(104.0f), PRESS);
#endif /* LSM6DSM_I2C_MASTER_LPS22HB */

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case INIT_I2C_MASTER_SENSOR_END:
        INFO_PRINT("Initialization completed successfully!\n");
        T(initState) = INIT_DONE;

        /* Disable accelerometer and sensor-hub */
        SPI_WRITE(LSM6DSM_MASTER_CONFIG_ADDR, LSM6DSM_MASTER_CONFIG_BASE);
        SPI_WRITE(LSM6DSM_CTRL10_C_ADDR, LSM6DSM_CTRL10_C_BASE);
        SPI_WRITE(LSM6DSM_CTRL1_XL_ADDR, LSM6DSM_CTRL1_XL_BASE);

        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

    default:
        break;
    }
}

/*
 * lsm6dsm_processPendingEvt: process pending events
 */
static void lsm6dsm_processPendingEvt(void)
{
    TDECL();
    enum SensorIndex i;

    if (T(pendingInt[LSM6DSM_INT1_INDEX])) {
        T(pendingInt[LSM6DSM_INT1_INDEX]) = false;
        lsm6dsm_readStatusReg(false);
        return;
    }

    for (i = ACCEL; i < NUM_SENSORS; i++) {
        if (T(pendingEnableConfig[i])) {
            T(pendingEnableConfig[i]) = false;
            LSM6DSMSensorOps[i].sensorPower(T(sensors[i]).pConfig.enable, (void *)i);
            return;
        }

        if (T(pendingRateConfig[i])) {
            T(pendingRateConfig[i]) = false;
            LSM6DSMSensorOps[i].sensorSetRate(T(sensors[i]).pConfig.rate, T(sensors[i]).pConfig.latency, (void *)i);
            return;
        }
    }
}

/*
 * lsm6dsm_processSensorThreeAxisData: elaborate three axis sensors data
 */
static bool lsm6dsm_processSensorThreeAxisData(struct LSM6DSMSensor *mSensor, uint8_t *data)
{
    TDECL();
    float kScale = 1.0f, x, y, z;

    switch (mSensor->idx) {
    case ACCEL:
        kScale = LSM6DSM_ACCEL_KSCALE;
        break;
    case GYRO:
        kScale = LSM6DSM_GYRO_KSCALE;
        break;
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    case MAGN:
        kScale = LSM6DSM_MAGN_KSCALE;
        break;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
    default:
        return false;
    }

    x = ((int16_t)(data[1] << 8) | data[0]) * kScale;
    y = ((int16_t)(data[3] << 8) | data[2]) * kScale;
    z = ((int16_t)(data[5] << 8) | data[4]) * kScale;

    mSensor->tADataEvt->referenceTime = T(timestampInt[LSM6DSM_INT1_INDEX]);
    mSensor->tADataEvt->samples[0].firstSample.numSamples = 1;

    switch (mSensor->idx) {
    case ACCEL:
    case GYRO:
        mSensor->tADataEvt->samples[0].x = LSM6DSM_REMAP_X_DATA(x, y, z, LSM6DSM_ROT_MATRIX);
        mSensor->tADataEvt->samples[0].y = LSM6DSM_REMAP_Y_DATA(x, y, z, LSM6DSM_ROT_MATRIX);
        mSensor->tADataEvt->samples[0].z = LSM6DSM_REMAP_Z_DATA(x, y, z, LSM6DSM_ROT_MATRIX);
        break;

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    case MAGN:
        mSensor->tADataEvt->samples[0].x = LSM6DSM_REMAP_X_DATA(x, y, z, LSM6DSM_MAGN_ROT_MATRIX);
        mSensor->tADataEvt->samples[0].y = LSM6DSM_REMAP_Y_DATA(x, y, z, LSM6DSM_MAGN_ROT_MATRIX);
        mSensor->tADataEvt->samples[0].z = LSM6DSM_REMAP_Z_DATA(x, y, z, LSM6DSM_MAGN_ROT_MATRIX);
        break;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */

    default:
        break;
    }

    if (mSensor->samplesToDiscard == 0) {
        mSensor->samplesCounter++;

        if (mSensor->samplesCounter >= mSensor->samplesDecimator) {
            switch (mSensor->idx) {
            case ACCEL:
#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
                accelCalRun(&T(accelCal), T(timestampInt[LSM6DSM_INT1_INDEX]),
                    mSensor->tADataEvt->samples[0].x,
                    mSensor->tADataEvt->samples[0].y,
                    mSensor->tADataEvt->samples[0].z, T(currentTemperature));

                accelCalBiasRemove(&T(accelCal),
                    &mSensor->tADataEvt->samples[0].x,
                    &mSensor->tADataEvt->samples[0].y,
                    &mSensor->tADataEvt->samples[0].z);
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
                if (T(sensors[GYRO].enabled)) {
                    gyroCalUpdateAccel(&T(gyroCal), T(timestampInt[LSM6DSM_INT1_INDEX]),
                        mSensor->tADataEvt->samples[0].x,
                        mSensor->tADataEvt->samples[0].y,
                        mSensor->tADataEvt->samples[0].z);
                }
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
                break;

            case GYRO:
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
                gyroCalUpdateGyro(&T(gyroCal), T(timestampInt[LSM6DSM_INT1_INDEX]),
                    mSensor->tADataEvt->samples[0].x,
                    mSensor->tADataEvt->samples[0].y,
                    mSensor->tADataEvt->samples[0].z, T(currentTemperature));

                gyroCalRemoveBias(&T(gyroCal),
                    mSensor->tADataEvt->samples[0].x,
                    mSensor->tADataEvt->samples[0].y,
                    mSensor->tADataEvt->samples[0].z,
                    &mSensor->tADataEvt->samples[0].x,
                    &mSensor->tADataEvt->samples[0].y,
                    &mSensor->tADataEvt->samples[0].z);
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
                break;

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
            case MAGN: ;
                float magnOffX, magnOffY, magnOffZ;

                magCalRemoveSoftiron(&T(magnCal),
                    mSensor->tADataEvt->samples[0].x,
                    mSensor->tADataEvt->samples[0].y,
                    mSensor->tADataEvt->samples[0].z,
                    &magnOffX, &magnOffY, &magnOffZ);

                T(newMagnCalibData) = magCalUpdate(&T(magnCal), NS_TO_US(T(timestampInt[LSM6DSM_INT1_INDEX])), magnOffX, magnOffY, magnOffZ);

                magCalRemoveBias(&T(magnCal), magnOffX, magnOffY, magnOffZ,
                    &mSensor->tADataEvt->samples[0].x,
                    &mSensor->tADataEvt->samples[0].y,
                    &mSensor->tADataEvt->samples[0].z);
                break;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

            default:
                break;
            }

            mSensor->samplesCounter = 0;
            return true;
        }
    } else
        mSensor->samplesToDiscard--;

    return false;
}

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
/*
 * lsm6dsm_processSensorOneAxisData: elaborate single axis sensors data
 */
static bool lsm6dsm_processSensorOneAxisData(struct LSM6DSMSensor *mSensor, uint8_t *data, void *store)
{
    TDECL();
    struct SingleAxisDataEvent *pressData = (struct SingleAxisDataEvent *)store;
    union EmbeddedDataPoint *tempData = (union EmbeddedDataPoint *)store;

    if (mSensor->samplesToDiscard == 0) {
        mSensor->samplesCounter++;

        if (mSensor->samplesCounter >= mSensor->samplesDecimator) {
            switch (mSensor->idx) {
            case PRESS:
                pressData->samples[0].fdata = ((data[2] << 16) | (data[1] << 8) | data[0]) * LSM6DSM_PRESS_KSCALE;
                pressData->referenceTime = T(timestampInt[LSM6DSM_INT1_INDEX]);
                pressData->samples[0].firstSample.numSamples = 1;
                break;
            case TEMP:
                tempData->fdata = ((int16_t)(data[1] << 8) | data[0]) * LSM6DSM_TEMP_KSCALE;
                break;
            default:
                return false;
            }

            mSensor->samplesCounter = 0;
            return true;
        }
    } else
            mSensor->samplesToDiscard--;

    return false;
}
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

/*
 * lsm6dsm_handleSpiDoneEvt: all SPI operation fall back here
 */
static void lsm6dsm_handleSpiDoneEvt(const void *evtData)
{
    TDECL();
    bool returnIdle = false, validData;
    struct LSM6DSMSensor *mSensor;
    int i;

    switch (GET_STATE()) {
    case SENSOR_BOOT:
        SET_STATE(SENSOR_VERIFY_WAI);

        SPI_READ(LSM6DSM_WAI_ADDR, 1, &T_SLAVE_INTERFACE(tmpDataBuffer));
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case SENSOR_VERIFY_WAI:
        if (T_SLAVE_INTERFACE(tmpDataBuffer[1]) != LSM6DSM_WAI_VALUE) {
            T(mRetryLeft)--;
            if (T(mRetryLeft) == 0)
                break;

            ERROR_PRINT("`Who-Am-I` register value not valid: %x\n", T_SLAVE_INTERFACE(tmpDataBuffer[1]));
            SET_STATE(SENSOR_BOOT);
            timTimerSet(100000000, 100, 100, lsm6dsm_timerCallback, NULL, true);
        } else {
            SET_STATE(SENSOR_INITIALIZATION);
            T(initState) = RESET_LSM6DSM;
            lsm6dsm_sensorInit();
        }

        break;

    case SENSOR_INITIALIZATION:
        if (T(initState) == INIT_DONE) {
            for (i = 0; i < NUM_SENSORS; i++)
                sensorRegisterInitComplete(T(sensors[i]).handle);

            returnIdle = true;
        } else
            lsm6dsm_sensorInit();

        break;

    case SENSOR_POWERING_UP:
        mSensor = (struct LSM6DSMSensor *)evtData;

        mSensor->enabled = true;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 1, 0);
        returnIdle = true;
        break;

    case SENSOR_POWERING_DOWN:
        mSensor = (struct LSM6DSMSensor *)evtData;

        mSensor->enabled = false;
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, 0, 0);
        returnIdle = true;
        break;

    case SENSOR_CONFIG_CHANGING:
        mSensor = (struct LSM6DSMSensor *)evtData;

        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_RATE_CHG, mSensor->rate, mSensor->latency);
        returnIdle = true;
        break;

    case SENSOR_INT1_STATUS_REG_HANDLING:
        if (T(sensors[STEP_DETECTOR].enabled)) {
            if (T_SLAVE_INTERFACE(funcSrcBuffer[1]) & LSM6DSM_FUNC_SRC_STEP_DETECTED) {
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_DETECT), NULL, NULL);
                DEBUG_PRINT("Step Detected!\n");
            }
        }

        if (T(sensors[STEP_COUNTER].enabled)) {
            if (T_SLAVE_INTERFACE(funcSrcBuffer[1]) & LSM6DSM_FUNC_SRC_STEP_COUNT_DELTA_IA) {
                T(readSteps) = true;
                SPI_READ(LSM6DSM_STEP_COUNTER_L_ADDR, 2, &T_SLAVE_INTERFACE(stepCounterDataBuffer));
            }
        }

        if (T(sensors[SIGN_MOTION].enabled)) {
            if (T_SLAVE_INTERFACE(funcSrcBuffer[1]) & LSM6DSM_FUNC_SRC_SIGN_MOTION) {
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_SIG_MOTION), NULL, NULL);
                DEBUG_PRINT("Significant Motion event!\n");
            }
        }

#ifdef LSM6DSM_I2C_MASTER_ENABLED
        if (T(masterConfigRegister) & LSM6DSM_MASTER_CONFIG_MASTER_ON) {
            T(statusRegisterSH) = T_SLAVE_INTERFACE(statusRegBuffer[1]) & LSM6DSM_FUNC_SRC_SENSOR_HUB_END_OP;
            if (T(statusRegisterSH))
                SPI_READ(LSM6DSM_SENSORHUB1_REG_ADDR, LSM6DSM_SH_READ_BYTE_NUM, &T_SLAVE_INTERFACE(SHDataBuffer));
        }
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
        T(statusRegisterTDA) = T_SLAVE_INTERFACE(statusRegBuffer[1]) & LSM6DSM_STATUS_REG_TDA;
        if (T(statusRegisterTDA))
            SPI_READ(LSM6DSM_OUT_TEMP_L_ADDR, LSM6DSM_TEMP_SAMPLE_BYTE, &T_SLAVE_INTERFACE(tempDataBuffer));
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */

        T(statusRegisterDA) = T_SLAVE_INTERFACE(statusRegBuffer[1]) & (LSM6DSM_STATUS_REG_XLDA | LSM6DSM_STATUS_REG_GDA);

        switch(T(statusRegisterDA)) {
        case LSM6DSM_STATUS_REG_XLDA:
            SPI_READ(LSM6DSM_OUTX_L_XL_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(accelDataBuffer));
            break;

        case LSM6DSM_STATUS_REG_GDA:
            SPI_READ(LSM6DSM_OUTX_L_G_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(gyroDataBuffer));
            break;

        case (LSM6DSM_STATUS_REG_XLDA | LSM6DSM_STATUS_REG_GDA):
            SPI_READ(LSM6DSM_OUTX_L_XL_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(accelDataBuffer));
            SPI_READ(LSM6DSM_OUTX_L_G_ADDR, LSM6DSM_ONE_SAMPLE_BYTE, &T_SLAVE_INTERFACE(gyroDataBuffer));
            break;

        default:
            if (!T(readSteps)) {
                SET_STATE(SENSOR_IDLE);
                lsm6dsm_processPendingEvt();
                return;
            }
            break;
        }

        SET_STATE(SENSOR_INT1_OUTPUT_DATA_HANDLING);
        lsm6dsm_spiBatchTxRx(&T_SLAVE_INTERFACE(mode), lsm6dsm_spiCallback, &mTask, __FUNCTION__);
        break;

    case SENSOR_INT1_OUTPUT_DATA_HANDLING:
        if (T(readSteps)) {
            union EmbeddedDataPoint step_cnt;

            step_cnt.idata = T_SLAVE_INTERFACE(stepCounterDataBuffer[1]) | (T_SLAVE_INTERFACE(stepCounterDataBuffer[2]) << 8);
            osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_STEP_COUNT), step_cnt.vptr, NULL);
            DEBUG_PRINT("Step Counter update: %ld steps\n", step_cnt.idata);
            T(totalNumSteps) = step_cnt.idata;
            T(readSteps) = false;
        }

#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
        if (T(statusRegisterTDA)) {
            T(currentTemperature) = LSM6DSM_TEMP_OFFSET +
                (float)((int16_t)((T_SLAVE_INTERFACE(tempDataBuffer[2]) << 8) | T_SLAVE_INTERFACE(tempDataBuffer[1]))) / 256.0f;
        }
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_ENABLED
        if (T(statusRegisterSH)) {
            T(statusRegisterSH) = 0;
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
            if (T(sensors[MAGN].enabled)) {
                validData = lsm6dsm_processSensorThreeAxisData(&T(sensors[MAGN]), &T_SLAVE_INTERFACE(SHDataBuffer[1]));
                if (validData) {
                    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_MAG), T(sensors[MAGN]).tADataEvt, NULL);

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
                    if T(newMagnCalibData) {
                        magCalGetBias(&T(magnCal), &T(magnCalDataEvt)->samples[0].x,
                            &T(magnCalDataEvt)->samples[0].y, &T(magnCalDataEvt)->samples[0].z);

                        T(newMagnCalibData) = false;
                        T(magnCalDataEvt)->referenceTime = T(timestampInt[LSM6DSM_INT1_INDEX]);
                        osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_MAG_BIAS), T(magnCalDataEvt), NULL);
                    }
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
                }
            }
#endif /* LSM6DSM_MAGN_CALIB_MAGNETOMETER_ENABLED */

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
            if (T(sensors[PRESS].enabled)) {
                validData = lsm6dsm_processSensorOneAxisData(&T(sensors[PRESS]),
                        &T_SLAVE_INTERFACE(SHDataBuffer[LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN + 1]), T(sensors[PRESS]).sADataEvt);
                if (validData)
                    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_BARO), T(sensors[PRESS]).sADataEvt, NULL);
            }

            if (T(sensors[TEMP].enabled)) {
                union EmbeddedDataPoint tempData;

                validData = lsm6dsm_processSensorOneAxisData(&T(sensors[TEMP]),
                        &T_SLAVE_INTERFACE(SHDataBuffer[LSM6DSM_SENSOR_SLAVE_MAGN_OUTDATA_LEN + LSM6DSM_PRESS_OUTDATA_LEN + 1]), &tempData);
                if (validData)
                    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TEMP), tempData.vptr, NULL);
            }
#endif /* LSM6DSM_MAGN_CALIB_BAROMETER_ENABLED */
        }
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

        if (T(statusRegisterDA) & LSM6DSM_STATUS_REG_XLDA) {
            validData = lsm6dsm_processSensorThreeAxisData(&T(sensors[ACCEL]), &T_SLAVE_INTERFACE(accelDataBuffer[1]));
            if (validData) {
                if (T(sensors[ACCEL].enabled)) {
                    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ACCEL), T(sensors[ACCEL]).tADataEvt, NULL);
                } else {
#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
                    if (accelCalUpdateBias(&T(accelCal), &T(accelBiasDataEvt)->samples[0].x,
                                &T(accelBiasDataEvt)->samples[0].y, &T(accelBiasDataEvt)->samples[0].z)) {
                        T(accelBiasDataEvt)->referenceTime = T(timestampInt[LSM6DSM_INT1_INDEX]);
                        osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_ACCEL_BIAS), T(accelBiasDataEvt), NULL);
                    }
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
                }
            }
        }

        if (T(statusRegisterDA) & LSM6DSM_STATUS_REG_GDA) {
            validData = lsm6dsm_processSensorThreeAxisData(&T(sensors[GYRO]), &T_SLAVE_INTERFACE(gyroDataBuffer[1]));
            if (validData) {
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_GYRO), T(sensors[GYRO]).tADataEvt, NULL);

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
                if (gyroCalNewBiasAvailable(&T(gyroCal))) {
                    gyroCalGetBias(&T(gyroCal), &T(gyroBiasDataEvt)->samples[0].x,
                        &T(gyroBiasDataEvt)->samples[0].y, &T(gyroBiasDataEvt)->samples[0].z);

                    T(gyroBiasDataEvt)->referenceTime = T(timestampInt[LSM6DSM_INT1_INDEX]);
                    osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_GYRO_BIAS), T(gyroBiasDataEvt), NULL);
                }
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
            }
        }

        returnIdle = true;
        break;

    default:
        break;
    }

    if (returnIdle) {
        SET_STATE(SENSOR_IDLE);
        lsm6dsm_processPendingEvt();
    }
}

/*
 * lsm6dsm_handleEvent: handle driver events
 */
static void lsm6dsm_handleEvent(uint32_t evtType, const void *evtData)
{
    TDECL();
    uint64_t currTime;

    switch (evtType) {
    case EVT_APP_START:
        T(mRetryLeft) = LSM6DSM_RETRY_CNT_WAI;
        SET_STATE(SENSOR_BOOT);
        osEventUnsubscribe(T(tid), EVT_APP_START);

        /* Sensor need 100ms to boot, use a timer callback to continue */
        currTime = timGetTime();
        if (currTime < 100000000ULL) {
            timTimerSet(100000000 - currTime, 100, 100, lsm6dsm_timerCallback, NULL, true);
            break;
        }

        /* If 100ms already passed, just fall through next step */
    case EVT_SPI_DONE:
        lsm6dsm_handleSpiDoneEvt(evtData);
        break;

    case EVT_SENSOR_INTERRUPT_1:
        lsm6dsm_readStatusReg(false);
        break;

    case EVT_APP_FROM_HOST:
        break;

    default:
        break;
    }
}

/*
 * lsm6dsm_initSensorStruct: initialize sensor struct variable
 */
static void lsm6dsm_initSensorStruct(struct LSM6DSMSensor *sensor, enum SensorIndex idx)
{
    sensor->idx = idx;
    sensor->rate = 0;
    sensor->hwRate = 0;
    sensor->latency = 0;
    sensor->enabled = false;
    sensor->samplesToDiscard = 0;
    sensor->samplesDecimator = 1;
    sensor->samplesCounter = 0;
    sensor->tADataEvt = NULL;
    sensor->sADataEvt = NULL;
}

/*
 * lsm6dsm_calculateSlabNumItems: calculate number of items needed to allocate memory
 */
static uint8_t lsm6dsm_calculateSlabNumItems()
{
    uint8_t slabElements = 2;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    slabElements++;
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    slabElements++;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
    slabElements++;
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    slabElements++;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    return slabElements;
}

/*
 * lsm6dsm_startTask: first function executed when App start
 */
static bool lsm6dsm_startTask(uint32_t task_id)
{
    TDECL();
    enum SensorIndex i;
    int err;

    DEBUG_PRINT("IMU: %lu\n", task_id);

    T(tid) = task_id;
    T(int1) = gpioRequest(LSM6DSM_INT1_GPIO);
    T(isr1).func = lsm6dsm_isr1;

    T_SLAVE_INTERFACE(mode).speed = LSM6DSM_SPI_SLAVE_FREQUENCY_HZ;
    T_SLAVE_INTERFACE(mode).bitsPerWord = 8;
    T_SLAVE_INTERFACE(mode).cpol = SPI_CPOL_IDLE_HI;
    T_SLAVE_INTERFACE(mode).cpha = SPI_CPHA_TRAILING_EDGE;
    T_SLAVE_INTERFACE(mode).nssChange = true;
    T_SLAVE_INTERFACE(mode).format = SPI_FORMAT_MSB_FIRST;
    T_SLAVE_INTERFACE(cs) = LSM6DSM_SPI_SLAVE_CS_GPIO;

    DEBUG_PRINT("Requested SPI on bus #%d @ %dHz, int1 on gpio#%d\n",
            LSM6DSM_SPI_SLAVE_BUS_ID, LSM6DSM_SPI_SLAVE_FREQUENCY_HZ, LSM6DSM_INT1_GPIO);

    err = spiMasterRequest(LSM6DSM_SPI_SLAVE_BUS_ID, &T_SLAVE_INTERFACE(spiDev));
    if (err < 0) {
        ERROR_PRINT("Failed to request SPI on this bus: #%d\n", LSM6DSM_SPI_SLAVE_BUS_ID);
        return false;
    }

    T(int1Register) = LSM6DSM_INT1_CTRL_BASE;
    T(int2Register) = LSM6DSM_INT2_CTRL_BASE;
    T(embeddedFunctionsRegister) = LSM6DSM_CTRL10_C_BASE;
    T(accelSensorDependencies) = 0;
    T(embeddedFunctionsDependencies) = 0;
    T(pendingInt[LSM6DSM_INT1_INDEX]) = false;
    T(pendingInt[LSM6DSM_INT2_INDEX]) = false;
    T(timestampInt[LSM6DSM_INT1_INDEX]) = 0;
    T(timestampInt[LSM6DSM_INT2_INDEX]) = 0;
    T(totalNumSteps) = 0;
    T(triggerRate) = SENSOR_HZ_RATE_TO_US(SENSOR_HZ(26.0f / 2.0f));
#if defined(LSM6DSM_GYRO_CALIB_ENABLED) || defined(LSM6DSM_ACCEL_CALIB_ENABLED)
    T(currentTemperature) = 0;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED, LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    T(newMagnCalibData) = false;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_ENABLED
    T(masterConfigRegister) = LSM6DSM_MASTER_CONFIG_BASE;
#endif /* LSM6DSM_I2C_MASTER_ENABLED */

    T(mDataSlabThreeAxis) = slabAllocatorNew(sizeof(struct TripleAxisDataEvent) + sizeof(struct TripleAxisDataPoint), 4, lsm6dsm_calculateSlabNumItems());
    if (!T(mDataSlabThreeAxis)) {
        ERROR_PRINT("Failed to allocate mDataSlabThreeAxis memory\n");
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }

#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    T(mDataSlabOneAxis) = slabAllocatorNew(sizeof(struct SingleAxisDataEvent) + sizeof(struct SingleAxisDataPoint), 4, 10);
    if (!T(mDataSlabOneAxis)) {
        ERROR_PRINT("Failed to allocate mDataSlabOneAxis memory\n");
        slabAllocatorDestroy(T(mDataSlabThreeAxis));
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    T(accelBiasDataEvt) = slabAllocatorAlloc(T(mDataSlabThreeAxis));
    if (!T(accelBiasDataEvt)) {
        ERROR_PRINT("Failed to allocate accelBiasDataEvt memory");
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        slabAllocatorDestroy(T(mDataSlabOneAxis));
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
        slabAllocatorDestroy(T(mDataSlabThreeAxis));
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }

    memset(&T(accelBiasDataEvt)->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
    T(accelBiasDataEvt)->samples[0].firstSample.biasCurrent = true;
    T(accelBiasDataEvt)->samples[0].firstSample.biasPresent = 1;
    T(accelBiasDataEvt)->samples[0].firstSample.biasSample = 0;
    T(accelBiasDataEvt)->samples[0].firstSample.numSamples = 1;
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    T(gyroBiasDataEvt) = slabAllocatorAlloc(T(mDataSlabThreeAxis));
    if (!T(gyroBiasDataEvt)) {
        ERROR_PRINT("Failed to allocate gyroBiasDataEvt memory");
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        slabAllocatorDestroy(T(mDataSlabOneAxis));
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
        slabAllocatorDestroy(T(mDataSlabThreeAxis));
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }

    memset(&T(gyroBiasDataEvt)->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
    T(gyroBiasDataEvt)->samples[0].firstSample.biasCurrent = true;
    T(gyroBiasDataEvt)->samples[0].firstSample.biasPresent = 1;
    T(gyroBiasDataEvt)->samples[0].firstSample.biasSample = 0;
    T(gyroBiasDataEvt)->samples[0].firstSample.numSamples = 1;
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    T(magnCalDataEvt) = slabAllocatorAlloc(T(mDataSlabThreeAxis));
    if (!T(magnCalDataEvt)) {
        ERROR_PRINT("Failed to allocate magnCalDataEvt memory");
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        slabAllocatorDestroy(T(mDataSlabOneAxis));
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
        slabAllocatorDestroy(T(mDataSlabThreeAxis));
        spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
        return false;
    }

    memset(&T(magnCalDataEvt)->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
    T(magnCalDataEvt)->samples[0].firstSample.biasCurrent = true;
    T(magnCalDataEvt)->samples[0].firstSample.biasPresent = 1;
    T(magnCalDataEvt)->samples[0].firstSample.biasSample = 0;
    T(magnCalDataEvt)->samples[0].firstSample.numSamples = 1;
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    for (i = 0; i < NUM_SENSORS; i++) {
        T(pendingEnableConfig[i]) = false;
        T(pendingRateConfig[i]) = false;
        lsm6dsm_initSensorStruct(&T(sensors[i]), i);

#ifdef LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED
        if ((i == ACCEL) || (i == GYRO) || (i == MAGN)) {
#else /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
        if ((i == ACCEL) || (i == GYRO)) {
#endif /* LSM6DSM_I2C_MASTER_MAGNETOMETER_ENABLED */
            T(sensors[i]).tADataEvt = slabAllocatorAlloc(T(mDataSlabThreeAxis));
            if (!T(sensors[i]).tADataEvt) {
                ERROR_PRINT("Failed to allocate tADataEvt memory");
                goto unregister_sensors;
            }

            memset(&T(sensors[i]).tADataEvt->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
        }
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
        if (i == PRESS) {
            T(sensors[i]).sADataEvt = slabAllocatorAlloc(T(mDataSlabOneAxis));
            if (!T(sensors[i]).sADataEvt) {
                ERROR_PRINT("Failed to allocate sADataEvt memory");
                goto unregister_sensors;
            }

            memset(&T(sensors[i]).sADataEvt->samples[0].firstSample, 0, sizeof(struct SensorFirstSample));
        }
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */

        T(sensors[i]).handle = sensorRegister(&LSM6DSMSensorInfo[i], &LSM6DSMSensorOps[i], NULL, false);
    }

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    accelCalInit(&T(accelCal),
            800000000,         /* stillness period = 0.8 seconds [ns] */
            5,                 /* minimum sample number */
            0.00025,           /* threshold */
            15,                /* nx bucket count */
            15,                /* nxb bucket count */
            15,                /* ny bucket count */
            15,                /* nyb bucket count */
            15,                /* nz bucket count */
            15,                /* nzb bucket count */
            15);               /* nle bucket count */
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */

#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    gyroCalInit(&T(gyroCal),
            5e9,               /* min stillness period = 5 seconds [ns] */
            6e9,               /* max stillness period = 6 seconds [ns] */
            0, 0, 0,           /* initial bias offset calibration values [rad/sec] */
            0,                 /* timestamp of initial bias calibration [ns] */
            1.5e9,             /* analysis window length = 1.5 seconds [ns] */
            5e-5f,             /* gyroscope variance threshold [rad/sec]^2 */
            1e-5f,             /* gyroscope confidence delta [rad/sec]^2 */
            8e-3f,             /* accelerometer variance threshold [m/sec^2]^2 */
            1.6e-3f,           /* accelerometer confidence delta [m/sec^2]^2 */
            1.4f,              /* magnetometer variance threshold [uT]^2 */
            0.25,              /* magnetometer confidence delta [uT]^2 */
            0.95f,             /* stillness threshold [0, 1] */
            1);                /* 1 : gyro calibrations will be applied */
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    initMagCal(&T(magnCal),
            0.0f, 0.0f, 0.0f,  /* magn offset x - y - z */
            1.0f, 0.0f, 0.0f,  /* magn scale matrix c00 - c01 - c02 */
            0.0f, 1.0f, 0.0f,  /* magn scale matrix c10 - c11 - c12 */
            0.0f, 0.0f, 1.0f); /* magn scale matrix c20 - c21 - c22 */
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */

    /* Initialize index used to fill/get data from buffer */
    T_SLAVE_INTERFACE(mWbufCnt) = 0;
    T_SLAVE_INTERFACE(mRegCnt) = 0;

    osEventSubscribe(T(tid), EVT_APP_START);

    DEBUG_PRINT("Enabling gpio#%d connected to int1\n", LSM6DSM_INT1_GPIO);
    lsm6dsm_enableInterrupt(T(int1), &T(isr1));

    return true;

unregister_sensors:
    for (i--; i >= ACCEL; i--)
        sensorUnregister(T(sensors[i]).handle);

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    accelCalDestroy(&T(accelCal));
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    destroy_mag_cal(&T(magnCal));
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    gyroCalDestroy(&T(gyroCal));
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    slabAllocatorDestroy(T(mDataSlabOneAxis));
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    slabAllocatorDestroy(T(mDataSlabThreeAxis));
    spiMasterRelease(T_SLAVE_INTERFACE(spiDev));
    return false;
}

/*
 * lsm6dsm_endTask: last function executed when App end
 */
static void lsm6dsm_endTask(void)
{
    TDECL();
    enum SensorIndex i;

#ifdef LSM6DSM_ACCEL_CALIB_ENABLED
    accelCalDestroy(&T(accelCal));
#endif /* LSM6DSM_ACCEL_CALIB_ENABLED */
#ifdef LSM6DSM_MAGN_CALIB_ENABLED
    destroy_mag_cal(&T(magnCal));
#endif /* LSM6DSM_MAGN_CALIB_ENABLED */
#ifdef LSM6DSM_GYRO_CALIB_ENABLED
    gyroCalDestroy(&T(gyroCal));
#endif /* LSM6DSM_GYRO_CALIB_ENABLED */

    lsm6dsm_disableInterrupt(T(int1), &T(isr1));
#ifdef LSM6DSM_I2C_MASTER_BAROMETER_ENABLED
    slabAllocatorDestroy(T(mDataSlabOneAxis));
#endif /* LSM6DSM_I2C_MASTER_BAROMETER_ENABLED */
    slabAllocatorDestroy(T(mDataSlabThreeAxis));
    spiMasterRelease(T_SLAVE_INTERFACE(spiDev));

    for (i = 0; i < NUM_SENSORS; i++)
        sensorUnregister(T(sensors[i]).handle);

    gpioRelease(T(int1));
}

INTERNAL_APP_INIT(LSM6DSM_APP_ID, 0, lsm6dsm_startTask, lsm6dsm_endTask, lsm6dsm_handleEvent);
