package dev.orderlab.pricing

import dev.orderlab.pricing.catalog.ProductCatalog
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class PricingApplicationTests @Autowired constructor(
    private val productCatalog: ProductCatalog,
    private val jdbcClient: JdbcClient,
) {

    @Test
    fun `flyway creates and seeds the pricing catalog`() {
        val product = productCatalog.findById("notebook-001")

        assertNotNull(product)
        assertEquals("NOTEBOOK-001", product.id)
        assertEquals("Notebook", product.name)
        assertEquals(BigDecimal("3500.00"), product.unitPrice)
        assertEquals(
            3,
            jdbcClient.sql("SELECT COUNT(*) FROM products").query(Int::class.java).single(),
        )
        assertEquals(
            2,
            jdbcClient.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success")
                .query(Int::class.java)
                .single(),
        )
    }
}
