# Create: Interoperable (CIO)

> A free and open **"addon for an addon"**, developed using free software and Anthropic's Sonnet 5 via Claude Code. All relevant Claude Code skills (skill.md) explaining the inner workings of this addon are also available in this repository.
>
> No copyrights will be asserted, but formally it has an MIT license.
> All CIO code and assets (including all hand-made models or textures) may be fully modified and utilised without need for credit.
>
> **Alpha.** Everything here is a work in progress. Nothing is finalised — blocks, recipes, models, wattages, balance and behaviour can all still change.
>
> NeoForge 1.21.1 · v0.1.x · mod id `createinteroperable` · MIT licensed


## What CIO is

Create: Interoperable was primarily devised to connect Create-based **energy mods** with **furniture mods** — mods
that originally didn't talk to one another — and Create-based energy mods themselves. The aim is to make them interoperate: furniture that runs on a real power grid and being able to build one grid that reaches across addons. Then, over time, build new, fun content on top of that shared ground.

In practice, two things get wired together:

- **Power → furniture.** A "Domestic Electrical Board" (called Power Kit in-game) turns a Create power grid into the
  electricity that furniture appliances run on, and a growing set of third-party furniture
  blocks gain a genuine "needs to be plugged in" requirement.
- **Grid → grid.** The two Create electrical addons — **Power Grid** and **Electro
  Energetics** — can hand voltage (and as much simulation as possible) to one another through a coupler block, so a build isn't locked to whichever one it started with. Neither is treated as the "main" one.

## About this project

