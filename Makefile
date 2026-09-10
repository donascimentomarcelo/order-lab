ifeq ($(OS),Windows_NT)
MVNW := ./mvnw.cmd
else
MVNW := ./mvnw
endif

SERVICE ?= checkout
OTEL_AGENT_VERSION ?= 2.31.1
OTEL_AGENT_DIR := .tools/opentelemetry
OTEL_AGENT_JAR := $(OTEL_AGENT_DIR)/opentelemetry-javaagent-$(OTEL_AGENT_VERSION).jar
PRICING_SERVER_PORT ?= 8081
OTEL_JAVAAGENT_CONFIG := infrastructure/observability/javaagent.properties
OBSERVABILITY_COMPOSE := infrastructure/observability/compose.yaml
DATABASE_COMPOSE := compose.yaml
K6_SCRIPT := infrastructure/load-tests/pricing.js
K6_BASE_URL ?= http://localhost:8081
K6_PROFILE ?= load
K6_PRODUCT_ID ?= NOTEBOOK-001
K6_QUANTITY ?= 2

.DEFAULT_GOAL := help

.PHONY: help clean test verify package run checkout pricing inventory payment fulfillment db-up db-down db-status db-logs otel-agent pricing-otel observability-up observability-down observability-status observability-logs k6-pricing k6-pricing-smoke

help: ## Lista os comandos disponiveis
	@echo "Comandos disponiveis:"
	@echo "  make clean              Remove os artefatos de build"
	@echo "  make test               Executa os testes"
	@echo "  make verify             Executa clean verify em todos os modulos"
	@echo "  make package            Gera os pacotes sem pular os testes"
	@echo "  make run                Inicia o servico definido em SERVICE (padrao: checkout)"
	@echo "  make run SERVICE=pricing"
	@echo "  make checkout           Inicia o servico checkout"
	@echo "  make pricing            Inicia o servico pricing"
	@echo "  make db-up              Cria/inicia o PostgreSQL local"
	@echo "  make db-down            Para o PostgreSQL local"
	@echo "  make db-status          Exibe o estado do PostgreSQL local"
	@echo "  make db-logs            Acompanha os logs do PostgreSQL local"
	@echo "  make pricing-otel       Inicia o pricing instrumentado com OpenTelemetry"
	@echo "  make otel-agent         Baixa o OpenTelemetry Java Agent"
	@echo "  make observability-up   Sobe Collector, Prometheus, PostgreSQL exporter e Grafana"
	@echo "  make observability-down Para a stack de observabilidade"
	@echo "  make observability-status Exibe o estado da stack de observabilidade"
	@echo "  make observability-logs Acompanha os logs da stack de observabilidade"
	@echo "  make k6-pricing         Executa a carga gradual do pricing"
	@echo "  make k6-pricing-smoke   Executa uma verificacao curta do teste k6"
	@echo "  make inventory          Inicia o servico inventory"
	@echo "  make payment            Inicia o servico payment"
	@echo "  make fulfillment        Inicia o servico fulfillment"

clean:
	$(MVNW) clean

test:
	$(MVNW) test

verify:
	$(MVNW) clean verify

package:
	$(MVNW) package

ifeq ($(SERVICE),pricing)
run: db-up
endif

run:
	$(MVNW) -pl $(SERVICE) spring-boot:run

checkout inventory payment fulfillment:
	$(MVNW) -pl $@ spring-boot:run

pricing: db-up
	$(MVNW) -pl pricing spring-boot:run

db-up:
	docker compose -f $(DATABASE_COMPOSE) up -d --wait

db-down:
	docker compose -f $(DATABASE_COMPOSE) down

db-status:
	docker compose -f $(DATABASE_COMPOSE) ps

db-logs:
	docker compose -f $(DATABASE_COMPOSE) logs -f postgres

otel-agent:
	$(MVNW) org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy \
		-Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:$(OTEL_AGENT_VERSION) \
		-DoutputDirectory=$(OTEL_AGENT_DIR)

pricing-otel: db-up otel-agent
	$(MVNW) -pl pricing spring-boot:run \
		-Dspring-boot.run.agents=../$(OTEL_AGENT_JAR) \
		-Dspring-boot.run.jvmArguments=-Dotel.javaagent.configuration-file=../$(OTEL_JAVAAGENT_CONFIG) \
		-Dspring-boot.run.arguments=--server.port=$(PRICING_SERVER_PORT)

observability-up:
	docker compose -f $(OBSERVABILITY_COMPOSE) up -d

observability-down:
	docker compose -f $(OBSERVABILITY_COMPOSE) down

observability-status:
	docker compose -f $(OBSERVABILITY_COMPOSE) ps

observability-logs:
	docker compose -f $(OBSERVABILITY_COMPOSE) logs -f

k6-pricing:
	k6 run \
		-e BASE_URL=$(K6_BASE_URL) \
		-e PROFILE=$(K6_PROFILE) \
		-e PRODUCT_ID=$(K6_PRODUCT_ID) \
		-e QUANTITY=$(K6_QUANTITY) \
		$(K6_SCRIPT)

k6-pricing-smoke:
	$(MAKE) k6-pricing K6_PROFILE=smoke
