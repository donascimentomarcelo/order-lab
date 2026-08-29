package dev.orderlab.pricing.api

import java.math.BigDecimal

data class PriceResponse(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal,
    val discountRate: BigDecimal,
    val discountAmount: BigDecimal,
    val total: BigDecimal,
)
