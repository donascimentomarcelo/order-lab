# OrderLab — planejamento do laboratório de observabilidade

## 1. Visão geral

O **OrderLab** será um ecossistema de microsserviços para praticar observabilidade com OpenTelemetry, Prometheus e Grafana, além de explorar recursos modernos do Java 25 em um cenário próximo de uma aplicação real. Plataformas comerciais, como Datadog, poderão ser conectadas posteriormente para comparação.

O domínio escolhido é o processamento de pedidos de uma loja durante períodos de alta demanda. Esse domínio permite exercitar:

- chamadas HTTP síncronas entre serviços;
- concorrência com virtual threads;
- processamento assíncrono com Kafka;
- acesso bloqueante a banco de dados;
- rastreamento distribuído entre HTTP, banco e mensageria;
- métricas de aplicação, JVM, containers, Kubernetes e Kafka;
- logs correlacionados com traces;
- falhas, retries, timeouts e compensações;
- profiling de CPU, memória, locks e threads;
- dashboards, monitores, SLOs e investigação de incidentes.

O objetivo não é reproduzir uma plataforma de comércio eletrônico completa. O projeto deve permanecer pequeno o suficiente para execução em Kubernetes local, mas rico o suficiente para produzir telemetria útil e independente de fornecedor.

## 2. Objetivos de aprendizagem

Ao terminar o laboratório, deverá ser possível:

1. Construir e executar aplicações Spring Boot com Java 25.
2. Utilizar APIs síncronas e bloqueantes com virtual threads.
3. Comparar platform threads e virtual threads sob a mesma carga.
4. Implementar fan-out concorrente com APIs nativas do JDK.
5. Produzir e consumir eventos Kafka.
6. Propagar uma trace por HTTP, JDBC e Kafka.
7. Correlacionar logs, métricas, traces e profiles.
8. Monitorar workloads e recursos do Kubernetes.
9. Identificar gargalos, saturação, erros e consumer lag.
10. Criar dashboards, alertas e SLOs com Prometheus e Grafana.
11. Investigar incidentes simulados usando evidências de observabilidade.

## 3. Stack inicial

Versões de referência no momento deste planejamento:

| Componente | Escolha inicial |
|---|---|
| Java | Java 25 LTS |
| Spring Boot | 4.1.1 |
| Aplicações HTTP | Spring MVC |
| Build | Maven Wrapper e Maven multi-module |
| Cliente HTTP | `java.net.http.HttpClient` |
| Concorrência | Virtual threads e `ExecutorService` nativo |
| Mensageria | Apache Kafka 4.3.1 em KRaft |
| Integração Kafka | Spring for Apache Kafka |
| Persistência | PostgreSQL e Spring Data JDBC |
| Migração de banco | Flyway |
| Containers | Docker/OCI com runtime Java 25 |
| Orquestração | Kubernetes local |
| Manifests próprios | Kustomize |
| Instrumentação | OpenTelemetry Java Agent e instrumentação manual seletiva |
| Coleta de telemetria | OpenTelemetry Collector via Helm |
| Métricas | Prometheus via Helm |
| Visualização e alertas | Grafana via Helm |
| Extensão comercial futura | Datadog, sem fazer parte do primeiro ciclo |
| Testes de integração | Testcontainers |
| Carga | k6 |
| Injeção de falhas | Endpoints administrativos controlados e, depois, Toxiproxy |

As versões devem ficar fixadas nos arquivos de build e infraestrutura. Antes da implementação, deve-se confirmar se ainda são as versões estáveis e compatíveis mais recentes.

### Decisões técnicas

- Usar **Spring MVC**, pois o foco é entender código bloqueante escalando com virtual threads. WebFlux não será usado no caminho principal.
- Usar o `HttpClient` nativo do Java para tornar explícito o uso dos recursos da plataforma.
- Usar Spring Data JDBC inicialmente, evitando que detalhes de ORM escondam parte do comportamento observado.
- Usar apenas APIs estáveis no perfil padrão.
- Manter experimentos com APIs preview em um perfil ou módulo separado.
- Usar instrumentação automática do OpenTelemetry antes de adicionar spans e métricas manuais.
- Usar um broker Kafka e uma instância PostgreSQL no ambiente local para reduzir o consumo de recursos.

