# Ascendant — TODO

- [x] Auditar a inicializacao e a configuracao do MDK.
- [x] Definir arquitetura inicial de poderes baseada na API do Forge.
- [x] Definir estado persistente, politica de cooldown/permissoes e contrato de sincronizacao.
- [x] Implementar cooldown runtime junto da primeira habilidade ativa que o exija (PowerRuntimeEngine).
- [x] Implementar sincronizacao de gameplay e canal de rede (AscendantNetwork).
- [ ] Implementar voo funcional. (ASC-003 REVIEW — correções de parser de comandos, predição de entrada do cliente, fallDistance, lava, transição de física residual e respawn implementadas)
- [ ] Refinamento profundo do voo 3D e visual inspirado no Fisk (ASC-003.1 REVIEW — vetor 3D look pitch/yaw, convergência aerodinâmica, inércia de planeio, boost/sprint, inclinação corporal e banking visual implementados; 18/18 GameTests aprovados; pendente validação tátil em cliente GUI)
- [ ] Homologar a animação corporal de voo heroico (ASC-003.2 REVIEW — mixins client-only, pose horizontal com braços à frente, cabeça contra-rotacionada, nove estados interpolados e 27/27 GameTests aprovados; pendente captura visual final da silhueta simplificada)
- [x] Implementar supervelocidade funcional (ASC-004 — kinetic_sprint, mach_dash, hyper_leap, kinetic_dampener).
- [x] Implementar superforca funcional (ASC-005 — titan_strength, seismic_slam, colossal_leap, shockwave_knockback).
- [x] Implementar super-resistencia funcional (ASC-006 — diamond_skin, kinetic_mitigation, fire_immunity, nanite_regeneration).
- [x] Implementar poderes aquaticos funcionais (ASC-007 — aquatic_respiration, tidal_wave, water_dash, atlantean_sovereignty, hydro_torpedo).
- [x] Cobrir capability NBT e respawn server-side com GameTest.
- [x] Implementar arquitetura modular de poderes, habilidades e componentes reutilizáveis.
- [x] Cadastrar catálogo completo de 500 poderes e 500 habilidades (106 poderes e 103 habilidades funcionais).
- [x] Implementar Catálogo de Trajes inspirado no Armoured Workshop (SuitRegistry, SuitManager, 6 trajes integrados).
- [x] Implementar Gerenciador de Pacotes inspirado no Fisk's Heroes (PackManager, PackSecurity, LocalPackCatalogProvider).
- [x] Criar ferramentas de validação e automação de conteúdo (ContentValidator, CatalogExporter).
- [x] Validar com 32/32 GameTests automatizados e build aprovado.
- [ ] Validar gameplay multiplayer com dois jogadores quando houver mecanica de rede interativa.
- [x] Configurar e confirmar o remoto Git origin autorizado.
- [x] Melhorar ContentValidator: diferenciar registrado/valido/funcional/testado, alertas para poderes funcionais sem executor.
- [x] Corrigir PackManager: limpeza de temp em falha, validacao de versao do manifesto, rollback atomico.
- [x] Corrigir PackManifest: validacao de id obrigatorio e versao MC/Forge.
- [x] Implementar sistema de animacoes: AnimationProfile, AnimationRegistry, AnimationPlayer (client-only), AnimationCoverageValidator.
- [x] Adicionar AnimationCatalogData com perfis para todos os poderes funcionais (placeholders genericos).
- [x] Adicionar GameTests de cobertura de animacao e validacao de manifesto de pacote.
- [ ] Criar scripts PowerShell para build/test/relatorio automatizado.
- [x] Corrigir runtime de habilidades: rejeitar ativação manual de gatilhos automáticos (PASSIVE, HURT_EVENT, ATTACK_HIT, TICK).
- [x] Adicionar dispatch de habilidades automáticas via `dispatchTriggeredAbilities` nos hooks `onLivingHurt` e `onPlayerTick`.
- [x] Corrigir `SuitManager.unequipSuit` para revogar poderes concedidos ao desequipar traje.
- [x] Adicionar `PowerRuntimeEngine.getActivationFailureReason` para feedback ao cliente.
- [x] Implementar tela Heróis/Packs como tela principal do catálogo (`HeroPackScreen`) com navegação para Poderes, Habilidades e Trajes.
- [x] Implementar `SuitScreen` para listagem e equipagem de trajes.
- [x] `AscendantClientControls` abre `HeroPackScreen` por padrão ao abrir o catálogo (F6).
- [x] Executar GameTests e confirmar 55/55 em 2026-10-22 (todas as suítes registradas; resultado real "All 55 required tests passed").
- [ ] Executar `runClient` para validação visual da interface HeroPackScreen.

