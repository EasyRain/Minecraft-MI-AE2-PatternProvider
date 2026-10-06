---
navigation:
  title: "The Hatch"
  icon: "modern_industrialization:me_pattern_provider_hatch"
  parent: index.md
  position: 2
item_ids:
  - modern_industrialization:me_pattern_provider_hatch
  - modern_industrialization:me_extended_pattern_provider_hatch
---

# The Hatch

<ItemLink id="modern_industrialization:me_pattern_provider_hatch" /> is a MI machine block, so it behaves like
every other MI hatch — with three additions: it is an AE2 network node, an AE2 pattern provider, and it packs four
hatches into one block.

## Four hatches in one block

| Hatch it replaces | Slots | Capacity per slot |
| --- | --- | --- |
| Item input | 9 | **unbounded** (`Integer.MAX_VALUE`) |
| Item output | **36** (4 rows) | 64 |
| Fluid input | 9 | **unbounded** (`Integer.MAX_VALUE` mB) |
| Fluid output | 9 | **unbounded** (`Integer.MAX_VALUE` mB) |

The input side is unbounded on purpose: AE2 pushes a whole pattern's worth of ingredients in one go, and a single
slot has to be able to swallow it. The **output** side is deliberately *not* unbounded — read the next section.

## Why the output slots stay at 64

The Processing Array decides how many batches it can run by asking the hatch **how much room its output slots
have left**, and then inserts into those same slots for real. If the hatch claimed infinite space, the array would
happily plan a 64-machine batch, the real insert would keep only 64 items per slot, and tesseract would
**silently discard** the rest — the ME crafting job would then wait forever for products that no longer exist.

So the output slots report the truth (64 per slot, 36 slots = **2304 items** of buffer), and the array splits large
crafts into several smaller batches instead of losing anything. Total energy is unchanged by that splitting.

To make sure the two views can never drift apart, the hatch also runs an **output capacity guard** before
accepting any ingredients: it compares the space its capacity query promises with what a real insert can deliver,
and refuses the whole push if they disagree. The job then simply shows as *Waiting* in the ME terminal and AE2
retries every tick — better a pause than vanished products.

## The two GUIs

| Click with | Opens |
| --- | --- |
| Empty hand | The **AE2 pattern provider** interface (put patterns in here) |
| Any **wrench** | The **MI machine** interface: every configurable item/fluid slot, so you can pull out stuck contents by hand |

MI recognises wrenches through the common `c:tools/wrench` tag, so wrenches from other mods work just as well as
MI's own.

<Row gap="12">
  <ItemImage id="modern_industrialization:me_pattern_provider_hatch" />
  <ItemImage id="ae2:pattern_provider" />
  <ItemImage id="ae2:processing_pattern" />
</Row>

## Output spreading

When a craft produces several different items, the hatch spreads them across the 36 output slots: every product
gets at least one slot of its own, and any slots left over go to the product that needs them most. This keeps a
recipe with one bulky output and one small output from being limited by a single slot.

## Naming in the ME terminal

The hatch names itself after the multiblock it is part of, so a terminal full of providers stays readable:

* On a Processing Array: `<the work block you inserted> Processing Array Pattern Supply Hatch`
  (for example *Centrifuge Processing Array Pattern Supply Hatch*).
* On an ordinary MI multiblock (electric blast furnace, distillery, …): `<controller name> Pattern Supply Hatch`.
* Not formed yet: the default name.
* The extended hatch adds *Extended* to the same names.

The detection is generic — it looks for a work block through the tesseract `ComponentStackHolder` interface, so
arrays from other mods (for example Industrialization Overdrive's multiblock Processing Array) are recognised too,
and it does not treat an upgrade item sitting in an upgrade slot as a work block.

## Status in Jade / The One Probe

Hovering the hatch shows **Device Online** or **Device Offline**, exactly like AE2's own machines. Both plugins are
independent of AE2's shared tooltip extension point, so other MI machines' tooltips are unaffected. Jade and TOP
are optional.

## One provider per array

A Processing Array accepts **only one** ME Pattern Provider Hatch (extended version included). A second one makes
the whole multiblock report **Structure invalid**, with an explanation in the server log; removing the extra hatch
fixes it. [Patterns & Autocrafting](patterns.md) explains why.
