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
   serializacao/copia e dos dois ramos do handler `PlayerEvent.Clone` ja existem.
   O teste instancia o evento e chama o handler, sem simular um respawn completo.
   Faltam decisoes de cooldowns e permissoes. Nao ha API de ativacao ficticia.
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

## ASC-010 — contrato inicial de supervelocidade

- `SpeedsterState` é capability transitória, separada dos poderes duráveis e do estado de voo.
  Guarda modo ativo, nível e última entrada recebida; não é serializada e é limpa no ciclo de vida.
- `SpeedsterClientInput` envia controles e máscara direcional pelo `AscendantNetwork`; o servidor
  exige posse de `ascendant:kinetic_sprint` (intrínseca ou por traje), limita níveis/entradas e
  calcula a velocidade. O cliente aplica apenas previsão local da mesma função física.
- `SpeedsterPhysics` é puro e compartilhado entre simulação/predição: 10 limites não lineares,
  aceleração, convergência de direção, retenção de inércia e frenagem. Valores são configuráveis
  em `ascendant-speedster.toml`; cada movimento segue a colisão normal de `Entity.move`.
- Voo e corrida rápida não escrevem movimento simultaneamente: velocidade não ativa com voo;
  ao ativar voo, `FlightController` encerra e sincroniza supervelocidade antes de assumir o controle.
- Os pacotes não carregam posição nem velocidade final escolhidas pelo cliente. Estado/nível visível
  é sincronizado para o jogador e observadores; inputs repetidos no mesmo tick são rejeitados e
  entradas antigas são descartadas.
- Os defaults próprios do Ascendant não compartilham teclas entre voo, catálogo/radial e velocista.
  `flight_brake` usa K porque X colidia com `loadToolbarActivator` vanilla observado na configuração
  local. Minecraft mantém keybinds persistidos ao atualizar; não sobrescrever preferências antigas.
- O primeiro lote não define um registry de perfis nem afirma perfis de Barry/Wally/outros,
  recursos energéticos, ataques, slow motion, habilidades de parede/água/fase, HUD ou animação.
  Essas mecânicas exigem lotes e critérios independentes.

## Politica de estado, cooldowns e permissoes

- Poderes desbloqueados sao direitos duraveis do jogador. O capability serializa
  IDs estaveis em NBT e a copia em `PlayerEvent.Clone` e substitutiva (set para
  set), tanto na morte quanto no retorno do End; nao acumula nem duplica IDs.
- A capability e autoridade do servidor. Nenhum pacote de sincronizacao e
  necessario enquanto nao houver interface, input cliente ou observador remoto
  que consuma esses dados. Quando houver consumidor, enviar apenas os campos
  usados e continuar validando toda mudanca de gameplay no servidor.
- Estado de execucao (por exemplo, voo ativo, carga de investida ou animacao)
  nao sobrevive a morte, troca de dimensao nem desconexao por padrao. Cada
  habilidade devera limpar seu estado nas transicoes do proprio ciclo de vida.
- Quando uma habilidade ativa justificar recarga, seu cooldown sera indexado
  pelo `ResourceLocation` da habilidade e guardara um tick de expiracao no
  relogio de jogo do servidor (Overworld). Habilidades passivas ou sem recarga
  de gameplay nao recebem cooldown artificial. O servidor decide disponibilidade;
  clientes nao enviam nem reduzem cooldowns. Cooldowns iniciados persistem em
  NBT e serao preservados em morte, retorno do End e reconexao, impedindo
  contorno por respawn. Com o servidor desligado, o relogio para; nao ha reducao
  de cooldown por tempo real offline. Consultas comparam o prazo de expiracao,
  sem contador por jogador a cada tick; expirados podem ser removidos durante
  consultas/mutacoes. Essa politica esta definida; armazenamento e runtime de
  cooldown ainda aguardam a primeira habilidade consumidora.
- Desbloquear um poder nao concede permissao para violar regras do mundo.
  Ativacoes sao verificadas no servidor quanto a posse, estado e cooldown.
  Alteracoes de blocos, dano e interacoes respeitam regras vanilla e hooks de
  protecao/permissao Forge ou de outros mods. Poderes pessoais nao exigem OP por
  padrao; uma permissao administrativa so sera registrada quando uma habilidade
  concreta justificar configuracao do servidor. Nao confiar em permissao enviada
  pelo cliente.
- Os contratos de cooldown e permissao serao implementados junto da primeira
  habilidade consumidora, evitando APIs sem uso. Antes disso, estes criterios
  definem o comportamento esperado e os testes de aceitacao.
