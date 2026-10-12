package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.MaterialQuantity
import java.util.UUID

/** Violacao de regra de negocio (ex.: RESERVED insuficiente): erro permanente, vai direto a DLQ. */
class BusinessRuleException(message: String) : RuntimeException(message)

/** Mensagem com JSON ou contrato invalido: erro permanente, vai direto a DLQ. */
class InvalidCommandException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Comando recebido de mechanical-hub-stock-commands.fifo. */
sealed interface StockCommand {
    /** Chave de deduplicacao no inbox: {transactionId}:{STEP}. */
    val idempotencyKey: String
    val transactionId: UUID
    val orderId: UUID
}

data class ReserveStockCommand(
    override val idempotencyKey: String,
    override val transactionId: UUID,
    override val orderId: UUID,
    val repairIds: List<UUID>,
) : StockCommand

data class ConsumeStockCommand(
    override val idempotencyKey: String,
    override val transactionId: UUID,
    override val orderId: UUID,
    val reservedMaterials: List<MaterialQuantity>,
) : StockCommand

data class RestoreStockCommand(
    override val idempotencyKey: String,
    override val transactionId: UUID,
    override val orderId: UUID,
    val reservedMaterials: List<MaterialQuantity>,
) : StockCommand

/** Porta de inbox_events (deduplicacao de comandos). Exige transacao ativa. */
interface InboxStore {
    /** Registra a chave; false se ja existia (duplicata). Espera o commit de quem tem a mesma chave. */
    fun claim(key: String): Boolean

    fun saveResponse(key: String, responsePayload: String)

    fun responsePayload(key: String): String?
}
