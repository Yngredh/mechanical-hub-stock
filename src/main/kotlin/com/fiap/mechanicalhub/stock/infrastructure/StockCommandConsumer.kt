package com.fiap.mechanicalhub.stock.infrastructure

import com.fiap.mechanicalhub.stock.application.BusinessRuleException
import com.fiap.mechanicalhub.stock.application.InvalidCommandException
import com.fiap.mechanicalhub.stock.application.StockCommandProcessor
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID

/** Mensagem recebida da fila de comandos, independente do cliente SQS. */
data class ReceivedCommand(
    val body: String,
    val messageId: String,
    val groupId: String,
    val receiveCount: Int,
    val attributes: Map<String, String> = emptyMap(),
)

/** Envio de uma mensagem com erro permanente a DLQ de comandos. */
fun interface DeadLetterSender {
    fun send(body: String, groupId: String, deduplicationId: String, attributes: Map<String, String>)
}

@Component
class SqsDeadLetterSender(
    private val sqs: SqsTemplate,
    @Value("\${stock.queues.commands-dlq}") private val queue: String,
) : DeadLetterSender {
    override fun send(body: String, groupId: String, deduplicationId: String, attributes: Map<String, String>) {
        sqs.send<String> { to ->
            to
                .queue(queue)
                .payload(body)
                .messageGroupId(groupId)
                .messageDeduplicationId(deduplicationId)
            attributes.forEach { (name, value) -> to.header(name, value) }
        }
    }
}

/**
 * Consome comandos com retry bloqueante: erro transitorio nao apaga a mensagem e ajusta o visibility timeout pela
 * ApproximateReceiveCount (1 s a 8 s); erro permanente vai a DLQ e a mensagem e apagada. A mensagem so e apagada
 * depois do COMMIT da transacao do processador.
 */
@Component
class StockCommandConsumer(
    private val parser: StockCommandParser,
    private val processor: StockCommandProcessor,
    private val deadLetters: DeadLetterSender,
) {
    private val log = LoggerFactory.getLogger(StockCommandConsumer::class.java)

    @Suppress("TooGenericExceptionCaught")
    fun consume(message: ReceivedCommand, acknowledge: () -> Unit, changeVisibility: (Int) -> Unit) {
        try {
            val command = parser.parse(message.body)
            withLogContext(
                LogContext.ORDER_ID to command.orderId.toString(),
                LogContext.TRANSACTION_ID to command.transactionId.toString(),
                LogContext.EVENT_ID to parser.eventIdOrNull(message.body),
            ) {
                val outcome = processor.process(command)
                log.info("Comando {} processado: {}", message.messageId, outcome)
            }
        } catch (e: Exception) {
            if (e.isPermanent()) {
                deadLetter(message, e, acknowledge, changeVisibility)
            } else {
                retryLater(message, e, changeVisibility)
            }
            return
        }
        acknowledge()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun deadLetter(
        message: ReceivedCommand,
        error: Exception,
        acknowledge: () -> Unit,
        changeVisibility: (Int) -> Unit,
    ) {
        log.error("Erro permanente no comando {}; enviando a DLQ", message.messageId, error)
        try {
            deadLetters.send(
                message.body,
                message.groupId,
                parser.eventIdOrNull(message.body) ?: message.messageId.ifBlank { UUID.randomUUID().toString() },
                message.attributes.entries
                    .take(MAX_COPIED_ATTRIBUTES)
                    .associate { it.key to it.value } +
                    mapOf(
                        "errorClass" to error.javaClass.name,
                        "errorMessage" to (error.message ?: "").take(MAX_ERROR_MESSAGE),
                    ),
            )
        } catch (e: Exception) {
            log.error("Falha ao enviar o comando {} a DLQ; tentara de novo", message.messageId, e)
            changeVisibility(backoffSeconds(message.receiveCount))
            return
        }
        acknowledge()
    }

    private fun retryLater(message: ReceivedCommand, error: Exception, changeVisibility: (Int) -> Unit) {
        val seconds = backoffSeconds(message.receiveCount)
        log.warn(
            "Erro transitorio no comando {} (tentativa {}); nova tentativa em {}s",
            message.messageId,
            message.receiveCount,
            seconds,
            error,
        )
        changeVisibility(seconds)
    }

    private fun Exception.isPermanent() = this is InvalidCommandException || this is BusinessRuleException

    companion object {
        private val BACKOFF_SECONDS = listOf(1, 2, 4, 8, 8)
        private const val MAX_COPIED_ATTRIBUTES = 8
        private const val MAX_ERROR_MESSAGE = 256

        /** Visibility timeout da proxima tentativa: 1 s, 2 s, 4 s, 8 s, 8 s conforme o numero de recebimentos. */
        fun backoffSeconds(receiveCount: Int): Int =
            BACKOFF_SECONDS[(receiveCount - 1).coerceIn(0, BACKOFF_SECONDS.lastIndex)]
    }
}
