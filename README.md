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
  `.claude/skills/cio-context/` is the engineer-facing context file the assistant works
  from; this README is the plain-language overview.
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

CIO also retrofits a power requirement onto furniture and utility blocks from **Let's Do
Furniture & Beachparty**, **Another Furniture**, **Alpine Whispers** and **Bibliocraft**
(the 1.21.1 fork), and reads **Cold Sweat** for the Brass Heater's temperature output. Each
of those is an optional integration that switches itself off cleanly when the mod isn't
installed.

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
  / Cold Sweat** — every retrofit no-ops if its mod is absent, and each has a config
  toggle (all default on).

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
- A **Redstone Switch** cuts or enables the appliance network a kit feeds.
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
| Bibliocraft **Fancy Lantern** | *(not a power feature)* light level capped to a Soul Lantern's, matching Bibliocraft's own dim variants |

Two things already work with no CIO code: Bibliocraft's alarm clock emits a normal redstone
pulse, and Cold Sweat temperature responds to the Brass Heater once it's fed steam.

### Plumbed water fixtures

Sinks, baths and toilets stop being infinite-water and need a real supply. On by default,
for both:

- **Refurbished Furniture** fixtures — a bare-hand right-click pulls fluid straight from an
  adjacent supply into the fixture.
- **Let's Do / Farm & Charm / Alpine Whispers** sinks & bathtubs — Create pipes fill a
  small internal buffer, and the faucet spends from that. Optional bucket top-up.

### Telephone

A working in-world telephone — dial, label and per-number screens, a number registry with
routing, dyeable. Three craftable variants (one that needs both grids, one for each grid on
its own) share a protocol-neutral core so neither grid mod forces the other to load.

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
- On the creative menu, no recipe yet, one texture still missing.

---

## In development — not yet available / rough drafts and ideas

These exist in the codebase but are **not** registered into a normal game: a `BRIDGE_EXTRAS`
switch in `CIOBlocks` / `CIOItems` is currently **off**, so most of them don't load at all,
and the rest aren't on the creative menu or craftable. They're documented here for context,
not for use.

### Interoperable Transformer (multiblock)

The intended flagship bridge: a 2-tall × 3-long structure — a Power Grid keystone at one
end, an Electro Energetics keystone at the other, fillers between, wrenched together. Three
block classes, because the two ends need incompatible Java parents rather than just
different states. Currently code-only — placeholder terminal positions, no models — so it's
switched off.

### Interim transformer & single coupler

- A 1×1 **interim transformer** that packs both grid halves into one block. It's where the
  coupler's electrical behaviour was first worked out; kept around as a reference.
- The single **Interoperable Coupler** is being reworked into a pure signal relay — copying
  a voltage reading with no current path. Until that lands it still carries the older
  two-terminal logic and stays disabled.

### Extra connector models

**Connector** and **Large Connector** — additional versions of the Power Grid wire
connector with their own models, for a different look at the end of a wire. They behave
exactly like the base connector (same wire type, same zero-resistance single terminal, same
block entity); only the model differs. Registered behind the `BRIDGE_EXTRAS` switch, so not
available yet.

### Steam & heat

Two kinetic blocks meant to link Create's boilers to Cold Sweat body temperature:

- **Steam Outlet** — sits on top of a Create Fluid Tank, reads the boiler's real heat and
  water levels, and produces a "steam" fluid into Create's pipe network.
- **Brass Heater** — a dead-end kinetic shaft block that consumes that steam and reports a
  0–100 % throttle (driven by shaft speed) to Cold Sweat.

Loadable but not surfaced — no creative-menu entry, no recipe.

### Set aside

- A **ceiling-tile** re-implementation was built and then removed.
- Bridging **Create Crafts & Additions** (Forge Energy) into the grid was designed and
  shelved — it duplicates an electrical layer Power Grid already provides and sits outside
  what CIO is for.

---

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
