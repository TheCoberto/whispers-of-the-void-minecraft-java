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

		State(boolean herobrine, int life) {
			this.herobrine = herobrine;
			this.life = life;
		}
	}

	private final WhispersPlugin plugin;
	// Not saved: these only exist while someone is around to see them.
	private final NPCRegistry registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
	private final Map<NPC, State> npcs = new HashMap<>();

	Npcs(WhispersPlugin plugin) {
		this.plugin = plugin;
		Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
	}

	void removeAll() {
		for (NPC npc : new ArrayList<>(npcs.keySet())) npc.destroy();
		npcs.clear();
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
			plugin.shifter.spawnShifter(at);
		}
	}

	// ---------- Both ----------

	private void tick() {
		for (Map.Entry<NPC, State> entry : new ArrayList<>(npcs.entrySet())) {
			NPC npc = entry.getKey();
			State state = entry.getValue();
			Entity body = npc.getEntity();
			if (!npc.isSpawned() || body == null) {
				npcs.remove(npc);
				npc.destroy();
				continue;
			}
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
				npc.faceLocation(player.getEyeLocation());
				if (distance < 8) {
					vanishHerobrine(npc);
					continue;
				}
				if (distance < 16) {
					// Backs away, still facing you.
					Vector away = body.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize().multiply(6);
					npc.getNavigator().setTarget(body.getLocation().add(away));
				} else if (npc.getNavigator().isNavigating()) {
					npc.getNavigator().cancelNavigation();
				}
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

	@EventHandler
	public void onDamage(NPCDamageByEntityEvent event) {
		State state = npcs.get(event.getNPC());
		if (state == null) return;
		event.setCancelled(true);
		// Hitting Herobrine only makes him disappear. Hitting a Shifted Player gives it away.
		if (state.herobrine) vanishHerobrine(event.getNPC());
		else reveal(event.getNPC());
	}
}