## Recuperação de regressões do commit a88f1cb (2026-10-22)
- [x] Restaurar arquivos e testes da supervelocidade (`speedster/*`, `SpeedsterClientInput`, `SpeedsterGameTests`).
- [x] Restaurar `PackManagerGameTests` e integrar os testes anteriores de `FrameworkGameTests` aos novos.
- [x] Remover a revogação indiscriminada da capability em `SuitManager.unequipSuit`.
- [x] Garantir que poderes permanentes sobrevivam à remoção do traje e que poderes exclusivos do traje fiquem inacessíveis.
- [x] Adicionar GameTest de voo permanente + voo concedido por traje + desequipamento.
- [x] Implementar autorização de habilidades concedidas diretamente por traje.
- [x] Implementar ativação manual validada, cooldown, feedback de falha e ciclo PASSIVE/TOGGLE.
- [x] Implementar dispatchers TICK, HURT_EVENT e ATTACK_HIT com cooldown e testes de gatilho.
- [x] Restaurar validação de manifesto/ID, proteção anti-traversal/ZIP Slip, limites e bloqueio de executáveis.
- [x] Restaurar instalação atômica, rollback e testes de importação válida/inválida.
- [x] Fazer `HeroPackScreen` listar packs realmente instalados via `PackManager`, sem confundir trajes com packs.
- [x] Fazer os botões Poderes e Habilidades abrirem as abas corretas do catálogo.
- [x] Validar `compileJava`, `runGameTestServer` (55/55), `build` e `git diff --check`.
- [ ] Revisão visual humana da UI e dos controles de supervelocidade no cliente gráfico. O `runClient` iniciou sem crash e ficou estável por 120s em 2026-10-22, mas não houve inspeção visual neste ambiente automatizado.

## Integracao do lote interrompido ASC-012 (2026-10-22)
- [x] Integrar o runtime de habilidades do clone interrompido sobre a base publicada `ee2fa9d`, preservando as APIs anteriores.
- [x] Adicionar `AbilityResult`, `activateAbilityWithResult`, `toggleAbility(id, enable)`, `getAccessibleAbilities` e `tickAbilities`.
- [x] Autorizar habilidades por poder pai, habilidade universal e concessao direta de traje.
- [x] Implementar caminhos explicitos para KEY_PRESS, SNEAK_KEY, TOGGLE, PASSIVE, TICK, HURT_EVENT e ATTACK_HIT.
- [x] Bloquear ativacao de templates e habilidades sem acao executavel.
- [x] Tornar `titan_mass_form` e `titan_growth_boost` TOGGLE com identidades de modificador distintas.
- [x] Usar leases gerenciados para efeitos e preservar efeitos de fontes externas na limpeza.
- [x] Implementar `FireWardComponent` como resistencia ao fogo real e finita.
- [x] Limpar estado em logout, respawn, troca de dimensao e perda da ultima fonte de acesso.
- [x] Rastrear packs carregados e origem de poderes/habilidades/trajes no `PackManager`.
- [x] Fazer `PowerCatalogScreen` abrir em HEROES com abas persistentes POWERS, ABILITIES e SUITS.
- [x] Compartilhar `CatalogInteraction` entre catalogo e menu radial.
- [x] Corrigir o filtro da aba HEROES para `suits` e `packs` e ocultar o filtro de origem quando nao se aplica.
- [x] Validar `compileJava`: BUILD SUCCESSFUL.
- [x] Validar `runGameTestServer`: 59/59 required tests passed.
- [x] Validar `build`: BUILD SUCCESSFUL.
- [x] Validar `git diff --check`: sem erros de conteudo (apenas aviso LF/CRLF em `PowerRuntimeEngine.java`).
- [x] Tentar `runClient`: iniciou sem crash e permaneceu estavel ate o ambiente encerrar a janela (execucao final de ~49s; uma execucao anterior passou de 2 minutos).
- [x] Fazer TICK sem cooldown executar a cada tick e cobrir o caso com assercao no GameTest.
- [x] Contar cooldown no cliente por game time e permitir desligar toggles ativos durante o cooldown.
- [x] Limpar toggles/passives/cooldown de habilidade removida do registro sem NPE.
- [x] Ajustar os botoes do catalogo para telas estreitas.
- [ ] Inspecao visual humana do catalogo, busca, filtros, botoes, feedback e menu radial em cliente grafico.
- [x] Commitar somente este lote, publicar com push normal e confirmar SHA local igual ao remoto (`934e00fa35eecef61272fc226f48cb5bd11aff4d`).

