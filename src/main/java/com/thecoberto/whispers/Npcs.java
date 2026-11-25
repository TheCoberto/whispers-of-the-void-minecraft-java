package com.thecoberto.whispers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCDamageByEntityEvent;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * The player-shaped creatures, made with Citizens (the only class that touches it, so the
 * plugin runs without Citizens and just skips these).
 *
 * Herobrine never attacks. He watches from a distance, backs away when you come closer,
 * vanishes when you get too close or hit him, and puts out torches near him.
 *
 * Shifted Players are the Shifter wearing armor and carrying tools, using an online
 * player's name (and skin) or one that sounds familiar. They come for you, and reveal the
 * Shifter when they get close.
 */
final class Npcs implements Listener {
	private static final List<String> PRESET_NAMES = List.of("WelloffGreat16", "Shep Farthies", "PiersFathesh",
			"Topher Fasshi", "Trisha Fesphe", "Seth Frasphie", "TheCoberto", "M1cha4l", "Pebbles44", "HelpfulTom",
			"FevreDream", "DR_ex", "TeeVee4", "Wackojacko", "Starstruckk", "Ay000", "RealBoy2000", "iKorbon",
			"Mrs iKorbon", "gamerkin", "minecraftalt84", "xxwolfgirlxx");

	private static final class State {
		final boolean herobrine;
		int age;
		final int life;
		/** Ticks in a row the NPC has been despawned (Citizens respawns it to apply a skin). */
		int unspawnedTicks;
		/** Herobrine: staring (standing still) or wandering, and until when. */
		boolean staring = true;
		int modeTicks;
		int modeLength = 20 * 6;

		State(boolean herobrine, int life) {
			this.herobrine = herobrine;
			this.life = life;
		}
	}

	private final WhispersPlugin plugin;
	// Not saved: these only exist while someone is around to see them.
	private final NPCRegistry registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
	private final Map<NPC, State> npcs = new HashMap<>();

	/** The Shifter's visible bodies: the (invisible) Enderman underneath -> the NPC that follows it. */
	private final Map<org.bukkit.entity.Enderman, NPC> bodies = new HashMap<>();

	Npcs(WhispersPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
		Bukkit.getScheduler().runTaskTimer(plugin, this::tickBodies, 1, 1);
	}

	void removeAll() {
		for (NPC npc : new ArrayList<>(npcs.keySet())) npc.destroy();
		npcs.clear();
		for (NPC npc : bodies.values()) npc.destroy();
		bodies.clear();
	}

	// ---------- The Shifter's body ----------

