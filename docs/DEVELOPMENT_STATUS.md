# Ascendant — Development Status & Content Registry Report

> **Target Platform:** Minecraft Java 1.20.1 | Forge 47.3.0 | Java 17 (Temurin 17.0.20.1)  
> **Package Base:** `br.com.ascendant`  
> **Status Date:** 2026-10-21
> **Build Status:** BUILD SUCCESSFUL (Gradle 8.8, 35/35 GameTests Passing)

---

## 1. Arquitetura Atual

A base do Ascendant foi expandida para um framework modular, server-authoritative e orientado a dados para superpoderes, habilidades, trajes e pacotes de conteúdo (inspirado nos conceitos do Fisk's Superheroes e Armoured Workshop), sem quebrar ou regredir o sistema de voo tridimensional existente.

```mermaid
flowchart TD
    subgraph Core [br.com.ascendant.core]
        PC[PowerCategory - 40 Categorias]
        PT[PowerType - Active/Passive/Toggle/Charged/Sustained]
        AC[AbilityCategory - 14 Categorias]
        AT[AbilityTrigger - Key/Sneak/Attack/Hurt/Tick/Toggle]
    end

    subgraph Component_System [br.com.ascendant.power.component]
        IPC[IPowerComponent]
        CR[ComponentRegistry]
        Dash[DashComponent]
        Blink[BlinkComponent]
        Leap[LeapComponent]
        Dmg[DamageActionComponent]
        Heal[HealActionComponent]
        Shield[ShieldActionComponent]
        Attr[AttributeModifierComponent]
        Mit[DamageMitigationComponent]
        Water[WaterAdaptationComponent]
        Vision[Thermal / NightVision]
        FlightComp[FlightActionComponent]
        Invis[InvisibilityComponent]
        Levitation[LevitationComponent]
        Vortex[VortexActionComponent]
        Summon[SummonEntityComponent]
        Phase[PhaseComponent]
        Templ[TemplateComponent]
    end

    subgraph Registries [br.com.ascendant.power]
        PR[PowerRegistry - 500 Powers]
        AR[AbilityRegistry - 500 Abilities]
        SR[SuitRegistry - Armoured Workshop Suits]
    end

    subgraph Runtime_Engine [br.com.ascendant.power.runtime]
        PRE[PowerRuntimeEngine]
        Cooldowns[Cooldown Tracking & Server Validation]
        Toggles[Active Toggle State per Player]
        EventHooks[LivingHurt, LivingFall, PlayerTick]
    end

    subgraph Packs [br.com.ascendant.pack]
        PM[PackManager]
        PS[PackSecurity - Anti Zip-Slip & Code Execution]
        Catalog[Local & Remote Catalog Provider]
    end

    Core --> Registries
    Component_System --> Registries
    Registries --> Runtime_Engine
    Packs --> Registries
```

---

## 2. Métricas de Conteúdo Auditadas

Todos os poderes e habilidades foram auditados pelo `ContentValidator` automatizado em tempo de inicialização e em testes de servidor integrados (`runGameTestServer`).

| Métrica | Quantidade | Observações |
| :--- | :--- | :--- |
| **Total de Poderes Cadastrados** | **500** | Distribuídos entre as 40 categorias oficiais |
| **Poderes Funcionais (Executores Reais)** | **113** | Apoiados por componentes com lógica física, atributos, combate e mobilidade reais |
| **Poderes em Template / Expansão Futura** | **387** | Marcados honestamente com `functional = false` e parâmetros mecânicos detalhados |
| **Total de Habilidades Cadastradas** | **500** | Distribuídas entre as 14 categorias de habilidades |
| **Habilidades Funcionais (Executores Reais)** | **111** | Apoiadas por componentes operacionais testados e validados |
| **Habilidades em Template / Expansão Futura** | **389** | Marcadas honestamente com `functional = false` |
| **Trajes Cadastrados (Armoured Workshop)** | **6** | Classic Ascendant, Speedster Mk-I, Titan Exosuit, Atlantean Regalia, Solar Aegis, Shadow Infiltrator |
| **Erros de Validação / Referências Órfãs** | **0** | Validação automatizada aprovada sem exceções |

---

## 3. Categorias de Poderes e Exemplos Funcionais

Os poderes estão distribuídos rigorosamente em 40 categorias (13 nas primeiras 20 e 12 nas restantes = 500 poderes):

1. **Voo e mobilidade aérea:** `ascendant:flight` (integração direta com o `FlightController` e física 3D), `ascendant:aerobatic_dash`, `ascendant:aerial_launch`.
2. **Supervelocidade e movimento cinético:** `ascendant:kinetic_sprint` (modificador de atributo `generic.movement_speed`), `ascendant:mach_dash`, `ascendant:hyper_leap`, `ascendant:kinetic_dampener`.
3. **Superforça e manipulação física:** `ascendant:titan_strength` (modificador de `generic.attack_damage`), `ascendant:seismic_slam`, `ascendant:colossal_leap`, `ascendant:shockwave_knockback`.
4. **Resistência, invulnerabilidade e regeneração:** `ascendant:diamond_skin` (modificador de `generic.armor`), `ascendant:kinetic_mitigation` (mitigação percentual em `LivingHurtEvent`), `ascendant:fire_immunity`, `ascendant:nanite_regeneration`.
5. **Teletransporte e portais:** `ascendant:blink_step` (teletransporte instantâneo via raycast com aterrissagem segura), `ascendant:spatial_leap`, `ascendant:void_step` (faseamento com destino carregado e livre de colisão), `ascendant:phase_dash`.
6. **Energia e rajadas:** `ascendant:energy_blast`, `ascendant:energy_burst`, `ascendant:kinetic_pulse`.
7. **Fogo e plasma:** `ascendant:pyroclastic_burst`, `ascendant:fireball_shot`, `ascendant:inferno_resistance`.
8. **Gelo e criocinese:** `ascendant:cryo_blast` (aplicação de freeze ticks e lentidão em área), `ascendant:frost_bite_touch`, `ascendant:subzero_burst`.
9. **Eletricidade e magnetismo:** `ascendant:thunderbolt_strike` (invocação de relâmpago no alvo com autoridade de servidor), `ascendant:static_shock`, `ascendant:electromagnetic_pulse`.
10. **Água e poderes aquáticos:** `ascendant:aquatic_respiration` (respiração subaquática infinita, graça do golfinho e visão), `ascendant:tidal_wave`, `ascendant:water_dash`.
11. **Ar e vento:** `ascendant:gale_blast`, `ascendant:cyclone_leap`, `ascendant:wind_cushion`.
12. **Terra e minerais:** `ascendant:stone_skin_plate`, `ascendant:tremor_shock`, `ascendant:boulder_knockback`.
13. **Plantas e vinhas:** `ascendant:vine_pull` (puxão cinético direcional), `ascendant:spore_cloud`.
14. **Som e ressonância:** `ascendant:sonic_screech`, `ascendant:acoustic_shockwave`, `ascendant:sonic_boost_leap`.
15. **Luz:** `ascendant:solar_healing`, `ascendant:photonic_dash`.
16. **Sombras:** `ascendant:shadow_teleport`, `ascendant:shadow_strike`.
17. **Telecinese:** `ascendant:kinetic_pull`, `ascendant:kinetic_push`, `ascendant:psychic_slam`, `ascendant:telekinetic_levitate` (levitação sustentada de alvo visível e não aliado).
18. **Gravidade:** `ascendant:gravity_leap`, `ascendant:gravitational_slam`, `ascendant:weightlessness_cushion`.
19. **Escudos e barreiras:** `ascendant:kinetic_barrier` (corações de absorção adicionais e resistência), `ascendant:energy_aegis`, `ascendant:reactive_deflection`.
20. **Cura e suporte:** `ascendant:healing_touch`, `ascendant:regeneration_aura`, `ascendant:restorative_surge`.
21. **Sentidos aprimorados:** `ascendant:sonar_pulse`, `ascendant:reflex_boost`.
22. **Visão especial:** `ascendant:night_vision_eyes`, `ascendant:thermal_scan` (efeito glowing em entidades próximas), `ascendant:infrared_toggle`, `ascendant:refractive_invisibility` (efeito vanilla de invisibilidade temporário).
23. **Camuflagem e furtividade:** `ascendant:chameleon_dash`, `ascendant:stealth_strike`.
24. **Metamorfose:** `ascendant:beast_leap`, `ascendant:feral_swipe`.
25. **Clones e duplicatas:** `ascendant:clone_dash_decoy`, `ascendant:decoy_burst`.
26. **Invocação:** `ascendant:lightning_elemental_strike`, `ascendant:healing_wisp`, `ascendant:spectral_wolf_pack` (três lobos vanilla domados).
27. **Armadilhas e minas:** `ascendant:kinetic_mine`, `ascendant:frost_trap_burst`.
28. **Controle de multidões:** `ascendant:staggering_wave`, `ascendant:cryo_lockdown`, `ascendant:sonic_repulsion`, `ascendant:vortex_maelstrom` (atração sustentada server-side).
29. **Manipulação tecnológica:** `ascendant:cybernetic_speed`, `ascendant:nanite_heal_injection`.
30. **Habilidades cósmicas:** `ascendant:stellar_dash`, `ascendant:supernova_blast`.
31. **Poderes psíquicos:** `ascendant:mind_blast`, `ascendant:psychic_push`.
32. **Combate corpo a corpo:** `ascendant:brawler_strength`, `ascendant:haymaker_punch`, `ascendant:uppercut_leap`.
33. **Armas de energia:** `ascendant:energy_shot`, `ascendant:plasma_grenade`.
34. **Defesa reativa:** `ascendant:reactive_shield`, `ascendant:thorns_retaliation`, `ascendant:emergency_teleport`.
35. **Adaptações ambientais:** `ascendant:feather_fall_passive`, `ascendant:lava_skin`, `ascendant:abyssal_lung`.
36. **Exploração:** `ascendant:mountaineer_leap`, `ascendant:expedition_dash`, `ascendant:night_explorer`.
37. **Poderes aquáticos especializados:** `ascendant:atlantean_sovereignty`, `ascendant:hydro_torpedo`, `ascendant:abyssal_pulse`.
38. **Transformação corporal:** `ascendant:titan_mass_form`, `ascendant:graphene_hardening`.
39. **Controle territorial:** `ascendant:seismic_disruption`, `ascendant:concussive_pulse_dome`.
40. **Poderes híbridos:** `ascendant:kinetic_thunder_strike`, `ascendant:blazing_dash`, `ascendant:glacial_shield`.

---

## 4. Catálogo de Trajes (Armoured Workshop)

O módulo `br.com.ascendant.suit` implementa:
- Separação entre aparência e lógica de superpoderes.
- Atributos visuais (`SuitModelInfo`: textura, camada emissiva, paleta primária/secundária, peças modulares e capa).
- Modificadores diretos de atributos de jogador (`AttributeModifier`).
- Concessão dinâmica de poderes e habilidades vinculados ao traje via `SuitManager`.
- Comandos integrados: `/ascendant suit list`, `/ascendant suit equip <player> <suit_id>`, `/ascendant suit unequip <player>`.

---

## 5. Gerenciador de Pacotes e Segurança

O módulo `br.com.ascendant.pack` implementa:
- Diretório de pacotes: `run/ascendant_packs/` (ou `ascendant_packs/` na pasta do jogo).
- Manifesto declarativo versionado (`manifest.json`) com ID, nome, versão, autor, compatibilidade (MC 1.20.1, Forge 47.3.0, Ascendant 0.1.0) e contagem de conteúdo.
- Extração atômica com diretório temporário e rollback em caso de falha.
- **Segurança estrita (`PackSecurity`):**
  - Rejeição de Path Traversal (Zip-Slip) — caminhos contendo `..` ou barras absolutas.
  - Bloqueio completo de arquivos executáveis (`.class`, `.jar`, `.exe`, `.bat`, `.cmd`, `.sh`, `.ps1`, `.js`).
  - Limite de descompressão contra Zip-Bombs (máx 5.000 entradas e 200 MB).
- Exportador de pacotes (`CatalogExporter` / `/ascendant pack export`).

---

## 6. Resultado dos Testes e Build

- **Gradle Build:** `BUILD SUCCESSFUL` (compilação limpa sem erros).
- **GameTests (`runGameTestServer`):** **35/35 testes obrigatórios aprovados**; servidor iniciou, executou os 35 testes e encerrou normalmente.
- **Poderes funcionais antes/depois do lote B1:** 108 → 113 (o estado anterior tinha 500 registros; a contagem de habilidades funcionais não foi alterada pelo B1: 111).
- **Catálogo após B1:** 500 poderes (113 funcionais, 387 templates); 500 habilidades (111 funcionais, 389 templates).
- **Poderes conectados:** `refractive_invisibility` (ACTIVE), `telekinetic_levitate` (SUSTAINED), `vortex_maelstrom` (SUSTAINED), `spectral_wolf_pack` (ACTIVE) e `void_step` (ACTIVE). Levitação levita uma entidade visível não aliada; vórtice atrai entidades próximas não aliadas; invocação cria três lobos vanilla domados e associados ao jogador; faseamento move o jogador para destino carregado e sem colisão.
- **Ativação de poderes ACTIVE:** adicionado caminho server-authoritative no runtime, pacote de rede e comando administrativo `/ascendant power use <targets> <power_id>`. Não há ainda interface/keybind/menu radial para essa ativação.
- **Ciclo de trajes:** ao trocar/desequipar traje, ou revogar poder, estado toggle/cooldown e callbacks são limpos quando o jogador perde a última fonte de acesso. A cobertura verifica limpeza de night vision e modificador de movimento, ausência de duplicação ao re-equipar e preservação quando o poder continua intrínseco.
- **Limite da verificação de expiração:** o teste confirma duração configurada finita para invisibilidade (5 ticks no fixture); o efeito não é esperado a expirar naturalmente no ServerPlayer criado pelo fixture do GameTest.
- **Arquivos principais do B1:** cinco componentes novos, `ComponentRegistry`, `PowerCatalogData`, `PowerRuntimeEngine`, `SuitManager`, `AscendantNetwork`, `AscendantCommands` e `FrameworkGameTests`.
- **GameTests cobertos pelo lote:** integridade/contagens, registro e round-trip dos cinco componentes, execução server-side, duração finita, limpeza de efeitos/modificadores no desequipar, preservação de posse alternativa e ausência de duplicação de modificadores.
- **Testes novos cobertos:**
  1. `catalogIntegrityAndCountValidation`: valida contagem de 500 poderes/500 habilidades, contagens funcionais/templates e integridade de referências.
  2. `coreComponentsExecution`: valida impulsão do DashComponent, cura do HealActionComponent e absorção do ShieldActionComponent.
  3. `abilityActivationAndCooldownEnforcement`: valida verificação de permissão, execução da habilidade e bloqueio por cooldown.
  4. `suitEquipGrantsAssociatedPowers`: valida concessão de poderes pelo traje e remoção ao desequipar.
  5. `packSecurityPathTraversalAndExecutableRejection`: valida bloqueio de Zip-Slip e arquivos `.class` executáveis.
  6. `newComponentsAreRegisteredAndExecuteOnServer`: valida mapeamento, round-trip e execução dos cinco novos componentes.
  7. `temporaryInvisibilityUsesFiniteDuration`: confirma aplicação de duração finita configurada.
  8. `suitRemovalCleansToggleButPreservesIntrinsicPower`: verifica limpeza de toggle/efeito/modificador, ausência de duplicação e preservação da fonte intrínseca.

---

## 7. Instruções para Continuidade por Próximos Agentes

1. **Adicionar novos poderes ou habilidades:**
   - Adicione novas entradas no `PowerCatalogData` ou `AbilityCatalogData`, ou crie um content pack em `ascendant_packs/`.
   - Se for introduzir uma nova mecânica, crie um novo componente em `br.com.ascendant.power.component` implementando `IPowerComponent` e registre sua fábrica em `ComponentRegistry`.
2. **Executar validação de catálogo e compilação:**
   - Execute `.\gradlew.bat compileJava` para verificar a compilação.
   - Execute `.\gradlew.bat runGameTestServer` para rodar os 35 GameTests automatizados.
3. **Próxima implementação:** construir UI de catálogo, keybinds e menu radial sobre os registries e o networking atuais; não duplicar essas camadas.
