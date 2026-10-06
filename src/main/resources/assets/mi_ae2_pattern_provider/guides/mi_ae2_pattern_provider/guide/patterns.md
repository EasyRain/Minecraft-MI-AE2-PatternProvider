---
navigation:
  title: "Patterns & Autocrafting"
  icon: "ae2:pattern_provider"
  parent: index.md
  position: 3
---

# Patterns & Autocrafting

The hatch is a real AE2 pattern provider, so everything you know about pattern providers applies — with a few
Processing-Array-specific rules.

## Use processing patterns

The array is a *machine*, not a crafting grid, so it needs **processing patterns**:

* **Inputs** = what the recipe consumes.
* **Outputs** = what the recipe produces.

A *crafting* pattern (the 3×3 grid kind) will not work here, because nothing in the array can "place" items in a
grid. Encode with the <ItemImage id="ae2:pattern_encoding_terminal" /> in **processing** mode.

<ItemImage id="ae2:processing_pattern" /> <ItemImage id="ae2:crafting_pattern" />

*Left: a processing pattern (correct). Right: a crafting pattern (will not run in an array).*

## The craft loop

1. Your ME system decides it needs something and picks a pattern from the hatch.
2. AE2 pushes the pattern's **inputs** into the hatch's input slots — items and fluids in one go.
3. The array pulls the ingredients, runs as many batches as the inputs and the output space allow, and writes the
   products into the hatch's output slots.
4. The hatch exports the products to the network and marks the pattern free again.

The hatch holds the pattern until **its own** products have come back (`LOCK_UNTIL_RESULT`). That is what makes the
buffer safe: one pattern is never re-fed while its results are still in flight.

## Why one hatch per array

The array writes its products into **any accepting output slot of its aggregated inventory** — it does not promise
they land back in the hatch that supplied the ingredients. Each hatch tracks its own output locks, leftover
recycling and result-waiting, so if products landed in a *different* hatch, the supplying hatch would wait forever
for products it will never see, and the craft would deadlock.

Rather than guess at cross-hatch ownership, the mod simply forbids a second hatch: **one Processing Array, one ME
Pattern Provider Hatch**. If you place two, the multiblock reports **Structure invalid** (and tells you why in the
server log) until you remove one.

## "Waiting" is not always a bug

When the hatch refuses a push — for example because its output space and its capacity report disagreed — the ME
terminal shows the job as **Waiting** rather than failing. AE2 retries every tick, and the hatch clears the
condition (and clamps its own leftover flags) as part of the same call, so the next attempt usually succeeds.

Practical consequences:

* A big craft may be split into several smaller batches. That is intentional: it keeps products from being
  destroyed when a slot is full.
* If the array has *no* room left for products, the craft waits. Empty the output by letting the network pull the
  products, or give the network room to accept them.
* Splitting batches does **not** increase total energy: EU and duration scale with the batch size upstream.

## Patterns and reloads

Patterns, the return buffer and in-transit outputs all survive breaking the hatch, and they are still there after
a reload.
