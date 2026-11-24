package com.thecoberto.whispers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Whispers of the Void for Paper: a port of the Bedrock add-on. Creatures that
 * stalk, hide and watch you (the Shifter, its presence, Herobrine, Eyes in the
 * Dark, staring animals) and two bosses (Mutant Creeper, Skeleton King).
 *
 * Creatures spawn like the Bedrock spawn rules: every so often near each player
 * in the Overworld, with a chance, a distance band and (for some) darkness.
 */
public final class WhispersPlugin extends JavaPlugin {
	static NamespacedKey KIND;

	/** A creature that spawns on its own, with the Bedrock spawn rule's numbers. */
	private record Rule(String setting, String kind, int everyTicks, double chance, int minDistance, int maxDistance, int maxLight) {
	}

	private static final List<Rule> RULES = List.of(
			new Rule("shifter", "disguise", 3000, 0.03, 24, 48, 15),
			new Rule("shifter", "shifter", 6000, 0.02, 30, 48, 15),
			new Rule("presence", "presence", 4800, 0.03, 16, 32, 7),
			new Rule("herobrine", "herobrine", 6000, 0.15, 28, 48, 15),
			new Rule("eyes", "eyes", 3000, 0.25, 18, 40, 4));

	static final List<String> SETTINGS = List.of("shifter", "presence", "herobrine", "eyes", "staring");
	static final List<String> SUMMONABLE = List.of("shifter", "disguise", "shifted_player", "presence", "herobrine",
			"eyes", "mutant_creeper", "skeleton_king");

	Shifter shifter;
	Presence presence;
	Eyes eyes;
	StaringAnimals staring;
	Bosses bosses;
	/** Herobrine and Shifted Players, or null without Citizens. */
	Npcs npcs;

