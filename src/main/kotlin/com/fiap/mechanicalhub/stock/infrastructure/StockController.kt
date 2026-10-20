package com.fiap.mechanicalhub.stock.infrastructure

import com.fiap.mechanicalhub.stock.application.MaterialBalance
import com.fiap.mechanicalhub.stock.application.RegisterStockEntryCommand
import com.fiap.mechanicalhub.stock.application.RegisterStockEntryUseCase
import com.fiap.mechanicalhub.stock.application.StockEntryResult
import com.fiap.mechanicalhub.stock.application.StockMovementView
import com.fiap.mechanicalhub.stock.application.StockQueries
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class StockEntryRequest(
    @field:NotNull val materialId: UUID?,
    @field:Min(1) val quantity: Int,
)

@RestController
class StockController(
    private val registerStockEntry: RegisterStockEntryUseCase,
    private val queries: StockQueries,
) {
    @PostMapping("/stock/entry")
    fun entry(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: StockEntryRequest,
    ): ResponseEntity<StockEntryResult> {
        require(idempotencyKey.isNotBlank()) { "Idempotency-Key obrigatoria" }
        val outcome =
            registerStockEntry.execute(
                RegisterStockEntryCommand(idempotencyKey, requireNotNull(request.materialId), request.quantity),
            )
        return ResponseEntity.status(outcome.status).body(outcome.result)
    }

    @GetMapping("/stock")
    fun balances(): List<MaterialBalance> = queries.balances()

    @GetMapping("/stock/{materialId}/movements")
    fun movements(
        @PathVariable materialId: UUID,
    ): List<StockMovementView> = queries.movements(materialId)
}
