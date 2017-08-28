/*
 * Copyright 2017 The Android Open Source Project
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

// #define LOG_NDEBUG 0
#define LOG_TAG "audio_utils_power"
#include <log/log.h>

#include <math.h>

#include <audio_utils/power.h>
#include <audio_utils/primitives.h>

#if defined(__aarch64__) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define USE_NEON
#endif

namespace {

constexpr inline bool isFormatSupported(audio_format_t format) {
    switch (format) {
    case AUDIO_FORMAT_PCM_8_BIT:
    case AUDIO_FORMAT_PCM_16_BIT:
    case AUDIO_FORMAT_PCM_24_BIT_PACKED:
    case AUDIO_FORMAT_PCM_8_24_BIT:
    case AUDIO_FORMAT_PCM_32_BIT:
    case AUDIO_FORMAT_PCM_FLOAT:
        return true;
    default:
        return false;
    }
}

template <typename T>
inline T getPtrPtrValueAndIncrement(const void **data)
{
    return *(*reinterpret_cast<const T **>(data))++;
}

template <audio_format_t FORMAT>
inline float convertToFloatAndIncrement(const void **data)
{
    switch (FORMAT) {
    case AUDIO_FORMAT_PCM_8_BIT:
        return float_from_u8(getPtrPtrValueAndIncrement<uint8_t>(data));

    case AUDIO_FORMAT_PCM_16_BIT:
        return float_from_i16(getPtrPtrValueAndIncrement<int16_t>(data));

    case AUDIO_FORMAT_PCM_24_BIT_PACKED: {
        const uint8_t *uptr = reinterpret_cast<const uint8_t *>(*data);
        *data = uptr + 3;
        return float_from_p24(uptr);
    }

    case AUDIO_FORMAT_PCM_8_24_BIT:
        return float_from_q8_23(getPtrPtrValueAndIncrement<int32_t>(data));

    case AUDIO_FORMAT_PCM_32_BIT:
        return float_from_i32(getPtrPtrValueAndIncrement<int32_t>(data));

    case AUDIO_FORMAT_PCM_FLOAT:
        return getPtrPtrValueAndIncrement<float>(data);

    default:
        // static_assert cannot use false because the compiler may interpret it
        // even though this code path may never be taken.
        static_assert(isFormatSupported(FORMAT), "unsupported format");
    }
}

// used to normalize integer fixed point value to the floating point equivalent.
template <audio_format_t FORMAT>
constexpr inline float normalizeAmplitude()
{
    switch (FORMAT) {
    case AUDIO_FORMAT_PCM_8_BIT:
        return 1.f / (1 << 7);

    case AUDIO_FORMAT_PCM_16_BIT:
        return 1.f / (1 << 15);

    case AUDIO_FORMAT_PCM_24_BIT_PACKED: // fall through
    case AUDIO_FORMAT_PCM_8_24_BIT:
        return 1.f / (1 << 23);

    case AUDIO_FORMAT_PCM_32_BIT:
        return 1.f / (1U << 31);

    case AUDIO_FORMAT_PCM_FLOAT:
         return 1.f;

    default:
        // static_assert cannot use false because the compiler may interpret it
        // even though this code path may never be taken.
        static_assert(isFormatSupported(FORMAT), "unsupported format");
    }
}

template <audio_format_t FORMAT>
constexpr inline float normalizeEnergy()
{
    const float val = normalizeAmplitude<FORMAT>();
    return val * val;
}

template <audio_format_t FORMAT>
inline float energyMonoRef(const void *amplitudes, size_t size)
{
    float accum(0.f);
    for (size_t i = 0; i < size; ++i) {
        const float amplitude = convertToFloatAndIncrement<FORMAT>(&amplitudes);
        accum += amplitude * amplitude;
    }
    return accum;
}

template <audio_format_t FORMAT>
inline float energyMono(const void *amplitudes, size_t size)
{
    return energyMonoRef<FORMAT>(amplitudes, size);
}

// fast float power computation for ARM processors that support NEON.
#ifdef USE_NEON

template <>
inline float energyMono<AUDIO_FORMAT_PCM_FLOAT>(const void *amplitudes, size_t size)
{
    float32x4_t *famplitudes = (float32x4_t *)amplitudes;

    // clear accumulator
    float32x4_t accum = vdupq_n_f32(0);

    // iterate over array getting sum of squares in 4 lanes.
    size_t i;
    for (i = 0; i < (size & ~3); i += 4) {
        accum = vmlaq_f32(accum, *famplitudes, *famplitudes);
        ++famplitudes;
    }

    // narrow 4 lanes of floats
    float32x2_t accum2 = vadd_f32(vget_low_f32(accum), vget_high_f32(accum)); // get stereo volume
    accum2 = vpadd_f32(accum2, accum2); // combine to mono

    // accumulate remainder
    float value = vget_lane_f32(accum2, 0);
    for (; i < size; ++i) {
        const float amplitude = ((float *)amplitudes)[i];
        value +=  amplitude * amplitude;
    }

    return value;
}

template <>
inline float energyMono<AUDIO_FORMAT_PCM_16_BIT>(const void *amplitudes, size_t size)
{
    int16x4_t *samplitudes = (int16x4_t *)amplitudes;

    // clear accumulator
    float32x4_t accum = vdupq_n_f32(0);

    // iterate over array getting sum of squares in 4 lanes.
    size_t i;
    for (i = 0; i < (size & ~3); i += 4) {
        // expand s16 to s32
        int32x4_t amplitude = vmovl_s16(*samplitudes);
        ++samplitudes;
        // convert s32 to f32
        float32x4_t famplitude = vcvtq_f32_s32(amplitude);
        accum = vmlaq_f32(accum, famplitude, famplitude);
    }

    // narrow 4 lanes of floats
    float32x2_t accum2 = vadd_f32(vget_low_f32(accum), vget_high_f32(accum)); // get stereo volume
    accum2 = vpadd_f32(accum2, accum2); // combine to mono

    // accumulate remainder
    float value = vget_lane_f32(accum2, 0);
    for (; i < size; ++i) {
        const float amplitude = (float)((int16_t *)amplitudes)[i];
        value +=  amplitude * amplitude;
    }

    return value * normalizeEnergy<AUDIO_FORMAT_PCM_16_BIT>();
}

// fast int32_t power computation for PCM_32
template <>
inline float energyMono<AUDIO_FORMAT_PCM_32_BIT>(const void *amplitudes, size_t size)
{
    int32x4_t *samplitudes = (int32x4_t *)amplitudes;

    // clear accumulator
    float32x4_t accum = vdupq_n_f32(0);

    // iterate over array getting sum of squares in 4 lanes.
    size_t i;
    for (i = 0; i < (size & ~3); i += 4) {
        // convert s32 to f32
        float32x4_t famplitude = vcvtq_f32_s32(*samplitudes);
        ++samplitudes;
        accum = vmlaq_f32(accum, famplitude, famplitude);
    }

    // narrow 4 lanes of floats
    float32x2_t accum2 = vadd_f32(vget_low_f32(accum), vget_high_f32(accum)); // get stereo volume
    accum2 = vpadd_f32(accum2, accum2); // combine to mono

    // accumulate remainder
    float value = vget_lane_f32(accum2, 0);
    for (; i < size; ++i) {
        const float amplitude = (float)((int32_t *)amplitudes)[i];
        value +=  amplitude * amplitude;
    }

    return value * normalizeEnergy<AUDIO_FORMAT_PCM_32_BIT>();
}

// fast int32_t power computation for PCM_8_24 (essentially identical to PCM_32 above)
template <>
inline float energyMono<AUDIO_FORMAT_PCM_8_24_BIT>(const void *amplitudes, size_t size)
{
    int32x4_t *samplitudes = (int32x4_t *)amplitudes;

    // clear accumulator
    float32x4_t accum = vdupq_n_f32(0);

    // iterate over array getting sum of squares in 4 lanes.
    size_t i;
    for (i = 0; i < (size & ~3); i += 4) {
        // convert s32 to f32
        float32x4_t famplitude = vcvtq_f32_s32(*samplitudes);
        ++samplitudes;
        accum = vmlaq_f32(accum, famplitude, famplitude);
    }

    // narrow 4 lanes of floats
    float32x2_t accum2 = vadd_f32(vget_low_f32(accum), vget_high_f32(accum)); // get stereo volume
    accum2 = vpadd_f32(accum2, accum2); // combine to mono

    // accumulate remainder
    float value = vget_lane_f32(accum2, 0);
    for (; i < size; ++i) {
        const float amplitude = (float)((int32_t *)amplitudes)[i];
        value +=  amplitude * amplitude;
    }

    return value * normalizeEnergy<AUDIO_FORMAT_PCM_8_24_BIT>();
}

#endif // USE_NEON

} // namespace

float audio_utils_compute_energy_mono(const void *buffer, audio_format_t format, size_t samples)
{
    switch (format) {
    case AUDIO_FORMAT_PCM_8_BIT:
        return energyMono<AUDIO_FORMAT_PCM_8_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_16_BIT:
        return energyMono<AUDIO_FORMAT_PCM_16_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_24_BIT_PACKED:
        return energyMono<AUDIO_FORMAT_PCM_24_BIT_PACKED>(buffer, samples);

    case AUDIO_FORMAT_PCM_8_24_BIT:
        return energyMono<AUDIO_FORMAT_PCM_8_24_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_32_BIT:
        return energyMono<AUDIO_FORMAT_PCM_32_BIT>(buffer, samples);

    case AUDIO_FORMAT_PCM_FLOAT:
        return energyMono<AUDIO_FORMAT_PCM_FLOAT>(buffer, samples);

    default:
        LOG_ALWAYS_FATAL("invalid format: %#x", format);
    }
}

float audio_utils_compute_power_mono(const void *buffer, audio_format_t format, size_t samples)
{
    return audio_utils_power_from_energy(
            audio_utils_compute_energy_mono(buffer, format, samples) / samples);
}

bool audio_utils_is_compute_power_format_supported(audio_format_t format)
{
    return isFormatSupported(format);
}

