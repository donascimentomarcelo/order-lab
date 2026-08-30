package dev.orderlab.pricing.catalog

import dev.orderlab.pricing.domain.Product
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.Locale

@Repository
class JdbcProductCatalog(
    private val jdbcClient: JdbcClient,
) : ProductCatalog {

    override fun findById(productId: String): Product? =
        jdbcClient.sql(
            """
            SELECT product_id, name, unit_price
            FROM products
            WHERE product_id = :productId
            """.trimIndent(),
        )
            .param("productId", productId.trim().uppercase(Locale.ROOT))
            .query(
                RowMapper { resultSet, _ ->
                    Product(
                        id = resultSet.getString("product_id"),
                        name = resultSet.getString("name"),
                        unitPrice = resultSet.getBigDecimal("unit_price"),
                    )
                },
            )
            .optional()
            .orElse(null)
}
