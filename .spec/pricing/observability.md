# Pricing — especificação de observabilidade

## Status

**Métricas, OpenTelemetry Java Agent e dashboard inicial do Pricing implementados e validados.**

## Objetivo

Construir a observabilidade do `pricing` de forma incremental, começando pela publicação de métricas no formato Prometheus. A instrumentação deve permitir relacionar tráfego HTTP, JVM, virtual threads, pool JDBC e PostgreSQL sem alterar as regras de cotação.

## Escopo implementado

- adicionar `micrometer-registry-prometheus`;
- expor `GET /actuator/prometheus`;
- adicionar a tag comum `application="pricing"`;
- validar o endpoint por teste HTTP e execução local;
- anexar o OpenTelemetry Java Agent ao processo do `pricing`;
- gerar automaticamente spans HTTP e JDBC;
- validar a relação dos spans pelo `traceId`;
- exportar traces por OTLP para o OpenTelemetry Collector;
- coletar métricas com Prometheus;
- consultar o Prometheus pelo datasource provisionado no Grafana;
- habilitar histogramas HTTP para cálculo de percentis;
- publicar métricas do scheduler de virtual threads com `micrometer-java21`;
- coletar disponibilidade e sessões do PostgreSQL com `postgres_exporter`;
- provisionar o dashboard `OrderLab — Pricing` no Grafana.

## Fora do escopo desta etapa

- backend de traces;
- alertas e gravação de regras;
- manifestos Kubernetes de observabilidade;
- carga com k6.

Esses itens serão implementados progressivamente com intervenção do usuário.

## Fluxo atual

```text
Cliente ──HTTP──> Pricing ──JDBC──> PostgreSQL
                    ├──OTLP──> OpenTelemetry Collector ──> debug exporter
                    │
                    └── /actuator/prometheus <── Prometheus <── Grafana
```

A operação completa da infraestrutura local está descrita em [`../observability/local-stack.md`](../observability/local-stack.md).

O Prometheus coleta periodicamente o endpoint de métricas. Os traces são enviados por OTLP ao Collector e exibidos pelo `debug` exporter; ainda não existe armazenamento em um backend de traces.

## OpenTelemetry Java Agent

### Estratégia

O agente é anexado na inicialização da JVM por meio de `-javaagent`. Isso permite instrumentar Spring MVC, Tomcat e JDBC sem adicionar dependências ao `pom.xml` e sem misturar código de observabilidade com a regra de negócio.

A versão usada pelo laboratório está fixada no `Makefile`:

```text
opentelemetry-javaagent 2.31.1
```

O binário é baixado do Maven Central para `.tools/opentelemetry/opentelemetry-javaagent-2.31.1.jar`. O arquivo JAR é local e está ignorado pelo Git.

Para baixar o agente separadamente:

```powershell
make otel-agent
```

É possível testar outra versão sem editar o Makefile:

```powershell
make otel-agent OTEL_AGENT_VERSION=2.31.1
```

### Execução instrumentada

Com o PostgreSQL disponível e as variáveis `PRICING_DB_*` configuradas:

```powershell
make pricing-otel
```

O alvo inicia o `pricing` pelo Spring Boot Maven Plugin com estas propriedades:

| Propriedade | Valor atual | Motivo |
|---|---|---|
| `otel.service.name` | `pricing` | Identificar o serviço |
| `otel.traces.exporter` | `otlp` | Enviar spans para o Collector |
| `otel.exporter.otlp.endpoint` | `http://localhost:4318` | Endereço OTLP/HTTP local |
| `otel.metrics.exporter` | `none` | Evitar duplicar as métricas do Micrometer |
| `otel.logs.exporter` | `none` | Adiar a coleta de logs para uma etapa própria |
| `service.version` | `0.1.0-SNAPSHOT` | Identificar a versão da aplicação |
| `deployment.environment.name` | `local` | Identificar o ambiente do laboratório |

### Trace esperado

Depois de iniciar a aplicação:

```powershell
curl.exe "http://localhost:8081/prices/NOTEBOOK-001?quantity=2"
```

Os logs do Collector devem apresentar spans equivalentes a:

```text
GET /prices/{productId}                 SERVER
└── SELECT orderlab.products            CLIENT
```

Os spans HTTP e JDBC devem ter o mesmo `traceId` e `spanId` diferentes. O span JDBC deve terminar antes do span HTTP, demonstrando que a consulta pertence à requisição.

## Endpoint

```http
GET /actuator/prometheus
```

Teste local:

```powershell
curl.exe http://localhost:8081/actuator/prometheus
```

O endpoint deve responder `200 OK` usando o formato de texto Prometheus.

## Métricas iniciais esperadas

### HTTP

- quantidade de requisições;
- duração das requisições;
- método HTTP;
- URI normalizada pelo template, por exemplo `/prices/{productId}`;
- status HTTP;
- exceção registrada pelo servidor.

O nome Prometheus normalmente começa com `http_server_requests_seconds`.

### JVM

- informação da JVM;
- heap e non-heap;
- garbage collector;
- classes carregadas;
- threads da JVM;
- CPU e uptime do processo.

Exemplo de métrica usada no teste: `jvm_info`.

### HikariCP

Depois que a primeira consulta usar o datasource, devem aparecer métricas relacionadas a:

- conexões ativas;
- conexões ociosas;
- conexões pendentes;
- tamanho máximo e mínimo do pool;
- tempo de aquisição e uso da conexão;
- timeouts.

O pool está identificado como `pricing-pool` na configuração do datasource.

## Tags

