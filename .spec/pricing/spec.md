# Pricing — especificação SDD

## Status

**Implementado e validado em 29/08/2026.**

## Objetivo

Fornecer uma cotação determinística para um produto e uma quantidade. O primeiro ciclo utilizará um catálogo em memória e não terá banco de dados, cache ou atualização dinâmica de preços.

## Endpoint

```http
GET /prices/{productId}?quantity={quantity}
```

Exemplo:

```http
GET /prices/NOTEBOOK-001?quantity=6
```

## Catálogo inicial

| Product ID | Nome | Preço unitário |
|---|---|---:|
| `NOTEBOOK-001` | Notebook | 3500.00 |
| `MONITOR-001` | Monitor | 1200.00 |
| `KEYBOARD-001` | Teclado | 250.00 |

Os valores serão armazenados como `BigDecimal`, criados a partir de `String`.

## Regras de negócio

1. `quantity` menor ou igual a zero é inválida.
2. Produto ausente no catálogo deve produzir resultado de produto não encontrado.
3. Quantidades de 1 a 4 não recebem desconto.
4. Quantidades de 5 a 9 recebem 5% de desconto.
5. Quantidades a partir de 10 recebem 10% de desconto.
6. `subtotal` é o preço unitário multiplicado pela quantidade.
7. `discountAmount` é o subtotal multiplicado pela taxa de desconto.
8. `total` é o subtotal menos o desconto.
9. Valores monetários da resposta devem possuir duas casas decimais e arredondamento `HALF_UP`.
10. A cotação não altera estoque e não reserva o preço.

## Resposta de sucesso

Status HTTP `200 OK`:

```json
{
  "productId": "NOTEBOOK-001",
  "productName": "Notebook",
  "quantity": 6,
  "unitPrice": 3500.00,
  "subtotal": 21000.00,
  "discountRate": 0.05,
  "discountAmount": 1050.00,
  "total": 19950.00
}
```

## Respostas de erro

Quantidade inválida — status HTTP `400 Bad Request`:

```json
{
  "code": "INVALID_QUANTITY",
  "message": "Quantity must be greater than zero"
}
```

Produto inexistente — status HTTP `404 Not Found`:

```json
{
  "code": "PRODUCT_NOT_FOUND",
  "message": "Product UNKNOWN was not found"
}
```

## Estrutura planejada

```text
dev.orderlab.pricing
├── api
│   ├── PricingController
│   ├── PriceResponse
│   └── ApiErrorResponse
├── application
│   └── PricingService
├── catalog
│   ├── ProductCatalog
│   └── InMemoryProductCatalog
└── domain
    ├── Product
    ├── PriceQuote
    └── PricingResult
```

## Pontos reservados ao usuário

- implementar `PricingService.quote`;
- implementar a escolha da taxa em `PricingService.discountRateFor`;
- implementar arredondamento em `PricingService.asMoney`;
- implementar o mapeamento HTTP em `PricingController.getPrice` usando `when` exaustivo.

## Critérios de aceite para a validação

- serviço compila com Kotlin e Java 25;
- aplicação inicia com Spring Boot 4.1.1;
- catálogo encontra os três produtos sem diferenciar maiúsculas e minúsculas;
- regras das três faixas de desconto são respeitadas;
- quantidade zero e negativa retornam HTTP 400;
- produto desconhecido retorna HTTP 404;
- valores monetários possuem escala igual a 2;
- endpoint é atendido por virtual thread;
- não são utilizadas coroutines;
- não restam `TODO("USER IMPLEMENTATION...")` nos métodos do fluxo;
- testes unitários e de integração são aprovados.

## Fora do escopo deste ciclo

- cache;
- promoções por cliente;
- moedas diferentes;
- Kafka;
- coroutines;
- chamadas para outros serviços;
- persistência ou reserva da cotação.

A persistência do catálogo em PostgreSQL foi adicionada em um ciclo posterior e está especificada em [`db.md`](db.md).

## Resultado da validação

- implementação revisada e corrigida contra esta especificação;
- testes unitários cobrem validações, catálogo case-insensitive, limites das faixas de desconto e cálculos monetários;
- testes HTTP cobrem sucesso, quantidade inválida e produto inexistente;
- atendimento das requisições por virtual thread confirmado em runtime;
- Maven Wrapper executado com Java 25.0.2;
- resultado final: 10 testes aprovados, sem falhas ou erros.
