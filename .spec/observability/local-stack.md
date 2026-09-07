# Stack local de observabilidade

## Status

**OpenTelemetry Collector, Prometheus, PostgreSQL exporter e Grafana implementados e validados em Docker Compose.**

## Objetivo

Disponibilizar uma stack local reproduzível para receber traces OTLP do Java Agent, coletar métricas Prometheus dos serviços e consultar essas métricas pelo Grafana.

## Topologia

```text
Pricing + OpenTelemetry Java Agent
        │
        ├── OTLP/HTTP :4318 ──> OpenTelemetry Collector ──> debug exporter
        │
        └── /actuator/prometheus <── scrape ── Prometheus <── Grafana
```

O Collector não armazena traces. Nesta etapa, o `debug` exporter confirma o recebimento nos logs. Para pesquisar e visualizar traces historicamente será necessário adicionar Tempo, Jaeger ou outro backend compatível.

O `debug` exporter está em modo detalhado exclusivamente para aprendizado local. Ele pode registrar consultas SQL sanitizadas, nomes de usuário do banco, parâmetros HTTP e metadados do processo; não deve ser usado assim em produção nem receber dados sensíveis.

## Componentes

| Componente | Imagem | Porta local | Responsabilidade |
|---|---|---:|---|
| OpenTelemetry Collector Contrib | `otel/opentelemetry-collector-contrib:0.159.0` | `4317`, `4318`, `13133` | Receber, processar e encaminhar traces |
| Prometheus | `prom/prometheus:v3.14.0` | `9090` | Fazer scrape e armazenar métricas por 7 dias |
| PostgreSQL exporter | `quay.io/prometheuscommunity/postgres-exporter:v0.20.1` | `9187` | Expor disponibilidade e métricas do PostgreSQL |
| Grafana | `grafana/grafana:13.2.0` | `3000` | Consultar e visualizar métricas |

## Arquivos

```text
infrastructure/observability/
├── compose.yaml
├── javaagent.properties
├── otel-collector-config.yaml
├── prometheus.yml
├── grafana/dashboards/pricing.json
├── grafana/provisioning/dashboards/orderlab.yaml
└── grafana/provisioning/datasources/prometheus.yaml
```

As versões estão fixadas para que o laboratório não mude silenciosamente ao baixar imagens novas.

## Operação

Subir:

```powershell
make observability-up
```

Consultar o estado:

```powershell
make observability-status
```

Acompanhar logs, incluindo os traces recebidos pelo Collector:

```powershell
make observability-logs
```

Parar sem apagar os volumes:

```powershell
make observability-down
```

## Endereços

| Recurso | Endereço |
|---|---|
| Saúde do Collector | `http://localhost:13133/` |
| OTLP gRPC | `http://localhost:4317` |
| OTLP HTTP | `http://localhost:4318` |
| Prometheus | `http://localhost:9090` |
| Targets do Prometheus | `http://localhost:9090/targets` |
| Grafana | `http://localhost:3000` |
| Dashboard do Pricing | `http://localhost:3000/d/orderlab-pricing` |
| Métricas do PostgreSQL exporter | `http://localhost:9187/metrics` |

## Grafana local

Credenciais padrão do laboratório:

```text
usuário: admin
senha: orderlab
```

Elas podem ser substituídas antes do primeiro start:

```powershell
$env:GRAFANA_ADMIN_USER = "meu-usuario"
$env:GRAFANA_ADMIN_PASSWORD = "uma-senha-local"
make observability-up
```

O datasource `Prometheus`, UID `prometheus`, é criado automaticamente e definido como padrão. Credenciais reais não devem ser versionadas.

O dashboard `OrderLab — Pricing` é provisionado automaticamente na pasta `OrderLab`. Alterações feitas apenas pela interface não persistem; o arquivo JSON versionado é a fonte de verdade.

## Integração com o pricing

Com a stack disponível:

```powershell
make pricing-otel
```

O Java Agent envia traces por `OTLP/HTTP` para `http://localhost:4318`. As métricas OpenTelemetry do agente permanecem desabilitadas porque métricas da aplicação são publicadas pelo Micrometer.

O Prometheus em container acessa a aplicação hospedada no Windows por:

```text
host.docker.internal:8081/actuator/prometheus
```

Se a porta `8081` estiver ocupada durante um experimento, outra instância pode ser iniciada assim:

```powershell
make pricing-otel PRICING_SERVER_PORT=18081
```

Para mudar o destino OTLP sem editar a configuração versionada:

```powershell
$env:OTEL_EXPORTER_OTLP_ENDPOINT = "http://localhost:14318"
make pricing-otel
```

## Persistência

Os dados do Prometheus e Grafana usam volumes nomeados do Docker. `make observability-down` preserva esses dados. A remoção deliberada dos volumes exigiria `docker compose down -v` e não faz parte dos comandos normais do laboratório.

## Critérios de aceite

- os quatro containers permanecem em estado `running`;
- saúde do Collector retorna HTTP 200;
- readiness do Prometheus retorna HTTP 200;
- saúde do Grafana retorna banco `ok`;
- datasource Prometheus é provisionado como padrão;
- target `pricing` aparece como `UP` quando a aplicação está na porta 8081;
- targets `postgresql` e `prometheus` aparecem como `UP`;
- `pg_up` informa se o exporter conseguiu conectar ao PostgreSQL;
- dashboard inicial do Pricing é provisionado e suas consultas são válidas;
- uma cotação instrumentada chega ao Collector com spans HTTP e JDBC;
- spans HTTP e JDBC preservam o mesmo `traceId`;
- nenhum backend de traces ou dashboard é antecipado nesta etapa.

## Resultado da validação

- Collector `0.159.0` iniciou com receivers OTLP gRPC e HTTP;
- Prometheus `3.14.0` aceitou sua configuração e ficou pronto;
- Grafana `13.2.0` iniciou com banco `ok`;
- datasource Prometheus respondeu `OK`;
- target `pricing` foi confirmado como `UP`;
- cotação de `NOTEBOOK-001` respondeu com sucesso;
- Collector recebeu `GET /prices/{productId}` e `SELECT orderlab.products` no mesmo trace.

## Próximas etapas

1. adicionar Grafana Tempo para pesquisar traces;
2. adicionar Grafana Loki para pesquisar logs;
3. gerar carga com k6;
4. criar alertas e recording rules;
5. transportar a stack validada para Kubernetes.
