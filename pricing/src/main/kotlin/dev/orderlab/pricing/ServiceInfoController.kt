package dev.orderlab.pricing

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal")
class ServiceInfoController(
    @Value("\${spring.application.name}") private val serviceName: String,
) {

    @GetMapping("/service-info")
    fun serviceInfo(): ServiceInfoResponse {
        val thread = Thread.currentThread()
        return ServiceInfoResponse(
            service = serviceName,
            status = "UP",
            javaVersion = Runtime.version().toString(),
            threadName = thread.name,
            virtualThread = thread.isVirtual,
        )
    }
}

data class ServiceInfoResponse(
    val service: String,
    val status: String,
    val javaVersion: String,
    val threadName: String,
    val virtualThread: Boolean,
)
