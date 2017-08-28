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

#ifndef BTIF_AV_CO_H
#define BTIF_AV_CO_H

#include "btif/include/btif_a2dp_source.h"
#include "stack/include/a2dp_codec_api.h"

#ifdef __cplusplus
extern "C" {
#endif

// Gets the A2DP peer parameters that are used to initialize the encoder.
// The parameters are stored in |p_peer_params|.
// |p_peer_params| cannot be null.
void bta_av_co_get_peer_params(tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params);

// Gets the current A2DP encoder interface that can be used to encode and
// prepare A2DP packets for transmission - see |tA2DP_ENCODER_INTERFACE|.
// Returns the A2DP encoder interface if the current codec is setup,
// otherwise NULL.
const tA2DP_ENCODER_INTERFACE* bta_av_co_get_encoder_interface(void);

// Sets the user preferred codec configuration.
// |codec_user_config| contains the preferred codec configuration.
// Returns true on success, otherwise false.
bool bta_av_co_set_codec_user_config(
    const btav_a2dp_codec_config_t& codec_user_config);

// Sets the Audio HAL selected audio feeding parameters.
// Those parameters are applied only to the currently selected codec.
// |codec_audio_config| contains the selected audio feeding configuration.
// Returns true on success, otherwise false.
bool bta_av_co_set_codec_audio_config(
    const btav_a2dp_codec_config_t& codec_audio_config);

// Initializes the control block.
// |codec_priorities| contains the A2DP Source codec priorities to use.
void bta_av_co_init(
    const std::vector<btav_a2dp_codec_config_t>& codec_priorities);

// Gets the initialized A2DP codecs.
// Returns a pointer to the |A2dpCodecs| object with the initialized A2DP
// codecs, or nullptr if no codecs are initialized.
A2dpCodecs* bta_av_get_a2dp_codecs(void);

// Gets the current A2DP codec.
// Returns a pointer to the current |A2dpCodec| if valid, otherwise nullptr.
A2dpCodecConfig* bta_av_get_a2dp_current_codec(void);

#ifdef __cplusplus
}
#endif

#endif  // BTIF_AV_CO_H
