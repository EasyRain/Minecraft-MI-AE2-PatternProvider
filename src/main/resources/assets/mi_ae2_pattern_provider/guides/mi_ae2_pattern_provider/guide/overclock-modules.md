---
navigation:
  title: "Overclock Modules"
  icon: "mi_ae2_pattern_provider:quantum_overclock_module"
  parent: index.md
  position: 6
item_ids:
  - mi_ae2_pattern_provider:advanced_overclock_module
  - mi_ae2_pattern_provider:quantum_overclock_module
---

# Overclock Modules

Two modules go into the **overdrive module slot** that every MI electric machine has — including the Processing
Array controller. They are mutually exclusive: one slot, one module.

| | MI's Overdrive Module | <ItemImage id="mi_ae2_pattern_provider:advanced_overclock_module" /> Advanced | <ItemImage id="mi_ae2_pattern_provider:quantum_overclock_module" /> Quantum |
| --- | --- | --- | --- |
| Efficiency | Creeps up from zero, and idle draining keeps it pinned | **Straight to maximum**, from the first tick | Same as Advanced |
| Recipe | **Locked** until you pull the module out or cut the power | **Released the moment it finishes** | Same as Advanced |
| Parallel | — | — | **Processing Array: up to machine count × multiplier** |

MI's own module is untouched — the mod uses MI's official efficiency hook (`MIHookEfficiency`), not a mixin of MI's
logic.

## The Advanced Overclock Module

<ItemImage id="mi_ae2_pattern_provider:advanced_overclock_module" scale="2" />

* Fits the overdrive module slot of **any** MI electric machine (single block or multiblock controller).
* Sets efficiency to the machine's maximum immediately, so the machine runs at full speed from the first tick
  instead of crawling up.
* Releases the recipe as soon as it completes, so the machine can immediately pick up the next one — no more
  waiting for a module to be pulled out.
* Works alongside MI-Tweaks: on machines that have this module, this mod's hook wins (see
  [Upgrade Cards](upgrade-cards.md)).

<Recipe id="mi_ae2_pattern_provider:advanced_overclock_module_asbl" />

*The crafting table recipe. The MI Assembler variant is the same shape but only needs four stainless steel plates
(`eu 16`, `duration 300`).*

## The Quantum Overclock Module

<ItemImage id="mi_ae2_pattern_provider:quantum_overclock_module" scale="2" />

Everything the Advanced module does, **plus parallel processing on the Processing Array**: it raises the array's
batch ceiling from *machine count* to **machine count × multiplier**.

* Default multiplier **4×** → a 64-machine array can run up to **256 parallel batches**.
* The multiplier is a **ceiling**, not a fixed batch size: the array still works out the real batch from the
  ingredients it has and the **output space** it can see, so an oversized recipe simply produces a smaller batch.
  Products are never voided to hit a number.
* EU/t and duration scale with the batch size exactly as they do upstream: 4× the batches is 4× the EU/t and 4×
  the total energy. If power runs short, the craft takes proportionally longer. **It is not free throughput.**

<Recipe id="mi_ae2_pattern_provider:quantum_overclock_module_asbl" />

*The crafting table recipe (4 superconductor plates, 2 quantum circuits, 1 Advanced Overclock Module). The MI
Assembler variant is identical but costs `eu 64` / `duration 1000`.*

### Where it works

**Only on a Processing Array controller.** In an ordinary machine the module is simply refused:

* Right-clicking a machine with it in hand shows a chat message: *"The Quantum Overclock Module only supports
  Processing Arrays"*.
* Dragging it into a machine's GUI slot is refused too — the item stays on your cursor instead of being swallowed.

The Advanced and vanilla modules still fit anywhere.

The reason is the "fits = parallel really happens" rule. Only arrays have a *machine count* to multiply; a
single-block machine runs one recipe at a time, and other multiblocks use their `getMaxMultiplier()` for coil-tier
batching instead. Rather than silently doing nothing there, the module is rejected.

## Configuring the multiplier

The multiplier lives in `config/mi_ae2_pattern_provider-common.toml`:

```toml
[quantum_overclock]
parallel_multiplier = 4   # range 2-16
```

The full path to that file is printed to the log at startup.

With [Cloth Config](https://modrinth.com/mod/cloth-config) installed you can change it in game instead:
**Mods screen → MI AE2 Pattern Provider → Config**. Changes apply to the **next recipe calculation** — no game
restart, no world reload.

### Why 16 is the maximum

The hatch's output space is `36 slots × 64 = 2304 items`, and the array's multiplier is limited to roughly
`output slots ÷ items per craft`. Across the official recipes the per-craft item output `P` is distributed as
`≤1: 52.6% / ≤2: 66.1% / ≤4: 77.2% / ≤9: 95.1%`, which puts the ceiling at `36/P` — 36× for `P=1`, 18× for `P=2`,
9× for `P=4`, 4× for `P=9`. 16× is comfortably under half of the single-output theoretical limit.

## Known upstream rough edges (amplified by the multiplier)

These are pre-existing issues in the upstream array code, **not introduced by this module** — but a higher
multiplier scales them up, so it is worth knowing before you fit one:

* **Several fluid outputs sharing one output slot**: the feasibility check computes each fluid output's share
  without subtracting what the previous one already took, so the real insert can be truncated and the excess is
  silently voided.
* **Probability outputs** (`probability < 1.0`) are excluded from the feasibility check entirely, so a lucky roll
  that does not fit is lost.
* **`recipeEnergy` is written as a `long` but read back as an `int`**, so a craft whose `total EU × multiplier`
  exceeds `2^31` corrupts its progress across a reload. The threshold is `total EU > 2^31 / (64 × multiplier)`.
  Only extreme recipes touch it (the Packer's quantum upgrade, the Fusion Reactor), and the Packer already crosses
  it at the *vanilla* 64× ceiling.

**Recommendation:** fit the Quantum module to ordinary production lines. Its scan of the official recipe set found
that the overwhelming majority of recipes with item outputs stay below 2.1M total EU. Leave the Packer's quantum
upgrade and the Fusion Reactor on a plain Advanced module.
