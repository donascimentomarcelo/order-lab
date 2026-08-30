package dev.orderlab.pricing.catalog

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.task.TaskExecutor
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "pricing.catalog.generated-product-seeding",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class GeneratedProductSeeder(
    private val jdbcClient: JdbcClient,
    @Qualifier("applicationTaskExecutor") private val taskExecutor: TaskExecutor,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun seedAfterStartup() {
        taskExecutor.execute {
            try {
                val insertedProducts = jdbcClient.sql(SEED_SQL).update()
                logger.info("Generated product seeding completed: {} products inserted", insertedProducts)
            } catch (exception: Exception) {
                logger.error("Generated product seeding failed", exception)
            }
        }
    }

    private companion object {
        val SEED_SQL =
            """
            INSERT INTO products (product_id, name, unit_price)
            SELECT
                'PRODUCT-' || lpad(sequence_number::text, 7, '0'),
                (ARRAY[
                    'Notebook', 'Monitor', 'Teclado', 'Mouse', 'Headset',
                    'Webcam', 'Impressora', 'Scanner', 'Roteador', 'Switch',
                    'SSD', 'Memoria RAM', 'Processador', 'Placa de Video', 'Placa Mae',
                    'Fonte', 'Gabinete', 'Cadeira', 'Mesa', 'Smartphone'
                ])[((sequence_number - 1) % 20) + 1]
                    || ' ' || lpad(sequence_number::text, 7, '0'),
                (((sequence_number::bigint * 7919) % 999900) + 100)::numeric / 100
            FROM generate_series(1, 999997) AS generated_products(sequence_number)
            ON CONFLICT (product_id) DO NOTHING
            """.trimIndent()
    }
}
