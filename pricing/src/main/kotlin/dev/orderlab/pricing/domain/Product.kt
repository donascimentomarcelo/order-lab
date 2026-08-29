package dev.orderlab.pricing.domain

import java.math.BigDecimal

data class Product(
    val id: String,
    val name: String,
    val unitPrice: BigDecimal,
)
