package com.sdhs

import com.sdhs.route.light
import com.sdhs.route.temperature
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        light()
        temperature()
    }
}