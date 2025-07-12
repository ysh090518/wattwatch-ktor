package com.sdhs.route

import com.sdhs.models.LightStatus
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
private data class LightResponse(val isOn: Boolean, val classroom: String, val timestamp: String)

fun Route.light() {
    val (start, end) = getTodayRange()
    route("/light/daily") {
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
                totalSeconds.seconds * 18 / 30
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

fun getTodayRange(): Pair<Instant, Instant> {
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now(zoneId)

    val startOfDay = today.atStartOfDay(zoneId).toInstant()
    val endOfDay = today.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1)

    return startOfDay to endOfDay
}

private fun getBetween(start: Instant, end: Instant, classroom: String): List<LightResponse> {
    return transaction {
        LightStatus.select {
            (LightStatus.timestamp greaterEq start) and (LightStatus.timestamp less end) and (LightStatus.classroom like "%$classroom%")
        }
            .orderBy(LightStatus.timestamp, SortOrder.ASC)
            .map { row ->
                LightResponse(
                    isOn = row[LightStatus.isOn],
                    classroom = row[LightStatus.classroom],
                    timestamp = row[LightStatus.timestamp].toString(),
                )
            }
    }
}

@Serializable
data class TotalTimeModel(
    val seconds: Long,
    val minutes: Long,
)

private fun calculateDurations(
    events: List<LightResponse>,
): TotalTimeModel {

    if (events.size <= 1) return TotalTimeModel(0, 0)

    var totalOnTime = Duration.ZERO

    events.windowed(2) { (currentEvent, nextEvent) ->
        val currentEventTimeStamp = Instant.parse(currentEvent.timestamp)
        val nextEventTimeStamp = Instant.parse(nextEvent.timestamp)
        val duration = Duration.between(currentEventTimeStamp, nextEventTimeStamp)
        if (currentEvent.isOn) {
            totalOnTime = totalOnTime.plus(duration)
        }
    }

    return TotalTimeModel(totalOnTime.seconds, totalOnTime.toMinutes())
}