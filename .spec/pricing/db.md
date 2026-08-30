# Pricing — especificação de banco de dados

## Status

**Implementado e validado em 29/08/2026.**

## Objetivo

Substituir o catálogo em memória usado em produção por um catálogo persistido no PostgreSQL, preservando a interface `ProductCatalog` e as regras definidas em `spec.md`. Esta etapa adiciona persistência somente para leitura de produtos; o cálculo da cotação continua na camada de aplicação.

## Decisões principais

- PostgreSQL 16 é o banco local de referência.
- Spring JDBC será usado no lugar de JPA para manter SQL, latência e chamadas ao pool visíveis durante o aprendizado de observabilidade.
- Flyway é o único responsável pela evolução do schema e pelos dados iniciais.
- HikariCP continua sendo o pool de conexões fornecido pelo Spring Boot.
- O adaptador JDBC implementa `ProductCatalog`; a camada de aplicação não conhece JDBC, tabelas ou SQL.
- `InMemoryProductCatalog` não é um bean de produção e permanece disponível para testes unitários rápidos.
- Não será usado H2: testes de integração devem exercitar PostgreSQL real por meio de Testcontainers.

## Fluxo

```text
GET /prices/{productId}
        │
        ▼
PricingController
        │
        ▼
PricingService
        │ ProductCatalog
        ▼
JdbcProductCatalog ── HikariCP ── PostgreSQL
```

## Conexão local

| Configuração Spring | Variável | Valor local esperado |
|---|---|---|
| `spring.datasource.url` | `PRICING_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/orderlab` |
| `spring.datasource.username` | `PRICING_DB_USERNAME` | `system_design` |
| `spring.datasource.password` | `PRICING_DB_PASSWORD` | segredo local, nunca versionado |

O arquivo `pricing/.env.example` documenta os nomes, mas o Spring Boot não carrega arquivos `.env` automaticamente. As variáveis devem existir no processo, IDE, terminal, Kubernetes Secret ou ferramenta de execução.

Exemplo temporário no PowerShell:

```powershell
$env:PRICING_DB_URL = "jdbc:postgresql://127.0.0.1:5432/orderlab"
$env:PRICING_DB_USERNAME = "system_design"
$env:PRICING_DB_PASSWORD = "<senha-local>"
.\mvnw.cmd -pl pricing spring-boot:run
```

## Schema

Tabela `products`:

| Coluna | Tipo | Regra |
|---|---|---|
| `product_id` | `VARCHAR(50)` | chave primária, não vazio, armazenado em maiúsculas |
| `name` | `VARCHAR(100)` | obrigatório e não vazio |
| `unit_price` | `NUMERIC(19,2)` | obrigatório e maior ou igual a zero |

O identificador recebido pela API deve ser normalizado com `trim()` e `uppercase(Locale.ROOT)` antes da consulta. Isso permite usar a chave primária diretamente, evita `UPPER(product_id)` no SQL e preserva o uso do índice.

## Migrations

```text
db/migration/
├── V1__create_products.sql
└── V2__seed_products.sql
```

Regras importantes:

1. Uma migration aplicada nunca deve ser editada; crie uma nova versão para qualquer alteração.
2. Não use `ddl-auto`, scripts manuais ou criação de tabela pelo código da aplicação.
3. Migrations devem ser pequenas, determinísticas e revisáveis.
4. Alterações destrutivas precisam de estratégia de compatibilidade e rollback operacional.
5. Dados de referência podem ser versionados; dados transacionais não devem ser tratados como migration.
6. O histórico deve ser validado no startup; checksum divergente é erro e não deve ser reparado automaticamente.

## Catálogo inicial

| Product ID | Nome | Preço unitário |
|---|---|---:|
| `NOTEBOOK-001` | Notebook | 3500.00 |
| `MONITOR-001` | Monitor | 1200.00 |
| `KEYBOARD-001` | Teclado | 250.00 |

## Pool, concorrência e virtual threads

Virtual threads aumentam a quantidade de requisições bloqueantes que a JVM pode sustentar, mas não aumentam a capacidade do PostgreSQL. O pool é um limite físico separado:

