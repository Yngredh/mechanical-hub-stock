package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.StockMovementTypeEnum
import com.fiap.mechanicalhub.stock.domain.StockStatusEnum
import com.fiap.mechanicalhub.stock.domain.resolvePendingItems
import org.springframework.stereotype.Component
import java.util.UUID

data class PendingResolutionOutcome(val availableAfter: Int, val completedOrders: List<UUID>)

/**
 * Rotina unica de resolucao de pendencias por antiguidade, usada pela entrada de estoque e pela devolucao.
 * Exige transacao ativa e as linhas de estoque do material ja travadas.
 */
@Component
class PendingItemsResolver(
    private val stock: StockRepository,
    private val locks: StockLocks,
    private val outbox: OutboxPublisher,
) {
    fun resolve(materialId: UUID): PendingResolutionOutcome {
        val resolution =
            resolvePendingItems(
                stock.pendingItems(materialId),
                stock.quantity(materialId, StockStatusEnum.AVAILABLE),
            )
        val completedOrders = mutableListOf<UUID>()
        resolution.resolutions.forEach { item ->
            val orderId = item.item.orderId
            locks.lockOrder(orderId)
            stock.addQuantity(materialId, StockStatusEnum.AVAILABLE, -item.reserved)
            stock.addQuantity(materialId, StockStatusEnum.RESERVED, item.reserved)
            stock.recordMovement(materialId, orderId, StockMovementTypeEnum.RESERVED, item.reserved)
            if (item.resolved) {
                stock.deletePendingItem(item.item.id)
                if (locks.countPendingItems(orderId) == 0) {
                    completedOrders += orderId
                    outbox.publish(
                        StockReserved(item.item.sagaTransactionId, orderId, stock.reservedMaterials(orderId)),
                    )
                }
            } else {
                stock.reducePendingItem(item.item.id, item.remaining)
            }
        }
        return PendingResolutionOutcome(resolution.availableAfter, completedOrders.distinct())
    }
}