- **Models** — every block model was hand-built or hand-modified in
  [Blockbench](https://www.blockbench.net/) by the author. You are free to use them in anyway you see fit.
- **Code** — the large majority of the Java was written by **Anthropic's Claude Code
  (Sonnet 5, High reasoning)**, from the author's direction. I (the author) am a programmer, but not nearly proficient enough in Java to make this from scratch. Help and assistive PRs are welcome.
  `.claude/skills/` holds the engineer-facing context the assistant works from — a
  lightweight `cio-context` entry point plus specialized skills per API/feature area;
  this README is the plain-language overview.
- **Status** — a personal project, shared openly, in **alpha**. It builds and runs, but
  none of it is "done-done", and no part is more settled than another. Suggestions are
  folded in as collaborators raise them.

## Credits

CIO is compatibility code. It only does anything because of the mods it builds on:

| Mod | By | What CIO builds on |
|---|---|---|
| **Create** | Creators of Create | The foundation everything here extends — blocks and block entities, the goggle / value-slider UI, kinetic shafts, fluid tanks, boilers. |
| **Create: Power Grid** | patryk3211 | One of the two grid standards CIO speaks. Its wire terminals, `ElectricBlock` API and circuit solver back the Power Grid side of every CIO device. |
| **Create: Electro Energetics** | george_vi | The other grid standard CIO speaks, a first-class equal to Power Grid. Its `SimulatedDevice` circuit API backs the Electro Energetics side of every CIO device. |
| **MrCrayfish's Furniture Mod: Refurbished** | MrCrayfish | Its appliance-electricity system is what the Domestic Electrical Board feeds. CIO's powered appliances register as nodes on Crayfish's own network — wrench-linking, wire rendering and the "missing power" overlay all come with it. Also its water fixtures. |
| **Create Train Navigator** | MrJulsen | Its Advanced Displays are retrofitted so a freestanding display only shows text while powered. |
| **Cold Sweat** | momosoftworks | Its temperature simulation is what the Steam Hearth warms and the Aircon cools — and CIO makes it keep running on Peaceful difficulty instead of silently switching off. |
| **Create: Pipes n Physics** | StaticFX | CIO's steam fluid declares itself a real, buoyant, low-viscosity gas so PnP's pipe network moves real throughput instead of a flat, capped rate — an optional integration. |

CIO also retrofits a power requirement onto furniture and utility blocks from **Let's Do
Furniture & Beachparty**, **Another Furniture**, **Alpine Whispers**, **Bibliocraft** (the
1.21.1 fork), **Vista** (cameramod)'s TV, **Iden's Decor** and **WaterFrames**' video screens
(the last one needs WaterFrames' own `waterframes_refurbished_compat` bridge jar too). Each of
those is an optional integration that switches itself off cleanly when the mod isn't installed.
A purely experimental **Immersive Vehicles** integration lives on the `experimental` branch
only — see [Branches](#branches).

CIO is an unofficial fan project and is not affiliated with or endorsed by any of these.

## Requirements

**Required:** NeoForge, Minecraft, Create.

**Optional — CIO adapts to whatever is present:**

- **Power Grid** and **Electro Energetics** — neither is required and neither is
  privileged. At least one makes the grid features meaningful; CIO simply exposes the
  half (or halves) you have installed.
- **Refurbished Furniture** — with it, appliances run on Crayfish's electricity network;
  without it, CIO's own built-in appliance grid stands in so the same features keep
  working.
- **Let's Do / Another Furniture / Alpine Whispers / Bibliocraft / Create Train Navigator
  / Vista / WaterFrames / Cold Sweat** — every retrofit no-ops if its mod is absent, and
  each has a config toggle (all default on).
- **Create: Pipes n Physics** — with it, CIO's steam fluid gets real pipe-network
  throughput instead of a flat rate; without it, steam still vents to atmosphere at an
  open pipe end for free, just without PnP's physics.

---

## Working now

Everything in this section is in the mod today and reachable in survival — it's on the
creative menu, and most of it is craftable. It's still alpha: expect rough edges and the
odd missing texture.

### Domestic Electrical Board — Power Kits

The bridge from a Create power grid to furniture electricity. A four-tier family —
**Improvised / Domestic / Commercial / Industrial** — that takes a single 120 V grid feed
and hands out the low- and medium-voltage power appliances consume, in place of Refurbished
Furniture's fuel-burning generator.

- Wall-, floor- and ceiling-mountable; a thermal model that can overheat and explode if
  overdriven; goggle readouts; a shared load budget across everything a kit feeds.
- Higher tiers add capacity and a scrollable intake-voltage selector.
- A **Redstone Switch** that cuts or enables the appliance network a kit feeds exists in
  both grid flavours, but is held back from the creative menu for now (`/give` only).
- Two boards accidentally sharing one electrical network refuse power instead of silently
  double-billing it.
- **Two full families, one per grid standard** — a Power Grid set and an Electro Energetics
  set, equal in every way. They share one block-entity implementation; a per-block check
  picks which grid's API to read, so the thermal model, load pools, goggles, rendering and
  the appliance link are the exact same code on both.
- All eight kits are craftable.

### Powered furniture appliances

CIO gives furniture blocks from other mods a real electrical requirement, drawn from the
grid above. When Refurbished Furniture is installed the appliance just registers as a node
on Crayfish's own network — so its wire-linking, "missing power" label and wire rendering
are free — and CIO's built-in grid mirrors that when it isn't.

Rules shared across all of them:

- Only the *active* behaviour is gated — a radio won't play, an auto-crafter won't craft —
  never the block's menu or its placement. You can lay out a crafter or stock a fridge
  before wiring it.
- A block bills power only while it's actually working.
- Retrofitted lights place **dark** and their redstone wiring is handed to the grid while
  wired, so there's no placement flash and one system owns the light.

| Retrofitted | When unpowered |
|---|---|
| Let's Do Furniture / Candlelight **lamps & street lanterns** | Stay dark; manual toggle kept; stacking posts put a node only on the head |
| Let's Do Furniture **gramophone** | Won't play; stops mid-disc on power loss, resumes on return |
| Let's Do Beachparty **radio & mini-fridge** | Radio won't tune in; fridge stops fermenting and blocks hopper automation |
| Another Furniture **lamps** | Stay dark; their redstone control is handed to the grid |
| Alpine Whispers **fairy lights** | Actually go dark — the block has no light state of its own, so this took real work |
| Bibliocraft **Fancy Crafter** | Won't auto-craft; the grid drives its `POWERED` state instead of redstone |
| Bibliocraft **Fancy Lamp** | Stays dark, through the shared lamp machinery |
| Create Train Navigator **Advanced Displays** | Freestanding displays go blank; contraption displays untouched. Wire any one block of a multi-block board and the whole board lights. |
| Vista (cameramod) **TV** | Off — with power it defaults *on*, and redstone becomes a manual kill switch instead of an on-switch. A grown, connected wall of TVs is billed n² the single-TV rate. |
| WaterFrames **video screens** | Stop rendering text (needs WaterFrames' own `waterframes_refurbished_compat` bridge jar, not just WaterFrames alone) |
| Iden's Decor **Computer** | Shows no floppy-disk text (disks still insert/eject). 15 W, billed only while a disk is in. |
| Bibliocraft **Fancy Lantern** | *(not a power feature)* light level capped to a Soul Lantern's, matching Bibliocraft's own dim variants |

**Iden's Decor** is supported two ways. Its lights (already Crayfish devices on Iden's side, in their
`refurbished_energy` pliers mode, which new lights now place in by default) are billed 3 W each on the 12 V rail; its Wall Lamp, Flood Lamp, Floodlight and Fluorescent Light Block become 3 W grid lamps too (dark when unpowered). Its buttons, levers and
switches (Heavy / Gate Button, Heavy / Emergency / Blast Lever, Light / Power / Valve Switch,
Core Button / Lever Control Panel) get **Electric** twins: same models, no redstone, and instead a
wire-in-wire-out grid switch like Crayfish's light switch, closed while the lever is on or the
button is held. Craft one from the original plus a CEE Copper Wire Spool or a CPG Copper Wire.
Iden's originals are renamed **Redstone …** in game so the two read apart.

Iden's **telephone** becomes a working CIO-compatible phone (CIO swaps its block class in at
Iden's own registration, keeping Iden's model, handset and loot). Sneak + right-click with an
empty hand sets its number, area code, label, the number it calls, and Auto-Answer. It needs
appliance-grid power (2 W, billed only on a call) and a tap wire: a CPG and/or CEE tap nub on the
back of the base, on the same tap line as any other phone, CIO or Iden. Lifting the handset (or a
redstone pulse while idle) calls the set number (a redstone-placed call is withdrawn if the signal
drops before it's answered); a called phone rings, answering is lifting the
handset (or automatic), and putting it back hangs up. The answering end emits redstone during the
call, like CIO's phones. Goggles show its number, status, power and tap wiring.

One thing already works with no CIO code: Bibliocraft's alarm clock emits a normal redstone
pulse.

### Plumbed water fixtures

Sinks, baths and toilets stop being infinite-water and need a real supply. On by default,
for both:

- **Refurbished Furniture** fixtures — a bare-hand right-click pulls fluid straight from an
  adjacent supply into the fixture.
- **Let's Do / Farm & Charm / Alpine Whispers** sinks & bathtubs — Create pipes fill a
  small internal buffer, and the faucet spends from that. Optional bucket top-up.

### Telephone

A working in-world telephone — a number registry with routing, dyeable. Three craftable
variants (one that needs both grids, one for each grid on its own) share a protocol-neutral
core so neither grid mod forces the other to load. Right-click the back plate for its settings
(own area code and number, label, the number it calls, Auto-Answer — the same screen Iden's
phone uses, plus a **Pulse (3s)** option: calls placed from that phone make the answering phone's Call
Breaker, Call Feeds and redstone output switch on and off every 3 seconds instead of staying on); right-click the dial
for a dial-only version. Every wire nub is labelled with what
it does (Telephone Positive/Negative 12 V in, Telephone Tap, Call Breaker, Call Feed ±).

### Grid Coupler

A one-block bridge that lets a Power Grid circuit and an Electro Energetics circuit trade
voltage — placed directly, wrench-rotated, on the creative menu.

- A 3-position switch — `CPG → CEE`, `Off`, `CEE → CPG` — carries current one direction at
  a time; a permanently two-way bridge is an unstable feedback loop.
- The side reading voltage acts as a near-open-circuit meter so it doesn't load the circuit
  it measures; the delivering side injects through near-zero resistance. Getting that split
  wrong was an early bug — the bridge read 0.2 V from a 120 V source.
- **Reflected impedance:** it estimates the load on the receiving side and pushes it back
  onto the source grid, so upstream generation feels a downstream load — whichever
  direction is selected.
- Changing direction eases an animated gauge needle across and cuts transfer for the
  ~3 seconds it's moving.
- A fresh device stays fully passive until it's confirmed its real configured role for the
  session, closing a startup window where it could briefly act as a live source into a
  circuit that already had its own power.
- Craftable, on the creative menu, needs both grid mods.

### CPG Double Connector

A Power Grid–only block for splitting a wire run: two independent terminals, each its own
zero-resistance connection point, in one block — no coupling or logic between them, just
two connectors where one used to be. Craftable, needs only Power Grid.

### Aircon (cooling)

A three-part air conditioner that carries `hot_air` / `cold_air` through Create's pipe
network and cools a room's real Cold Sweat ambient temperature — the cooling mirror of the
Steam Hearth below. These gasses are work in progress.

- **Aircon Motor** (bottom unit) — needs real electrical terminals, so it comes in a Power
  Grid variant and an Electro Energetics variant.
- **Aircon Fan** (top) and **Aircon Venter** (ducting) are electrical-backend-agnostic and
  register regardless of which grid mod(s) are installed, so either motor variant — or
  neither, on a Create-only install — pairs with the same top half.
- The venter also melts nearby snow and ice when it's running warm, and freezes standing
  water near it when running cold.
- All four pieces are craftable; see `recipes.md` for the exact grids.

### Steam Hearth (heating)

A Create-only heating loop, built in the world rather than placed as one block: a **Steam
Outlet** sits on a Create Fluid Tank and reads the boiler's real heat and water level into a
"steam" fluid that flows through Create pipes (with real throughput when Create: Pipes n
Physics is installed). Feed that steam to a **Steam Hearth Inlet** / **Water Outlet** pair
with 0–3 plain Copper Blocks between them, wrench either end, and the copper blocks convert
into linked **Steam Hearth Radiator** segments.

- Warms the room like a Cold Sweat Hearth — a real, unconditional temperature change for
  nearby players, not just a status icon — and melts nearby snow/ice the hotter it runs.
- A **Brass Heater** (dead-end kinetic shaft block, reads the same steam loop) reports a
  0–100% throttle to Cold Sweat too; in the game but not yet craftable.
- Vanilla cats will seek out and lie on an assembled, sufficiently warm Steam Hearth, the
  same way they do a lit furnace or a bed.
- The whole loop is craftable except the Brass Heater; see `recipes.md`.

---

## In development — not yet available / rough drafts and ideas

Everything below is either switched off entirely (a `BRIDGE_EXTRAS` flag in `CIOBlocks` /
`CIOItems` disables the extra connector models), still settling in, or — for the Pantographs &
Wires bridge and the Immersive Vehicles integration — real and loadable but experimental enough
to live only on the `experimental` branch (see [Branches](#branches)). None of it is on the
creative menu or craftable. Documented here for context, not for use.

### Extra connector models

**Connector** and **Large Connector** — additional versions of the Power Grid wire
connector with their own models, for a different look at the end of a wire. They behave
exactly like the base connector (same wire type, same zero-resistance single terminal, same
block entity); only the model differs. Registered behind the `BRIDGE_EXTRAS` switch, so not
available yet.

### Pantographs & Wires bridge

An experimental conductor bridge letting a Create: Pantographs & Wires conductor
participate in an Electro Energetics circuit. Entirely inert unless both Electro Energetics
and Pantographs & Wires are installed together. Real code, but early and untested enough
that it's kept off the mod's `main` branch for now — `experimental` branch only.

### Immersive Vehicles (purely experimental)

**Purely experimental, `experimental` branch only — not on `main`, untested in real play.**
It patches Immersive Vehicles' (formerly MTS) internals through mixins, so any Immersive
Vehicles update can break it. Each half has its own config toggle (`mtsAaSpotlight`,
`mtsPoleLights`) to hand the blocks straight back to Immersive Vehicles.

- **AA Spotlight** (MTS Official Content Pack) — won't light its beam, auto-rotate or
  traverse, and every light on it (standby LED included) goes dark; looking at it shows
  "Missing power". Wire the **AA Base Plate** it sits on: a ground-placed plate grows a power
  terminal on one corner. A searchlight-class load: 6 kW on the 120 V rail while lit (+5 %
  while auto-rotating). Spotlights on vehicles, and the AA guns, are untouched.
- **Street lights & traffic signals** — every lamp goes dark (a traffic signal shows nothing
  at all, not even its unlinked red flash), and a street light stops lighting the world. Wire
  the **pole block** they hang on — only a pole block that carries a lamp gets a nub. Power is
  checked every tick: cut the supply, or let a brownout drop the rail, and they go dark again.
  100 W per street light, 25 W per signal head, 120 V rail. The **Signal Controller** is a node
  too but needs no power (yet). Signs are untouched.

### Simple Voice Chat telephone support

With [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) installed (optional, server-side plugin), an
*answered* call carries voice: anyone talking within 5 blocks of either phone is heard, from that
phone, out of the one at the other end - spatially, so bystanders next to it hear it too. Works with
all five phones (Interoperable, CPG, CEE, and Iden's two). Whispering carries half as far. Both phones also play a quiet line hiss (with the odd soft crackle)
for as long as the call is up, over a faint open-line dial tone. Radius, playback range and hiss loudness (`lineNoise`, 0 = off) are in
the `telephoneVoice` config section, or switch the whole thing off with `relayEnabled`. Without
Simple Voice Chat nothing changes.

---

## Branches

- **`main`** — the alpha line everything in *Working now* above ships from.
- **`experimental`** — `main` plus integrations that are real but not yet trusted:
  the Pantographs & Wires bridge and the Immersive Vehicles integration. It is rebased onto
  (or merges in) `main` as `main` moves; an experimental feature graduates by landing on
  `main`.

## Building

Standard NeoForge / Gradle. The wrapper provisions the JDK 21 toolchain itself (Gradle
itself can run on JDK 17). Build from a **local NTFS path** — a WSL-mounted path breaks
Gradle's file hasher. `./gradlew build` produces
`build/libs/createinteroperable-<version>.jar`.

---

*Create: Interoperable is an unofficial fan project, not affiliated with or endorsed by the
authors of Create, Power Grid, Electro Energetics, MrCrayfish's mods, Create Train
Navigator, or any other mod it works with. All outstanding rights belong to their respective
owners.*
