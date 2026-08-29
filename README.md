# OrderLab

Laboratório de microsserviços com Java 25, Spring Boot 4.1.1 e observabilidade.

## Serviços

| Serviço | Linguagem | Porta local | Responsabilidade inicial |
|---|---|---:|---|
| `checkout` | Java 25 | 8080 | Orquestrar o checkout |
| `pricing` | Kotlin | 8081 | Fornecer preços |
| `inventory` | Java 25 | 8082 | Controlar estoque |
| `payment` | Java 25 | 8083 | Simular pagamentos |
| `fulfillment` | Java 25 | 8084 | Processar fulfillment |

## Pré-requisitos

- Java 25
- Maven Wrapper incluído no repositório

## Compilar e testar

No PowerShell:

```powershell
./mvnw.cmd clean verify
```

## Executar um serviço

Exemplo com o `checkout`:

```powershell
./mvnw.cmd -pl checkout spring-boot:run
```

Os demais serviços podem ser iniciados em terminais separados, substituindo o caminho do módulo.

## Endpoints iniciais

```text
GET /actuator/health
GET /actuator/info
GET /internal/service-info
```

O endpoint `/internal/service-info` informa o serviço, a versão do Java, o nome da thread e se a requisição foi executada em uma virtual thread.
