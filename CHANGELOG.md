# Changelog

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

## 0.1.0 — Sistema de Animações e Correções de Estabilidade (2026-10-20)
- Sistema de animações modular e orientado a dados (`br.com.ascendant.animation`):
  - `AnimationProfile`: perfil visual com camadas (particulas, som, aura, pose, impacto).
  - `AnimationRegistry`: registro comum seguro de perfis por ID de poder/habilidade.
  - `AnimationCatalogData`: perfis default para poderes funcionais e placeholders genericos.
  - `AnimationCoverageValidator`: relatorio de cobertura visual (com/sem perfil).
  - `AnimationPlayer` (client-only, `@OnlyIn(Dist.CLIENT)`): reproducao de efeitos no cliente.
- Cobertura visual aplicada a todos os 106 poderes funcionais (placeholders para os sem perfil dedicado).
- `ContentValidator` aprimorado: diferenca registrado/valido/funcional/testado, alertas para poderes
  funcionais sem executor nao-template.
- `PackManager` corrigido: limpeza de pasta temp em falha, validacao de versao MC/Forge do manifesto,
  rollback atomico ao substituir pacote ja instalado.
- `PackManifest` corrigido: validacao de id obrigatorio e nao em branco.
- Novos GameTests: `animationProfileRegistrationAndLookup`, `animationCoverageForAllFunctionalPowers`,
  `packManifestRequiresValidId`, `functionalPowersHaveExecutableComponents`.
- `gradlew.bat build`: BUILD SUCCESSFUL.

## 0.1.0 — Correção de Habilidades e Catálogo Heróis/Packs (2026-10-22)
- `PowerRuntimeEngine.activateAbility` agora rejeita ativação manual de habilidades com gatilhos automáticos (PASSIVE, HURT_EVENT, ATTACK_HIT, TICK, TOGGLE). Apenas KEY_PRESS e SNEAK_KEY são aceitas.
- Adicionado `PowerRuntimeEngine.dispatchTriggeredAbilities(AbilityTrigger)` executado nos hooks `onLivingHurt` (HURT_EVENT, ATTACK_HIT) e `onPlayerTick` (TICK) para habilidades automáticas server-authoritative.
- `SuitManager.unequipSuit` agora revoga os poderes concedidos pela capability do jogador ao desequipar um traje.
- Adicionado `PowerRuntimeEngine.getActivationFailureReason` para feedback específico ao cliente quando uma habilidade não pode ser ativada.
- Criada `HeroPackScreen` como tela principal do catálogo (abre em Heróis/Packs), com navegação persistente para Poderes, Habilidades e Trajes.
- Criada `SuitScreen` para listagem, seleção e equipagem de trajes dentro do catálogo.
- `AscendantClientControls` agora abre `HeroPackScreen` por padrão ao abrir o catálogo (tecla F6).
- Adicionados 3 GameTests: `automaticTriggerAbilitiesCannotBeManuallyActivated`, `suitUnequipRevokesGrantedPowers`, `activationFailureReasonProvidesFeedback`.
- `gradlew.bat compileJava`: BUILD SUCCESSFUL.

## 0.1.0 - Recuperação de regressões do commit a88f1cb (2026-10-22)
- Restaurada a supervelocidade completa e seus testes (`speedster/*`, `SpeedsterClientInput`, `SpeedsterGameTests`) e reintegrados os pacotes de rede e a coordenação com o voo.
- Restaurado `PackManagerGameTests`; testes anteriores de `FrameworkGameTests` foram preservados e integrados aos novos testes de regressão.
- `SuitManager.unequipSuit` não revoga mais a capability persistente. Poderes concedidos apenas pelo traje ficam inacessíveis; poderes permanentes continuam disponíveis; toggles/efeitos só são limpos quando não há outra fonte.
- Adicionado GameTest de jogador com voo permanente e voo concedido por traje que mantém o voo após desequipar.
- Runtime de habilidades ampliado: autorização por poder pai ou concessão direta do traje, ativação manual validada, cooldown, feedback real de falha, PASSIVE com ciclo de vida, TOGGLE explícito e dispatchers TICK/HURT_EVENT/ATTACK_HIT.
- Restaurada a integridade de packs: validação de ID/manifesto, anti-traversal/ZIP Slip, limites de tamanho/entradas, bloqueio de executáveis, substituição atômica e rollback, com validação de versão MC/Forge.
- `HeroPackScreen` agora lista packs realmente instalados via `PackManager.getInstalledPacks()`, documenta a limitação do manifesto (sem previews de personagem) e abre Poderes/Habilidades nas abas corretas; trajes permanecem na `SuitScreen`.
- Restaurados os componentes de gameplay Invisibility, Levitation, Phase, SummonEntity e VortexAction e as cinco entradas funcionais correspondentes no catálogo.
- Validação confirmada: `compileJava` BUILD SUCCESSFUL; `runGameTestServer` com 55/55 testes obrigatórios aprovados; `build` BUILD SUCCESSFUL; `git diff --check` sem erros; `runClient` iniciou sem crash e permaneceu estável por 120s (inspeção visual humana pendente).

