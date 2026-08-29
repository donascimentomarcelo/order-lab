package dev.orderlab.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
class ServiceInfoController {

    private final String serviceName;

    ServiceInfoController(@Value("${spring.application.name}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/service-info")
    ServiceInfoResponse serviceInfo() {
        var thread = Thread.currentThread();
        return new ServiceInfoResponse(
                serviceName,
                "UP",
                Runtime.version().toString(),
                thread.getName(),
                thread.isVirtual());
    }

    record ServiceInfoResponse(
            String service,
            String status,
            String javaVersion,
            String threadName,
            boolean virtualThread) {
    }
}
