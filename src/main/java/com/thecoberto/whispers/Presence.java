package com.thecoberto.whispers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Shifter presence: an unseen Shifter near one player for 30-60 seconds. It whistles in
 * the dark, knocks, puts out their torches and, rarely, jumpscares them. Only that
 * player hears it.
 */
final class Presence implements Listener {
	private final WhispersPlugin plugin;
	private final Set<UUID> haunted = new HashSet<>();

	Presence(WhispersPlugin plugin) {
		this.plugin = plugin;
	}

	void start(Player player) {
		if (player == null || !haunted.add(player.getUniqueId())) return;
		int seconds = ThreadLocalRandom.current().nextInt(30, 61);
		final int[] elapsed = {0};
		final boolean[] scared = {false};
		final BukkitTask[] task = new BukkitTask[1];
		task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			elapsed[0] += 5;
			if (!player.isOnline() || elapsed[0] > seconds || !WhispersPlugin.isPlaying(player)) {
				haunted.remove(player.getUniqueId());
				task[0].cancel();
				return;
			}
			ThreadLocalRandom random = ThreadLocalRandom.current();
			int roll = random.nextInt(100);
			if (roll < 30) whistle(player);
			else if (roll < 55) knock(player);
			else if (roll < 75) WhispersPlugin.snuffTorches(player.getLocation(), 12, random.nextInt(1, 3));
			else if (roll < 90) footsteps(player);
			else if (!scared[0]) {
				scared[0] = true;
				jumpscare(player);
			}
		}, 40, 100);
	}

	/** Somewhere 6-10 blocks away, behind the player. */
	private static Location behind(Player player) {
		Vector back = player.getLocation().getDirection().setY(0).normalize().multiply(-ThreadLocalRandom.current().nextDouble(6, 10));
		back.rotateAroundY(ThreadLocalRandom.current().nextDouble(-0.8, 0.8));
		return player.getLocation().add(back);
	}

	private void whistle(Player player) {
		Location at = behind(player);
		float pitch = (float) ThreadLocalRandom.current().nextDouble(0.9, 1.3);
		player.playSound(at, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.HOSTILE, 0.5f, pitch);
		Bukkit.getScheduler().runTaskLater(plugin, () -> player.playSound(at, Sound.BLOCK_NOTE_BLOCK_FLUTE, SoundCategory.HOSTILE, 0.5f, pitch * 0.75f), 6);
	}

	private void knock(Player player) {
		Location at = behind(player);
		for (int i = 0; i < 3; i++) {
			Bukkit.getScheduler().runTaskLater(plugin,
					() -> player.playSound(at, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 0.6f, 0.8f), i * 8L);
		}
	}

	private void footsteps(Player player) {
		Location at = behind(player);
		for (int i = 0; i < 4; i++) {
			Bukkit.getScheduler().runTaskLater(plugin,
					() -> player.playSound(at, Sound.BLOCK_GRASS_STEP, SoundCategory.HOSTILE, 0.5f, 0.8f), i * 7L);
		}
	}

	/** A Shifter right in front of them for a moment, with a scream and a flash of blindness. */
	private void jumpscare(Player player) {
		Location front = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.6));
		front.setDirection(player.getLocation().toVector().subtract(front.toVector()));
		Enderman flash = player.getWorld().spawn(front, Enderman.class, org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
			WhispersPlugin.tag(e, "shifter_flash");
			e.setAI(false);
			e.setSilent(true);
			e.setInvulnerable(true);
		});
		player.playSound(front, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.5f);
		player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 4, 0, false, false));
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
			flash.remove();
		}, 12);
	}
}