## 4. Domínio de negócio

### Nome

- Projeto: `OrderLab`
- Repositório/diretório: `order-lab`
- Namespace Kubernetes: `order-lab`
- Ambiente Datadog: `local-lab`

### Caso de uso principal

Um cliente solicita um checkout. O sistema consulta preço, disponibilidade e análise de pagamento, cria o pedido e publica um evento para processamento posterior.

Fluxo planejado:

1. Receber `POST /checkouts`.
2. Consultar preço e disponibilidade.
3. Realizar análise/autorização de pagamento.
4. Reservar estoque.
5. Persistir o pedido.
6. Publicar `orders.confirmed` ou `orders.cancelled`.
7. Processar pedidos confirmados no fulfillment.
8. Atualizar o estado final do pedido.

### Estados simplificados do pedido

```text
CREATED
  -> VALIDATING
  -> CONFIRMED
  -> FULFILLING
  -> COMPLETED

Em caso de falha:
  -> CANCELLED
  -> COMPENSATION_PENDING
```

## 5. Serviços

### `checkout`

Responsável por:

- expor a API principal;
- coordenar o checkout;
- executar chamadas concorrentes;
- persistir pedidos;
- iniciar publicação dos eventos de domínio.

Principais sinais:

- taxa de requisições;
- latência p50, p95 e p99;
- taxa de erro;
- duração das dependências;
- saturação do banco;
- traces do fluxo completo.

### `pricing`

Responsável por:

- consultar preços;
- aplicar descontos simples;
- simular cache hit e cache miss;
- introduzir latência controlada em exercícios.

### `inventory`

Responsável por:

- consultar disponibilidade;
- reservar estoque;
- liberar reservas após cancelamento;
- demonstrar concorrência, locks e idempotência.

### `payment`

Responsável por:

- simular análise e autorização de pagamento;
- produzir respostas aprovadas, negadas ou com timeout;
- permitir taxas de erro configuráveis para os laboratórios.

Nenhum provedor de pagamento real será acessado.

### `fulfillment`

Responsável por:

- consumir `orders.confirmed`;
- simular separação e expedição;
- atualizar o processamento;
- demonstrar consumer lag, retry, dead-letter topic e idempotência.

## 6. Arquitetura lógica

```text
                         +------------------+
                         |       k6         |
                         +--------+---------+
                                  |
                                  v
                         +------------------+
                         |     checkout     |
                         +--+------+------+--+
                            |      |      |
                    HTTP    | HTTP | HTTP |
                            v      v      v
                       +--------+ +---------+ +---------+
                       |pricing | |inventory| | payment |
                       +---+----+ +----+----+ +----+----+
                           |           |           |
                           +-----------+-----------+
                                       |
                                  PostgreSQL
                                       |
                         +-------------+-------------+
                         |                           |
                         v                           v
                  orders.confirmed            orders.cancelled
                         |
                         v
                  +-------------+
                  | fulfillment |
                  +-------------+

Aplicações -> OpenTelemetry -> Prometheus -> Grafana
Kubernetes -----------------> Prometheus -> Grafana
```

### Trace distribuída esperada

```text
POST /checkouts
├── HTTP pricing
│   └── JDBC SELECT price
├── HTTP inventory
│   └── JDBC SELECT/UPDATE stock
├── HTTP payment
│   └── payment simulation
├── JDBC INSERT order
└── Kafka PRODUCE orders.confirmed
    └── Kafka CONSUME fulfillment
        └── JDBC UPDATE order
```

## 7. Java 25 e concorrência

### Caminho estável

O perfil padrão deverá usar:

- virtual threads habilitadas pelo Spring Boot;
- `Executors.newVirtualThreadPerTaskExecutor()` para fan-out;
- `java.net.http.HttpClient.send(...)` para HTTP síncrono;
- `ScopedValue` para experiências de contexto imutável;
- records para comandos, respostas e eventos;
- sealed types quando ajudarem a modelar resultados de negócio;
- `Semaphore` para limitar acesso a dependências escassas.

Configuração base:

