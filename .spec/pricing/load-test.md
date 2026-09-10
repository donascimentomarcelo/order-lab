# Pricing — teste de carga com k6

## Status

**Script, perfis, thresholds e comandos implementados. A execução da carga foi reservada ao usuário.**

## Objetivo

Gerar tráfego controlado no endpoint de cotação para observar no Grafana o comportamento HTTP, JVM, virtual threads, HikariCP e PostgreSQL. O teste mede a aplicação; ele não substitui testes funcionais ou um benchmark científico.

## Arquivo

```text
infrastructure/load-tests/pricing.js
```

## Perfis

### Smoke

- 1 usuário virtual;
- duração de 10 segundos;
- serve para confirmar URL, produto, banco e contrato da resposta.

### Load

Usa `ramping-arrival-rate`, mantendo a taxa solicitada independente da latência enquanto houver VUs disponíveis:

| Período | Taxa-alvo |
|---|---:|
| aquecimento — 10 s | 5 → 10 req/s |
| carga normal — 20 s | 10 → 25 req/s |
| pico — 20 s | 25 → 50 req/s |
| encerramento — 10 s | 50 → 0 req/s |

A duração planejada do perfil `load` é de 1 minuto, além de no máximo 10 segundos de `gracefulStop` caso ainda existam requisições em andamento.

O teste reserva 20 VUs e pode crescer até 200. `dropped_iterations > 0` significa que o k6 não conseguiu iniciar todas as requisições na taxa planejada.

## Thresholds

- mais de 99% dos checks devem passar;
- menos de 1% das requisições pode falhar;
- p95 deve permanecer abaixo de 500 ms;
- p99 deve permanecer abaixo de 1 s;
- nenhuma iteração pode ser descartada.

Falhar um threshold faz o k6 terminar com código diferente de zero. Isso é um resultado do experimento, não necessariamente um defeito no script.

## Preparação

Use quatro terminais se quiser acompanhar aplicação, traces e carga simultaneamente.

Terminal 1 — infraestrutura:

```powershell
make observability-up
make observability-status
```

Terminal 2 — aplicação instrumentada:

```powershell
make pricing-otel
```

Terminal 3 — spans recebidos pelo Collector:

```powershell
docker logs -f orderlab-otel-collector
```

Terminal 4 será usado para executar o k6.

Antes da carga, confirme:

```powershell
curl.exe http://localhost:8081/actuator/health
curl.exe "http://localhost:8081/prices/NOTEBOOK-001?quantity=2"
```

Abra o dashboard e selecione um período curto, como `Last 5 minutes`, com atualização de 5 segundos:

```text
http://localhost:3000/d/orderlab-pricing
```

## Execução pelo usuário

Primeiro execute o smoke test:

```powershell
make k6-pricing-smoke
```

Se ele passar, execute a carga gradual:

```powershell
make k6-pricing
```

Execução direta equivalente:

```powershell
k6 run -e BASE_URL=http://localhost:8081 -e PROFILE=load -e PRODUCT_ID=NOTEBOOK-001 -e QUANTITY=2 infrastructure/load-tests/pricing.js
```

É possível mudar os parâmetros sem editar o script:

```powershell
make k6-pricing K6_PRODUCT_ID=PHONE-001 K6_QUANTITY=6
```

## O que observar no Grafana

1. A taxa HTTP deve acompanhar a rampa solicitada.
2. Compare p95 com conexões `pending` e tempo de aquisição do HikariCP.
3. Observe virtual threads montadas, em fila, carriers e eventos de pinning.
4. Verifique memória e pausas do GC durante o pico e a recuperação.
5. Confirme `Pricing disponível = 1` e `PostgreSQL disponível = 1`.
6. Se `pending` crescer com p95, a contenção provavelmente está no pool ou banco, não na quantidade de threads.

## Logs e traces durante a execução

Na stack atual, Grafana apresenta as métricas, mas Tempo e Loki ainda não foram instalados.

- logs produzidos pelo Spring aparecem no terminal que executa `make pricing-otel`; atualmente não há access log para imprimir toda requisição;
- traces recebidos aparecem no `debug` exporter do Collector:

```powershell
docker logs -f orderlab-otel-collector
```

O teste adiciona `User-Agent: orderlab-k6/1.0` e `X-Load-Test: k6-load`. O Java Agent foi configurado para capturar `X-Load-Test` nos spans como `http.request.header.x_load_test`, facilitando a identificação da carga. Para pesquisar e filtrar traces e logs pela interface do Grafana, as próximas etapas devem adicionar Tempo e Loki.

## Cuidados

- execute apenas contra o ambiente local;
- não aumente a carga enquanto o PostgreSQL ou o Pricing estiverem instáveis;
- o Collector está com `debug` detalhado e pode produzir muito texto durante a carga;
- não use identificadores únicos como labels Prometheus;
- preserve o resumo final produzido pelo k6 para comparar experimentos futuros.
