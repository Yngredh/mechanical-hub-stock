package com.fiap.mechanicalhub.stock.application

import com.fiap.mechanicalhub.stock.domain.MaterialQuantity
import com.fiap.mechanicalhub.stock.domain.PendingItem
import com.fiap.mechanicalhub.stock.domain.StockMovementTypeEnum
import com.fiap.mechanicalhub.stock.domain.StockStatusEnum
import java.time.Instant
import java.util.UUID

data class MaterialBalance(val materialId: UUID, val name: String, val available: Int, val reserved: Int)

data class StockMovementView(
    val id: UUID,
    val orderId: UUID?,
    val type: StockMovementTypeEnum,
    val quantity: Int,
    val createdAt: Instant,
)

/** Porta de persistencia do estoque. Escritas exigem transacao ativa. */
interface StockRepository {
    fun materialExists(materialId: UUID): Boolean

    fun quantity(materialId: UUID, status: StockStatusEnum): Int

    fun addQuantity(materialId: UUID, status: StockStatusEnum, delta: Int)

    fun recordMovement(materialId: UUID, orderId: UUID?, type: StockMovementTypeEnum, quantity: Int)

    fun pendingItems(materialId: UUID): List<PendingItem>

    fun reducePendingItem(itemId: UUID, newQuantity: Int)

    fun deletePendingItem(itemId: UUID)

    /** Soma das movimentacoes RESERVED da ordem, por material. */
    fun reservedMaterials(orderId: UUID): List<MaterialQuantity>

    fun balances(): List<MaterialBalance>

    fun movements(materialId: UUID): List<StockMovementView>
}

/** Resultado de POST /stock/entry; tambem e o corpo armazenado para repeticao idempotente. */
data class StockEntryResult(
    val materialId: UUID,
    val quantity: Int,
    val available: Int,
    val reservedOrderIds: List<UUID>,
)

data class StoredEntryResponse(val requestHash: String, val status: Int, val result: StockEntryResult)

/** Porta de idempotency_requests. */
interface IdempotencyStore {
    /** Busca a chave serializando requisicoes concorrentes com a mesma chave ate o fim da transacao. */
    fun findLocking(key: String): StoredEntryResponse?

    fun save(key: String, response: StoredEntryResponse)
}

class MaterialNotFoundException(val materialId: UUID) : RuntimeException("Material $materialId nao encontrado")

class IdempotencyKeyReusedException(key: String) :
    RuntimeException("Idempotency-Key $key ja foi usada com outro corpo")
