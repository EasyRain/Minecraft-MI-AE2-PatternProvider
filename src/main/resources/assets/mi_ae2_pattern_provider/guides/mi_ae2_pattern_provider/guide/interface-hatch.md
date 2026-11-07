---
navigation:
  title: "ME Interface Hatch"
  icon: "modern_industrialization:me_interface_hatch"
  parent: index.md
  position: 5
item_ids:
  - modern_industrialization:me_interface_hatch
  - modern_industrialization:me_extended_interface_hatch
  - modern_industrialization:me_oversize_interface_hatch
---

# ME Interface Hatch

<BlockImage id="modern_industrialization:me_interface_hatch" scale="6" />

<ItemLink id="modern_industrialization:me_interface_hatch" /> is the opposite half of the pattern provider: the
provider **pushes ingredients into the array**, the interface hatch **keeps the array supplied between orders**.
It is an AE2 ME interface, but it is an MI hatch, so it fits the input/output positions of a Processing Array.

## How it works

* Configure a stock amount for a slot in the AE interface. As long as the network has that resource, the interface
  refills it automatically;
* When the array consumes that stock, the interface pulls more from the network — it is a permanent buffer;
* Anything not configured (or above the configured amount) is pushed back into the network, it is not kept;
* Products written into the interface's output slots are drained back into the network immediately (and stay in the
  buffer while the network is unreachable).

## Three tiers

| Block | Config slots | Per-slot capacity |
| --- | --- | --- |
| ME Interface Hatch | 9 | items 64 / fluids 4 B |
| ME Extended Interface Hatch (ExtendedAE) | 36 (4 pages) | items 64 / fluids 4 B |
| ME Oversize Interface Hatch (ExtendedAE) | 36 (4 pages) | **items 1024 / fluids 64 B** |

The oversize tier reuses ExtendedAE's own oversize implementation, so it follows EAE's multiplier configuration.

## Together with the pattern provider

1. **While an order is running the interface's inputs are hidden** — the array can neither see nor extract its
   stock, so the order's ingredients can only come from the provider;
2. When the order finishes, the interface resumes supplying;
3. The interface keeps a permanent stock, i.e. a supply line that does not depend on AE2 orders.

> An idle provider locks all of its output slots empty, so products of interface-driven crafting land in the
> interface's own output buffer.

## Two GUIs

| Action | Opens |
| --- | --- |
| **Right click** | the AE interface GUI: config slots, stock amounts, priority, upgrade cards |
| **Wrench right click** | MI's own slot GUI (3 pages on 36-slot tiers: item in / fluid in / output) |

## Upgrade cards

Same as AE2's interface: **crafting card ×1, fuzzy card ×1**. In addition, every card registered for the vanilla
interface by addons is grafted automatically (for example ExtendedAE-Plus' channel card). The virtual crafting card
belongs to pattern providers and is not accepted.

## Tooltips

With Jade / The One Probe the hatch shows **Device Online / Device Offline**; 36-slot tiers additionally list their
fluid contents (one line per fluid, capacity per slot on Shift) and the remaining free capacity.
