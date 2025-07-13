package com.sdhs

import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.sdhs.models.LightStatus
import com.sdhs.models.MqttResponseModel
import com.sdhs.models.TemperatureStatus
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.jetbrains.exposed.sql.insert
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant

private val logger = KotlinLogging.logger {}

fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080, module = Application::module).start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    install(CORS) {
        anyHost()

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeaders { true }

        allowCredentials = true
        allowNonSimpleContentTypes = true

    }
    DBFactory.init()

    subscribeMqttSensorLux(this)

    configureRouting()
}

val format = Json {
    prettyPrint = true
}

fun subscribeMqttSensorLux(scope: CoroutineScope) {
    val client = getMqttClient()
    client.connect().whenComplete { _, error ->
        if (error != null) {
            logger.error("MQTT 연결 실패: ${error.message}")
        } else {
            logger.info("MQTT 연결 성공")

            client.subscribeWith()
                .topicFilter("home")
                .qos(MqttQos.AT_LEAST_ONCE)
                .callback { publish ->
                    scope.launch {
                        val payload = publish.payload
                            .map { StandardCharsets.UTF_8.decode(it).toString() }
                            .orElse("no payload")

                        val json = format.decodeFromString<MqttResponseModel>(payload)
                        val now = Instant.now(Clock.systemDefaultZone())
                        logger.info("json $json / $now")

                        DBFactory.dbQuery {
                            LightStatus.insert {
                                it[timestamp] = now
                                it[isOn] = when (json.isLightOn) {
                                    "1" -> true
                                    else -> false
                                }
                                it[classroom] = json.classroom
                            }
                            val temperatureData = json.temperature.toIntOrNull() ?: return@dbQuery
                            TemperatureStatus.insert {
                                it[timestamp] = now
                                it[temperature] = temperatureData
                                it[classroom] = json.classroom
                            }
                        }
                    }
                }
                .send()
        }
    }
}

fun getMqttClient(): Mqtt5AsyncClient = MqttClient.builder()
    .useMqttVersion5()
    .serverHost("222.110.147.50")
    .serverPort(1884)
    .buildAsync()