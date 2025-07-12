package com.sdhs.route

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

fun Route.temperature() {
    route("/temperature") {

    }
}