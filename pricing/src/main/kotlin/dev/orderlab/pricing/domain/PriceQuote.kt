package dev.orderlab.pricing.domain

import java.math.BigDecimal

data class PriceQuote(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal,
    val discountRate: BigDecimal,
    val discountAmount: BigDecimal,
    val total: BigDecimal,
)
