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


## 0.1.0 — Hardening e Conclusão do Backend de Voo (ASC-003 Backend, 2026-10-20)
- Rate limiting anti-spam de rede: o servidor agora restringe solicitações de ativação/desativação a no máximo 1 a cada 2 ticks por jogador, prevenindo ataques por flooding de pacotes e macros.
- Otimização de tráfego de rede: solicitações redundantes de desativação não geram mais broadcast para observadores; adicionado `syncToClient` para sincronizações unicast eficientes.
- Resiliência de rede e segurança contra NPE: verificação defensiva de conexões nulas (`connection == null`) em `FlightNetwork`.
- Consistência de autorização com a configuração: `canMaintainFlight` agora respeita a configuração global `FlightTuning.ENABLED`.
- Aprimoramento da detecção de solo e sobrevoo de terreno irregular: o voo não é mais cancelado acidentalmente ao raspar o solo em alta velocidade de cruzeiro/boost; o pouso (`shouldLand`) requer desaceleração, planeio neutro, frenagem ou mergulho íngreme direcionado.
- Tratamento de morte e queda de corpos (`LivingDeathEvent`): cancelamento imediato de voo e restauração de gravidade ao morrer, impedindo que corpos fiquem flutuando no ar.
- Recuperação de crash em login/respawn: restauração de gravidade normal caso o jogador tenha permanecido com `noGravity` após encerramento inesperado do servidor.
- Predição segura no cliente: suspensão imediata da gravidade nula local ao entrar em água, lava, veículos ou dormir.
- Expansão da suíte de testes: adicionados 3 novos GameTests para validação de rate limiting, desativação por configuração e pouso inteligente em terreno (total de 31 GameTests aprovados).
