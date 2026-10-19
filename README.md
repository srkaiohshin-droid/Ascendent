# Ascendant — Superhuman Framework

Framework modular de super-humanos para Minecraft Java 1.20.1 com Forge 47.3.0.

## Ambiente
- Java 17
- Gradle Wrapper incluso no projeto
- Mod ID: `ascendant`
- Pacote-base: `br.com.ascendant`

## Compilar no Windows

Use JDK 17 para iniciar o Gradle. Neste ambiente, o `java` no PATH aponta para
Java 8u51 e ForgeGradle recusa iniciar com essa versao. Configure `JAVA_HOME`
para uma instalacao JDK 17 e coloque `$env:JAVA_HOME\bin` no inicio do PATH.

```powershell
.\gradlew.bat build
```

Executar os GameTests em servidor dedicado:

```powershell
.\gradlew.bat runGameTestServer
```

O artefato compilado deve ser procurado em `build/libs/`.

## Estado
A capability armazena IDs de poderes desbloqueados por jogador, com persistencia
NBT. Ainda nao ha habilidades de gameplay implementadas. Um GameTest verifica a
persistencia NBT e a copia da capability; a copia pelo evento de morte ainda
precisa de validacao direta.

## Documentacao
- `AI_CONTEXT.txt`: identidade, prioridades e restricoes.
- `AGENT_COORDINATION.txt`: fila e criterios das tarefas.
- `AGENT_LOG.txt`: historico de lotes e validacoes.
- `docs/ARCHITECTURE.md`: diretrizes de arquitetura.
- `docs/POWER_DEVELOPMENT.md`: criterios de desenvolvimento dos poderes.
- `docs/NETWORKING.md`: regras de multiplayer e rede.
- `docs/TESTING.md`: estrategia de validacao.
