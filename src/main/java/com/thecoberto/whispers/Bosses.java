package com.thecoberto.whispers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

/**
 * The two bosses: summoned (/whispers summon) or, very rarely, in place of a natural creeper or skeleton. Same health, abilities and loot as
 * Bedrock; they're scaled-up vanilla mobs, since Paper can't add their models.
 *
 * Mutant Creeper: 200 health, 2.4x size. Its blast is much stronger than a creeper's, it
 * survives it and keeps coming after a short recovery. Follows the mobGriefing rule.
 *
 * Skeleton King: 250 health, 1.7x size with a crown. Slams players who get close, summons
 * skeletons (up to 6) and gets enraged below half health.
 */
final class Bosses implements Listener {
	private static final float BLAST_POWER = 8;
	private static final int MAX_MINIONS = 6;
	private static final String MINION = "king_minion";

	private static final class King {
		long nextSummon = -1;
		long nextSlam;
		boolean enraged;
	}

	private final WhispersPlugin plugin;
	private final Map<UUID, King> kings = new HashMap<>();
	private long now;

	Bosses(WhispersPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getScheduler().runTaskTimer(plugin, this::tickKings, 10, 10);
	}

	private static void setBase(LivingEntity e, Attribute attribute, double value) {
		var instance = e.getAttribute(attribute);
		if (instance != null) instance.setBaseValue(value);
	}

	private static Component bossName(String name, NamedTextColor color) {
		return Component.text(name, color).decoration(TextDecoration.BOLD, true);
	}

	// ---------- Mutant Creeper ----------

	void spawnMutantCreeper(Location at) {
		at.getWorld().spawn(at, Creeper.class, CreatureSpawnEvent.SpawnReason.CUSTOM, c -> {
			WhispersPlugin.tag(c, "mutant_creeper");
			c.customName(bossName("Mutant Creeper", NamedTextColor.GREEN));
			setBase(c, Attribute.GENERIC_MAX_HEALTH, 200);
			c.setHealth(200);
			setBase(c, Attribute.GENERIC_MOVEMENT_SPEED, 0.26);
			setBase(c, Attribute.GENERIC_SCALE, 2.4);
			c.setRemoveWhenFarAway(false);
		});
	}

