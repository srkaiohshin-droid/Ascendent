<!-- ASCENDANT // animated project showcase -->
<div align="center">

<img src="assets/ascendant-reactor.svg" alt="ASCENDANT — animated energy core" width="100%" />

<br/>

<img src="https://img.shields.io/badge/MINECRAFT-1.20.1-43B581?style=for-the-badge&labelColor=0B1020" alt="Minecraft 1.20.1" />
<img src="https://img.shields.io/badge/FORGE-47.3.0-EA7A35?style=for-the-badge&labelColor=0B1020" alt="Forge 47.3.0" />
<img src="https://img.shields.io/badge/JAVA-17-65C7F7?style=for-the-badge&labelColor=0B1020" alt="Java 17" />

<br/><br/>

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=600&size=19&duration=2800&pause=850&color=75E8FF&center=true&vCenter=true&width=780&lines=SUPERHUMAN+POWERS.+REAL+GAMEPLAY.;FLIGHT.+SPEED.+STRENGTH.+DURABILITY.;BUILT+FOR+IMMERSION.+ENGINEERED+FOR+MULTIPLAYER.;ONE+POWER+AT+A+TIME.+NO+SHORTCUTS." alt="Animated Ascendant tagline" />

<br/>

<a href="#-the-vision">THE VISION</a> ◈
<a href="#-power-protocol">POWER PROTOCOL</a> ◈
<a href="#-development-status">STATUS</a> ◈
<a href="#-build--test">BUILD</a> ◈
<a href="#-architecture">ARCHITECTURE</a>

</div>

---

<div align="center">

## ◉ SYSTEM ONLINE

**Ascendant** is a modular superhuman framework for Minecraft Java Edition.

Not a collection of flashy buttons. A foundation for powers that change how you move, fight, explore, and interact with the world.

</div>

## ✦ The vision

<table>
<tr>
<td width="50%" valign="top">

### ⚡ Powers with weight
Acceleration, momentum, control, impact, limitations, and consequences. A power should feel different to use—not just look different.

</td>
<td width="50%" valign="top">

### ◈ A world that reacts
Movement, terrain, water, collisions, damage, and player state are part of the mechanic.

</td>
</tr>
<tr>
<td width="50%" valign="top">

### ⛨ Multiplayer by design
The server validates meaningful actions and owns authoritative gameplay outcomes. Client input is a request, never proof.

</td>
<td width="50%" valign="top">

### ⌘ Built to evolve
Modular systems, explicit state, controlled networking, dedicated-server safety, and tests that verify behavior.

</td>
</tr>
</table>

## ◈ Power protocol

<div align="center">

| CORE SYSTEM | DESIGN TARGET | STATUS |
|:--|:--|:--:|
| **01 · Flight** | Takeoff, steering, acceleration, hovering, braking, landing, collision and fall rules | **IN PROGRESS** |
| **02 · Super speed** | Acceleration, momentum, precision control and safe terrain interaction | **PLANNED** |
| **03 · Super strength** | Server-validated combat and meaningful world interactions | **PLANNED** |
| **04 · Super durability** | Configurable mitigation across damage sources | **PLANNED** |
| **05 · Aquatic powers** | Distinct movement and abilities in and around water | **PLANNED** |

</div>

<details>
<summary><strong>▸ What does “complete” mean?</strong></summary>

A power is not complete just because the project compiles or a particle effect appears. It needs real gameplay behavior, correct server/client integration, lifecycle rules, relevant tests, and documented limitations. Status reflects development progress—not a promise that every feature is ready for players.
</details>

## ⌁ Development status

<div align="center">

| CHECKPOINT | RESULT |
|:--|:--:|
| Forge project foundation | ✓ |
| Player power-state capability and NBT persistence | ✓ |
| Flight controller, physics and input integration | In progress |
| Automated flight/GameTest validation | ✓ 8/8 at the recorded checkpoint |
| Remote-client multiplayer validation | Pending |
| Remaining core powers | Pending |

