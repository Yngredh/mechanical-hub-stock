package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.StockMovementTypeEnum
import com.fiap.mechanicalhub.stock.domain.StockStatusEnum
import com.fiap.mechanicalhub.stock.domain.resolvePendingItems
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

data class RegisterStockEntryCommand(val idempotencyKey: String, val materialId: UUID, val quantity: Int)

data class RegisterStockEntryOutcome(val status: Int, val result: StockEntryResult)

@Service
class RegisterStockEntryUseCase(
    private val stock: StockRepository,
    private val locks: StockLocks,
    private val idempotency: IdempotencyStore,
    private val outbox: OutboxPublisher,
) {
    @Transactional
    fun execute(command: RegisterStockEntryCommand): RegisterStockEntryOutcome {
        locks.applyLockTimeout()
        val requestHash = hashOf(command)
        val stored = idempotency.findLocking(command.idempotencyKey)
        if (stored != null) {
            if (stored.requestHash != requestHash) throw IdempotencyKeyReusedException(command.idempotencyKey)
            return RegisterStockEntryOutcome(stored.status, stored.result)
        }
        if (!stock.materialExists(command.materialId)) throw MaterialNotFoundException(command.materialId)

        val result = registerEntry(command)
        idempotency.save(command.idempotencyKey, StoredEntryResponse(requestHash, CREATED, result))
        return RegisterStockEntryOutcome(CREATED, result)
    }

    private fun registerEntry(command: RegisterStockEntryCommand): StockEntryResult {
        locks.lockMaterials(listOf(command.materialId))
        stock.addQuantity(command.materialId, StockStatusEnum.AVAILABLE, command.quantity)
        stock.recordMovement(command.materialId, null, StockMovementTypeEnum.ENTRY, command.quantity)

        val resolution =
            resolvePendingItems(
                stock.pendingItems(command.materialId),
                stock.quantity(command.materialId, StockStatusEnum.AVAILABLE),
            )
        val completedOrders = mutableListOf<UUID>()
        resolution.resolutions.forEach { item ->
            val orderId = item.item.orderId
            locks.lockOrder(orderId)
            stock.addQuantity(command.materialId, StockStatusEnum.AVAILABLE, -item.reserved)
            stock.addQuantity(command.materialId, StockStatusEnum.RESERVED, item.reserved)
            stock.recordMovement(command.materialId, orderId, StockMovementTypeEnum.RESERVED, item.reserved)
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
        return StockEntryResult(
            materialId = command.materialId,
            quantity = command.quantity,
            available = resolution.availableAfter,
            reservedOrderIds = completedOrders.distinct(),
        )
    }

    private fun hashOf(command: RegisterStockEntryCommand): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("${command.materialId}:${command.quantity}".toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val CREATED = 201
    }
}
