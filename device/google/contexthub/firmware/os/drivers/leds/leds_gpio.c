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

#include <stdlib.h>
#include <string.h>

#include <eventnums.h>
#include <gpio.h>
#include <hostIntf.h>
#include <leds_gpio.h>
#include <nanohubPacket.h>
#include <sensors.h>
#include <seos.h>
#include <plat/gpio.h>
#include <plat/exti.h>
#include <plat/syscfg.h>
#include <variant/variant.h>

#define LEDS_GPIO_APP_ID	APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 20)
#define LEDS_GPIO_APP_VERSION	1

#define DBG_ENABLE		0

static struct LedsTask
{
    struct Gpio *led[LEDS_GPIO_MAX];
    uint32_t num;

    uint32_t id;
    uint32_t sHandle;
} mTask;

static bool sensorConfigLedsGpio(void *cfgData, void *buf)
{
    struct LedsCfg *lcfg = (struct LedsCfg *)cfgData;

    if (lcfg->led_num >= mTask.num) {
        osLog(LOG_INFO, "Wrong led number %"PRIu32"\n", lcfg->led_num);
        return false;
    }
    gpioSet(mTask.led[lcfg->led_num], lcfg->value ? 1 : 0);
    osLog(LOG_INFO, "Set led[%"PRIu32"]=%"PRIu32"\n", lcfg->led_num, lcfg->value);
    return true;
}

static bool sensorSelfTestLedsGpio(void *buf)
{
    uint32_t i;

    gpioSet(mTask.led[0], 1);
    for (i=1; i < mTask.num; i++) {
        gpioSet(mTask.led[i-1], 0);
        gpioSet(mTask.led[i], 1);
    }
    gpioSet(mTask.led[i-1], 0);
    return true;
}

static const struct SensorInfo sensorInfoLedsGpio = {
    .sensorName = "Leds-Gpio",
    .sensorType = SENS_TYPE_LEDS,
};

static const struct SensorOps sensorOpsLedsGpio = {
    .sensorCfgData = sensorConfigLedsGpio,
    .sensorSelfTest = sensorSelfTestLedsGpio,
};

static void handleEvent(uint32_t evtType, const void *evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        osEventUnsubscribe(mTask.id, EVT_APP_START);
        /* Test leds */
        if (DBG_ENABLE)
            sensorSelfTest(mTask.sHandle);
        osLog(LOG_INFO, "[Leds-Gpio] detected\n");
        break;
    }
}

static bool startTask(uint32_t taskId)
{
    const struct LedsGpio *leds;
    struct Gpio *led;
    uint32_t i;

    mTask.id = taskId;
    mTask.num = 0;

    leds = ledsGpioBoardCfg();
    for (i=0; i < leds->num && i < LEDS_GPIO_MAX; i++) {
        led = gpioRequest(leds->leds_array[i]);
        if (!led)
            continue;
        mTask.led[mTask.num++] = led;
        gpioConfigOutput(led, GPIO_SPEED_LOW, GPIO_PULL_NONE, GPIO_OUT_PUSH_PULL, 0);
    }
    if (mTask.num == 0)
        return false;
    mTask.sHandle = sensorRegister(&sensorInfoLedsGpio, &sensorOpsLedsGpio, NULL, true);
    osEventSubscribe(taskId, EVT_APP_START);
    return true;
}

static void endTask(void)
{
    uint32_t i;

    sensorUnregister(mTask.sHandle);
    for (i=0; i < mTask.num; i++) {
        gpioSet(mTask.led[i], 0);
        gpioRelease(mTask.led[i]);
    }
}

INTERNAL_APP_INIT(LEDS_GPIO_APP_ID, LEDS_GPIO_APP_VERSION, startTask, endTask, handleEvent);
