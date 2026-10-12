package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.StockMovementTypeEnum
import com.fiap.mechanicalhub.stock.domain.StockStatusEnum
import com.fiap.mechanicalhub.stock.domain.reserveStock
import com.fiap.mechanicalhub.stock.domain.sumByMaterial
import org.springframework.stereotype.Service

/** reserveStock: reserva o disponivel e grava o faltante como pendencia (transacao do processador). */
@Service
class ReserveStockUseCase(
    private val stock: StockRepository,
    private val locks: StockLocks,
    private val outbox: OutboxPublisher,
) {
    /** Retorna o envelope da resposta publicada no outbox. */
    fun execute(command: ReserveStockCommand): String {
        val required = stock.requiredMaterials(command.repairIds).sumByMaterial()
        locks.lockMaterials(required.map { it.materialId })
        var hasMissing = false
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
                hasMissing = true
                stock.insertPendingItem(
                    command.orderId,
                    command.transactionId,
                    requirement.materialId,
                    reservation.missing,
                )
            }
        }
        val event =
            if (hasMissing) {
                StockPending(command.transactionId, command.orderId)
            } else {
                StockReserved(command.transactionId, command.orderId, stock.reservedMaterials(command.orderId))
            }
        return outbox.publish(event)
    }
}
