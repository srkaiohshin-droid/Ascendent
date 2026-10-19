# Testes e validacao

## Validacao minima por lote
1. Executar `.\gradlew.bat build`.
2. Examinar o primeiro erro real se a compilacao falhar.
3. Executar testes relevantes quando existirem.
4. Testar inicializacao no cliente e, quando aplicavel, servidor dedicado.
5. Registrar resultado e limitacoes em AGENT_LOG.txt.

Uma compilacao aprovada confirma compilacao, nao garante que toda mecanica
funcione corretamente dentro do jogo.