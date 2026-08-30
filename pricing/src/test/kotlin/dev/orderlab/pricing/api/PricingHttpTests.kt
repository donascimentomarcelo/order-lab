package dev.orderlab.pricing.api

import dev.orderlab.pricing.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["pricing.catalog.generated-product-seeding.enabled=false"],
)
@Import(TestcontainersConfiguration::class)
class PricingHttpTests {

    @LocalServerPort
    private var port: Int = 0

    private val httpClient = HttpClient.newHttpClient()

    @Test
    fun `returns a price quote`() {
        val response = get("/prices/NOTEBOOK-001?quantity=6")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"productId\":\"NOTEBOOK-001\""))
        assertTrue(response.body().contains("\"subtotal\":21000.00"))
        assertTrue(response.body().contains("\"discountRate\":0.05"))
        assertTrue(response.body().contains("\"discountAmount\":1050.00"))
        assertTrue(response.body().contains("\"total\":19950.00"))
    }

    @Test
    fun `returns bad request for an invalid quantity`() {
        val response = get("/prices/NOTEBOOK-001?quantity=0")

        assertEquals(400, response.statusCode())
        assertTrue(response.body().contains("\"code\":\"INVALID_QUANTITY\""))
        assertTrue(response.body().contains("\"message\":\"Quantity must be greater than zero\""))
    }

    @Test
    fun `returns not found for an unknown product`() {
        val response = get("/prices/UNKNOWN?quantity=1")

        assertEquals(404, response.statusCode())
        assertTrue(response.body().contains("\"code\":\"PRODUCT_NOT_FOUND\""))
        assertTrue(response.body().contains("\"message\":\"Product UNKNOWN was not found\""))
    }

    @Test
    fun `serves requests on a virtual thread`() {
        val response = get("/internal/service-info")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("\"virtualThread\":true"))
    }

    @Test
    fun `exposes prometheus metrics`() {
        get("/prices/NOTEBOOK-001?quantity=1")

        val response = get("/actuator/prometheus")

        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("jvm_info"))
        assertTrue(response.body().contains("http_server_requests_seconds"))
        assertTrue(response.body().contains("application=\"pricing\""))
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI("http://localhost:$port$path"))
            .GET()
            .build()

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
