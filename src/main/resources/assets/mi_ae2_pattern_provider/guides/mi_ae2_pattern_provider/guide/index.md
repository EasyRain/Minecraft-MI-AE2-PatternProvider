---
navigation:
  title: "ME Pattern Provider Hatch"
  icon: "modern_industrialization:me_pattern_provider_hatch"
  position: 100
item_ids:
  - mi_ae2_pattern_provider:guide
---

# ME Pattern Provider Hatch

![Logo](assets/logo.png)

**MI AE2 Pattern Provider** is a bridge between
[Modern Industrialization](https://modrinth.com/mod/modern-industrialization) /
[Extended Industrialization](https://modrinth.com/mod/extended-industrialization) and
[Applied Energistics 2](https://modrinth.com/mod/ae2).

It adds one block: the <ItemLink id="modern_industrialization:me_pattern_provider_hatch" />. That single block
takes the place of **all four** input/output hatches of the EI **Processing Array**
(<ItemImage id="extended_industrialization:processing_array" />) *and* acts as an AE2 pattern provider at the same
time. Put patterns in it, connect it to your ME network, and your ME system can push autocrafting jobs straight
into the array — items and fluids are routed automatically, and the finished products are pulled back out of it
into the network.

<BlockImage id="modern_industrialization:me_pattern_provider_hatch" scale="6" />

*The ME Pattern Provider Hatch you start with. Its bigger brother, the **ME Extended Pattern Provider Hatch**
(needs [ExtendedAE](https://modrinth.com/mod/extended-ae)), is described in [The Extended
Hatch](extended-hatch.md) — optional-mod content is written out as text in this guide, because an item icon whose
mod is missing would be an error box.*

## What this guide covers

<SubPages />

## Six steps at a glance

1. Build an Extended Industrialization **Processing Array**, but place the ME Pattern Provider Hatch where an item
   input hatch would go — you do not need the four separate hatches any more.
2. Feed the array power (energy input hatch + MI cables) and insert the electric machines you want it to run
   through the controller's own interface.
3. Connect the hatch to your ME network with ME glass cable.
4. Right-click the hatch with an empty hand and drop in **processing patterns**.
5. Request the craft from your ME terminal.
6. The array pulls the ingredients out of the hatch, crafts, and writes the products back into it; the hatch then
   returns them to the network.

If something does not work, jump straight to [Troubleshooting](troubleshooting.md) — the symptoms there are
written the way they actually look in game ("stuck at *Waiting*", "Structure invalid", …).

## Requirements

| Mod | Requirement |
| --- | --- |
| [Modern Industrialization](https://modrinth.com/mod/modern-industrialization) | Required |
| [Extended Industrialization](https://modrinth.com/mod/extended-industrialization) | Required (provides the Processing Array) |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | Required |
| [ExtendedAE](https://modrinth.com/mod/extended-ae) | Optional — only for the extended hatch |
| [ExtendedAE-Plus](https://github.com/GaLicn/ExtendedAE_Plus) | Optional — adds upgrade cards (channel card, capacity card) |
| [AppliedFlux](https://github.com/GlodBlock/AppliedFlux) | Optional — adds the induction card |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Optional — lets you change the parallel multiplier in game |
| [Jade](https://modrinth.com/mod/jade) / [The One Probe](https://modrinth.com/mod/the-one-probe) | Optional — show "Device Online / Offline" on the hatch |