```properties
spring.threads.virtual.enabled=true
```

Virtual threads não devem ser agrupadas em um pool. Quando for necessário limitar concorrência contra uma dependência, deverá ser utilizado um mecanismo como `Semaphore`.

### Fan-out concorrente

O checkout terá uma operação que consulta dependências em paralelo. O laboratório deve medir a diferença entre:

- chamadas sequenciais;
- fan-out em platform threads;
- fan-out em virtual threads.

### Caminho preview

Structured Concurrency continua sendo preview no Java 25. Seu uso deverá ficar isolado em um perfil Maven ou módulo experimental que habilite `--enable-preview` tanto na compilação quanto na execução.

O caminho principal da aplicação não poderá depender dessa API.

### Experimentos obrigatórios

- I/O bloqueante com muitas requisições concorrentes.
- Operação CPU-bound para demonstrar que virtual threads não aumentam capacidade de CPU.
- Limitação de chamadas externas com `Semaphore`.
- Cancelamento e timeout de tarefas concorrentes.
- Lock contention no estoque.
- Inspeção de thread dump.
- Investigação de pinning ou bloqueios prolongados, quando reproduzíveis.

## 8. Kafka e eventos

### Tópicos iniciais

```text
orders.confirmed
orders.cancelled
orders.fulfillment
orders.dead-letter
```

### Regras

- Eventos terão `eventId`, `orderId`, `occurredAt`, `eventType`, `version` e payload.
- Consumidores deverão ser idempotentes.
- O número de tentativas e o backoff deverão ser configuráveis.
- Mensagens que excederem o limite de tentativas irão para `orders.dead-letter`.
- O contexto de trace deverá atravessar produtor e consumidor.
- Não usar dados de alta cardinalidade como tags globais no Datadog.

### Evolução posterior

Após o fluxo básico estar observável, implementar o padrão transactional outbox para evitar inconsistência entre a transação PostgreSQL e a publicação Kafka.

## 9. Persistência

Para o laboratório local será usada uma única instância PostgreSQL, com bancos ou schemas separados por serviço. Isso simplifica a infraestrutura sem permitir acesso direto às tabelas de outro serviço.

Regras:

- cada serviço possui suas próprias migrations;
- um serviço não consulta tabelas de outro serviço;
- queries devem aparecer nas traces JDBC;
- o pool de conexões deve ter limite pequeno e configurável para permitir exercícios de saturação;
- credenciais devem ser fornecidas por Secret do Kubernetes;
- nenhuma senha deve ser versionada no Git.

## 10. Kubernetes local

Antes da implementação, identificar o contexto atual com `kubectl current-context` e confirmar se o cluster é Docker Desktop, kind, minikube ou outro. A forma de disponibilizar imagens locais será adaptada ao runtime encontrado.

### Recursos planejados

- namespace `order-lab`;
- Deployments dos cinco serviços;
- Services do tipo ClusterIP;
- Ingress para `checkout`;
- StatefulSet e volume para Kafka em KRaft;
- StatefulSet e volume para PostgreSQL;
- ConfigMaps;
- Secrets;
- readiness, liveness e startup probes;
- resource requests e limits;
- Jobs de criação/validação de tópicos, se necessários;
- Job ou pod dedicado para k6;
- NetworkPolicies em uma fase avançada.

### Organização

```text
infrastructure/kubernetes/
├── base/
│   ├── applications/
│   ├── kafka/
│   ├── postgres/
│   └── kustomization.yaml
└── overlays/
    └── local/
        ├── patches/
        └── kustomization.yaml
```

Helm será utilizado para OpenTelemetry Collector, Prometheus e Grafana. Os componentes do próprio OrderLab serão declarados com manifests e Kustomize para tornar o aprendizado de Kubernetes mais visível.

## 11. Plano principal de observabilidade

### Ordem de aprendizagem

```text
OpenTelemetry -> Prometheus -> Grafana
```

As três ferramentas serão estudadas em sequência, mas funcionarão integradas:

- **OpenTelemetry** instrumenta as aplicações, padroniza a telemetria e a envia ao Collector;
- **Prometheus** coleta, armazena e consulta as métricas;
- **Grafana** consulta o Prometheus e apresenta dashboards e alertas.

