package com.thecoberto.whispers;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Staring animals: very rarely, a farm animal near a player who hasn't slept in over a
 * day freezes and turns to stare at them in total silence, with the rest of its herd.
 * A few seconds later they go back to normal. Same numbers as the Bedrock script.
 */
final class StaringAnimals implements Listener {
	private static final Set<EntityType> ANIMALS = EnumSet.of(EntityType.COW, EntityType.MOOSHROOM, EntityType.PIG,
			EntityType.SHEEP, EntityType.CHICKEN);
	private static final int DAY = 24000;
	private static final int ROLL_TICKS = 400;
	private static final double CHANCE = 0.03;
	private static final long COOLDOWN = 2L * DAY;
	private static final double FIND_RANGE = 24;
	private static final double MIN_RANGE = 3;
	private static final double HERD_RANGE = 8;
	private static final double LONELY_RANGE = 32;
	private static final int STARE_TICKS = 66;

	private final WhispersPlugin plugin;
	private final Map<UUID, Long> lastStare = new HashMap<>(); // player -> world full time
	private final Set<UUID> staring = new HashSet<>();

	StaringAnimals(WhispersPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getScheduler().runTaskTimer(plugin, this::roll, ROLL_TICKS, ROLL_TICKS);
	}

	private void roll() {
		if (!plugin.enabled("staring")) return;
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!WhispersPlugin.isPlaying(player)) continue;
			// Same counter that brings phantoms: time since the player last slept.
			if (player.getStatistic(Statistic.TIME_SINCE_REST) < DAY) continue;
			long now = player.getWorld().getFullTime();
			Long last = lastStare.get(player.getUniqueId());
			if (last != null && now - last < COOLDOWN && now >= last) continue;
			if (ThreadLocalRandom.current().nextDouble() >= CHANCE) continue;
			boolean company = player.getWorld().getPlayers().stream()
					.anyMatch(o -> o != player && o.getLocation().distance(player.getLocation()) < LONELY_RANGE);
			if (company) continue;
			if (stare(player)) lastStare.put(player.getUniqueId(), now);
		}
	}

	private List<Mob> animalsNear(Location at, double radius) {
		List<Mob> found = new ArrayList<>();
		for (Entity e : at.getWorld().getNearbyEntities(at, radius, radius, radius)) {
			if (ANIMALS.contains(e.getType()) && e instanceof Mob mob && !staring.contains(e.getUniqueId())
					&& e.getLocation().distance(at) <= radius) found.add(mob);
		}
		return found;
	}

	/** Makes a herd near the player stare. False if there were no animals around. */
	boolean stare(Player player) {
		List<Mob> candidates = animalsNear(player.getLocation(), FIND_RANGE).stream()
				.filter(m -> m.getLocation().distance(player.getLocation()) >= MIN_RANGE).toList();
		if (candidates.isEmpty()) return false;
		Mob anchor = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
		List<Mob> herd = animalsNear(anchor.getLocation(), HERD_RANGE);
		Map<UUID, Boolean> wasSilent = new HashMap<>();
		for (Mob animal : herd) {
			staring.add(animal.getUniqueId());
			wasSilent.put(animal.getUniqueId(), animal.isSilent());
			animal.setSilent(true); // not a sound while they look at you
			animal.setAI(false);
		}
		final int[] tick = {0};
		final BukkitTask[] task = new BukkitTask[1];
		task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Mob animal : herd) {
				if (!animal.isValid() || !player.isOnline()) continue;
				faceTowards(animal, player.getEyeLocation());
			}
			tick[0] += 2;
			if (tick[0] >= STARE_TICKS) {
				task[0].cancel();
				for (Mob animal : herd) {
					staring.remove(animal.getUniqueId());
					if (!animal.isValid()) continue;
					animal.setAI(true);
					animal.setSilent(wasSilent.getOrDefault(animal.getUniqueId(), false));
				}
			}
		}, 0, 2);
		return true;
	}

	private static void faceTowards(LivingEntity entity, Location target) {
		Vector dir = target.toVector().subtract(entity.getEyeLocation().toVector());
		Location look = entity.getLocation().setDirection(dir);
		entity.setRotation(look.getYaw(), look.getPitch());
	}
}
