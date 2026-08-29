package dev.orderlab.pricing.application

import dev.orderlab.pricing.catalog.InMemoryProductCatalog
import dev.orderlab.pricing.domain.PriceQuote
import dev.orderlab.pricing.domain.PricingResult
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PricingServiceTests {

    private val pricingService = PricingService(InMemoryProductCatalog())

    @Test
    fun `rejects zero and negative quantities`() {
        assertEquals(
            PricingResult.InvalidQuantity(0),
            pricingService.quote("NOTEBOOK-001", 0),
        )
        assertEquals(
            PricingResult.InvalidQuantity(-1),
            pricingService.quote("NOTEBOOK-001", -1),
        )
    }

    @Test
    fun `returns product not found preserving the requested id`() {
        assertEquals(
            PricingResult.ProductNotFound("UNKNOWN"),
            pricingService.quote("UNKNOWN", 1),
        )
    }

    @Test
    fun `finds products without distinguishing letter case`() {
        val quote = quoted("notebook-001", 1)

        assertEquals("NOTEBOOK-001", quote.productId)
        assertEquals("Notebook", quote.productName)
    }

    @Test
    fun `applies discount rates at every boundary`() {
        assertEquals(BigDecimal("0.00"), quoted("KEYBOARD-001", 4).discountRate)
        assertEquals(BigDecimal("0.05"), quoted("KEYBOARD-001", 5).discountRate)
        assertEquals(BigDecimal("0.05"), quoted("KEYBOARD-001", 9).discountRate)
        assertEquals(BigDecimal("0.10"), quoted("KEYBOARD-001", 10).discountRate)
    }

    @Test
    fun `calculates subtotal discount and total with two decimal places`() {
        val quote = quoted("NOTEBOOK-001", 6)

        assertEquals(BigDecimal("3500.00"), quote.unitPrice)
        assertEquals(BigDecimal("21000.00"), quote.subtotal)
        assertEquals(BigDecimal("0.05"), quote.discountRate)
        assertEquals(BigDecimal("1050.00"), quote.discountAmount)
        assertEquals(BigDecimal("19950.00"), quote.total)
        listOf(quote.unitPrice, quote.subtotal, quote.discountAmount, quote.total)
            .forEach { value -> assertEquals(2, value.scale()) }
    }

    private fun quoted(productId: String, quantity: Int): PriceQuote =
        assertIs<PricingResult.Quoted>(pricingService.quote(productId, quantity)).quote
}
