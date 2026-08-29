package dev.orderlab.pricing.domain

sealed interface PricingResult {

    data class Quoted(val quote: PriceQuote) : PricingResult

    data class ProductNotFound(val productId: String) : PricingResult

    data class InvalidQuantity(val quantity: Int) : PricingResult
}