- `maximum-pool-size`: 10 conexões;
- `minimum-idle`: 1 conexão;
- `connection-timeout`: 3 segundos;
- nome do pool: `pricing-pool`.

Se centenas de virtual threads consultarem o banco simultaneamente, somente dez poderão possuir conexão ao mesmo tempo; as demais aguardarão o pool. Essa espera é um sinal relevante para métricas e testes de carga.

## Observabilidade esperada

- tempo total do endpoint de preço;
- span da consulta JDBC e identificação do PostgreSQL como dependência;
- conexões ativas, ociosas e pendentes do HikariCP;
- tempo de aquisição de conexão;
- erros de conexão, timeout e SQL;
- número de consultas durante uma requisição;
- health check do datasource sem expor senha ou URL completa em logs.

Consultas não devem concatenar entrada do usuário. Parâmetros nomeados do `JdbcClient` são obrigatórios para evitar SQL injection e melhorar a leitura dos traces.

## Segurança

- Nenhuma senha deve entrar no Git, migration, YAML ou imagem Docker.
- O usuário local atual pode administrar o laboratório, mas ambientes compartilhados devem separar um usuário de migration e outro de runtime.
- O usuário de runtime deve receber somente `CONNECT`, `USAGE` no schema e `SELECT` nas tabelas necessárias.
- O endpoint e logs nunca devem devolver credenciais ou a connection string completa.
- Backups e restauração devem ser testados antes de alterações destrutivas.

## Estratégia de testes

- Testes unitários de `PricingService` usam `InMemoryProductCatalog` e não iniciam Spring nem Docker.
- Testes de integração iniciam PostgreSQL 16 com Testcontainers.
- Flyway deve criar o schema no container de teste.
- O teste do catálogo valida consulta case-insensitive e precisão `NUMERIC(19,2)`.
- Testes HTTP comprovam que o endpoint usa o catálogo persistido.
- O banco local não deve ser requisito para executar `mvn test`.

## Falhas que devem ser visíveis

- banco indisponível no startup;
- senha inválida;
- migration com checksum divergente;
- pool esgotado;
- query acima do limite esperado;
- produto ausente, que continua sendo resultado de domínio e HTTP 404, não exceção de infraestrutura.

## Critérios de aceite

- Flyway cria `products` e insere os três produtos iniciais;
- `JdbcProductCatalog` é o adaptador usado em runtime;
- busca continua ignorando caixa e espaços externos;
- endpoint mantém o contrato atual;
- nenhum segredo está versionado;
- testes unitários não dependem de PostgreSQL;
- testes de integração utilizam PostgreSQL via Testcontainers;
- aplicação inicia contra o banco local `orderlab`;
- schema e dados podem ser inspecionados pelo MCP PostgreSQL;
- métricas do HikariCP ficam disponíveis ao Micrometer para a etapa de observabilidade.

## Próximas evoluções, fora deste ciclo

- usuário PostgreSQL exclusivo e com menor privilégio para runtime;
- separação entre credencial de migration e credencial da aplicação;
- índices adicionais guiados por consultas reais e `EXPLAIN ANALYZE`;
- timeout de statement por usuário ou transação;
- dashboards e alertas do pool no Prometheus/Grafana;
- políticas de backup, restore e retenção para ambientes persistentes.

## Resultado da validação

- banco local `orderlab` migrado do schema vazio para a versão Flyway `v2`;
- tabelas `products` e `flyway_schema_history` confirmadas pelo MCP PostgreSQL;
- três produtos e duas migrations bem-sucedidas confirmados pelo MCP;
- aplicação iniciada contra PostgreSQL 16.13 na porta local `5432`;
- respostas HTTP `200`, catálogo case-insensitive, health `UP` e virtual thread confirmados em runtime;
- Testcontainers 2.0.5 iniciou PostgreSQL 16 e aplicou as migrations em bancos descartáveis;
- resultado do Maven: 10 testes aprovados, sem falhas ou erros.