### Limite desta etapa

O primeiro ciclo será centrado em **métricas**. O OpenTelemetry também produzirá traces, mas Prometheus não armazena traces. Inicialmente, eles serão validados no Collector com um exporter de diagnóstico. Quando for necessário navegar e pesquisar traces no Grafana, Tempo poderá ser incluído como uma extensão posterior, sem mudar a instrumentação das aplicações.

### Arquitetura alvo

```text
Aplicações Java 25
    |
    | OTLP: métricas e traces
    v
OpenTelemetry Collector
    |                         Kubernetes / Kafka / PostgreSQL
    | endpoint de métricas                  |
    +-------------------+-------------------+
                        |
                        v
                   Prometheus
                        |
                        | PromQL
                        v
                     Grafana
```

### OpenTelemetry: conteúdo a praticar

- anexar o OpenTelemetry Java Agent sem alterar o código de negócio;
- configurar `service.name`, `service.version`, `deployment.environment.name` e atributos de recurso;
- receber OTLP gRPC ou HTTP no Collector;
- compreender receivers, processors, exporters e pipelines;
- instrumentar automaticamente HTTP, JDBC e Kafka;
- criar manualmente apenas métricas e spans que representem operações de negócio;
- propagar contexto por chamadas HTTP, tarefas em virtual threads e mensagens Kafka;
- usar batch, memory limiter e filtros no Collector;
- aplicar sampling às traces;
- evitar dados pessoais e atributos de alta cardinalidade;
- validar a telemetria produzida antes de configurar os dashboards.

### Prometheus: conteúdo a praticar

- compreender o modelo pull e o processo de scraping;
- configurar targets e service discovery no Kubernetes;
- coletar métricas expostas pelo Collector e pelos componentes do cluster;
- consultar métricas com PromQL;
- trabalhar com counters, gauges e histograms;
- calcular rate, error rate, p50, p95 e p99;
- acompanhar JVM, pools JDBC, Kafka, pods e containers;
- entender labels e controlar cardinalidade;
- criar recording rules e alerting rules;
- definir retenção e limites compatíveis com o Kubernetes local.

### Grafana: conteúdo a praticar

- provisionar o Prometheus como data source por arquivo;
- construir dashboards reproduzíveis e versionáveis;
- usar variáveis para ambiente, serviço, namespace e versão;
- criar painéis RED para aplicações e USE para infraestrutura;
- visualizar JVM, virtual threads, Kafka e Kubernetes;
- configurar alertas a partir das métricas;
- criar links entre painéis para facilitar investigação;
- exportar dashboards como código, sem configuração manual obrigatória.

### Dashboards mínimos

1. **OrderLab Overview:** tráfego, erros, latência e saúde dos serviços.
2. **Java/JVM:** heap, GC, CPU, classes, platform threads e sinais disponíveis de virtual threads.
3. **Kubernetes:** CPU, memória, restarts, throttling e estado dos pods.
4. **Kafka:** produção, consumo, falhas e consumer lag.
5. **Dependências:** latência e erro de pricing, inventory, payment e PostgreSQL.
6. **Comparação de concorrência:** platform threads versus virtual threads sob a mesma carga k6.

### Métricas de negócio iniciais

```text
orderlab.checkout.requests
orderlab.checkout.duration
orderlab.orders.created
orderlab.orders.confirmed
orderlab.orders.cancelled
orderlab.payment.outcomes
orderlab.inventory.reservations
orderlab.fulfillment.processing.duration
```

Identificadores como `orderId`, `customerId` e `eventId` não poderão ser labels de métricas. Eles poderão existir em logs ou spans quando necessários para o laboratório.

### Critérios de conclusão da trilha

- todas as aplicações enviam telemetria ao Collector;
- o Collector permanece saudável sob a carga prevista;
- Prometheus apresenta todos os targets esperados como `UP`;
- consultas PromQL calculam taxa de requisição, erros e percentis de latência;
- Grafana é provisionado automaticamente com data source e dashboards;
- uma carga k6 aparece nos dashboards em poucos segundos;
- é possível comparar platform threads e virtual threads usando as mesmas métricas;
- uma falha controlada gera sinais suficientes para identificar serviço, impacto e provável causa;
- manifests, values Helm, dashboards e regras ficam versionáveis no repositório;
- nenhuma métrica utiliza labels com cardinalidade ilimitada.

