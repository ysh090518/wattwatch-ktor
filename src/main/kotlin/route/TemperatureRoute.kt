package com.sdhs.route

import com.sdhs.models.TemperatureStatus
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Serializable
private data class TempResponse(val temperature: Int, val classroom: String, val timestamp: String)

fun Route.temperature() {
    val (start, end) = getTodayRange()
    route("/temperature/daily") {
        get("/all") {
            val classroom = call.request.queryParameters["classroom"] ?: return@get
            val list = getBetween(start, end, classroom)
            val totalSeconds = calculateDurations(list)

            call.respond(mapOf("total_minutes" to totalSeconds.minutes))
        }

        get("/graph") {
            val classroom = call.request.queryParameters["classroom"] ?: return@get
            val list = getBetween(start, end, classroom)

            val groupByList =
                list.groupBy { Instant.parse(it.timestamp).atZone(ZoneId.systemDefault()).hour / 3 }

            val results = groupByList.map {
                val (_, list) = it
                val totalSeconds = calculateDurations(list)
                totalSeconds.seconds * 2 * 4.7 / 3600
            }

            call.respond(
                List(8) {
                    results.getOrNull(it)
                },
            )
        }
        get("/hours") {
            val classroom = call.request.queryParameters["classroom"] ?: return@get
            val list = getBetween(start, end, classroom)

            val groupByList =
                list.groupBy { Instant.parse(it.timestamp).atZone(ZoneId.systemDefault()).hour }

            val results = groupByList.map {
                val (_, list) = it
                val totalSeconds = calculateDurations(list)
                totalSeconds.seconds > 1800
            }
            val nullableResults = List(24) {
                results.getOrNull(it) ?: false
            }

            val am = nullableResults.take(12)
            val pm = nullableResults.drop(12)

            call.respond(mapOf("am" to am, "pm" to pm))
        }
    }
}

private fun getBetween(start: Instant, end: Instant, classroom: String): List<TempResponse> {
    return transaction {
        TemperatureStatus.select {
            (TemperatureStatus.timestamp greaterEq start) and (TemperatureStatus.timestamp less end) and (TemperatureStatus.classroom like "%$classroom%")
        }
            .orderBy(TemperatureStatus.timestamp, SortOrder.ASC)
            .map { row ->
                TempResponse(
                    temperature = row[TemperatureStatus.temperature],
                    classroom = row[TemperatureStatus.classroom],
                    timestamp = row[TemperatureStatus.timestamp].toString(),
                )
            }
    }
}

private fun calculateDurations(
    events: List<TempResponse>,
): TotalTimeModel {

    if (events.size <= 1) return TotalTimeModel(0, 0)

    var totalOnTime = Duration.ZERO

    events.windowed(2) { (currentEvent, nextEvent) ->
        val currentEventTimeStamp = Instant.parse(currentEvent.timestamp)
        val nextEventTimeStamp = Instant.parse(nextEvent.timestamp)
        val duration = Duration.between(currentEventTimeStamp, nextEventTimeStamp)
        if (currentEvent.temperature<=25) {
            totalOnTime = totalOnTime.plus(duration)
        }
    }

    return TotalTimeModel(totalOnTime.seconds, totalOnTime.toMinutes())
}

