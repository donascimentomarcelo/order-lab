package dev.orderlab.pricing.catalog

import dev.orderlab.pricing.domain.Product

interface ProductCatalog {

    fun findById(productId: String): Product?
}
