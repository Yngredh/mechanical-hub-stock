package com.fiap.mechanicalhub.stock

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Tag("integration")
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MechanicalHubStockApplicationTests {
    @Test
    fun `given the application when the context starts then it loads`() {
        // Falha se o contexto Spring nao subir.
    }
}
