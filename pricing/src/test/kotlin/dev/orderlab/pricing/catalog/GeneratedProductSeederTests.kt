package dev.orderlab.pricing.catalog

import dev.orderlab.pricing.TestcontainersConfiguration
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import kotlin.test.assertEquals

@SpringBootTest(properties = ["pricing.catalog.generated-product-seeding.enabled=true"])
@Import(TestcontainersConfiguration::class)
class GeneratedProductSeederTests @Autowired constructor(
    private val jdbcClient: JdbcClient,
) {

    @Test
    fun `seeds one million products after startup`() {
        await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(100))
            .until {
                jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM products WHERE product_id = 'PRODUCT-0999997')")
                    .query(Boolean::class.java)
                    .single()
            }

        assertEquals(
            1_000_000,
            jdbcClient.sql("SELECT COUNT(*) FROM products").query(Int::class.java).single(),
        )
    }
}
