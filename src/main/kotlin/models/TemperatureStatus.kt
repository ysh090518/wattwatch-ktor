package com.sdhs.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Clock
import java.time.Instant

object TemperatureStatus : Table("temperature_status") {
    val temperature = integer("temperature")
    val classroom = char("classroom", 4)
    val timestamp = timestamp("timestamp")
}