	/**
	 * Gives a Shifter its dark player-shaped body: the Enderman goes invisible and silent
	 * and keeps doing the moving and fighting, and an NPC with the Shifter skin follows it
	 * every tick. Returns false (leaving the plain Enderman) if no skin is set up.
	 */
	boolean attachShifterBody(org.bukkit.entity.Enderman shifter) {
		String value = plugin.getConfig().getString("shifter-skin.value", "");
		String signature = plugin.getConfig().getString("shifter-skin.signature", "");
		if (value.isEmpty() || signature.isEmpty()) return false;
		NPC npc = registry.createNPC(EntityType.PLAYER, "Shifter");
		npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, false);
		npc.data().set(NPC.Metadata.COLLIDABLE, false);
		npc.getOrAddTrait(SkinTrait.class).setSkinPersistent("whispers_shifter", signature, value);
		npc.spawn(shifter.getLocation());
		shifter.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY,
				org.bukkit.potion.PotionEffect.INFINITE_DURATION, 0, false, false));
		shifter.setSilent(true);
		bodies.put(shifter, npc);
		return true;
	}

	private void tickBodies() {
		bodies.entrySet().removeIf(entry -> {
			org.bukkit.entity.Enderman shifter = entry.getKey();
			NPC npc = entry.getValue();
			if (!shifter.isValid()) {
				npc.destroy();
				return true;
			}
			if (npc.getEntity() instanceof org.bukkit.entity.LivingEntity body) {
				// Re-applied each tick, since Citizens respawns the body when it sets the skin.
				var scale = body.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
				if (scale != null && scale.getBaseValue() != 1.2) scale.setBaseValue(1.2);
				body.teleport(shifter.getLocation());
				Integer hidden = hiddenBodies.get(npc);
				if (hidden != null) {
					if (hidden <= 1) hiddenBodies.remove(npc);
					else hiddenBodies.put(npc, hidden - 1);
					body.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY, 5, 0, false, false));
				}
			}
			return false;
		});
	}

	private final Map<NPC, Integer> hiddenBodies = new HashMap<>();

	/** Makes a Shifter's body invisible for a while. False if it has no body. */
	boolean hideShifterBody(org.bukkit.entity.Enderman shifter, int ticks) {
		NPC npc = bodies.get(shifter);
		if (npc == null) return false;
		hiddenBodies.put(npc, ticks);
		return true;
	}

	/** The Enderman a Shifter body belongs to, or null. */
	private org.bukkit.entity.Enderman shifterOf(NPC npc) {
		for (Map.Entry<org.bukkit.entity.Enderman, NPC> entry : bodies.entrySet()) {
			if (entry.getValue() == npc) return entry.getKey();
		}
		return null;
	}

	// ---------- Herobrine ----------

	void spawnHerobrine(Location at) {
		NPC npc = registry.createNPC(EntityType.PLAYER, "Herobrine");
		npc.data().set(NPC.Metadata.NAMEPLATE_VISIBLE, false);
		String value = plugin.getConfig().getString("herobrine-skin.value", "");
		String signature = plugin.getConfig().getString("herobrine-skin.signature", "");
		if (!value.isEmpty() && !signature.isEmpty()) {
			npc.getOrAddTrait(SkinTrait.class).setSkinPersistent("whispers_herobrine", signature, value);
		}
		npc.spawn(at);
		if (npc.getEntity() != null) WhispersPlugin.tag(npc.getEntity(), "herobrine");
		npcs.put(npc, new State(true, 20 * ThreadLocalRandom.current().nextInt(90, 181)));
	}

	private void vanishHerobrine(NPC npc) {
		Location at = npc.getStoredLocation();
		if (at != null) {
			WhispersPlugin.smoke(at);
			ThreadLocalRandom random = ThreadLocalRandom.current();
			if (random.nextDouble() < 0.3) {
				// Sometimes slowed down, so it sounds a little wrong.
				float pitch = random.nextDouble() < 0.4 ? (float) random.nextDouble(0.5, 0.7) : 1f;
				at.getWorld().playSound(at, Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, 0.8f, pitch);
			}
		}
		npcs.remove(npc);
		npc.destroy();
	}

	// ---------- Shifted Players ----------

	void spawnShiftedPlayer(Location at) {
		List<String> names = new ArrayList<>(PRESET_NAMES);
		Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
		String name = names.get(ThreadLocalRandom.current().nextInt(names.size()));
		NPC npc = registry.createNPC(EntityType.PLAYER, name);
		// The skin of the account with that name, if there is one.
		npc.getOrAddTrait(SkinTrait.class).setSkinName(name.replace(" ", ""), true);
		Equipment gear = npc.getOrAddTrait(Equipment.class);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (random.nextBoolean()) gear.set(Equipment.EquipmentSlot.HAND, new ItemStack(random.nextInt(4) == 0 ? Material.IRON_SWORD : Material.IRON_PICKAXE));
		if (random.nextBoolean()) {
			String set = List.of("LEATHER", "GOLDEN", "CHAINMAIL", "IRON", "DIAMOND").get(random.nextInt(5));
			gear.set(Equipment.EquipmentSlot.HELMET, new ItemStack(Material.valueOf(set + "_HELMET")));
			gear.set(Equipment.EquipmentSlot.CHESTPLATE, new ItemStack(Material.valueOf(set + "_CHESTPLATE")));
			gear.set(Equipment.EquipmentSlot.LEGGINGS, new ItemStack(Material.valueOf(set + "_LEGGINGS")));
			gear.set(Equipment.EquipmentSlot.BOOTS, new ItemStack(Material.valueOf(set + "_BOOTS")));
		}
		npc.spawn(at);
		if (npc.getEntity() != null) WhispersPlugin.tag(npc.getEntity(), "shifted_player");
		npcs.put(npc, new State(false, 20 * 180));
	}

	private void reveal(NPC npc) {
		Location at = npc.getStoredLocation();
		npcs.remove(npc);
		npc.destroy();
		if (at != null) {
			at.getWorld().playSound(at, Sound.ENTITY_ENDERMAN_STARE, SoundCategory.HOSTILE, 1.5f, 0.6f);
			WhispersPlugin.smoke(at);
			plugin.shifter.spawnRevealed(at);
		}
	}

	// ---------- Both ----------

	private void tick() {
		for (Map.Entry<NPC, State> entry : new ArrayList<>(npcs.entrySet())) {
			NPC npc = entry.getKey();
			State state = entry.getValue();
			Entity body = npc.getEntity();
			if (!npc.isSpawned() || body == null) {
				// Citizens briefly respawns an NPC while it applies its skin (any time, not just
				// at first); only give up if it stays gone.
				state.unspawnedTicks += 10;
				if (state.unspawnedTicks < 20 * 15) continue;
				npcs.remove(npc);
				npc.destroy();
				continue;
			}
			state.unspawnedTicks = 0;
			state.age += 10;
			Player player = WhispersPlugin.nearestPlayer(body.getLocation(), 64);
			if (state.age > state.life || player == null && state.age > 20 * 30) {
				if (state.herobrine) vanishHerobrine(npc);
				else {
					npcs.remove(npc);
					npc.destroy();
				}
				continue;
			}
			if (player == null) continue;
			double distance = player.getLocation().distance(body.getLocation());
			if (state.herobrine) {
				tickHerobrine(npc, state, body, player, distance);
				if (!npcs.containsKey(npc)) continue;
				if (state.age % 100 == 0 && ThreadLocalRandom.current().nextDouble() < 0.2) {
					WhispersPlugin.snuffTorches(body.getLocation(), 4, 1);
				}
			} else {
				if (distance <= 4) {
					reveal(npc);
					continue;
				}
				if (distance <= 20 && !npc.getNavigator().isNavigating()) npc.getNavigator().setTarget(player, true);
			}
		}
	}

	/**
	 * Herobrine lurks: he stands and stares for a while, then wanders off somewhere else
	 * nearby, and sometimes moves while you aren't looking. Looking at him is fine; he
	 * only vanishes when you get too close (or hit him).
	 */
	private void tickHerobrine(NPC npc, State state, Entity body, Player player, double distance) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (distance < 7) {
			vanishHerobrine(npc);
			return;
		}
		state.modeTicks += 10;
		boolean watched = isWatching(player, body);
		if (state.modeTicks >= state.modeLength) {
			state.modeTicks = 0;
			if (state.staring) {
				// Done staring. Sometimes he's simply somewhere else the moment you look away.
				if (!watched && random.nextDouble() < 0.25) {
					Location spot = WhispersPlugin.findSpot(player.getLocation(), 22, 40, 15);
					if (spot != null) {
						npc.getNavigator().cancelNavigation();
						npc.teleport(spot, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
						state.modeLength = 20 * random.nextInt(5, 13);
						return;
					}
				}
				Location spot = WhispersPlugin.findSpot(player.getLocation(), 18, 36, 15);
				if (spot != null) {
					state.staring = false;
					state.modeLength = 20 * random.nextInt(6, 16);
					npc.getNavigator().getLocalParameters().speedModifier(0.7f);
					npc.getNavigator().setTarget(spot);
					return;
				}
			}
			state.staring = true;
			state.modeLength = 20 * random.nextInt(4, 13);
			npc.getNavigator().cancelNavigation();
		}
		if (state.staring) {
			npc.faceLocation(player.getEyeLocation());
			// Too close for comfort: he slowly steps back, still staring.
			if (distance < 12 && !npc.getNavigator().isNavigating() && random.nextDouble() < 0.5) {
				Vector away = body.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize().multiply(5);
				npc.getNavigator().getLocalParameters().speedModifier(0.6f);
				npc.getNavigator().setTarget(body.getLocation().add(away));
			}
		} else {
			// Caught while walking, he may stop dead and turn to look at you.
			if (watched && random.nextDouble() < 0.15) state.modeTicks = state.modeLength;
			else if (!npc.getNavigator().isNavigating()) state.modeTicks = state.modeLength;
		}
	}

	/** True if the player is looking roughly at it, with line of sight. */
	private static boolean isWatching(Player player, Entity entity) {
		Vector toEntity = entity.getLocation().add(0, 1.5, 0).toVector().subtract(player.getEyeLocation().toVector());
		return player.getEyeLocation().getDirection().angle(toEntity) < Math.toRadians(12) && player.hasLineOfSight(entity);
	}

	@EventHandler
	public void onDamage(NPCDamageByEntityEvent event) {
		org.bukkit.entity.Enderman shifter = shifterOf(event.getNPC());
		if (shifter != null) {
			// Hitting the Shifter's body hits the Shifter.
			event.setCancelled(true);
			if (event.getDamager() instanceof org.bukkit.entity.LivingEntity attacker) shifter.damage(event.getDamage(), attacker);
			else shifter.damage(event.getDamage());
			return;
		}
		State state = npcs.get(event.getNPC());
		if (state == null) return;
		event.setCancelled(true);
		// Hitting Herobrine only makes him disappear. Hitting a Shifted Player gives it away.
		if (state.herobrine) vanishHerobrine(event.getNPC());
		else reveal(event.getNPC());
	}
}
