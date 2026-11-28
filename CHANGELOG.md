# Changelog

## 1.0.5
- A Shifter that spawns on its own (30-48 blocks away) now stands still and watches you half the time. Walk within 20 blocks and it vanishes (1 in 4 times it chases you instead); left alone, it's gone after 20-40 seconds. Far-off Shifters spawn twice as often as before (4% chance every 5 minutes, up from 2%).

## 1.0.4
- The Mutant Creeper and Skeleton King can now spawn on their own: a naturally spawning overworld creeper or skeleton has a 1 in 1,000 chance (`boss-chance` in `config.yml`) to be the boss instead, never two of the same boss within 128 blocks. Nearby players get a warning. Turn it off with `/whispers spawns bosses off`.

## 1.0.3
- Herobrine, Shifted Players and the Shifter's body no longer count as players. Each one found *itself* as the nearest player, which made Herobrine vanish and Shifted Players reveal the moment they appeared.
- Herobrine stays and stares when you look at him, with only a tiny chance of vanishing. Getting within 7 blocks or hitting him still makes him vanish.
- Herobrine now and then strolls a few blocks near where he stands (never toward you). Before, he tried to walk to spots too far away, gave up and only stared.
- The Shifter's body no longer has an Enderman's glowing eyes floating above it: underneath it's now an invisible Husk (a plain Enderman only when there's no skin).
- Disguises and Shifted Players reveal themselves only after you stay within 3 blocks for 2 seconds (or hit them). Shifted Players walk up, stop a few blocks away and look at you.

## 1.0.2
- The Shifter has a dark, player-shaped body (with Citizens and `shifter-skin` set in `config.yml`, from `shifter_skin.png`) instead of looking like an Enderman.
- The Shifter no longer vanishes the moment it appears: one that drops its disguise (or a Shifted Player) next to you always comes after you, stalkers stay 30-60 seconds, and one that vanishes when watched needs a few seconds of staring.
- Herobrine lurks: he stares, wanders to new spots, stops dead when you catch him walking, and sometimes moves while you look away. He only vanishes if you get within 7 blocks or hit him.
- Herobrine and Shifted Players are no longer deleted when Citizens reloads them to apply a skin.

## 1.0.1
- Shifted Players no longer vanish while their skin loads.
- The Shifter waits up to 30 seconds when nobody in survival is nearby, instead of disappearing at once.
- `/whispers summon <creature> [distance]`: by default far enough away that Herobrine and Shifted Players don't vanish or reveal straight away, on a standing spot facing you.

## 1.0.0
- First Paper version of the Bedrock Whispers of the Void add-on (without Foxy): the Shifter and its disguises, Shifter presence, Herobrine and Shifted Players (with Citizens), Eyes in the Dark, staring animals, and the Mutant Creeper and Skeleton King bosses.
