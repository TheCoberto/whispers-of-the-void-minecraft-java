package com.thecoberto.whispers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * The Shifter. It hides as an ordinary mob (a "disguise") until a player comes within
 * 4 blocks, then reveals itself: a tall dark figure (an Enderman underneath, since Paper
 * can't add its model) that stalks, chases or vanishes, depending on its mood.
 */
final class Shifter implements Listener {
	private static final List<EntityType> DISGUISES = List.of(EntityType.SHEEP, EntityType.PIG, EntityType.COW,
			EntityType.CHICKEN, EntityType.WOLF, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.SPIDER,
			EntityType.CREEPER, EntityType.VILLAGER);
	static final double REVEAL_RANGE = 3;
	/** How long someone has to stay within REVEAL_RANGE before a disguise reveals itself. */
	static final int REVEAL_AFTER_TICKS = 40;
	private static final int DISGUISE_LIFE_TICKS = 180 * 20;
	private static final double HEALTH = 500;
	private static final double DAMAGE = 13;

	/** How a revealed Shifter behaves, picked at random like the Bedrock entity's moods. */
	private enum Mood {
		STALK_AND_VANISH(30), STALK_THEN_CHASE(20), CHASE(15), VANISH_WHEN_WATCHED(25), SEEKER(10);

		final int weight;

		Mood(int weight) {
			this.weight = weight;
		}

		static Mood roll() {
			int pick = ThreadLocalRandom.current().nextInt(100);
			for (Mood m : values()) {
				pick -= m.weight;
				if (pick < 0) return m;
			}
			return STALK_AND_VANISH;
		}
	}

	private static final class State {
		Mood mood;
		boolean chasing;
		int age;
		int moodTicks;
		/** When STALK_AND_VANISH gives up, picked once. */
		int vanishAfter = 20 * ThreadLocalRandom.current().nextInt(30, 61);
		/** How long the player has kept looking straight at it. */
		int watchedTicks;
	}

	private final WhispersPlugin plugin;
	private final Map<UUID, State> shifters = new HashMap<>();
	private final Map<UUID, Integer> disguises = new HashMap<>(); // id -> age in ticks
	private final Map<UUID, Integer> closeTicks = new HashMap<>(); // id -> ticks a player has been close

	Shifter(WhispersPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
	}

	void removeAll() {
		for (UUID id : shifters.keySet()) {
			Entity e = Bukkit.getEntity(id);
			if (e != null) e.remove();
		}
		for (UUID id : disguises.keySet()) {
			Entity e = Bukkit.getEntity(id);
			if (e != null) e.remove();
		}
	}

	// ---------- Disguises ----------

	void spawnDisguise(Location at) {
		// One in eleven is a Shifted Player, when Citizens is there.
		if (plugin.npcs != null && ThreadLocalRandom.current().nextInt(DISGUISES.size() + 1) == 0) {
			plugin.npcs.spawnShiftedPlayer(at);
			return;
		}
		EntityType type = DISGUISES.get(ThreadLocalRandom.current().nextInt(DISGUISES.size()));
		// CUSTOM, so other plugins (like Skeleton Variants) leave it alone.
		Entity mob = at.getWorld().spawnEntity(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM);
		WhispersPlugin.tag(mob, "disguise");
		disguises.put(mob.getUniqueId(), 0);
	}

	/** Turns a disguise (or Shifted Player) into the Shifter, where it stands. */
	void reveal(Entity disguise) {
		Location at = disguise.getLocation();
		disguises.remove(disguise.getUniqueId());
		at.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1.5f, 0.6f);
		WhispersPlugin.smoke(at);
		disguise.remove();
		spawnRevealed(at);
	}

	/**
	 * A Shifter that just dropped its disguise right next to someone. It never vanishes
	 * straight away: it stalks then chases, chases, or sniffs you out.
	 */
	Mob spawnRevealed(Location at) {
		Mob shifter = spawnShifter(at);
		State state = shifters.get(shifter.getUniqueId());
		if (state != null) {
			int pick = ThreadLocalRandom.current().nextInt(100);
			state.mood = pick < 45 ? Mood.STALK_THEN_CHASE : pick < 80 ? Mood.CHASE : Mood.SEEKER;
		}
		return shifter;
	}

	// ---------- The Shifter ----------

	Mob spawnShifter(Location at) {
		// With its player-shaped body, the mob underneath is an invisible Husk: an invisible
		// Enderman still shows its glowing eyes, floating above the body. Without a body
		// (no Citizens or no skin) it's a plain Enderman.
		boolean body = plugin.npcs != null && plugin.npcs.hasShifterSkin();
		Class<? extends Mob> type = body ? org.bukkit.entity.Husk.class : org.bukkit.entity.Enderman.class;
		Mob shifter = at.getWorld().spawn(at, type, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
			WhispersPlugin.tag(e, "shifter");
			if (e instanceof org.bukkit.entity.Enderman enderman) enderman.setCarriedBlock(null);
			if (e instanceof org.bukkit.entity.Husk husk) {
				husk.setAdult();
				husk.setCanPickupItems(false);
				husk.setShouldBurnInDay(false);
				husk.getEquipment().clear();
			}
			setBase(e, Attribute.GENERIC_MAX_HEALTH, HEALTH);
			e.setHealth(HEALTH);
			setBase(e, Attribute.GENERIC_ATTACK_DAMAGE, DAMAGE);
			setBase(e, Attribute.GENERIC_MOVEMENT_SPEED, 0.25);
			// The Husk matches the body's size (1.2x a player); the Enderman is tall already.
			setBase(e, Attribute.GENERIC_SCALE, body ? 1.2 : 1.1);
			e.setRemoveWhenFarAway(true);
		});
		if (body) plugin.npcs.attachShifterBody(shifter);
		State state = new State();
		state.mood = Mood.roll();
		shifters.put(shifter.getUniqueId(), state);
		return shifter;
	}

	private static void setBase(LivingEntity e, Attribute attribute, double value) {
		var instance = e.getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	private void tick() {
		// Disguises: stare at the nearest player, reveal when one gets close, despawn after a while.
		disguises.entrySet().removeIf(entry -> {
			Entity e = Bukkit.getEntity(entry.getKey());
			if (!(e instanceof Mob mob) || !mob.isValid()) return true;
			int age = entry.getValue() + 5;
			entry.setValue(age);
			if (age > DISGUISE_LIFE_TICKS) {
				mob.remove();
				return true;
			}
			Player near = WhispersPlugin.nearestPlayer(mob.getLocation(), 64);
			if (near == null) return false;
			mob.lookAt(near);
			// Reveals only once someone has stayed close for a couple of seconds, not on one step in.
			int close = near.getLocation().distance(mob.getLocation()) <= REVEAL_RANGE ? closeTicks.merge(entry.getKey(), 5, Integer::sum) : 0;
			if (close == 0) closeTicks.remove(entry.getKey());
			if (close >= REVEAL_AFTER_TICKS) {
				closeTicks.remove(entry.getKey());
				Bukkit.getScheduler().runTask(plugin, () -> reveal(mob));
			}
			return false;
		});

		shifters.entrySet().removeIf(entry -> {
			Entity e = Bukkit.getEntity(entry.getKey());
			if (!(e instanceof Mob shifter) || !shifter.isValid()) return true;
			return tickShifter(shifter, entry.getValue());
		});
	}

	/** Returns true when the Shifter is gone. */
	private boolean tickShifter(Mob shifter, State state) {
		state.age += 5;
		state.moodTicks += 5;
		Player player = WhispersPlugin.nearestPlayer(shifter.getLocation(), 64);
		if (state.age > 20 * 60 * 3 || player == null && state.age > 20 * 30) {
			shifter.remove();
			return true;
		}
		// Nobody in survival/adventure nearby (e.g. you're in creative): it just waits.
		if (player == null) return false;
		double distance = player.getLocation().distance(shifter.getLocation());
		if (state.chasing) {
			if (shifter.getTarget() != player) shifter.setTarget(player);
			if (state.age % 60 == 0) shifter.getWorld().playSound(shifter, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 0.6f, 0.5f);
			// Gives up after a minute and a half of chasing.
			if (state.moodTicks > 20 * 90) return vanish(shifter);
			return false;
		}
		shifter.lookAt(player);
		switch (state.mood) {
			case STALK_AND_VANISH -> {
				// Stalks for 30-60 seconds before vanishing. Caught up close early on, it chases instead.
				if (state.moodTicks > state.vanishAfter) return vanish(shifter);
				if (distance <= 3) {
					if (state.age < 20 * 20) chase(shifter, player, state);
					else return vanish(shifter);
				} else stalk(shifter, player, distance);
			}
			case STALK_THEN_CHASE -> {
				if (state.moodTicks > 20 * 10 || distance <= 3) chase(shifter, player, state);
				else stalk(shifter, player, distance);
			}
			case CHASE -> chase(shifter, player, state);
			case VANISH_WHEN_WATCHED -> {
				// Needs a few seconds of being stared at (after stalking a while), not one glance.
				if (isWatching(player, shifter)) state.watchedTicks += 5;
				else state.watchedTicks = Math.max(0, state.watchedTicks - 5);
				if (state.age > 20 * 15 && state.watchedTicks >= 20 * 3) {
					// Gone... then back, invisible, creeping up from behind.
					Location behind = player.getLocation().subtract(player.getLocation().getDirection().setY(0).normalize().multiply(12));
					Location spot = WhispersPlugin.findSpot(behind, 0, 3, 15);
					vanish(shifter);
					if (spot != null) {
						Bukkit.getScheduler().runTaskLater(plugin, () -> {
							Mob creeper = spawnShifter(spot);
							{
								if (plugin.npcs == null || !plugin.npcs.hideShifterBody(creeper, 20 * 60)) {
									creeper.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60, 0, false, false));
								}
								State s = shifters.get(creeper.getUniqueId());
								if (s != null) {
									s.mood = Mood.CHASE;
									chase(creeper, player, s);
								}
							}
						}, 60);
					}
					return true;
				}
				stalk(shifter, player, distance);
			}
			case SEEKER -> {
				// Sniffs you out unless you sneak.
				if (player.isSneaking()) {
					shifter.getPathfinder().stopPathfinding();
					if (state.age % 40 == 0) shifter.getWorld().playSound(shifter, Sound.ENTITY_WARDEN_SNIFF, SoundCategory.HOSTILE, 1f, 0.7f);
				} else if (distance <= 1.5 || state.moodTicks > 20 * 30) {
					chase(shifter, player, state);
				} else {
					shifter.getPathfinder().moveTo(player.getLocation(), 0.9);
				}
			}
		}
		return false;
	}

	/** Keeps 8-16 blocks from the player. */
	private static void stalk(Mob shifter, Player player, double distance) {
		if (distance > 16) shifter.getPathfinder().moveTo(player.getLocation(), 1.0);
		else if (distance < 8) {
			Vector away = shifter.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize().multiply(6);
			shifter.getPathfinder().moveTo(shifter.getLocation().add(away), 1.0);
		} else shifter.getPathfinder().stopPathfinding();
	}

	private static void chase(Mob shifter, Player player, State state) {
		state.chasing = true;
		state.moodTicks = 0;
		setBase(shifter, Attribute.GENERIC_MOVEMENT_SPEED, 0.34);
		shifter.setTarget(player);
		shifter.getWorld().playSound(shifter, Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1.5f, 0.5f);
	}

	/** True if the player is looking right at it (within about 8 degrees, with line of sight). */
	private static boolean isWatching(Player player, Entity entity) {
		Vector toEntity = entity.getLocation().add(0, 1.5, 0).toVector().subtract(player.getEyeLocation().toVector());
		return player.getEyeLocation().getDirection().angle(toEntity) < Math.toRadians(8) && player.hasLineOfSight(entity);
	}

	/** Vanishes in smoke, blinding players nearby for 5 seconds. Returns true for the tick loop. */
	private boolean vanish(Mob shifter) {
		Location at = shifter.getLocation();
		WhispersPlugin.smoke(at);
		at.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1f, 0.5f);
		for (Player p : at.getWorld().getPlayers()) {
			if (p.getLocation().distance(at) <= 10 && WhispersPlugin.isPlaying(p)) {
				p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 5, 0));
			}
		}
		shifter.remove();
		return true;
	}

	// ---------- Keeping the Enderman underneath in line ----------

	private static boolean isShifter(Entity e) {
		return "shifter".equals(WhispersPlugin.kindOf(e));
	}

	@EventHandler(ignoreCancelled = true)
	public void onTarget(EntityTargetEvent event) {
		String kind = WhispersPlugin.kindOf(event.getEntity());
		if ("disguise".equals(kind)) event.setCancelled(true); // disguises never attack
		// The Shifter only targets when it decides to chase (setTarget is CUSTOM).
		if ("shifter".equals(kind) && event.getReason() != EntityTargetEvent.TargetReason.CUSTOM
				&& event.getReason() != EntityTargetEvent.TargetReason.FORGOT_TARGET) event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onTeleport(EntityTeleportEvent event) {
		if (isShifter(event.getEntity())) event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onChangeBlock(EntityChangeBlockEvent event) {
		if (isShifter(event.getEntity())) event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		// Rain and water don't hurt the Shifter like they would an Enderman.
		if (isShifter(event.getEntity()) && event.getCause() == EntityDamageEvent.DamageCause.DROWNING) event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		// Its hits leave you weakened.
		if (isShifter(event.getDamager()) && event.getEntity() instanceof Player player) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20 * 10, 0));
		}
		// Hurting a disguise gives it away.
		if ("disguise".equals(WhispersPlugin.kindOf(event.getEntity())) && event.getDamager() instanceof Player) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (event.getEntity().isValid()) reveal(event.getEntity());
			});
		}
	}

	/** Leftovers from before a restart or crash aren't tracked any more, so they go. */
	@EventHandler
	public void onLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
		for (Entity e : event.getEntities()) {
			String kind = WhispersPlugin.kindOf(e);
			if (("shifter".equals(kind) && !shifters.containsKey(e.getUniqueId()))
					|| ("disguise".equals(kind) && !disguises.containsKey(e.getUniqueId()))
					|| "shifter_flash".equals(kind)) e.remove();
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onTransform(org.bukkit.event.entity.EntityTransformEvent event) {
		// A Husk underwater turns into a Zombie: not the Shifter.
		if (isShifter(event.getEntity())) event.setCancelled(true);
	}

	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		if (isShifter(event.getEntity())) {
			event.getDrops().clear(); // no ender pearls
			shifters.remove(event.getEntity().getUniqueId());
		}
	}
}
