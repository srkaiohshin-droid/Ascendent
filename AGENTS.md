# Ascendant agent instructions

Ascendant targets Minecraft Java 1.20.1, Forge 47.3.0, Java 17, and the
`br.com.ascendant` package. `Master.txt` is the primary project specification;
follow it when secondary operational instructions conflict.

## Start of each task

- Read `Master.txt`, `AI_CONTEXT.txt`, `AGENT_COORDINATION.txt`,
  `AGENT_LOG.txt`, and `TODO.md`. Read relevant technical docs as needed.
- Inspect the current implementation and existing tests before designing a
  change. Check Git status, branch, latest commit, and `origin`; fetch before
  work when network access permits.
- Check for existing responsibilities and preserve all pre-existing edits.

## Implementation and validation

- Make complete, integrated gameplay changes. Do not add empty stubs,
  placeholder methods, or cosmetic effects presented as working mechanics.
- Keep shared gameplay server-authoritative and isolate client-only classes so
  dedicated servers can load the mod.
- Run relevant existing tests and the Windows Gradle Wrapper build when viable.
  Use JDK 17. Report failures, tests not run, and limits accurately; a successful
  build alone does not prove gameplay works.
- After meaningful changes, update the appropriate context, coordination,
  log, TODO, and changelog files without duplicating their content.

## Safety and Git

- Review the diff, `git diff --check`, staged files, and untracked files before
  committing. Include only files related to the task and never discard user
  changes.
- Never run `git reset --hard`, `git clean -fd`, force-push, or another
  destructive discard operation. Never commit secrets, credentials, or tokens.
- Follow `Master.txt` for commit and push authorization and procedure. Verify
  the expected branch and remote, fetch and check synchronization, then confirm
  the remote SHA after a normal push. Do not claim synchronization without that
  confirmation.

<!-- AUTO-GITHUB-SYNC-POLICY -->
## Sincronizacao obrigatoria com GitHub

Ao concluir cada lote de trabalho:
1. Confira git status e revise as alteracoes, preservando trabalho preexistente e alheio.
2. Execute os testes pertinentes usando o Gradle Wrapper e corrija falhas reais.
3. Revise git diff e execute git diff --check.
4. Adicione ao staging somente os arquivos pertencentes a tarefa; nunca use git add . indiscriminadamente.
5. Crie um commit com mensagem especifica quando houver alteracoes validadas.
6. O hook post-commit tenta enviar automaticamente o commit para origin na branch atual.
7. Confira o resultado do hook. Se o push falhar, investigue a causa e tente novamente com push normal, sem force-push.
8. Se o remoto tiver avancado, integre as alteracoes de forma nao destrutiva, resolva conflitos com cuidado e repita os testes pertinentes.
9. Confirme que o SHA remoto corresponde ao commit local antes de declarar sincronizacao concluida.
10. Nunca afirme que houve commit, testes ou sincronizacao sem verificar os resultados reais.

O hook automatiza o envio de commits; ele nao cria commits automaticamente. Portanto, conclua explicitamente as etapas de validacao, commit e verificacao do push antes de encerrar a tarefa.
