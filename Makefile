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

.DEFAULT_GOAL := help

.PHONY: help clean test verify package run checkout pricing inventory payment fulfillment otel-agent pricing-otel observability-up observability-down observability-status observability-logs

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
	@echo "  make pricing-otel       Inicia o pricing instrumentado com OpenTelemetry"
	@echo "  make otel-agent         Baixa o OpenTelemetry Java Agent"
	@echo "  make observability-up   Sobe Collector, Prometheus, PostgreSQL exporter e Grafana"
	@echo "  make observability-down Para a stack de observabilidade"
	@echo "  make observability-status Exibe o estado da stack de observabilidade"
	@echo "  make observability-logs Acompanha os logs da stack de observabilidade"
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

run:
	$(MVNW) -pl $(SERVICE) spring-boot:run

checkout pricing inventory payment fulfillment:
	$(MVNW) -pl $@ spring-boot:run

otel-agent:
	$(MVNW) org.apache.maven.plugins:maven-dependency-plugin:3.9.0:copy \
		-Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:$(OTEL_AGENT_VERSION) \
		-DoutputDirectory=$(OTEL_AGENT_DIR)

pricing-otel: otel-agent
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
