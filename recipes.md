# CIO Crafting Recipes

A player-facing guide to everything you can currently craft in **Create: Interoperable**
(mod id `createinteroperable`). This lists the recipes that actually exist in the mod's
data right now — some things mentioned in the README or in-game tooltips are still
work-in-progress and are **not** craftable yet; those are called out at the bottom so you
don't go hunting for a recipe that isn't there.

## Before you start: conditional recipes

CIO bridges two *optional* mods — **Create: Power Grid** (PG) and **Create: Electro
Energetics** (CEE) — plus Create itself (required). Most recipes below only exist if the
mod(s) they need are installed:

- Recipes marked **PG** need Power Grid installed.
- Recipes marked **CEE** need Electro Energetics installed.
- Recipes marked **PG + CEE** need *both* installed.
- Anything not marked needs only Create.

If a recipe isn't showing up in your recipe book, check you have the right mod(s) loaded
before assuming something is broken.

Every grid below is read like a normal 3x3 crafting table, top row first, blank cells
empty.

---

## Grid Coupler — the Power Grid ↔ Electro Energetics bridge

**Requires: PG + CEE**

The main "wire two different grid mods together" block — a switchable one-way bridge with
a 3-position flow slider (`CPG → CEE` / Off / `CEE → PG`).

| | | |
|---|---|---|
| Heavy Wire Connector (PG) | — | Connector (CEE) |
| Heavy Wire Connector (PG) | Clock | Connector (CEE) |
| Iron Block | Item Vault | Iron Block |

→ **Grid Coupler**

---

## Interoperable Telephone

A working in-world telephone. The same shape gives you a different item depending on
which grid mod(s) you have — the recipe book will only ever show you the one you can
actually use.

| | | |
|---|---|---|
| — | Desk Bell | — |
| Connector | Item Vault | Connector |
| — | Display Link | — |

| Your setup | Connector used | Result |
|---|---|---|
| PG + CEE | Device Connector (PG) | **Interoperable Telephone** |
| PG only | Device Connector (PG) | **CPG Telephone** |
| CEE only | Connector (CEE) | **CEE Telephone** |

> Note: with both mods installed, the PG-only recipe's ingredients are identical to the
> combined recipe's — you'll only ever get one telephone out of that grid, not both.

---

## Domestic Electrical Board — Power Kits

The "wall socket" that turns a Create power grid into the electricity Mr. Crayfish-style
furniture appliances run on. Comes in two full families — one per grid mod, otherwise
identical — each with four tiers: **Improvised → Domestic → Commercial → Industrial**.

### Power Grid family (PG)

**Improvised Power Kit** (Tier 1)

| | | |
|---|---|---|
| Wire Connector | — | Wire Connector |
| Valve Handle | Rheostat | Valve Handle |
| Wire Connector | — | Wire Connector |

**Domestic Power Kit** (base tier)

| | | |
|---|---|---|
| Wire Connector | Wire Connector | Wire Connector |
| Andesite Alloy | Rheostat | Andesite Alloy |
| Wire Connector | Wire Connector | Wire Connector |

**Commercial Power Kit** (Tier 3)

| | | |
|---|---|---|
| Heavy Wire Connector | Wire Connector | Heavy Wire Connector |
| Andesite Alloy | Variac (PG) | Andesite Alloy |
| Heavy Wire Connector | Wire Connector | Heavy Wire Connector |

**Industrial Power Kit** (Tier 4)

| | | |
|---|---|---|
| Heavy Wire Connector | Heavy Wire Connector | Heavy Wire Connector |
| Variac (PG) | Variac (PG) | Variac (PG) |
| Heavy Wire Connector | Heavy Wire Connector | Heavy Wire Connector |

### Electro Energetics family (CEE)

**Improvised Power Kit** (Tier 1)

| | | |
|---|---|---|
| Connector | — | Connector |
| Valve Handle | Potentiometer | Valve Handle |
| Connector | — | Connector |

**Domestic Power Kit** (base tier)

| | | |
|---|---|---|
| Connector | Connector | Connector |
| Andesite Alloy | Potentiometer | Andesite Alloy |
| Connector | Connector | Connector |

**Commercial Power Kit** (Tier 3)

| | | |
|---|---|---|
| Double Connector | Connector | Double Connector |
| Andesite Alloy | Variac (CEE) | Andesite Alloy |
| Double Connector | Connector | Double Connector |

**Industrial Power Kit** (Tier 4)