### Organização dos arquivos

```text
infrastructure/observability/
├── opentelemetry/
│   ├── values.yaml
│   └── collector-config.yaml
├── prometheus/
│   ├── values.yaml
│   └── rules/
└── grafana/
    ├── values.yaml
    ├── provisioning/
    └── dashboards/
```

## 11A. Extensão futura: Datadog

### Importante

O ambiente local executará o **Datadog Agent** e o **Datadog Cluster Agent**. A interface, ingestão e armazenamento permanecem no Datadog SaaS. Portanto, serão necessários:

- conta Datadog ou trial;
- API key;
- site correto da organização, por exemplo `datadoghq.com` ou outro;
- acesso de saída do cluster para o Datadog.

A API key deverá existir somente em Secret local e nunca ser gravada no repositório.

### Instalação planejada

- Helm chart oficial com versão fixada;
- Agent como DaemonSet;
- Cluster Agent habilitado;
- kube-state-metrics;
- coleta de logs;
- APM por Unix Domain Socket quando suportado no cluster local;
- Admission Controller para injetar o Java Agent;
- Continuous Profiler habilitado apenas para o ambiente do laboratório;
- integração de Kafka e PostgreSQL.

### Unified Service Tagging

Todos os workloads deverão declarar:

```text
env=local-lab
service=<nome-do-servico>
version=<versao-da-aplicacao>
team=order-lab
```

### Sinais a coletar

- métricas de requisições e JVM;
- logs estruturados em JSON;
- trace e span IDs nos logs;
- traces HTTP de entrada e saída;
- spans JDBC;
- spans de produção e consumo Kafka;
- métricas de pods, nodes e containers;
- métricas do Kafka e consumer lag;
- profiles de CPU, alocação e locks;
- eventos de deployment.

### Estratégia de instrumentação

1. Começar com instrumentação automática.
2. Confirmar que HTTP, JDBC e Kafka aparecem automaticamente.
3. Adicionar spans customizados somente em operações de negócio que não estejam claras.
4. Evitar instrumentar cada método.
5. Validar cardinalidade antes de criar tags customizadas.

## 12. Estrutura do repositório

```text
order-lab/
├── .spec/
│   └── codex.md
├── README.md
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
├── checkout/
├── pricing/
├── inventory/
├── payment/
├── fulfillment/
├── contracts/
│   ├── asyncapi/
│   └── json-schema/
├── infrastructure/
│   ├── kubernetes/
│   ├── observability/
│   ├── datadog/
│   ├── kafka/
│   ├── postgres/
│   └── load-tests/
├── dashboards/
├── monitors/
├── labs/
└── docs/
```

Evitar uma biblioteca compartilhada com regras de negócio. O compartilhamento inicial deve se limitar a contratos, configuração de build e utilidades técnicas realmente comuns.

## 13. Fases de implementação

## Fase 0 — Descoberta e pré-requisitos

### Atividades

- verificar Java, Maven, Docker, Helm e kubectl;
- identificar o Kubernetes local e seus recursos disponíveis;
- verificar StorageClass e Ingress Controller;
- confirmar os recursos necessários para OpenTelemetry Collector, Prometheus e Grafana;
- confirmar StorageClass para retenção local do Prometheus;
- medir memória e CPU disponíveis para definir requests e limits;
- confirmar as versões finais da stack.

### Critério de conclusão

- cluster acessível;
- ferramentas necessárias funcionando;
- credenciais fora do repositório;
- decisão registrada sobre como carregar imagens no cluster.

## Fase 1 — Fundação do projeto

### Atividades

- criar o agregador Maven e os cinco módulos;
- configurar Java 25 e Maven Wrapper;
- criar endpoints de health e informações de build;
- criar imagens OCI pequenas e reproduzíveis;
- adicionar testes unitários básicos;
- adicionar formato estruturado de logs;
- preparar configuração por ambiente.

### Critério de conclusão