## Overhaul de gameplay, catalogo, animacoes e camera (2026-10-23)
- [x] Tornar `kinetic_sprint` um TOGGLE real com `SpeedsterComponent` e manter o modificador de velocidade.
- [x] Adicionar `canToggle`, `setPowerToggleSilently`, `getPowerToggleFailureReason` e `hasPowerAccess(Player, id)`.
- [x] Unificar tecla Z, catalogo e radial no runtime; limpar toggle em paradas automaticas.
- [x] Reconciliar toggle e modo transitorio em logout, respawn, clone e mudanca de dimensao.
- [x] Corrigir voo concedido por traje em `FlightController`.
- [x] Enviar recusas do servidor por action bar, incluindo cooldown em ticks.
- [x] Aceitar `theme` opcional em `PackManifest` e corrigir o parser de campo opcional.
- [x] Reescrever `PowerCatalogScreen` com HEROES inicial, abas, busca, filtros e detalhes.
- [x] Transformar `HeroPackScreen` em subclasse fina do catalogo.
- [x] Reescrever `RadialMenuScreen` com tema, estado, cooldown e dicas.
- [x] Derivar tema do pack/traje selecionado ou equipado; corrigir chaves de tipo do radial.
- [x] Integrar texturas comicas e documentar origem/licenca/derivacao em `docs/ASSETS.md`.
- [x] Adicionar manobra aerea de 360 graus autoritativa e sincronizada com cooldown.
- [x] Adicionar camera/FOV opcionais e animacoes de corrida/supervelocidade/idle.
- [x] Adicionar/atualizar GameTests de manobra, tema e voo por traje.
- [x] Validar `compileJava`, `runGameTestServer` (62/62), `build` e `git diff --check`.
- [x] Cobrir o toggle de `kinetic_sprint` pelo caminho do catalogo em GameTest, com
  velocidade real e restauracao do atributo base (63/63 apos o novo teste).
- [ ] Homologacao visual humana em cliente grafico (telas, poses, camera e manobra).
- [x] Confirmar SHA remoto apos o push normal deste lote (codigo/assets em `b2e1c97`,
  teste/documentacao em `5d9f22e`; `git ls-remote` conferido).

## Estados reais do catalogo, lore e traducao (2026-10-23)
- [x] Criar `CatalogEntryState` e unificar selo, cor, botao, detalhe e motivo de recusa
  para READY/ACTIVE/COOLDOWN/LOCKED/TEMPLATE/LOADING/PASSIVE/AUTOMATIC/METADATA/
  EQUIPPED/AVAILABLE.
- [x] Exibir mecanica, ativacao, tecla, cooldown, requisitos, estado e origem no detalhe.
- [x] Adicionar `LoreRegistry` e 12 entradas originais com desbloqueio derivado de acesso
  real a poder, habilidade ou traje; aba LORE somente leitura.
- [x] Validar trajes concedentes, referencias/desbloqueios de lore e cobertura de traducao.
- [x] Restringir traducao obrigatoria aos trajes internos via `BuiltinContentManifest`.
- [x] Adicionar GameTests de estados/recusas e de lore/cobertura de traducao.
- [x] Validar `compileJava`, `runGameTestServer` (70/70), `build` e `git diff --check`.
- [x] Smoke de `runClient`: entrou no mundo integrado e encerrou sem crash.
- [ ] Homologacao visual humana da aba LORE, selos e motivos de recusa.
- [ ] Descrever os 379 poderes e 389 habilidades restantes apenas quando virarem mecanicas
  reais; o relatorio de cobertura deve continuar expondo esse gap ate la.
- [x] Publicar o lote com push normal e confirmar o SHA remoto
  (`2542676b27b44aca97ee80a39213871c33626bd9`) via `git ls-remote`.

## Overhaul de voo, animacao, manobras e camera (2026-10-23)
- [x] Auditar fisica, estados, poses, pivo do corpo, cabeca, rede, camera e testes antes
  de alterar; preservar voo 3D, inercia, frenagem, boost, colisao e autoridade do servidor.
- [x] Criar `FlightMotionSample` e `FlightAnimationSelector` com histerese e dwell minimo.
- [x] Reescrever a biblioteca de poses: hover vivo, slow/cruise/aceleracao/desaceleracao,
  decolagem solo/ar, subida, mergulho, curva, boost, frenagem, manobra e pouso.
- [x] Corrigir pivo para o centro do tronco e pitch local da cabeca (sem copiar o torso,
  sem deslocamento e com yaw/roll/limites).
- [x] Implementar impulso de decolagem autoritativo e sincronizado, com pose de
  antecipacao/extensao/estabilizacao.
- [x] Implementar aproximacao/contato/recuperacao de pouso e motivo `END_LANDED`.
- [x] Derivar banco da trajetoria real; unificar camera/FOV em um unico controller e
  eliminar o roll duplo.
- [x] Elevar o protocolo para "3" e sincronizar manobra, invertido, solo, motivo e impulso.
- [x] Corrigir a velocidade remota de animacao (sem multiplicador 3x).
- [x] Adicionar/atualizar GameTests de estados, limites, fisica, rede e ciclo de vida;
  suite em 80/80.
- [x] Validar `compileJava`, `runGameTestServer` (80/80), `build` e `git diff --check`.
- [x] Executar smoke de `runClient` (quickPlay em "New World"): mundo carregado,
  protocolo "3" aceito nos dois lados, mixins de jogador aplicados, sem crash.
- [x] Avaliar loop vertical e reversao de 180 graus: bloqueados por ausencia de trajetoria
  segura com colisao verificada; roll de 360 graus preservado.
- [ ] Homologacao visual humana de decolagem, hover, voo, boost, frenagem, pouso, manobra,
  cabeca/torso e camera/FOV.
- [ ] Implementar loop/reversao somente quando houver fisica de trajetoria segura e
  testes de colisao; nao aceitar efeito visual como mecanica.
- [ ] Autorizacao do dono do projeto para commit/push deste lote.