## 0.1.0 - Integracao do runtime de habilidades e catalogo Hero/Pack (2026-10-22)
- Integrado o lote interrompido do worktree `srkaiohshin-droid-glowing-fortnight` sobre a base publicada `ee2fa9d`, com resolucao manual de conflitos e preservacao da recuperacao anterior.
- `PowerRuntimeEngine` agora expoe `AbilityResult`, `activateAbilityWithResult`, `toggleAbility(id, enable)`, `getAccessibleAbilities` e `tickAbilities`, mantendo os entry points usados por testes e rede.
- Autorizacao de habilidades considera poder pai, habilidade universal e concessao direta por traje.
- Gatilhos KEY_PRESS, SNEAK_KEY, TOGGLE, PASSIVE, TICK, HURT_EVENT e ATTACK_HIT possuem caminhos explicitos; templates e habilidades sem executor nao ativam.
- `titan_mass_form` e `titan_growth_boost` passaram a TOGGLE com identidades de modificador distintas e estaveis.
- Efeitos gerenciados usam `ManagedEffectLease`; `FireWardComponent` executa uma janela real de resistencia ao fogo e a limpeza preserva efeitos de fontes externas.
- Limpeza de estado cobre logout, respawn, troca de dimensao e perda da ultima fonte de acesso.
- `PackManager` rastreia packs carregados e a origem de poderes, habilidades e trajes.
- `PowerCatalogScreen` abre em HEROES e mantem abas persistentes POWERS, ABILITIES e SUITS, usando `CatalogClientState` autoritativo para ownership, cooldown e toggles.
- Catalogo e menu radial compartilham `CatalogInteraction`; a aba HEROES filtra corretamente `suits` e `packs`.
- `HeroPackScreen` continua sendo a entrada F6 de Herois/Packs e lista somente packs instalados, sem inventar personagens, retratos ou modelos.
- Novos arquivos: `FireWardComponent.java`, `ManagedEffectLease.java` e `CatalogClientState.java`.
- Ajustes finais de auditoria: TICK sem cooldown executa a cada tick (com assercao no GameTest), o cooldown do cliente conta por game time, toggles ativos podem ser desligados durante o cooldown no catalogo e no radial, habilidade removida do registro limpa toggles/passives/cooldown, e os botoes do catalogo cabem em telas estreitas.
- Validacao confirmada: `compileJava` BUILD SUCCESSFUL; `runGameTestServer` com "All 59 required tests passed"; `build` BUILD SUCCESSFUL; `git diff --check` sem erros de conteudo; `runClient` iniciou sem crash e permaneceu estavel ate o ambiente encerrar a janela (execucao final de ~49s; uma execucao anterior passou de 2 minutos), sem inspecao visual humana.
- Publicado em `origin/srkaiohshin-droid-ascendant-gameplay-ui` no commit `934e00fa35eecef61272fc226f48cb5bd11aff4d`; SHA local e remoto conferem.

## 0.1.0 - Overhaul de gameplay, catalogo, animacoes e camera (2026-10-23)
- Supervelocidade exposta como poder TOGGLE real (`kinetic_sprint`) com `SpeedsterComponent`;
  tecla Z, catalogo e radial compartilham o mesmo estado server-authoritative.
- `IPowerComponent.canToggle` e validacao previa em `PowerRuntimeEngine.togglePower`;
  `setPowerToggleSilently`, `getPowerToggleFailureReason` e `hasPowerAccess(Player, id)`.
- Paradas automaticas da supervelocidade limpam o toggle e reenviam o catalogo; recusas do
  servidor chegam ao jogador por action bar, inclusive cooldown em ticks.
- `clearForLifecycle` reconcilia o toggle de `kinetic_sprint` com o modo transitorio em
  logout, respawn, clone e mudanca de dimensao, sem depender da ordem dos eventos.
- Voo concedido por traje passa a autorizar ativacao e manutencao em `FlightController`.
- Catalogo com identidade comica: HEROES como primeira pagina, abas HEROES/POWERS/ABILITIES/SUITS,
  busca, filtros de categoria/origem, cartoes, detalhes de mecanica, ativacao, requisitos,
  cooldown, estado e origem. `HeroPackScreen` virou subclasse fina; radial usa a mesma linguagem.
- Tema visual derivado de metadados de pack (`theme` opcional) ou do universo do traje
  selecionado/equipado, sem nomes de herois no codigo.
