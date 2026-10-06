---
navigation:
  title: "Troubleshooting"
  icon: "mi_ae2_pattern_provider:guide"
  parent: index.md
  position: 7
---

# Troubleshooting

| Symptom | Most likely cause | Fix |
| --- | --- | --- |
| Controller says **Structure invalid** | Two ME Pattern Provider Hatches in one array | Remove one — [one provider per array](patterns.md) |
| Craft is stuck showing **Waiting** in the terminal | The hatch has no room for the products, so it refuses ingredients | Let the network pull the products out, or make room in it; see below |
| Nothing happens at all | No power, no machines inserted, or a *crafting* pattern instead of a *processing* one | See below |
| Quantum module refuses to go in | It only fits a Processing Array controller | Expected everywhere else — use the Advanced module |
| A huge craft restarts after a reload | Upstream `recipeEnergy` `long`/`int` truncation | Keep the Quantum module off that recipe |
| A fluid output vanishes when a craft produces several fluids | Upstream multi-fluid feasibility bug | Split the recipe into separate crafts |
| Jade / TOP says **Device Offline** | No cable, no channel, or the network is unpowered | Check the cable and give the hatch a channel |

## "Waiting" in the ME terminal

Waiting is not a failure — AE2 retries every tick. What it usually means here:

* **The output slots are full.** The hatch refuses the whole push rather than accepting ingredients it cannot
  turn into products (products would be destroyed otherwise). Let the network drain the hatch's output, or free
  space in the network's storage, and the craft continues on the next tick.
* **The batch had to be split.** Large crafts are run as several smaller batches so nothing is lost. The craft
  completes normally, just in more steps.
* **The batch size is enormous.** A single push is capped at `input slots × Integer.MAX_VALUE` per ingredient. A
  pattern that exceeds that is refused as a whole and retried forever — split the pattern into a couple of smaller
  ones.

## The craft never starts

Check, in this order:

1. **Is the array formed and powered?** The controller should be lit and the energy input hatch supplied with MI
   cables. Remember the array's EU/t is `recipe EU/t × batch size` — a 256-parallel array needs a lot of power.
2. **Are there machines inside the array?** Open the controller and insert the electric single-block machines whose
   recipes you want. No machines = nothing the array can run.
3. **Is the pattern a processing pattern?** A crafting pattern (3×3 grid) cannot be executed by a machine. Re-encode
   it in the pattern encoding terminal's processing mode.
4. **Does the pattern's output match what the inserted machines actually produce?** AE2 only cares about the
   pattern's declared outputs; the array produces what the machine produces. If they disagree, the craft waits for
   items that will never exist.
5. **Is the hatch online?** Hover it with Jade/TOP: **Device Online** means the network sees it.

## The Quantum module does not add parallel

* Make sure it is in a **Processing Array controller** — that is the only place parallel applies. In any other
  machine, quantum and advanced modules behave identically.
* Check the multiplier: `config/mi_ae2_pattern_provider-common.toml` → `[quantum_overclock] parallel_multiplier`
  (2–16, default 4), or the in-game config screen with Cloth Config. The startup log prints the file path.
* Remember the multiplier is a **ceiling**: if the array's inputs or its output space only allow 3 batches, you get
  3 batches no matter what the config says.

## Upstream issues that a higher multiplier amplifies

These come from the upstream array code, not from this mod, but they are worth avoiding:

* **Packer's quantum upgrade and the Fusion Reactor**: their total EU is high enough that `total EU × multiplier`
  can exceed `2^31`, which corrupts the in-progress craft across a reload (upstream writes the value as a `long`
  and reads it back as an `int`). With the Quantum module fitted, prefer a plain Advanced module on those.
* **Recipes with several fluid outputs**: the feasibility check does not subtract one fluid output's space from the
  next, so a share can be truncated and lost. Split such recipes into separate crafts.
* **Recipes with probability outputs**: those outputs are not part of the feasibility check at all, so a roll that
  does not fit is lost. This can already happen at the vanilla 64× ceiling.

## The modules or this manual are missing from JEI

They are all in **MI's own creative tab**, right after MI's Overdrive Module. JEI builds its item list from
creative tab contents, so an item that is in no tab is in no JEI list either — if you cannot find them, search MI's
general tab.
