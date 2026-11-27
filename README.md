# Whispers of the Void (Paper plugin)

Paper port of the Bedrock add-on [whispers-of-the-void](https://github.com/TheCoberto/whispers-of-the-void): creatures that stalk, hide and watch you, plus two bosses. Spawn timing, chances, distances, health and loot match the Bedrock version.

## Creatures

- **The Shifter**: hides as an ordinary mob (sheep, pig, cow, chicken, wolf, skeleton, zombie, spider, creeper, villager) that never attacks and keeps looking at you. Stay within 3 blocks of it for 2 seconds (or hit it) and it reveals itself as a tall dark figure (a player-shaped NPC with its own skin when Citizens is installed and `shifter-skin` is set) that, depending on its mood, stalks you and vanishes, stalks then chases, chases straight away, vanishes when you look at it and comes back invisible from behind, or sniffs you out unless you sneak. 500 health; its hits weaken you; vanishing blinds anyone within 10 blocks.
- **Shifted Players** (needs Citizens): the Shifter as a player with armor and tools, using an online player's name and skin, or a familiar-sounding one. They walk up, stop a few blocks away and look at you, and reveal the Shifter if you stay within 3 blocks for 2 seconds (or hit them).
- **Shifter presence**: an unseen Shifter near you for 30-60 seconds in the dark. Whistles, knocks, footsteps, torches going out, and maybe a jumpscare.
- **Herobrine** (needs Citizens): never attacks. Appears 28-48 blocks away and watches you. He lurks: mostly stares, now and then strolls a few blocks (never toward you), stops dead when you catch him walking, and sometimes moves while you look away. Looking back at him is safe, with only a tiny chance he vanishes. Get within 7 blocks or hit him and he's gone. Torches near him go out.
- **Eyes in the Dark**: pale eyes in the darkness that blink, and vanish when you get within 12 blocks.
- **Staring animals**: if you haven't slept in over a day, very rarely a herd of farm animals freezes and stares at you in total silence, then goes back to normal.

## Bosses (`/whispers summon`, or very rarely on their own)

A naturally spawning overworld creeper or skeleton has a 1 in 1,000 chance (`boss-chance` in `config.yml`) to be the Mutant Creeper or Skeleton King instead, never two of the same boss within 128 blocks. Toggle with `/whispers spawns bosses on|off`.

- **Mutant Creeper**: 2.4x size, 200 health. Explosions far stronger than a creeper's (power 8); it survives them and keeps coming after a short recovery. Follows `mobGriefing`. Drops 6-14 gunpowder and 1-3 TNT.
- **Skeleton King**: 1.7x size with a crown, 250 health. Shoots, slams players within 5 blocks, summons up to 6 skeletons, and gets enraged (faster, summons more) below half health. His skeletons crumble when he dies. Drops an enchanted Skeleton King's Crown, an enchanted bow, bones and gold. With Skeleton Variants installed, some of his skeletons come out as variants.

## Commands (ops, `whispers.admin`)

- `/whispers spawns` lists what spawns on its own; `/whispers spawns <shifter|presence|herobrine|eyes|staring> on|off` changes it.
- `/whispers summon <shifter|disguise|shifted_player|presence|herobrine|eyes|mutant_creeper|skeleton_king>`
- `/whispers stare` makes nearby farm animals stare (for testing).

## Differences from the Bedrock version

Paper can't add new mobs or models, so:

- The Shifter is an invisible Husk underneath (it never burns or turns into a Zombie), with a Citizens NPC wearing `shifter_skin.png` as its body. Without Citizens or `shifter-skin` set, it's a plain Enderman instead (no block carrying or teleporting, unharmed by water). Its disguises are the real mobs.
- Herobrine and Shifted Players are Citizens NPCs. Without Citizens they're skipped. Herobrine looks like Steve unless you set `herobrine-skin` in `config.yml` (upload a skin at mineskin.org and paste its value and signature).
- Eyes in the Dark are glowing text, the bosses are scaled-up creeper and skeleton (one head, not three), and staring animals don't tilt their heads.
- Sounds are vanilla ones (the Bedrock add-on's Shifter sounds aren't included). There's no fog, and Foxy isn't included.

## Build
```
./gradlew build
```
Needs JDK 25 or newer to run Gradle. Requires Paper 1.21.1 or newer; Citizens is optional.

## Credits

The Shifter and everything around it come from From The Forest by iKorbon.
