package com.fiap.mechanicalhub.stock.infrastructure

import com.fiap.mechanicalhub.stock.application.ShortageItem
import com.fiap.mechanicalhub.stock.application.StockShortageNotifier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/** Adapter de alerta: igual ao EmailSender do monolito, registra o email em log. */
@Component
class LogStockShortageNotifier : StockShortageNotifier {
    private val log = LoggerFactory.getLogger(LogStockShortageNotifier::class.java)

    override fun notifyShortage(orderId: UUID, transactionId: UUID, shortages: List<ShortageItem>) {
        val items = shortages.joinToString("; ") { "${it.materialName} (faltam ${it.missingQuantity})" }
        withLogContext(
            LogContext.ORDER_ID to orderId.toString(),
            LogContext.TRANSACTION_ID to transactionId.toString(),
            LogContext.MATERIAL_ID to shortages.joinToString(",") { it.materialId.toString() },
        ) {
            log.info(
                "Enviando email com assunto: [ALERTA ESTOQUE] Pendencia de estoque na ordem {} e corpo: " +
                    "sem disponibilidade para os materiais: {}. Providencie a reposicao.",
                orderId,
                items,
            )
        }
    }
}