</div>

> **Current focus:** finish and validate flight before expanding the framework with more powers. Automated tests are useful evidence, but they do not replace a real client/server multiplayer session.

## ⌘ Technology stack

<div align="center">

<img src="https://skillicons.dev/icons?i=java,gradle,git&theme=dark" alt="Java, Gradle and Git" />

<br/>

<img src="https://img.shields.io/badge/Minecraft_Java-1.20.1-43B581?style=flat-square&logo=minecraft&logoColor=white" alt="Minecraft Java 1.20.1" />
<img src="https://img.shields.io/badge/Forge-47.3.0-EA7A35?style=flat-square" alt="Forge 47.3.0" />
<img src="https://img.shields.io/badge/JDK-17-65C7F7?style=flat-square&logo=openjdk&logoColor=white" alt="JDK 17" />
<img src="https://img.shields.io/badge/Build-Gradle_Wrapper-7C83FD?style=flat-square&logo=gradle&logoColor=white" alt="Gradle Wrapper" />

</div>

| Setting | Value |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Forge | 47.3.0 |
| Java | 17 |
| Mod ID | ascendant |
| Base package | br.com.ascendant |
| Project version | 0.1.0 |

## ⌘ Build & test

Requirements: JDK 17, Git, and the repository's Gradle Wrapper.

\`\`\`powershell
git clone https://github.com/srkaiohshin-droid/Ascendent.git
cd Ascendent
.\\gradlew.bat build
.\\gradlew.bat runGameTestServer
\`\`\`

On Linux/macOS, use \`./gradlew build\` and \`./gradlew runGameTestServer\`.

Look for the built mod in \`build/libs/\`. Run commands with JDK 17 selected. A successful build alone does not prove a gameplay feature works correctly.

## ⌬ Architecture

\`\`\`mermaid
flowchart TD
    A["Forge events & player input"] --> B["Player power state"]
    B --> C["Power controllers & physics"]
    C --> D["Server-authoritative gameplay"]
    D <--> E["Network synchronization"]
    E <--> F["Client presentation"]
    F --> G["Animation · Audio · VFX"]
    D --> H["GameTests & validation"]
\`\`\`

**Engineering rules**

- **Server authority:** the server validates requests and decides gameplay outcomes.
- **Clear ownership:** controllers, persistent state, networking, and visual effects have separate responsibilities.
- **Lifecycle safety:** death, respawn, dimension changes, water, disconnects, and transitions must be handled deliberately.
- **Dedicated-server safety:** client-only rendering code must not break a server launch.
- **Evidence over claims:** document what was implemented, tested, and remains unverified.

## ⌁ Project files

| Document | Purpose |
|---|---|
| [Master.txt](Master.txt) | Central vision, requirements, and priorities |
| [AI_CONTEXT.txt](AI_CONTEXT.txt) | Technical context and constraints for coding agents |
| [AGENT_COORDINATION.txt](AGENT_COORDINATION.txt) | Task queue, checkpoints, and acceptance criteria |
| [AGENT_LOG.txt](AGENT_LOG.txt) | Work history and validation notes |
| [Architecture](docs/ARCHITECTURE.md) | Structure and extension points |
| [Power development](docs/POWER_DEVELOPMENT.md) | Standards for implementing abilities |
| [Networking](docs/NETWORKING.md) | Communication and server validation |
| [Testing](docs/TESTING.md) | Build and test workflow |

---

<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=0:20113b,50:243b63,100:080b16&height=130&section=footer" width="100%" alt="" />

<h3>ASCENDANT // POWER IS A SYSTEM.</h3>

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&size=13&duration=3200&pause=1200&color=8B7CFF&center=true&vCenter=true&width=600&lines=BUILD+WITH+PURPOSE.;VALIDATE+EVERY+ABILITY.;THE+NEXT+EVOLUTION+IS+IN+PROGRESS." alt="Animated footer tagline" />

<sub>Independent Minecraft mod project · Work in progress</sub>

</div>
