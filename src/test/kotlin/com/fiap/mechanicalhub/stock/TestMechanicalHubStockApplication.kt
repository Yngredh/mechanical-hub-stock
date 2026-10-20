package com.fiap.mechanicalhub.stock

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
    fromApplication<MechanicalHubStockApplication>().with(TestcontainersConfiguration::class).run(*args)
}
