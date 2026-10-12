package com.fiap.mechanicalhub.stock.infrastructure

import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID
import javax.sql.DataSource

@Configuration
@EnableScheduling
class SchedulingConfig

data class OutboxRow(val sequence: Long, val eventId: UUID, val orderId: UUID, val payload: String)

/** Envio de um evento ao SQS; falha lanca excecao. */
fun interface OutboxSender {
    fun send(row: OutboxRow)
}

@Component
class SqsOutboxSender(
    private val sqs: SqsTemplate,
    @Value("\${stock.queues.orchestrator-events}") private val queue: String,
) : OutboxSender {
    override fun send(row: OutboxRow) {
        sqs.send<String> {
            it
                .queue(queue)
                .payload(row.payload)
                .messageGroupId(row.orderId.toString())
                .messageDeduplicationId(row.eventId.toString())
        }
    }
}

/**
 * Relay do outbox: a cada 1 s, lote de ate 100 eventos PENDING por sequence. Apenas uma instancia
 * atua por vez (pg_try_advisory_lock). Erro de envio interrompe o lote e incrementa attempts.
 */
@Component
@ConditionalOnProperty("stock.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
class OutboxRelay(
    private val jdbc: JdbcTemplate,
    private val dataSource: DataSource,
    private val sender: OutboxSender,
    @Value("\${stock.outbox.relay.batch-size:100}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    @Scheduled(fixedDelayString = "\${stock.outbox.relay.interval-ms:1000}")
    fun relay(): Int {
        dataSource.connection.use { leader ->
            val acquired =
                leader.prepareStatement("SELECT pg_try_advisory_lock(?)").use { st ->
                    st.setLong(1, LEADER_LOCK_KEY)
                    st.executeQuery().use { rs -> rs.next() && rs.getBoolean(1) }
                }
            if (!acquired) return 0
            try {
                return sendBatch()
            } finally {
                leader.prepareStatement("SELECT pg_advisory_unlock(?)").use { st ->
                    st.setLong(1, LEADER_LOCK_KEY)
                    st.execute()
                }
            }
        }
    }

    private fun sendBatch(): Int {
        val rows =
            jdbc.query(
                "SELECT sequence, event_id, aggregate_id, payload::text AS payload FROM outbox_events " +
                    "WHERE status = 'PENDING' ORDER BY sequence LIMIT ?",
                { rs, _ ->
                    OutboxRow(
                        rs.getLong("sequence"),
                        rs.getObject("event_id", UUID::class.java),
                        rs.getObject("aggregate_id", UUID::class.java),
                        rs.getString("payload"),
                    )
                },
                batchSize,
            )
        var sent = 0
        for (row in rows) {
            try {
                sender.send(row)
            } catch (
                @Suppress("TooGenericExceptionCaught") ex: Exception,
            ) {
                log.warn("Falha ao enviar evento {} do outbox", row.eventId, ex)
                jdbc.update(
                    "UPDATE outbox_events SET attempts = attempts + 1, last_error = ? WHERE sequence = ?",
                    ex.message ?: ex.javaClass.name,
                    row.sequence,
                )
                break
            }
            jdbc.update(
                "UPDATE outbox_events SET status = 'SENT', sent_at = now() WHERE sequence = ?",
                row.sequence,
            )
            sent++
        }
        return sent
    }

    companion object {
        const val LEADER_LOCK_KEY = 7_304_201L
    }
}
