# Whispers of the Void (Paper plugin)

Paper port of the Bedrock add-on [whispers-of-the-void](https://github.com/TheCoberto/whispers-of-the-void): creatures that stalk, hide and watch you, plus two bosses. Spawn timing, chances, distances, health and loot match the Bedrock version.

## Creatures

- **The Shifter**: hides as an ordinary mob (sheep, pig, cow, chicken, wolf, skeleton, zombie, spider, creeper, villager) that never attacks and keeps looking at you. Walk within 4 blocks (or hit it) and it reveals itself as a tall dark figure that, depending on its mood, stalks you and vanishes, stalks then chases, chases straight away, vanishes when you look at it and comes back invisible from behind, or sniffs you out unless you sneak. 500 health; its hits weaken you; vanishing blinds anyone within 10 blocks.
- **Shifted Players** (needs Citizens): the Shifter as a player with armor and tools, using an online player's name and skin, or a familiar-sounding one. They come for you and reveal the Shifter when close.
- **Shifter presence**: an unseen Shifter near you for 30-60 seconds in the dark. Whistles, knocks, footsteps, torches going out, and maybe a jumpscare.
- **Herobrine** (needs Citizens): never attacks. Appears 28-48 blocks away and watches you. Walk toward him and he backs away; get within 8 blocks or hit him and he's gone. Torches near him go out.
- **Eyes in the Dark**: pale eyes in the darkness that blink, and vanish when you get within 12 blocks.
- **Staring animals**: if you haven't slept in over a day, very rarely a herd of farm animals freezes and stares at you in total silence, then goes back to normal.

## Bosses (`/whispers summon` only)

- **Mutant Creeper**: 2.4x size, 200 health. Explosions far stronger than a creeper's (power 8); it survives them and keeps coming after a short recovery. Follows `mobGriefing`. Drops 6-14 gunpowder and 1-3 TNT.
- **Skeleton King**: 1.7x size with a crown, 250 health. Shoots, slams players within 5 blocks, summons up to 6 skeletons, and gets enraged (faster, summons more) below half health. His skeletons crumble when he dies. Drops an enchanted Skeleton King's Crown, an enchanted bow, bones and gold. With Skeleton Variants installed, some of his skeletons come out as variants.

## Commands (ops, `whispers.admin`)

- `/whispers spawns` lists what spawns on its own; `/whispers spawns <shifter|presence|herobrine|eyes|staring> on|off` changes it.
- `/whispers summon <shifter|disguise|shifted_player|presence|herobrine|eyes|mutant_creeper|skeleton_king>`
- `/whispers stare` makes nearby farm animals stare (for testing).

## Differences from the Bedrock version

Paper can't add new mobs or models, so:

- The Shifter's true form is an Enderman underneath (a little bigger, no block carrying or teleporting, unharmed by water). Its disguises are the real mobs.
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
