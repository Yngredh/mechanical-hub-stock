package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.StockMovementTypeEnum
import com.fiap.mechanicalhub.stock.domain.StockStatusEnum
import com.fiap.mechanicalhub.stock.domain.reserveStock
import com.fiap.mechanicalhub.stock.domain.sumByMaterial
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** reserveStock: reserva o disponivel e grava o faltante como pendencia (transacao do processador). */
@Service
class ReserveStockUseCase(
    private val stock: StockRepository,
    private val locks: StockLocks,
    private val outbox: OutboxPublisher,
    private val notifier: StockShortageNotifier,
) {
    private val log = LoggerFactory.getLogger(ReserveStockUseCase::class.java)

    /** Retorna o envelope da resposta publicada no outbox. */
    fun execute(command: ReserveStockCommand): String {
        val required = stock.requiredMaterials(command.repairIds).sumByMaterial()
        locks.lockMaterials(required.map { it.materialId })
        val shortages = mutableListOf<ShortageItem>()
        required.forEach { requirement ->
            val available = stock.quantity(requirement.materialId, StockStatusEnum.AVAILABLE)
            val reservation = reserveStock(requirement, available)
            if (reservation.reserved > 0) {
                stock.addQuantity(requirement.materialId, StockStatusEnum.AVAILABLE, -reservation.reserved)
                stock.addQuantity(requirement.materialId, StockStatusEnum.RESERVED, reservation.reserved)
                stock.recordMovement(
                    requirement.materialId,
                    command.orderId,
                    StockMovementTypeEnum.RESERVED,
                    reservation.reserved,
                )
            }
            if (reservation.missing > 0) {
                shortages +=
                    ShortageItem(
                        requirement.materialId,
                        stock.materialName(requirement.materialId),
                        reservation.missing,
                    )
                stock.insertPendingItem(
                    command.orderId,
                    command.transactionId,
                    requirement.materialId,
                    reservation.missing,
                )
            }
        }
        val event =
            if (shortages.isNotEmpty()) {
                StockPending(command.transactionId, command.orderId)
            } else {
                StockReserved(command.transactionId, command.orderId, stock.reservedMaterials(command.orderId))
            }
        val response = outbox.publish(event)
        if (shortages.isNotEmpty()) notifyAfterCommit(command, shortages)
        return response
    }

    /** Alerta aos administradores so depois do COMMIT; falha no envio gera apenas log. */
    @Suppress("TooGenericExceptionCaught")
    private fun notifyAfterCommit(command: ReserveStockCommand, shortages: List<ShortageItem>) {
        val send = {
            try {
                notifier.notifyShortage(command.orderId, command.transactionId, shortages)
            } catch (e: Exception) {
                log.error("Falha ao enviar o alerta de falta de estoque da ordem {}", command.orderId, e)
            }
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = send()
                },
            )
        } else {
            send()
        }
    }
}