	@EventHandler(ignoreCancelled = true)
	public void onPrime(ExplosionPrimeEvent event) {
		if (!(event.getEntity() instanceof Creeper creeper) || !"mutant_creeper".equals(WhispersPlugin.kindOf(creeper))) return;
		// Cancelling keeps the creeper alive (its fuse just resets); the blast is ours.
		event.setCancelled(true);
		Location center = creeper.getLocation().add(0, 1.5, 0);
		boolean breaksBlocks = Boolean.TRUE.equals(creeper.getWorld().getGameRuleValue(GameRule.MOB_GRIEFING));
		creeper.getWorld().createExplosion(center, BLAST_POWER, false, breaksBlocks, creeper);
		// Recovers for 4 seconds, barely moving.
		setBase(creeper, Attribute.GENERIC_MOVEMENT_SPEED, 0.06);
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (creeper.isValid()) setBase(creeper, Attribute.GENERIC_MOVEMENT_SPEED, 0.3);
		}, 80);
	}

	// ---------- Skeleton King ----------

	void spawnSkeletonKing(Location at) {
		Skeleton king = at.getWorld().spawn(at, Skeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
			WhispersPlugin.tag(s, "skeleton_king");
			s.customName(bossName("Skeleton King", NamedTextColor.GOLD));
			setBase(s, Attribute.GENERIC_MAX_HEALTH, 250);
			s.setHealth(250);
			setBase(s, Attribute.GENERIC_MOVEMENT_SPEED, 0.25);
			setBase(s, Attribute.GENERIC_SCALE, 1.7);
			s.setRemoveWhenFarAway(false);
			ItemStack crown = new ItemStack(Material.GOLDEN_HELMET);
			crown.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 3);
			s.getEquipment().setHelmet(crown);
			s.getEquipment().setHelmetDropChance(0);
			s.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
			s.getEquipment().setItemInMainHandDropChance(0);
		});
		kings.put(king.getUniqueId(), new King());
	}

	private void tickKings() {
		now += 10;
		kings.entrySet().removeIf(entry -> {
			Entity e = Bukkit.getEntity(entry.getKey());
			if (!(e instanceof Skeleton king) || !king.isValid()) return true; // unloaded or dead; picked up again by onLoad
			King state = entry.getValue();
			double max = king.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
			if (!state.enraged && king.getHealth() <= max / 2) {
				state.enraged = true;
				setBase(king, Attribute.GENERIC_MOVEMENT_SPEED, 0.32);
				king.getWorld().playSound(king, Sound.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5f, 1.3f);
			}
			List<Player> aware = playersNear(king, 24);
			if (aware.isEmpty()) return false;
			if (state.nextSummon < 0) state.nextSummon = now + 100; // first wave after 5 seconds
			if (now >= state.nextSummon) {
				summon(king, state.enraged ? 3 : 2);
				state.nextSummon = now + (state.enraged ? 12 : 20) * 20L;
			}
			if (now >= state.nextSlam && slam(king)) state.nextSlam = now + 6 * 20L;
			return false;
		});
	}

	private static List<Player> playersNear(Entity at, double radius) {
		return at.getWorld().getPlayers().stream()
				.filter(p -> WhispersPlugin.isPlaying(p) && p.getLocation().distance(at.getLocation()) <= radius).toList();
	}

	private void summon(Skeleton king, int count) {
		long alive = king.getWorld().getNearbyEntities(king.getLocation(), 48, 48, 48).stream()
				.filter(e -> MINION.equals(WhispersPlugin.kindOf(e))).count();
		int toSpawn = (int) Math.min(count, MAX_MINIONS - alive);
		if (toSpawn <= 0) return;
		king.getWorld().playSound(king, Sound.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 2f, 0.6f);
		for (int i = 0; i < toSpawn; i++) {
			Location spot = WhispersPlugin.findSpot(king.getLocation(), 2, 5, 15);
			if (spot == null) continue;
			// DEFAULT, so Skeleton Variants (if installed) turns some into its variants.
			king.getWorld().spawn(spot, Skeleton.class, CreatureSpawnEvent.SpawnReason.DEFAULT, s -> WhispersPlugin.tag(s, MINION));
			king.getWorld().spawnParticle(Particle.SOUL, spot.clone().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.02);
		}
	}

	/** Players within 5 blocks are knocked back and hurt. */
	private boolean slam(Skeleton king) {
		List<Player> targets = playersNear(king, 5);
		if (targets.isEmpty()) return false;
		king.getWorld().playSound(king, Sound.ENTITY_RAVAGER_STUNNED, SoundCategory.HOSTILE, 2f, 0.7f);
		king.getWorld().spawnParticle(Particle.EXPLOSION, king.getLocation().add(0, 0.5, 0), 1);
		for (Player p : targets) {
			p.damage(8, king);
			Vector push = p.getLocation().toVector().subtract(king.getLocation().toVector()).setY(0);
			if (push.lengthSquared() < 0.01) push = new Vector(1, 0, 0);
			p.setVelocity(push.normalize().multiply(1.4).setY(0.6));
		}
		return true;
	}

	/** Kings in chunks that load again (or were there when the server started). */
	@EventHandler
	public void onLoad(org.bukkit.event.world.EntitiesLoadEvent event) {
		for (Entity e : event.getEntities()) {
			if ("skeleton_king".equals(WhispersPlugin.kindOf(e))) kings.putIfAbsent(e.getUniqueId(), new King());
		}
	}

	// ---------- Rare natural spawns ----------

	/**
	 * A naturally spawning overworld creeper or skeleton is, very rarely, replaced by its boss
	 * (boss-chance in config.yml, 1 in 1,000 by default). Never with another of the same boss
	 * within 128 blocks.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onNaturalSpawn(CreatureSpawnEvent event) {
		if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !plugin.enabled("bosses")) return;
		Location at = event.getLocation();
		if (at.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
		String kind = switch (event.getEntityType()) {
			case CREEPER -> "mutant_creeper";
			case SKELETON -> "skeleton_king";
			default -> null;
		};
		if (kind == null) return;
		if (ThreadLocalRandom.current().nextDouble() >= plugin.getConfig().getDouble("boss-chance", 0.001)) return;
		if (WhispersPlugin.nearby(at, kind, 128)) return;
		event.setCancelled(true);
		if (kind.equals("mutant_creeper")) spawnMutantCreeper(at);
		else spawnSkeletonKing(at);
		for (Player p : playersNear(at, 48)) {
			p.playSound(p, Sound.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.8f, 0.5f);
			p.sendActionBar(Component.text("Something big is nearby...", NamedTextColor.DARK_RED));
		}
	}

	private static List<Player> playersNear(Location at, double radius) {
		return at.getWorld().getPlayers().stream()
				.filter(p -> WhispersPlugin.isPlaying(p) && p.getLocation().distance(at) <= radius).toList();
	}

	// ---------- Both ----------

	private static boolean isBoss(Entity e) {
		String kind = WhispersPlugin.kindOf(e);
		return "mutant_creeper".equals(kind) || "skeleton_king".equals(kind);
	}

	@EventHandler(ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		// The Mutant Creeper survives its own blasts.
		if ("mutant_creeper".equals(WhispersPlugin.kindOf(event.getEntity()))
				&& (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
				|| event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) event.setCancelled(true);
	}

	@EventHandler(ignoreCancelled = true)
	public void onCombust(EntityCombustEvent event) {
		if (isBoss(event.getEntity())) event.setCancelled(true); // the sun doesn't burn them
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onDeath(EntityDeathEvent event) {
		String kind = WhispersPlugin.kindOf(event.getEntity());
		if (kind == null) return;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		List<ItemStack> drops = event.getDrops();
		if (kind.equals("mutant_creeper")) {
			drops.clear();
			drops.add(new ItemStack(Material.GUNPOWDER, random.nextInt(6, 15)));
			drops.add(new ItemStack(Material.TNT, random.nextInt(1, 4)));
			event.setDroppedExp(50);
		} else if (kind.equals("skeleton_king")) {
			drops.clear();
			ItemStack crown = Bukkit.getItemFactory().enchantWithLevels(new ItemStack(Material.GOLDEN_HELMET), 30, true, random);
			ItemMeta meta = crown.getItemMeta();
			meta.itemName(Component.text("Skeleton King's Crown", NamedTextColor.GOLD));
			crown.setItemMeta(meta);
			drops.add(crown);
			drops.add(Bukkit.getItemFactory().enchantWithLevels(new ItemStack(Material.BOW), 30, true, random));
			drops.add(new ItemStack(Material.BONE, random.nextInt(8, 17)));
			drops.add(new ItemStack(Material.GOLD_INGOT, random.nextInt(4, 10)));
			event.setDroppedExp(50);
			kings.remove(event.getEntity().getUniqueId());
			// His skeletons crumble when he dies.
			Location at = event.getEntity().getLocation();
			for (Entity e : at.getWorld().getNearbyEntities(at, 64, 64, 64)) {
				if (MINION.equals(WhispersPlugin.kindOf(e)) && e instanceof LivingEntity minion) {
					at.getWorld().spawnParticle(Particle.SOUL, minion.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.02);
					minion.setHealth(0);
				}
			}
		}
	}
}