| | | |
|---|---|---|
| Double Connector | Double Connector | Double Connector |
| Variac (CEE) | Variac (CEE) | Variac (CEE) |
| Double Connector | Double Connector | Double Connector |

> The **Redstone Switch** / **CEE Redstone Switch** (cuts or enables a Power Kit's
> appliance network) exists on the `experimental` branch only, with **no crafting recipe
> yet** — `/give` only there.

---

## CPG Double Connector

**Requires: PG**

A 2-terminal Power Grid connector for splitting a wire run.

| | | |
|---|---|---|
| — | — | — |
| Connector\* | — | Connector\* |
| Iron Block | Iron Block | Iron Block |

\* Any Power Grid–style connector works here: **Wire Connector**, **Heavy Wire
Connector**, or **Device Connector** (all PG).

---

## Aircon (cooling)

A three-part air conditioner: a bottom motor unit (grid-specific), a fan on top, and an
optional venter for ducting.

**Aircon Motor** (bottom unit, PG)

| | | |
|---|---|---|
| Electric Fan (PG) | Electric Fan (PG) | Electric Fan (PG) |
| Electric Motor (PG) | Electric Pump (PG) | Electric Motor (PG) |
| Fluid Pipe | Basin | Fluid Pipe |

**CEE Aircon Motor** (bottom unit, CEE)

| | | |
|---|---|---|
| Encased Fan | Encased Fan | Encased Fan |
| Electric Motor (any CEE motor) | Electric Pump (CEE) | Electric Motor (any CEE motor) |
| Fluid Pipe | Basin | Fluid Pipe |

**Aircon Fan** (top unit — no mod required)

| | | |
|---|---|---|
| — | — | — |
| Encased Fan | Encased Fan | Encased Fan |
| — | — | — |

**Aircon Venter** (ducting — no mod required)

| | | |
|---|---|---|
| — | — | — |
| Fluid Pipe | Encased Fan | Fluid Pipe |
| — | — | — |

---

## Steam Hearth (heating)

A Create-only heating loop: a Steam Outlet reads a boiler and feeds Create pipes, which
feed an extendable Steam Hearth radiator run.

**Steam Outlet** (no mod required beyond Create)

| | | |
|---|---|---|
| — | Fluid Pipe | — |
| — | Valve Handle | — |
| Copper Block | Copper Block | Copper Block |

**Steam Hearth Inlet** (`multi_radiator_north` — no mod required)

| | | |
|---|---|---|
| — | — | — |
| Fluid Pipe | Fluid Pipe | Valve Handle |
| Brass Block | Brass Block | Brass Block |

**Water Outlet** (`multi_radiator_south` — no mod required)

| | | |
|---|---|---|
| — | — | — |
| Fluid Pipe | Fluid Pipe | Bucket |
| Brass Block | Brass Block | Brass Block |

### Assembling the radiator (not a crafting-table recipe)

The middle **Steam Hearth Radiator** segments have no recipe or item of their own — you
build them in the world:

1. Place a **Steam Hearth Inlet** and a **Water Outlet** in a straight line, both facing
   *outward*, away from each other (so their fronts point away from the gap between them).
2. Fill the 0–3 blocks between them with plain **Copper Blocks**.
3. Wrench either end cap. If the run is valid, every copper block between them converts
   into a **Steam Hearth Radiator** block and the two ends link up.

You can have anywhere from 0 up to 3 radiator segments in one run.

> The **Brass Heater** (reads the steam loop, reports a throttle to Cold Sweat) is in the
> game but currently has **no crafting recipe** — creative-menu / `/give` only.

---

## Not craftable yet (work in progress)

These exist in the mod's code but are switched **off** right now (a `BRIDGE_EXTRAS` flag
in the mod's source), so they aren't registered at all — no item, no recipe, not on the
creative menu, not obtainable even with `/give`:

- **Interoperable Transformer** (the flagship 2-tall x 3-long PG↔CEE multiblock) — keystone,
  filler, and assembled blocks.
- **Interim Interoperable Transformer** — the 1x1 stand-in block used to prototype the
  transformer's electrics before the real multiblock's model was ready.
- **Interoperable Coupler** (the single-block version — being reworked into a pure
  signal relay; the two-terminal **Grid Coupler** above is the current, working bridge).
- **Connector** / **Large Connector** — alternate-looking Power Grid wire connectors.

If you're exploring the jar and see these mentioned in the lang file or tooltips, that's
why you can't find a recipe for them — they're mid-development, not missing documentation.
