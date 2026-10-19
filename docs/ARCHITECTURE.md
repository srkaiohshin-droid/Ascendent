# Arquitetura

## Principios
- Modulos pequenos, coesos e com contratos explicitos.
- Separar dominio dos poderes, integracao Forge, rede, persistencia e apresentacao.
- Nao criar interfaces sem uma necessidade concreta e implementacao utilizavel.
- Evitar dependencias circulares e estado global mutavel desnecessario.

## Camadas planejadas
1. Bootstrap: entrada do mod e registro de componentes.
2. Poderes: regras de ativacao, execucao e encerramento.
3. Estado do jogador: capacidades, duracao, cooldown e persistencia quando necessaria.
4. Integracao Forge: eventos, atributos e interacoes com o jogo.
5. Rede: pedidos do cliente, validacao do servidor e sincronizacao.
6. Cliente: animacoes, camera, audio e efeitos visuais.
7. Conteudo: configuracao e futura extensibilidade por HeroPacks.

A estrutura final evolui conforme cada sistema real for implementado. Primeira
decisao apos a auditoria (Minecraft 1.20.1 / Forge 47.3.0):

## Pontos de extensao e contratos iniciais

- Entrada: `@Mod` na classe `Ascendant`; registrar capacidades no mod event bus
  e tratar eventos de entidades/jogadores no Forge event bus.
- Estado duravel por jogador: capability anexada a `Player` via
  `AttachCapabilitiesEvent<Entity>`, provider serializavel em NBT. Copiar apenas
  dados que devam sobreviver a morte em `PlayerEvent.Clone`, sem duplicar estado
  ao retornar do End. Invalidar o `LazyOptional` ao encerrar o provider.
- Definicoes de poderes: dados estaveis identificados por `ResourceLocation`;
  manter definicao separada do estado mutavel de cada jogador. Usar registry
  somente quando conteudo registravel por outros mods realmente for consumido;
  nao criar API vazia antecipadamente.
- Gameplay: validar e alterar poderes no logical server. Eventos de tecla do
  cliente enviam intencoes; nunca concedem autoridade ou definem resultado.
- Rede: canal Forge SimpleImpl com versao de protocolo, discriminadores unicos,
  handlers pequenos que usam `enqueueWork`; validar jogador, permissao, estado,
  frequencia e argumentos no servidor. Sincronizar somente campos consumidos
  pelo cliente e encaminhar atualizacoes aos destinatarios necessarios.
- Cliente: classes de render/input/audio em pacote cliente ou registro limitado
  por `DistExecutor`, sem referencias a `net.minecraft.client` no bootstrap
  comum carregado no servidor dedicado.
- Ciclo de vida: definir politica de persistencia separadamente para poderes
  desbloqueados, estado temporario e cooldowns; morte/respawn, troca de dimensao
  e desconexao devem limpar ou copiar explicitamente cada categoria.

## Primeira sequencia de implementacao

1. ASC-002 (em andamento): capability de estado do jogador, NBT e GameTest de
   serializacao/copia ja existem; falta validar o evento real de morte/respawn
   e decidir cooldowns e permissoes. Nao ha API de ativacao ficticia.
2. Rede de estado apenas quando definida a primeira transicao de gameplay que
   precise de input cliente ou observacao remota.
3. ASC-003 voo: estados e movimento server-authoritative, colisao e interrupcao;
   efeitos/animações refletem esse estado e nao o substituem.

O setup do MDK oferece `client`, `server` e `gameTestServer`; GameTests ficam
disponiveis pelo namespace ascendant. Um GameTest de capability ja esta
registrado; `runGameTestServer` e o comando para executa-lo.

## Fontes Forge consultadas

- [Capabilities (Forge 1.20.x)](https://docs.minecraftforge.net/en/1.20.x/datastorage/capabilities/)
- [SimpleImpl networking (Forge 1.20.x)](https://docs.minecraftforge.net/en/1.20.x/networking/simpleimpl/)
- [Events (Forge 1.20.1)](https://docs.minecraftforge.net/en/1.20.1/concepts/events/)
- [Sides (Forge 1.20.x)](https://docs.minecraftforge.net/en/1.20.x/concepts/sides/)
