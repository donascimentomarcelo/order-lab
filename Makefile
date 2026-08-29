ifeq ($(OS),Windows_NT)
MVNW := ./mvnw.cmd
else
MVNW := ./mvnw
endif

SERVICE ?= checkout

.DEFAULT_GOAL := help

.PHONY: help clean test verify package run checkout pricing inventory payment fulfillment

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
