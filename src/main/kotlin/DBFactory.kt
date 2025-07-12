package com.sdhs

import com.sdhs.models.LightStatus
import com.sdhs.models.TemperatureStatus
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DBFactory {
    fun init() {
        val yamlConfig = loadYamlConfig()
        val hikariConfig = HikariConfig().apply {
            this.driverClassName = yamlConfig.database.driver
            this.jdbcUrl = yamlConfig.database.url
            this.username = yamlConfig.database.username
            this.password = yamlConfig.database.password
            this.maximumPoolSize = 3
            this.isAutoCommit = false
            this.transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        transaction {
            SchemaUtils.create(LightStatus)
            SchemaUtils.create(TemperatureStatus)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}