	private long tick;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		KIND = new NamespacedKey(this, "kind");
		shifter = new Shifter(this);
		presence = new Presence(this);
		eyes = new Eyes(this);
		staring = new StaringAnimals(this);
		bosses = new Bosses(this);
		if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
			npcs = new Npcs(this);
		} else {
			getLogger().info("Citizens isn't installed: Herobrine and Shifted Players are off.");
		}
		for (Object listener : new Object[] {shifter, presence, eyes, staring, bosses}) {
			getServer().getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
		}
		if (npcs != null) getServer().getPluginManager().registerEvents(npcs, this);
		Bukkit.getScheduler().runTaskTimer(this, this::spawnTick, 20, 20);
	}

	@Override
	public void onDisable() {
		shifter.removeAll();
		eyes.removeAll();
		if (npcs != null) npcs.removeAll();
	}

	// ---------- Settings ----------

	boolean enabled(String setting) {
		return getConfig().getBoolean("spawns." + setting, true);
	}

	// ---------- Natural spawning ----------

	private void spawnTick() {
		tick += 20;
		for (Rule rule : RULES) {
			if (tick % rule.everyTicks() != 0 || !enabled(rule.setting())) continue;
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getWorld().getEnvironment() != World.Environment.NORMAL || !isPlaying(player)) continue;
				if (ThreadLocalRandom.current().nextDouble() >= rule.chance()) continue;
				// Like the Bedrock density limit: one of each near a player at a time.
				if (nearby(player.getLocation(), rule.kind(), 64)) continue;
				Location spot = findSpot(player.getLocation(), rule.minDistance(), rule.maxDistance(), rule.maxLight());
				if (spot != null) spawn(rule.kind(), spot, player);
			}
		}
	}

	/** Spawns a creature. Returns false if it can't (like Herobrine without Citizens). */
	boolean spawn(String kind, Location at, Player near) {
		switch (kind) {
			case "disguise" -> shifter.spawnDisguise(at);
			case "shifter" -> shifter.spawnShifter(at);
			case "presence" -> presence.start(near);
			case "eyes" -> eyes.spawn(at);
			case "mutant_creeper" -> bosses.spawnMutantCreeper(at);
			case "skeleton_king" -> bosses.spawnSkeletonKing(at);
			case "herobrine" -> {
				if (npcs == null) return false;
				npcs.spawnHerobrine(at);
			}
			case "shifted_player" -> {
				if (npcs == null) return false;
				npcs.spawnShiftedPlayer(at);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/**
	 * A random standing spot min-max blocks from a player, at their level (for caves) or
	 * on the surface, no brighter than maxLight. Null if none was found.
	 */
	static Location findSpot(Location center, int min, int max, int maxLight) {
		World world = center.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble(Math.PI * 2);
			double distance = random.nextDouble(min, max + 1);
			int x = (int) Math.floor(center.getX() + Math.cos(angle) * distance);
			int z = (int) Math.floor(center.getZ() + Math.sin(angle) * distance);
			if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
			// First near the player's height (caves), then the surface.
			Block feet = null;
			for (int dy = 8; dy >= -8 && feet == null; dy--) {
				Block b = world.getBlockAt(x, center.getBlockY() + dy, z);
				if (standable(b)) feet = b;
			}
			if (feet == null) {
				Block top = world.getHighestBlockAt(x, z).getRelative(0, 1, 0);
				if (standable(top)) feet = top;
			}
			if (feet == null || feet.getLightLevel() > maxLight) continue;
			return feet.getLocation().add(0.5, 0, 0.5);
		}
		return null;
	}

	private static boolean standable(Block feet) {
		return feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable()
				&& feet.getRelative(0, -1, 0).getType().isSolid() && feet.getRelative(0, -1, 0).getType() != Material.BEDROCK;
	}

	// ---------- Shared helpers ----------

	static void tag(Entity entity, String kind) {
		entity.getPersistentDataContainer().set(KIND, PersistentDataType.STRING, kind);
	}

	static String kindOf(Entity entity) {
		return entity == null ? null : entity.getPersistentDataContainer().get(KIND, PersistentDataType.STRING);
	}

	static boolean nearby(Location at, String kind, double radius) {
		for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
			if (kind.equals(kindOf(e))) return true;
		}
		return false;
	}

	/** Survival or adventure: creative and spectator players are left alone. */
	static boolean isPlaying(Player player) {
		return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
	}

	static Player nearestPlayer(Location at, double radius) {
		Player best = null;
		double bestDistance = radius * radius;
		for (Player p : at.getWorld().getPlayers()) {
			if (!isPlaying(p)) continue;
			double d = p.getLocation().distanceSquared(at);
			if (d <= bestDistance) {
				best = p;
				bestDistance = d;
			}
		}
		return best;
	}

	private static final java.util.Set<Material> TORCHES = java.util.EnumSet.of(Material.TORCH, Material.WALL_TORCH,
			Material.SOUL_TORCH, Material.SOUL_WALL_TORCH);

	/** Puts out up to max torches within radius, with a puff of smoke. */
	static void snuffTorches(Location at, int radius, int max) {
		int snuffed = 0;
		for (int dx = -radius; dx <= radius && snuffed < max; dx++) {
			for (int dy = -1; dy <= 2 && snuffed < max; dy++) {
				for (int dz = -radius; dz <= radius && snuffed < max; dz++) {
					Block b = at.getBlock().getRelative(dx, dy, dz);
					if (!TORCHES.contains(b.getType())) continue;
					b.setType(Material.AIR);
					Location c = b.getLocation().add(0.5, 0.7, 0.5);
					at.getWorld().spawnParticle(Particle.SMOKE, c, 4, 0.05, 0.05, 0.05, 0.01);
					at.getWorld().playSound(c, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.4f);
					snuffed++;
				}
			}
		}
	}

	static void smoke(Location at) {
		at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1, 0), 12, 0.3, 0.8, 0.3, 0.01);
	}

	// ---------- Commands ----------

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (args.length >= 1 && args[0].equalsIgnoreCase("spawns")) {
			if (args.length == 3 && SETTINGS.contains(args[1].toLowerCase(Locale.ROOT))
					&& (args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("off"))) {
				String setting = args[1].toLowerCase(Locale.ROOT);
				boolean on = args[2].equalsIgnoreCase("on");
				getConfig().set("spawns." + setting, on);
				saveConfig();
				Bukkit.broadcast(Component.text("Whispers of the Void: ", NamedTextColor.DARK_PURPLE)
						.append(Component.text(sender.getName() + " turned " + setting + " spawning " + (on ? "on" : "off") + ".", NamedTextColor.WHITE)));
				return true;
			}
			Component list = Component.text("Spawning (change with /whispers spawns <creature> on|off):", NamedTextColor.DARK_PURPLE);
			for (String s : SETTINGS) {
				list = list.append(Component.newline()).append(Component.text(s + ": ", NamedTextColor.WHITE))
						.append(enabled(s) ? Component.text("on", NamedTextColor.GREEN) : Component.text("off", NamedTextColor.RED));
			}
			sender.sendMessage(list);
			return true;
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("summon")) {
			if (!(sender instanceof Player player)) return error(sender, "Only players can summon (it appears in front of you).");
			String kind = args[1].toLowerCase(Locale.ROOT);
			if (!SUMMONABLE.contains(kind)) return error(sender, "Creatures: " + String.join(", ", SUMMONABLE));
			Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(kind.equals("herobrine") ? 12 : 4));
			at.setY(player.getLocation().getY());
			if (!spawn(kind, at, player)) return error(sender, "That needs the Citizens plugin.");
			sender.sendMessage(Component.text("Summoned " + kind + "."));
			return true;
		}
		if (args.length == 1 && args[0].equalsIgnoreCase("stare")) {
			if (!(sender instanceof Player player)) return error(sender, "Only players can do that.");
			if (!staring.stare(player)) return error(sender, "No cows, pigs, sheep or chickens within 24 blocks.");
			return true;
		}
		return false;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> options = new ArrayList<>();
		if (args.length == 1) options.addAll(List.of("spawns", "summon", "stare"));
		else if (args.length == 2 && args[0].equalsIgnoreCase("spawns")) options.addAll(SETTINGS);
		else if (args.length == 2 && args[0].equalsIgnoreCase("summon")) options.addAll(SUMMONABLE);
		else if (args.length == 3 && args[0].equalsIgnoreCase("spawns")) options.addAll(List.of("on", "off"));
		String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
		return options.stream().filter(o -> o.startsWith(typed)).toList();
	}

	private static boolean error(CommandSender sender, String message) {
		sender.sendMessage(Component.text(message, NamedTextColor.RED));
		return true;
	}
}
