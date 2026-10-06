---
navigation:
  title: "Getting Started"
  icon: "modern_industrialization:me_pattern_provider_hatch"
  parent: index.md
  position: 1
---

# Getting Started

This page walks through a complete, working setup: one Processing Array, one hatch, one pattern, one craft.

## 1. Build the Processing Array

An Extended Industrialization **Processing Array** is a multiblock: a controller, a shell of machine casing, an
**energy input hatch** for power, and hatches for the items and fluids it works with.

<GameScene zoom="2" interactive={true} fullWidth={true}>
  <MultiblockShape controller="extended_industrialization:processing_array" />
  <MultiblockShape controller="extended_industrialization:processing_array" useBigShape={true} x="-6" z="-8" />
</GameScene>

*The small and the large array shape — drag with the left mouse button to rotate, right button to pan.*

With this mod you do **not** place the four separate hatches any more: the
<ItemLink id="modern_industrialization:me_pattern_provider_hatch" /> occupies the hatch slot instead, and provides
item input, item output, fluid input and fluid output all at once. You still need the **energy input hatch**, and
whatever machine casing the shape requires.

<Recipe id="mi_ae2_pattern_provider:me_pattern_provider_hatch" />

*The hatch is crafted from a vanilla AE2 ME Pattern Provider, one hatch of each of the four types, and glass
cables of any colour. Fluid and item hatches of any tier work.*

## 2. Form the multiblock

Place the controller, build the shell around it, and put the ME Pattern Provider Hatch and the energy input hatch in
their slots. MI multiblocks **form themselves** as soon as the shape is complete — there is no extra step and no
wrench needed. A correctly built array lights up and starts accepting machines and power.

## 3. Give the array machines and power

* Open the **controller** and insert the electric single-block machines you want the array to run (up to 64 by
  default). The array runs **their** recipes, in batches.
* Feed the energy input hatch with MI cables and enough EU/t. The array's EU/t is
  `recipe EU/t × batch size`, so more parallel needs proportionally more power.

## 4. Connect the ME network

Run **ME glass cable** to the hatch and make sure the network has a channel free (or give it a **channel card**,
see [Upgrade Cards](upgrade-cards.md)). The hatch is a normal AE2 network node: hover it with Jade or The One
Probe and it should say **Device Online**.

## 5. Put patterns in the hatch

Right-click the hatch with an **empty hand**. You get the AE2 pattern provider interface. Drop in
**processing patterns** encoded in your ME Pattern Encoding Terminal:

* **Inputs**: the ingredients one craft step consumes (items and fluids).
* **Outputs**: what the array should produce.

<ItemImage id="ae2:pattern_provider" /> <ItemImage id="ae2:processing_pattern" />

The hatch has **9 pattern slots** — one full page, the same as a vanilla ME Pattern Provider. The extended hatch
has more (see [The Extended Hatch](extended-hatch.md)).

## 6. Request the craft

Order the item from your ME crafting terminal. The flow from there is automatic:

1. AE2 pushes the ingredients for one pattern into the hatch's **input slots**.
2. The array pulls them out, runs the batch, and writes the products into the hatch's **output slots**.
3. The hatch pushes the products back into the ME network and releases the pattern for the next craft.

Because the hatch is its own little buffer, autocrafting keeps working even if the network briefly has no room for
the products — the hatch holds them until it can push.

## Next steps

* [The Hatch](hatch.md) — what every slot and both GUIs do.
* [Patterns & Autocrafting](patterns.md) — how the hatch talks to AE2, and the one-hatch-per-array rule.
* [Overclock Modules](overclock-modules.md) — make the array run up to 16× more parallel than its machine count.
