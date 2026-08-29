package dev.orderlab.pricing.api

import dev.orderlab.pricing.application.PricingService
import dev.orderlab.pricing.domain.PricingResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PricingController(
    private val pricingService: PricingService,
) {

    @GetMapping("/prices/{productId}")
    fun getPrice(
        @PathVariable productId: String,
        @RequestParam(defaultValue = "1") quantity: Int,
    ): ResponseEntity<Any> {
        val result = pricingService.quote(productId, quantity);
        return when (result) {
            is PricingResult.Quoted -> {
                val quote = result.quote
                ResponseEntity.ok(
                    PriceResponse(
                        productId = quote.productId,
                        productName = quote.productName,
                        quantity = quote.quantity,
                        unitPrice = quote.unitPrice,
                        subtotal = quote.subtotal,
                        discountRate = quote.discountRate,
                        discountAmount = quote.discountAmount,
                        total = quote.total,
                    ),
                )
            }

            is PricingResult.InvalidQuantity -> {
                ResponseEntity.badRequest().body<Any>(
                    ApiErrorResponse(
                        code = "INVALID_QUANTITY",
                        message = "Quantity must be greater than zero",
                    ),
                )
            }

            is PricingResult.ProductNotFound -> {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body<Any>(
                    ApiErrorResponse(
                        code = "PRODUCT_NOT_FOUND",
                        message = "Product ${result.productId} was not found",
                    ),
                )
            }
        }
    }
}