- todos os módulos compilam e testam;
- cada serviço inicia localmente;
- as imagens são construídas com versão explícita;
- nenhuma credencial está no código.

## Fase 2 — Fluxo HTTP síncrono

### Atividades

- implementar `pricing`, `inventory` e `payment`;
- implementar `checkout` como orquestrador;
- usar `java.net.http.HttpClient` em modo síncrono;
- definir connect timeout e request timeout;
- persistir o pedido no PostgreSQL;
- criar testes de integração com Testcontainers;
- documentar a API com exemplos HTTP.

### Critério de conclusão

- um checkout percorre os serviços e é persistido;
- falhas HTTP geram respostas consistentes;
- timeouts são configurados e testados;
- testes de integração validam o caminho feliz e os principais erros.

## Fase 3 — Kubernetes funcional

### Atividades

- criar namespace e manifests base;
- subir PostgreSQL e Kafka;
- implantar os serviços;
- configurar DNS interno entre serviços;
- configurar probes;
- configurar resources;
- expor apenas o checkout por Ingress;
- criar scripts ou comandos documentados para deploy e remoção.

### Critério de conclusão

- todos os pods ficam Ready;
- o checkout completo funciona a partir do host;
- reiniciar um pod não perde a configuração;
- Kafka e PostgreSQL usam volumes persistentes quando suportados.

## Fase 4 — OpenTelemetry, Prometheus e Grafana

### Atividades

- instalar o OpenTelemetry Collector com Helm;
- anexar o OpenTelemetry Java Agent aos serviços;
- padronizar nomes, versões, ambiente e atributos de recurso;
- receber OTLP e validar métricas e traces no exporter de diagnóstico;
- instalar Prometheus com retenção e recursos adequados ao ambiente local;
- expor as métricas do Collector para scraping;
- coletar métricas de aplicações, JVM e Kubernetes;
- instalar Grafana e provisionar o Prometheus como data source;
- criar os dashboards OrderLab Overview, Java/JVM e Kubernetes;
- executar carga k6 e validar a atualização dos painéis;
- criar os primeiros alertas de erro, latência e indisponibilidade;
- documentar consultas PromQL usadas nos exercícios.

### Critério de conclusão

- os cinco serviços enviam telemetria ao Collector;
- Prometheus mostra os targets esperados como `UP`;
- métricas das aplicações, JVM, pods e containers estão disponíveis;
- consultas PromQL calculam rate, error rate e percentis de latência;
- Grafana inicia com data source e dashboards provisionados;
- o dashboard básico reage a uma carga k6 e a uma falha controlada;
- traces HTTP, JDBC e Kafka são geradas e validadas no Collector, ainda sem retenção de longo prazo.

## Fase 5 — Virtual threads

### Atividades

- criar perfil com platform threads;
- criar perfil com virtual threads;
- habilitar virtual threads no Spring Boot;
- implementar fan-out concorrente;
- executar a mesma carga nos dois perfis;
- coletar throughput, p95, p99, CPU, memória e conexões;
- analisar profiles e thread dumps;
- testar limitação com `Semaphore`;
- testar carga CPU-bound e I/O-bound.

### Critério de conclusão

- resultados comparativos documentados;
- traces preservam o contexto nas tarefas concorrentes;
- é possível explicar quando virtual threads ajudam e quando não ajudam;
- saturação da dependência permanece controlada.

## Fase 6 — Processamento assíncrono com Kafka

### Atividades

- publicar `orders.confirmed` e `orders.cancelled`;
- criar consumidor no `fulfillment`;
- propagar contexto de trace;
- configurar retries e dead-letter topic;
- implementar idempotência;
- instrumentar métricas de consumo;
- criar dashboard de throughput e consumer lag.

### Critério de conclusão

- uma trace conecta o produtor ao consumidor;
- mensagens duplicadas não duplicam efeitos;
- falhas permanentes chegam ao dead-letter topic;
- consumer lag está visível no Prometheus e no Grafana.

## Fase 7 — Resiliência e consistência

### Atividades

