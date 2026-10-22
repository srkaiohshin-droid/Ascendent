# Changelog

## 0.1.0 — ASC-010: núcleo inicial de supervelocidade
- Adicionado estado transitório de velocista com 10 níveis, progressão de velocidade configurável,
  aceleração, convergência direcional, inércia, sprint e frenagem de emergência.
- Keybinds configuráveis usam o canal de rede existente; o servidor valida posse de `kinetic_sprint`,
  controla o movimento e sincroniza modo/nível. A ativação de voo encerra supervelocidade primeiro;
  perder a última fonte do poder encerra o modo imediatamente, preservando-o se outra fonte permanecer.
- Seis GameTests cobrem progressão, frenagem, direção, colisão vanilla, limites do estado, autorização,
  exclusão mútua com voo e remoção de traje com preservação de fonte intrínseca.
  `runGameTestServer`: 45/45 aprovados.
- `compileJava` e `build` aprovados. `runClient` carregou o menu e mundo local; automação não
  conseguiu enviar controles durante gameplay, portanto não há validação visual/funcional no cliente.
- Este lote não implementa habilidades especiais, perfis de personagens, HUD cinemático, animações,
  efeitos de velocidade, corrida sobre água/parede, faseamento ou validação visual em `runClient`.

## 0.1.0 — Importação local segura de HeroPacks
- IDs de manifesto agora precisam ser identificadores portáveis; campos de contagem e dependências
  são validados antes de selecionar o diretório de instalação.
- A extração rejeita caminhos absolutos/traversal em formatos Windows e ZIP, nomes reservados do
  Windows, aliases com ponto/espaço final, entradas duplicadas e limites excedidos durante a gravação;
  manifesto acima de 1 MiB é recusado.
- A atualização mantém a instalação anterior em staging até a nova pasta ser movida atomicamente;
  falha na movimentação restaura a versão anterior e preserva o backup se o rollback também falhar.
- GameTests cobrem importação inválida/válida, preservação/substituição e falha simulada na troca.
- A invocação de lobos tenta pontos alternativos carregados, dentro do world border e sem colisão;
  teste bloqueia o ponto preferencial para verificar o fallback.
- `compileJava`, `runGameTestServer` (39/39) e `build` aprovados; a tarefa `test` do Gradle permanece `NO-SOURCE`.

## 0.1.0 — Interface de catálogo e menu radial B2
- Adicionadas telas client-only para navegar poderes, habilidades e trajes, com busca por nome/ID/categoria,
  filtro de categoria, detalhes e indicação explícita de entradas funcionais ou templates.
- Ações de poderes, habilidades e trajes enviadas pelo canal de rede existente; a validação e execução
  permanecem server-authoritative. Poderes alternáveis são decididos pelo estado no servidor.
- Adicionados keybinds configuráveis para abrir o catálogo (F6), abrir o radial (G), ativar (V),
  alternar (B) e navegar/folhear (PageUp/PageDown).
- Menu radial apresenta até oito opções por página, seleção por direção do mouse, confirmação,
  cancelamento e navegação sem executar ações ao abrir ou cancelar.
- `CatalogInteraction` fornece filtragem/classificação compartilhada; GameTest cobre busca, filtros,
  acionabilidade e setores do radial.
- Validação: `compileJava`, `runGameTestServer` (36/36) e `build` aprovados. Validação visual em
  `runClient` permanece pendente.

## 0.1.0 — Componentes funcionais e ciclo de vida B1
- Conectados `refractive_invisibility`, `telekinetic_levitate`, `vortex_maelstrom`,
  `spectral_wolf_pack` e `void_step` a executores reais e serializáveis.
- Invisibility aplica efeito vanilla com duração finita; levitação e vórtice são
  sustentados por toggle; invocação usa lobos vanilla domados; faseamento desloca
  o jogador apenas até destino carregado, dentro do world border e sem colisão.
- Adicionado caminho server-side para ativar poderes `ACTIVE` pelo runtime,
  pacote de rede e `/ascendant power use`; levitação e vórtice mantêm SUSTAINED.
- Troca/remoção de traje e revogação limpam toggle, cooldown e callbacks quando
  a última fonte de acesso desaparece, preservando o poder quando outra fonte o concede.
- GameTests cobrem registro/round-trip, execução, duração finita, limpeza de efeito
  e atributo, posse alternativa e contagens do catálogo: 500 poderes (113 funcionais,
  387 templates) e 500 habilidades (111 funcionais, 389 templates).
- Validação final: `compileJava`, `runGameTestServer` (35/35) e `build` aprovados.
- A interface client-side, keybinds e menu radial foram adicionados no lote B2; validação gráfica
  interativa ainda está pendente.

## 0.1.0 — Fundacao inicial
- Identidade Ascendant configurada.
- Classe principal dedicada criada.
- Documentacao inicial de arquitetura e coordenacao preparada.
- Capability de jogador para IDs de poderes desbloqueados, com persistencia NBT
  e copia do estado na morte/respawn.