- Assets: `theme_cosmic.png` e `theme_retro.png` derivadas de *Race for the Moon* #2
  (dominio publico nos EUA) e `halftone.png` procedural; proveniencia em `docs/ASSETS.md`.
- Voo: manobra aerea de 360 graus autoritativa e sincronizada (protocolo de rede "2"),
  com cooldown, interpolacao por arco mais curto e sem alterar posicao/fisica.
- Camera de terceira pessoa e FOV opcionais (`ascendant-client.toml`), restauraveis;
  animacoes de corrida/supervelocidade/idle com voo prioritario nos mixins.
- Teclas C/V para rolamento aereo; cliques sao drenados quando fora de voo ou em telas.
- Testes: `aerialManeuverMakesOneFullTurnAndHonoursCooldown`,
  `packThemeIsOptionalMetadataAndValidated` e cobertura de voo por traje; 62/62 GameTests.
- Validacao: `compileJava` BUILD SUCCESSFUL; `runGameTestServer` "All 62 required tests passed";
  `build` BUILD SUCCESSFUL; `git diff --check` sem erros (avisos LF/CRLF).
- Publicacao: codigo e assets em `b2e1c97e6e33285ff05c98de1f8cc8e093ec635f`; teste de
  ciclo de vida e documentacao em `5d9f22ee55fa11699156781c8c6c1e6b1a4d1c47`; ambos
  publicados, com SHA remoto conferido por `git ls-remote`.
- Pendente: homologacao visual humana em cliente grafico (telas, poses, camera e manobra).
- Teste `catalogTogglePathAppliesRealSpeedAndRestoresBaseline`: o toggle de
  `kinetic_sprint` pelo runtime (mesmo caminho do catalogo/radial) inicia a
  supervelocidade, aplica +0,15 de movement_speed, move mais que um passo vanilla e
  restaura o atributo base ao desligar. Suite passa a 63/63 GameTests.

## 0.1.0 - Estados reais do catalogo, arquivo de lore e traducao (2026-10-23)
- `CatalogEntryState`: classificacao compartilhada de entrada (READY, ACTIVE, COOLDOWN,
  LOCKED, TEMPLATE, LOADING, PASSIVE, AUTOMATIC, METADATA, EQUIPPED, AVAILABLE) com
  motivo de recusa e ticks restantes. O catalogo aplica o mesmo resultado em selo, cor,
  botao de execucao, detalhe e feedback.
- Detalhe da entrada passa a mostrar mecanica, ativacao, tecla, cooldown, requisitos,
  estado de runtime, origem e conteudo relacionado; packs continuam apenas metadados.
- `LoreRegistry`, `LoreEntry`, `LoreUnlock`, `LoreAccess`, `LoreCategory` e
  `NativeLoreData`: 12 entradas originais com desbloqueio derivado de poder, habilidade
  ou traje realmente acessivel. Aba LORE somente leitura, com filtro e requisito visivel.
- `ContentValidator` valida trajes concedentes, lore e cobertura de traducao.
  `BuiltinContentManifest` define os seis trajes internos cuja traducao e obrigatoria;
  conteudo de GameTest/pack e validado estruturalmente, sem exigir traducao da base.
- 127 chaves novas por idioma (316 em `en_us.json` e `pt_br.json`): UI, categorias,
  seis trajes, 18 poderes e 7 habilidades concedidos e 12 entradas de lore.
- Transparencia: 379 poderes e 389 habilidades permanecem templates; o relatorio declara
  482 descricoes de poderes e 493 de habilidades ausentes em vez de fingir cobertura.
- Testes: `catalogEntryStateExplainsRefusalReasons` e
  `loreRegistryUnlocksAndTranslationCoverage`; suite em 70/70.
- Validacao: `compileJava` BUILD SUCCESSFUL; `runGameTestServer` "All 70 required tests
  passed"; `build` BUILD SUCCESSFUL; `git diff --check` sem erros; smoke de `runClient`
  entrou no mundo integrado e encerrou com BUILD SUCCESSFUL. Sem inspecao visual humana.
- Fronteira: nenhum arquivo de voo/animacao/camera/render foi alterado neste lote.

## 0.1.0 - Overhaul de voo, animacao, manobras e camera (2026-10-23)
- `FlightMotionSample` centraliza a telemetria de um tick (velocidade, aceleracao,
  heading real, erro de mira, banco, frenagem, boost, manobra e pouso).
- `FlightAnimationSelector` classifica o estado com histerese e dwell minimo de 3 ticks;
  manobra, pouso, decolagem e frenagem tem prioridade sobre estados comuns.
- Novas poses/estados: SLOW_FLIGHT, CRUISE, ACCELERATING, DECELERATING e LANDING, alem
  do hover com respiracao real de corpo, bracos, pernas e cabeca.
- Decolagem com antecipacao, impulso vertical autoritativo
  (`takeoffImpulse=0.32`), extensao e estabilizacao; 14 ticks no solo e 8 no ar.
