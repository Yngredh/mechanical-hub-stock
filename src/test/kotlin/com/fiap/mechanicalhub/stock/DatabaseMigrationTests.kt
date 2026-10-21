package com.fiap.mechanicalhub.stock

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

// As outras classes de integracao compartilham o mesmo contexto Spring (cacheado)
// e, com ele, o mesmo container PostgreSQL, onde gravam dados. Esta classe confere
// o estado logo apos as migrations, entao precisa de um banco limpo: a property
// distinta gera um contexto proprio e, portanto, um container novo, independente
// da ordem em que o Surefire executa as classes.
@Tag("integration")
@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["stock.test.context=database-migration"])
class DatabaseMigrationTests {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun count(sql: String): Int = jdbc.queryForObject(sql, Int::class.java)!!

    @Test
    fun `given the migrations when applied then the seed catalog is loaded`() {
        assertEquals(10, count("SELECT count(*) FROM materials"))
        assertEquals(10, count("SELECT count(*) FROM stock WHERE status = 'AVAILABLE'"))
        assertEquals(10, count("SELECT count(*) FROM stock WHERE status = 'RESERVED' AND quantity = 0"))
        assertEquals(10, count("SELECT count(*) FROM service_materials"))
        assertEquals(2, count("SELECT count(*) FROM stock WHERE status = 'AVAILABLE' AND quantity = 0"))
    }

    @Test
    fun `given the migrations when applied then the control and movement tables exist and are empty`() {
        listOf("outbox_events", "inbox_events", "idempotency_requests", "stock_movements", "stock_pending_items")
            .forEach { assertEquals(0, count("SELECT count(*) FROM $it"), it) }
    }
}