- GameTest de serializacao, idempotencia e copia de dados criado e aprovado.
- Nenhuma habilidade de gameplay esta implementada ainda.

## 0.1.0 — Checkpoint ASC-003 (parcial, 2026-10-19)
- Sistema de voo parcialmente implementado e preservado como checkpoint:
  capability de estado de voo, controller e fisica server-authoritative,
  rede SimpleImpl, entrada cliente, configuracao comum, eventos, GameTests
  de voo e comandos `/ascendant power grant|revoke|list`.
- Build `BUILD SUCCESSFUL` e 8/8 GameTests aprovados no ambiente validado.
- Voo NAO concluido: sessao real cliente-servidor ainda nao validada.

## 0.1.0 — Correções Estruturais e Auditoria ASC-003 (2026-10-19)
- Correção nos comandos `/ascendant power grant|revoke`: substituição de `StringArgumentType.word()`
  por `ResourceLocationArgument.id()`, permitindo o parsing correto de identificadores com `:` (`ascendant:flight`).
- Correção de predição no cliente: sincronização da máscara de teclas no `FlightState` local antes
  do cálculo de `tickClient`, resolvendo problemas severos de jitter/rubberbanding e atraso no cliente.
- Proteção contra acúmulo de dano de queda (`fallDistance`): reset contínuo em voo, pouso e cancelamento
  em `LivingFallEvent`, prevenindo morte instantânea ao desativar ou pousar.
- Restrição em lava: `canActivate` e `canMaintainFlight` agora verificam `isInLava()`, bloqueando ativação
  e interrompendo voo submerso em lava.
- Transição suave de física e amortecimento de velocidade residual: controle de velocidade horizontal e
  vertical ao tocar o solo e ao desativar o voo no ar.
- Sincronização em respawn: adição de listener para `PlayerEvent.PlayerRespawnEvent` em `FlightEvents`.
- Correção de visibilidade: método `@SubscribeEvent registerKeyMappings` ajustado para `public static void`.
- Expansão da suíte de GameTests para 14 testes aprovados.

## 0.1.0 — Refinamento do Voo 3D Inspirado no Fisk (ASC-003.1, 2026-10-19)
- Movimentação direcional tridimensional completa: a direção da câmera (pitch e yaw) governa
  a trajetória tridimensional do voo ao manter o comando de avanço. Olhar para cima sobe, olhar para baixo mergulha.
- Convergência aerodinâmica: curvas suaves e conservação de momento inspiradas no modelo de voo do Fisk's Superheroes.
- Planeio inercial com desaceleração progressiva (`drag = 0.94`) ao soltar a propulsão, sem paradas secas.
- Modo Boost / Sprint: correr durante o voo ativa a velocidade máxima ampliada (até 1.25 blocos/tick).
- Apresentação visual e renderização corporal (`FlightRenderEvents`):
  - Inclinação do corpo para a frente (pitch tilt) no `RenderPlayerEvent.Pre`, alinhando a postura do jogador com o vetor de voo.
  - Rolagem/banking dinâmico nas curvas baseado no produto vetorial da velocidade e do vetor de visão.
  - Rolagem sutil da câmera em curvas de alta velocidade (`ViewportEvent.ComputeCameraAngles`).
- Configurações centralizadas em `FlightTuning`: cruise speed, boost speed, steering factor, drag e hover stability.
- Expansão da suíte de GameTests de 14 para 18 testes aprovados.

## 0.1.0 — Animação corporal de voo heroico (ASC-003.2, 2026-10-19)
- Pose de voo heroico inspirada no Superman, aplicada ao modelo vanilla sem armadura, item ou modelo externo.
- `FlightAnimationState`/`FlightPose`/`FlightPoseLibrary`: uma única biblioteca de pose para HOVER,
  TAKEOFF, HEROIC_FLIGHT, CRUISE, CLIMB, DIVE, TURNING, BRAKING e EXIT.
- Corpo inteiro inclinado para a horizontal no renderizador; braços estendidos à frente com pequena
  assimetria, pernas atrás com variações naturais e cabeça contra-rotacionada para a direção do voo.
- Transições por `partialTick`, rampa de peso na ativação/saída, limites de juntas e retorno seguro à
  pose vanilla; sem rotações cumulativas e sem alterar física, yaw, pitch ou colisão.
- Dois Mixins client-only (`HumanoidModel.setupAnim` e `PlayerRenderer.setupRotations`) e configuração
  MixinGradle/refmap; servidor dedicado continua carregando o mod sem classes de renderização.
- `FlightRenderEvents` mantém apenas o camera roll; a inclinação corporal anterior em
  `RenderPlayerEvent.Pre` foi removida para evitar rotação dupla.
- GameTests expandidos de 18 para 27; `gradlew.bat build` e `runGameTestServer` aprovados.
- Validação gráfica em `runClient`: Mixins aplicados, corpo horizontal e face voltada para frente
  observados; homologação visual final da silhueta simplificada permanece pendente.
