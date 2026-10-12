package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.MaterialQuantity
import com.fiap.mechanicalhub.stock.domain.StockMovementTypeEnum
import com.fiap.mechanicalhub.stock.domain.StockStatusEnum
import com.fiap.mechanicalhub.stock.domain.sumByMaterial
import org.springframework.stereotype.Service

/** consumeStock: baixa do RESERVED (OUT); o AVAILABLE nao muda. Roda na transacao do processador. */
@Service
class ConsumeStockUseCase(
    private val stock: StockRepository,
    private val locks: StockLocks,
    private val outbox: OutboxPublisher,
) {
    fun execute(command: ConsumeStockCommand): String {
        val materials = command.reservedMaterials.sumByMaterial()
        locks.lockMaterials(materials.map { it.materialId })
        requireReserved(stock, materials)
        materials.forEach { material ->
            stock.addQuantity(material.materialId, StockStatusEnum.RESERVED, -material.quantity)
            stock.recordMovement(material.materialId, command.orderId, StockMovementTypeEnum.OUT, material.quantity)
        }
        return outbox.publish(StockConsumed(command.transactionId, command.orderId))
    }
}

/** RESERVED menor que a quantidade a baixar ou devolver e erro permanente. */
internal fun requireReserved(stock: StockRepository, materials: List<MaterialQuantity>) {
    materials.forEach { material ->
        val reserved = stock.quantity(material.materialId, StockStatusEnum.RESERVED)
        if (reserved < material.quantity) {
            throw BusinessRuleException(
                "RESERVED insuficiente para o material ${material.materialId}: $reserved < ${material.quantity}",
            )
        }
    }
}
