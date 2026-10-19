# Multiplayer e rede

- O servidor e a autoridade final sobre efeitos de gameplay.
- Mensagens do cliente sao pedidos, nao prova de permissao.
- Validar jogador, contexto, frequencia, argumentos e estado antes de agir.
- Evitar enviar atualizacoes redundantes a cada tick sem necessidade.
- Sincronizar somente os dados necessarios aos clientes relevantes.
- Testar servidor dedicado, entrada tardia, morte, respawn e desconexao.
- Nao carregar classes exclusivas do cliente em caminhos comuns do servidor.