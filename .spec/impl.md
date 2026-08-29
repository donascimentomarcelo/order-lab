# Padrão de implementação colaborativa

## Objetivo

O OrderLab será desenvolvido com uma variação de Spec-Driven Development (SDD) que reserva a implementação das regras principais para o usuário e a validação para o Codex.

## Fluxo obrigatório

1. **Especificação:** Codex e usuário registram comportamento, contratos e critérios de aceite em `.spec/<serviço>/spec.md`.
2. **Esqueleto:** Codex cria pacotes, classes, tipos, interfaces e assinaturas dos métodos.
3. **Orientação no código:** métodos reservados ao usuário recebem `TODO(...)` com instruções objetivas, sem a solução pronta.
4. **Implementação:** o usuário substitui os `TODO(...)` pela própria implementação.
5. **Sinalização:** o usuário informa que concluiu a intervenção.
6. **Validação:** Codex revisa a implementação contra a especificação, cria ou completa testes, executa o build e valida o comportamento em runtime.
7. **Correções:** Codex relata divergências encontradas. Não reescreve a lógica principal do usuário sem solicitação, exceto por ajustes mecânicos claramente necessários para compilar ou testar.

## Convenção dos TODOs

Todo ponto reservado ao usuário deve usar o formato:

```kotlin
TODO(
    """
    USER IMPLEMENTATION:
    1. Primeira regra esperada.
    2. Segunda regra esperada.
    3. Resultado que deve ser retornado.
    """.trimIndent(),
)
```

O TODO deve explicar o comportamento, mas não fornecer o algoritmo completo pronto para copiar. Quando o usuário ainda estiver aprendendo a linguagem, deve incluir também sintaxe exemplificada, imports necessários, explicação dos operadores utilizados e placeholders explícitos para a parte que será implementada.

## Responsabilidades

### Codex antes da intervenção

- manter a especificação atualizada;
- criar contratos e estruturas coerentes;
- deixar o projeto compilável e inicializável quando tecnicamente possível;
- não implementar a regra central reservada ao usuário;
- indicar claramente quais arquivos aguardam intervenção.

### Usuário

- implementar somente os pontos marcados como `USER IMPLEMENTATION`;
- preservar os contratos definidos na especificação ou discutir alterações antes de modificá-los;
- avisar quando a implementação estiver pronta para validação.

### Codex depois da intervenção

- localizar TODOs remanescentes;
- revisar tipos, null safety, precisão monetária e tratamento de erros;
- criar testes unitários, de contrato e integração necessários;
- executar o Maven Wrapper;
- iniciar o serviço e testar endpoints relevantes;
- registrar o resultado e os próximos ajustes.

## Regras gerais

- não compartilhar regras de negócio entre microsserviços por uma biblioteca comum;
- priorizar código legível antes de abstrações prematuras;
- usar `BigDecimal` para valores monetários;
- manter I/O síncrono e bloqueante no caminho principal;
- não introduzir Kotlin Coroutines no primeiro ciclo;
- preservar compatibilidade com instrumentação OpenTelemetry;
- manter credenciais e configurações locais fora do Git.
