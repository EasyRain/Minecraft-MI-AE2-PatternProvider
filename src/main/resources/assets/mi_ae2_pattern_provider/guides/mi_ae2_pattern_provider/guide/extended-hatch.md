---
navigation:
  title: "The Extended Hatch"
  icon: "ae2:pattern_provider"
  parent: index.md
  position: 4
---

# The Extended Hatch

The **ME Extended Pattern Provider Hatch** is the big brother of the normal hatch: same block, same four hatches in
one, but with a **paged pattern inventory**.

## Pattern capacity

| | Normal hatch | Extended hatch |
| --- | --- | --- |
| Pattern pages | 1 | up to **4** |
| Pattern slots per page | 9 | 36 (4×9) |
| Slots unlocked by default | 9 | **36** (1 page) |
| With 3 Expansion Cards | — | **144** (4 pages) |

Paging matches [ExtendedAE](https://modrinth.com/mod/extended-ae)'s ME Extended Pattern Provider: the 36 slots of
one page are laid out 4×9, and its GUI is reused for the interface.

## Requirements

**ExtendedAE is required** for this block. Without it the extended hatch is not registered at all — no crash, no
broken recipe, it simply does not exist, and the normal hatch keeps working exactly as before.

For the same reason this page spells ExtendedAE's items out in words instead of showing their icons: an icon whose
mod is missing would be an error box in the guide, so anything optional is written as text.

## How to get one

Two equivalent routes:

1. **Upgrade in place (recommended).** Hold ExtendedAE's **Pattern Provider Upgrade** and **shift + right-click**
   the normal hatch. The block is replaced on the spot, contents included, and the upgrade item is consumed (not in
   creative mode).
2. **Craft it in the Crystal Assembler** — one ME Pattern Provider Hatch, capacity cards, crafting tables, a
   concurrent processor and glass cables. The result is ExtendedAE's own extended pattern provider.

Both routes give the same block, and it cannot be turned back into a normal hatch.

## Unlocking more pages

Each ExtendedAE-Plus **Expansion Card** unlocks **one extra page** (36 more pattern slots), up to 3 cards = 4 pages
total. The card goes into the hatch's upgrade slot, the same slot used by the channel and induction cards.

Without ExtendedAE-Plus you still get the first page of 36 slots; the extra cards simply do not exist to be
inserted.

## Everything else is identical

Slot capacities, the two GUIs, output spreading, the output capacity guard, the "one hatch per array" rule,
naming and the online/offline hover text are all the same as for the normal hatch — see
[The Hatch](hatch.md) and [Patterns & Autocrafting](patterns.md).
