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
