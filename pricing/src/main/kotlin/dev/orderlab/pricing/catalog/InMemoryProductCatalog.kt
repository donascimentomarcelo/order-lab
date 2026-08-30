package dev.orderlab.pricing.catalog

import dev.orderlab.pricing.domain.Product
import java.math.BigDecimal
import java.util.Locale

class InMemoryProductCatalog : ProductCatalog {

    private val products = listOf(
        Product("NOTEBOOK-001", "Notebook", BigDecimal("3500.00")),
        Product("MONITOR-001", "Monitor", BigDecimal("1200.00")),
        Product("KEYBOARD-001", "Teclado", BigDecimal("250.00")),
    ).associateBy(Product::id)

    override fun findById(productId: String): Product? =
        products[productId.trim().uppercase(Locale.ROOT)]
}
