package com.sdhs.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MqttResponseModel(
    @SerialName("wodnjsqkqh_isLightOn") val isLightOn: String,
    @SerialName("wodnjsqkqh_temperature") val temperature: String,
    @SerialName("wodnjsqkqh_classroom") val classroom: String,
)