- adicionar retries apenas em operações seguras;
- implementar exponential backoff com jitter;
- adicionar circuit breaker onde fizer sentido;
- implementar transactional outbox;
- criar compensação para estoque e pagamento;
- documentar garantias de entrega e idempotência;
- testar indisponibilidade temporária do Kafka e PostgreSQL.

### Critério de conclusão

- um pedido confirmado não é perdido por falha entre banco e Kafka;
- retries não causam efeitos duplicados;
- compensações ficam visíveis em logs, métricas e traces.

## Fase 8 — Cenários de falha

### Cenários

1. Payment com taxa de erro configurável.
2. Pricing com latência crescente.
3. Inventory com lock contention.
4. Pool JDBC saturado.
5. Consumer fulfillment parado.
6. Kafka temporariamente indisponível.
7. Pod entrando em CrashLoopBackOff.
8. Limite de CPU causando throttling.
9. Limite de memória causando OOMKilled.
10. Erro de alta cardinalidade em uma tag controlada.
11. Vazamento de memória somente no perfil de laboratório.
12. Carga CPU-bound executada em virtual threads.

Cada cenário deverá conter:

- forma de ativar;
- comportamento esperado;
- sinais esperados no Datadog;
- consulta ou dashboard sugerido;
- hipótese de diagnóstico;
- forma de desativar e recuperar o ambiente.

### Critério de conclusão

- todos os cenários são reproduzíveis;
- cada cenário possui um runbook curto;
- a causa pode ser encontrada usando a telemetria coletada.

## Fase 9 — Dashboards, monitores e SLOs

### Dashboards

- visão geral RED: rate, errors e duration;
- visão de recursos USE: utilization, saturation e errors;
- JVM e virtual threads;
- Kafka e consumer lag;
- PostgreSQL e pool de conexões;
- Kubernetes workloads;
- comparação de versões/releases.

### Monitores

- taxa de erro do checkout;
- latência p95/p99;
- ausência de tráfego inesperada;
- consumer lag;
- reinícios de pod;
- CPU throttling;
- memória próxima ao limit;
- disponibilidade do Kafka e PostgreSQL.

### SLO inicial

Definir um SLO educacional para o checkout, por exemplo:

- disponibilidade baseada em requisições bem-sucedidas;
- latência abaixo de um limite acordado;
- janela curta apropriada ao laboratório;
- error budget visível.

Os números definitivos deverão ser escolhidos após obter uma baseline, evitando metas arbitrárias sem medição.

### Critério de conclusão

- dashboards contam a história do sistema sem depender de acesso ao código;
- monitores disparam nos cenários correspondentes;
- o SLO e o consumo do error budget são compreendidos.

## Fase 10 — Recursos avançados do Java 25

### Atividades

- usar `ScopedValue` em um experimento de contexto imutável;
- comparar com o uso tradicional de `ThreadLocal`;
- criar módulo preview de Structured Concurrency;
- estudar cancelamento de subtarefas relacionadas;
- verificar representação das tarefas nas ferramentas disponíveis;
- registrar limitações da instrumentação encontrada.

### Critério de conclusão

- o build estável continua sem `--enable-preview`;
- o experimento preview é reproduzível separadamente;
- diferenças de propagação de contexto, cancelamento e observabilidade estão documentadas.

## Fase 11 — Automação e documentação final

### Atividades

- automatizar build, testes e criação das imagens;
- documentar deploy e teardown;
- exportar dashboards e monitores como código quando possível;
- criar runbooks dos incidentes;
- documentar custos e cuidados de ingestão no Datadog;
- criar roteiro de demonstração do projeto.

### Critério de conclusão

- uma pessoa consegue preparar o ambiente seguindo apenas o README;
- um comando ou sequência curta executa o deploy;
- um comando ou sequência curta remove os recursos do OrderLab;
- nenhuma credencial ou dado sensível é versionado;
- os principais laboratórios possuem resultados esperados.

## 14. Laboratórios sugeridos

### Laboratório 1 — Primeira trace

Executar um checkout e localizar:

- requisição de entrada;
- chamadas HTTP;
- queries JDBC;
- logs correlacionados;
- consumo de recursos do pod.

### Laboratório 2 — Caça à latência

Adicionar atraso ao payment e usar flame graph, spans e métricas para localizar a dependência lenta.