Todas as métricas registradas pelo Spring devem receber:

```text
application="pricing"
```

Tags devem representar dimensões pequenas e conhecidas. Não devem ser usados como tags:

- `productId`;
- identificadores de pedido ou cliente;
- mensagens de exceção;
- URL completa com parâmetros;
- valores monetários;
- thread ID ou nome de cada virtual thread.

Esses valores criariam alta cardinalidade, aumentando memória, armazenamento e custo de consulta no Prometheus.

## Segurança do endpoint

`/actuator/prometheus` é apropriado para o laboratório local, mas não deve ficar público na internet. No Kubernetes ele deverá ser acessível apenas pelo Prometheus, por uma porta de gerenciamento ou política de rede apropriada.

Métricas não devem conter:

- senha ou usuário do PostgreSQL;
- connection string completa;
- payloads HTTP;
- dados pessoais;
- tokens e cabeçalhos de autenticação.

## Relação com virtual threads

Virtual threads permitem muitas requisições bloqueantes simultâneas, enquanto o HikariCP continua limitado a dez conexões. Portanto, a análise precisa comparar pelo menos:

```text
requisições concorrentes
        versus
conexões ativas + conexões pendentes + tempo de aquisição
```

Um aumento de latência com conexões pendentes indica contenção no pool ou no banco, e não necessariamente falta de threads na JVM.

## Dashboard inicial

O dashboard provisionado como código está disponível em:

```text
http://localhost:3000/d/orderlab-pricing
```

Ele contém painéis para:

- disponibilidade do `pricing` e do PostgreSQL;
- taxa, erros e latência p95/média das requisições HTTP;
- memória, garbage collector e threads da JVM;
- virtual threads montadas e enfileiradas, carriers, paralelismo, pinning e falhas de submit;
- conexões ativas, ociosas, pendentes e capacidade do HikariCP;
- tempo médio e máximo de aquisição de conexão;
- conexões abertas no banco `orderlab`.

O filtro `Rota HTTP` usa URIs normalizadas, evitando cardinalidade por `productId`. Um painel vazio de erros ou pinning significa que nenhum evento desse tipo ocorreu no período selecionado.

Este é um dashboard de **métricas**. Traces pesquisáveis exigem Tempo e logs pesquisáveis exigem Loki; o OpenTelemetry Collector ainda envia traces apenas ao `debug` exporter.

## Critérios de aceite das etapas concluídas

- dependência Prometheus presente no módulo `pricing`;
- actuator expõe `health`, `info` e `prometheus`;
- `/actuator/prometheus` retorna HTTP 200;
- resposta contém `jvm_info`;
- após uma cotação, resposta contém `http_server_requests_seconds`;
- métricas contêm `application="pricing"`;
- testes existentes continuam aprovados;
- agente oficial pode ser obtido por `make otel-agent`;
- `make pricing-otel` inicia o serviço com o agente anexado;
- versão do agente é registrada no terminal;
- chamada de cotação gera um span HTTP `SERVER`;
- consulta de produto gera um span JDBC `CLIENT`;
- spans HTTP e JDBC compartilham o mesmo `traceId`;
- Collector recebe traces por OTLP/HTTP;
- Prometheus coleta o endpoint do `pricing`;
- Grafana possui o Prometheus como datasource padrão.
- dashboard `OrderLab — Pricing` é provisionado na pasta `OrderLab`;
- todas as consultas PromQL do dashboard são aceitas pelo Prometheus;
- `pg_up{job="postgresql"}` diferencia disponibilidade do banco da disponibilidade do serviço.

## Próximas etapas para execução conjunta

1. conectar Tempo para armazenar e pesquisar traces no Grafana;
2. conectar Loki para armazenar e pesquisar logs no Grafana;
3. executar pelo usuário a carga k6 definida em [`load-test.md`](load-test.md);
4. analisar em conjunto a saturação do HikariCP e o comportamento das virtual threads;
5. levar a configuração validada para o Kubernetes local.

## Resultado da validação

- `/actuator/prometheus` respondeu HTTP 200 no teste com servidor real em porta aleatória;
- resposta confirmou `jvm_info`, `http_server_requests_seconds` e `application="pricing"`;
- Spring Boot registrou três endpoints Actuator expostos: `health`, `info` e `prometheus`;
- PostgreSQL 16, Flyway e catálogo JDBC continuaram funcionando durante a validação;
- resultado final: 12 testes aprovados, sem falhas ou erros;
- OpenTelemetry Java Agent `2.31.1` carregado na JVM Java 25;
- cotação instrumentada respondeu HTTP 200;
- span HTTP `GET /prices/{productId}` criado como `SERVER`;
- span JDBC `SELECT orderlab.products` criado como `CLIENT`;
- ambos compartilharam o mesmo `traceId`, confirmando a relação entre requisição e consulta;
- aplicação instrumentada encerrada com graceful shutdown após a validação;
- Collector recebeu os traces por OTLP/HTTP;
- Prometheus confirmou o target `pricing` como `UP`;
- Grafana confirmou o datasource Prometheus com status `OK`.
- módulo `micrometer-java21` publicou métricas do scheduler de virtual threads no Java 25;
- histogramas HTTP publicaram buckets usados no cálculo de p95;
- PostgreSQL exporter publicou `pg_up=1` e métricas do banco `orderlab`;
- dashboard com 16 painéis foi carregado automaticamente pelo Grafana;
- todas as 25 expressões PromQL do dashboard foram executadas sem erro;
- targets `pricing`, `postgresql` e `prometheus` foram confirmados como `UP`.