- Pouso com sonda de solo, aproximacao, contato e recuperacao de 12 ticks; motivo
  `END_LANDED` distingue pouso de saida manual/revogacao/ciclo de vida.
- Corpo rotacionado no centro do tronco (`FLIGHT_BODY_PIVOT_Y=1.0`) em vez do pivo de
  joelho; cabeca usa angulo local derivado (`0.52 * bodyPitch`, limite -75..0), sem
  copiar o pitch do torso nem deslocar a cabeca do pescoco.
- Banco calculado pela mudanca real de heading horizontal, com suavizacao por tick;
  camera usa o mesmo banco e o roll duplo de `FlightRenderEvents` foi removido.
- FOV suavizado por tick cliente e interpolado por `partialTick`.
- Protocolo de voo "3": estado sincroniza tipo/direcao/ticks da manobra, invertido,
  decolagem do solo, motivo de fim e impulso. O cliente remoto usa o passo real de
  posicao, sem multiplicador 3x.
- Roll de 360 graus preservado. Loop vertical e reversao de 180 graus foram avaliados e
  permanecem bloqueados: nao existe trajetoria circular/inversao com colisao verificada,
  e um efeito visual isolado seria mecanica falsa.
- Testes: 10 novos e 1 atualizado em `FlightGameTests`, incluindo pivo/cabeca, histerese,
  prioridades, decolagem solo/ar, pouso, hover vivo, boost, motivo de fim e round-trip
  do pacote completo; suite em 80/80 GameTests.
- Validacao final (arvore estavel, sobre `e870892`): `compileJava` BUILD SUCCESSFUL;
  `runGameTestServer` "All 80 required tests passed"; `build` BUILD SUCCESSFUL;
  `git diff --check` sem erros de conteudo (avisos LF/CRLF).
- Smoke de `runClient` (quickPlay em "New World"): entrou no mundo, mixins de jogador
  aplicados, protocolo de voo "3" aceito nos dois lados e nenhum crash-report. Isso nao
  substitui a inspecao visual humana.
- Robustez final: offset de pescoco zerado fora do voo, picos de camera/FOV da manobra
  comecam e terminam em zero, boost exige velocidade real e a sonda de pouso nao dispara
  em hover baixo.
- Sem homologacao visual humana; o smoke de `runClient` nao substitui essa inspecao.
- O agente do speedster commitou o proprio lote como `e870892`; ele nao faz parte deste
  lote de voo.
- Publicacao: commit `df49ac6ef4249d5d8339c8a2190bc6477aee6e30` enviado com push normal
  na branch `srkaiohshin-droid-ascendant-gameplay-ui`; `git ls-remote` confirmou o SHA
  remoto.

## 0.1.0 - Loop vertical, reversao 180 e captura de voo (2026-10-23)
- Loop vertical e reversao horizontal de 180 graus implementados como trajetorias
  analiticas reais, server-authoritative, sem efeito visual isolado.
- Loop de 28 ticks no plano do heading de entrada; reversao de 18 ticks com lado
  escolhido por A/D e banco scriptado de ate 30 graus.
- `FlightManeuverSafety` varre a curva completa antes de iniciar: caixa STANDING,
  clearance de 0.35, 4 subpassos, recusa de bloco, fluido, borda, altura e chunk nao
  carregado; contato durante a manobra aborta sem teleporte.
- Entrada limitada a velocidade 0.50/0.40 ate 1.25, pitch <= 24 graus, mira <= 32 graus e
  cooldown de 24 ticks.
- Protocolo de voo "4" com kind/direcao/ticks, yaw/velocidade de base e flag de aborto.
- Input vanilla zerado nos dois lados durante a trajetoria, mantendo o caminho real igual
  ao caminho verificado.
- Teclas G (loop) e H (reversao); animacao por progresso com pitch do loop e banco da
  reversao na pose, camera/FOV sincronizados.
- Harness de captura dev-only (`-Dascendant.flightCapture=true`) com 16 frames e relatorio
  HTML; sessao 20261023-233605. A telemetria confirma o pitch 47.6/109.3/189.8/262.0/320.7
  do loop e o banco 16.1/28.8/26.4/7.6 da reversao.
- Testes: caminho fechado/invertido, entrada nivelada, sweep obstruido e round-trip;
  suite em 83/83 GameTests. `compileJava` e `build` BUILD SUCCESSFUL.
- Sem homologacao visual humana; o pack automatico e evidencia para a revisao, nao a
  substitui.
- Publicacao: commit `15d264facc790f7fa02a0a00b54124bc1ab0ce46` enviado com push normal
  na branch `srkaiohshin-droid-ascendant-gameplay-ui`; `git ls-remote` confirmou o SHA
  remoto.