### Laboratório 3 — Platform versus virtual threads

Executar duas cargas idênticas e comparar throughput, latência, quantidade de threads do sistema, CPU e memória.

### Laboratório 4 — Kafka atrasado

Parar o fulfillment, acumular eventos e observar consumer lag. Reiniciar o consumidor e acompanhar a recuperação.

### Laboratório 5 — Saturação do banco

Reduzir o pool JDBC, aumentar concorrência e identificar tempo de espera, impacto em latência e propagação da falha.

### Laboratório 6 — Incidente Kubernetes

Aplicar limits baixos, provocar throttling ou OOMKilled e investigar a partir do Datadog.

### Laboratório 7 — Deploy regressivo

Implantar uma versão mais lenta do payment e correlacionar a mudança de versão com a degradação.

### Laboratório 8 — Retry storm

Indisponibilizar uma dependência, usar retries mal configurados, observar amplificação de carga e depois corrigir com limites, backoff e jitter.

## 15. Testes

### Testes unitários

- regras de preço;
- transições de estado;
- idempotência;
- decisões de retry e compensação.

### Testes de integração

- PostgreSQL com Testcontainers;
- Kafka com Testcontainers;
- chamadas HTTP entre componentes;
- publicação, consumo, retry e DLT;
- migrations Flyway.

### Testes end-to-end

- checkout completo no Kubernetes;
- cancelamento;
- falha de pagamento;
- processamento assíncrono;
- validação da saúde dos componentes.

### Testes de carga

- baseline com baixa concorrência;
- carga crescente;
- pico repentino;
- soak test curto;
- comparação platform/virtual threads;
- recuperação depois de indisponibilidade.

## 16. Segurança e boas práticas

- não armazenar API keys, senhas ou tokens no Git;
- fornecer `.env.example` apenas com nomes de variáveis;
- criar Secrets localmente por comando documentado;
- evitar registrar payloads sensíveis;
- não usar número de cartão real nem dados pessoais reais;
- limitar endpoints de injeção de falha ao perfil `lab`;
- usar imagens com tags fixas, evitando `latest`;
- definir requests e limits apropriados ao cluster local;
- configurar retenção, cardinalidade e volume de telemetria para evitar consumo desnecessário de disco e memória;
- remover ou reduzir componentes opcionais quando o laboratório não estiver em uso.

## 17. Fora do escopo inicial

- autenticação completa de usuários;
- interface web;
- gateway complexo;
- service mesh;
- múltiplos brokers Kafka;
- alta disponibilidade real;
- pagamentos reais;
- Kubernetes de produção;
- autoscaling antes de existir uma baseline;
- backend permanente de traces antes da conclusão da trilha de métricas;

Esses itens podem virar extensões depois que o fluxo principal estiver estável e observável.

## 18. Definição de pronto do projeto

O OrderLab será considerado completo quando:

- todos os serviços executarem com Java 25 e Spring Boot compatível;
- o ecossistema subir no Kubernetes local;
- o checkout funcionar por HTTP síncrono;
- houver concorrência observável com virtual threads;
- o evento atravessar Kafka e for processado pelo fulfillment;
- métricas de HTTP, JDBC, Kafka, JVM e Kubernetes estiverem disponíveis no Prometheus;
- traces conectarem HTTP, JDBC, Kafka e consumidor e forem validadas no Collector;
- os dashboards Grafana permitirem investigar carga, erros e saturação;
- existirem dashboards, monitores e ao menos um SLO;
- os principais cenários de falha forem reproduzíveis;
- houver comparação documentada entre platform e virtual threads;
- o setup e o teardown forem documentados;
- nenhuma credencial estiver versionada.

## 19. Primeiro marco recomendado

O primeiro marco funcional será:

> Executar um checkout no Kubernetes local, visualizar no Grafana suas métricas RED e confirmar no OpenTelemetry Collector uma trace contendo a entrada no `checkout`, as chamadas HTTP aos serviços, a persistência PostgreSQL, a publicação Kafka e o consumo pelo `fulfillment`.

Somente depois desse marco serão adicionados resiliência avançada, falhas controladas, Structured Concurrency preview e automações adicionais.
