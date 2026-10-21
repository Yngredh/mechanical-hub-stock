package com.fiap.mechanicalhub.stock

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("integration")
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StockApiTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private val http = HttpClient.newHttpClient()

    private fun newMaterial(available: Int): UUID {
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO materials (id, name) VALUES (?, ?)", id, "Material $id")
        jdbc.update("INSERT INTO stock (material_id, status, quantity) VALUES (?, 'AVAILABLE', ?)", id, available)
        jdbc.update("INSERT INTO stock (material_id, status, quantity) VALUES (?, 'RESERVED', 0)", id)
        return id
    }

    private fun available(materialId: UUID): Int =
        jdbc.queryForObject("SELECT quantity FROM stock WHERE material_id = ? AND status = 'AVAILABLE'", Int::class.java, materialId)!!

    private fun send(method: String, path: String, body: String? = null, key: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type", "application/json")
        key?.let { builder.header("Idempotency-Key", it) }
        builder.method(method, body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun entry(materialId: UUID, quantity: Int, key: String?) =
        send("POST", "/stock/entry", """{"materialId":"$materialId","quantity":$quantity}""", key)

    @Test
    fun `given a material when registering an entry then it adds to available and records the movement`() {
        val materialId = newMaterial(available = 2)

        val response = entry(materialId, 5, UUID.randomUUID().toString())

        assertEquals(201, response.statusCode())
        assertEquals(7, available(materialId))
        assertTrue(send("GET", "/stock/$materialId/movements").body().contains("ENTRY"))
    }

    @Test
    fun `given the same key and body when registering twice then the entry is applied once`() {
        val materialId = newMaterial(available = 0)
        val key = UUID.randomUUID().toString()

        val first = entry(materialId, 4, key)
        val second = entry(materialId, 4, key)

        assertEquals(first.body(), second.body())
        assertEquals(4, available(materialId))
    }

    @Test
    fun `given a used key when sending another body then it answers 422`() {
        val materialId = newMaterial(available = 0)
        val key = UUID.randomUUID().toString()
        entry(materialId, 4, key)

        val response = entry(materialId, 9, key)

        assertEquals(422, response.statusCode())
        assertEquals(4, available(materialId))
    }

    @Test
    fun `given no idempotency key when registering an entry then it answers 400`() {
        val materialId = newMaterial(available = 0)

        val response = entry(materialId, 1, null)

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `given an invalid quantity when registering an entry then it answers 400`() {
        val materialId = newMaterial(available = 0)

        val response = entry(materialId, 0, UUID.randomUUID().toString())

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `given an unknown material when registering an entry then it answers 404`() {
        val response = entry(UUID.randomUUID(), 1, UUID.randomUUID().toString())

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `given pending items when registering an entry then they are resolved by age and orders are reported`() {
        val materialId = newMaterial(available = 0)
        val firstOrder = UUID.randomUUID()
        val secondOrder = UUID.randomUUID()
        listOf(firstOrder to 3, secondOrder to 4).forEachIndexed { index, (orderId, quantity) ->
            jdbc.update(
                "INSERT INTO stock_pending_items (service_order_id, saga_transaction_id, material_id, quantity, created_at) " +
                    "VALUES (?, ?, ?, ?, now() + (? * interval '1 second'))",
                orderId,
                UUID.randomUUID(),
                materialId,
                quantity,
                index,
            )
        }

        val response = entry(materialId, 5, UUID.randomUUID().toString())

        assertEquals(201, response.statusCode())
        assertTrue(response.body().contains(firstOrder.toString()))
        val events = jdbc.queryForList("SELECT event_type, payload::text AS payload FROM outbox_events WHERE aggregate_id = ?", firstOrder)
        assertEquals(1, events.size)
        assertEquals("stockReserved", events[0]["event_type"])
        assertTrue((events[0]["payload"] as String).contains(materialId.toString()))
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Int::class.java, secondOrder))
        assertEquals(0, available(materialId))
        assertEquals(
            2,
            jdbc.queryForObject("SELECT quantity FROM stock_pending_items WHERE service_order_id = ?", Int::class.java, secondOrder),
        )
        assertEquals(
            5,
            jdbc.queryForObject("SELECT quantity FROM stock WHERE material_id = ? AND status = 'RESERVED'", Int::class.java, materialId),
        )
    }

    @Test
    fun `given the seeded catalog when listing balances then it returns available and reserved`() {
        val response = send("GET", "/stock")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("available"))
        assertTrue(response.body().contains("reserved"))
    }

    @Test
    fun `given an unknown material when listing movements then it answers 404`() {
        val response = send("GET", "/stock/${UUID.randomUUID()}/movements")

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `given the application when requesting the api docs then swagger is served`() {
        val response = send("GET", "/v3/api-docs")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("/stock/entry"))
    }
}
