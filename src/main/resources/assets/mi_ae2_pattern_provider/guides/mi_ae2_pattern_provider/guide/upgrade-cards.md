---
navigation:
  title: "Upgrade Cards"
  icon: "ae2:pattern_provider"
  parent: index.md
  position: 5
---

# Upgrade Cards

The hatch has the same upgrade slot as a vanilla AE2 ME Pattern Provider, and it inherits **everything the vanilla
provider accepts** — automatically.

## Why it just works

AE2 keeps a static table of "which card fits which machine". Addon mods register their cards against the *vanilla*
pattern provider, and this mod copies every one of those registrations onto its own hatches when the game finishes
loading. That includes cards from mods that did not exist when this mod was written — there is no hard-coded list
of supported cards, only a short blacklist of cards that genuinely conflict.

(The cards below are written out as text rather than shown as item icons: all of them come from optional mods, and
an icon whose mod is missing would be an error box in the guide.)

## Cards you are likely to use

| Card | Mod | Effect on the hatch |
| --- | --- | --- |
| **Channel Card** | ExtendedAE-Plus | Lets the hatch hold more channels, so it can sit on a busy network |
| **Induction Card** | AppliedFlux | Powers the array's energy input hatch directly (see below) |
| **Expansion Card** | ExtendedAE-Plus | **Extended hatch only**: +1 pattern page (36 slots) per card, up to 4 pages |
| **Virtual Crafting Card** | ExtendedAE-Plus | **Deliberately not installable** (see below) |

Other cards (speed cards, energy cards, and so on) are accepted exactly as they are on a vanilla provider — if a
card works there, it works here.

## The induction card is redirected

AppliedFlux's induction card normally powers the **six blocks around** the provider. On a hatch that means the
array's machine casing, which is useless.

This mod therefore **redirects it at the array's energy input hatch** — the hatch on the controller's front face.
Everything else about the card stays AppliedFlux's business: the EU conversion, the rate limit and the retry logic
are all theirs; the mod only changes *which position* the energy is sent to. You still need a card for it, and the
array still needs an energy input hatch.

## The virtual crafting card is excluded

The **Virtual Crafting Card** makes a pattern provider push ingredients into its own internal inventory and pretend
the craft happened there. That is the opposite of what this hatch does — its whole point is that the **Processing
Array really crafts the items** and returns real products. The two mechanisms cannot coexist, so the card is
refused: its registration is deliberately skipped, which means the upgrade slot will not accept it at all.

## Efficiency mods

| Mod | Behaviour |
| --- | --- |
| [MI-Tweaks](https://github.com/Swedz/MI-Tweaks) | **Coexists, and this mod wins on machines that have an overclock module installed.** MI-Tweaks registers its efficiency hook at the lowest priority; the advanced/quantum module uses a high priority, so it decides the outcome. Machines without a module keep following MI-Tweaks' config. |
| MI-Efficiency-Remover | **Declared incompatible.** It forces every MI machine to maximum efficiency by mixin-ing MI directly, and those cancellations cannot be undone at runtime. The Advanced Overclock Module does the same thing *per machine and per item* — remove MI-Efficiency-Remover and use the module instead. |
