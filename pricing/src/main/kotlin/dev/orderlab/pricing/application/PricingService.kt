package dev.orderlab.pricing.application

import dev.orderlab.pricing.catalog.ProductCatalog
import dev.orderlab.pricing.domain.PriceQuote
import dev.orderlab.pricing.domain.PricingResult
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class PricingService(
    private val productCatalog: ProductCatalog,
) {
    fun quote(productId: String, quantity: Int): PricingResult {
        if (quantity <= 0) {
            return PricingResult.InvalidQuantity(quantity)
        }

        val product = productCatalog.findById(productId)
            ?: return PricingResult.ProductNotFound(productId)

        val unitPrice = asMoney(product.unitPrice)
        val subtotal = asMoney(unitPrice.multiply(quantity.toBigDecimal()))
        val discountRate = discountRateFor(quantity)
        val discountAmount = asMoney(subtotal.multiply(discountRate))
        val total = asMoney(subtotal.subtract(discountAmount))

        return PricingResult.Quoted(
            PriceQuote(
                productId = product.id,
                productName = product.name,
                quantity = quantity,
                unitPrice = unitPrice,
                subtotal = subtotal,
                discountRate = discountRate,
                discountAmount = discountAmount,
                total = total,
            ),
        )
    }

    private fun discountRateFor(quantity: Int): BigDecimal =
        when (quantity) {
            in 1..4 -> BigDecimal("0.00")
            in 5..9 -> BigDecimal("0.05")
            else -> BigDecimal("0.10")
        }

    private fun asMoney(value: BigDecimal): BigDecimal =
        value.setScale(2, RoundingMode.HALF_UP)
}
