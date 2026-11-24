package com.thecoberto.whispers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;

/**
 * Eyes in the Dark: a pair of pale eyes staring out of the darkness. They blink, and
 * vanish without a sound when a player gets within 12 blocks or after 30-90 seconds.
 * Drawn as glowing text, so they need no resource pack.
 */
final class Eyes implements Listener {
	private static final Component OPEN = Component.text("●  ●", TextColor.color(0xF4E8E8));
	private static final Component SHUT = Component.text("-  -", TextColor.color(0x3A2A2A));

	private static final class State {
		int age;
		int life;
		int nextBlink;
		int openAt = -1;
	}

	private final Map<UUID, State> eyes = new HashMap<>();

	Eyes(WhispersPlugin plugin) {
		Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2, 2);
	}

	void spawn(Location feet) {
		Location at = feet.clone().add(0, 1.6, 0);
		TextDisplay display = at.getWorld().spawn(at, TextDisplay.class, d -> {
			WhispersPlugin.tag(d, "eyes");
			d.text(OPEN);
			d.setBillboard(Display.Billboard.CENTER);
			d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
			d.setShadowed(false);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setPersistent(false);
		});
		State s = new State();
		s.life = 20 * ThreadLocalRandom.current().nextInt(30, 91);
		s.nextBlink = ThreadLocalRandom.current().nextInt(40, 120);
		eyes.put(display.getUniqueId(), s);
	}

	void removeAll() {
		for (UUID id : eyes.keySet()) {
			Entity e = Bukkit.getEntity(id);
			if (e != null) e.remove();
		}
		eyes.clear();
	}

	private void tick() {
		eyes.entrySet().removeIf(entry -> {
			Entity e = Bukkit.getEntity(entry.getKey());
			if (!(e instanceof TextDisplay display) || !display.isValid()) return true;
			State s = entry.getValue();
			s.age += 2;
			if (s.age > s.life || WhispersPlugin.nearestPlayer(display.getLocation(), 12) != null) {
				display.remove();
				return true;
			}
			if (s.age >= s.nextBlink) {
				display.text(SHUT);
				s.openAt = s.age + 4; // a blink lasts a few ticks
				s.nextBlink = s.age + ThreadLocalRandom.current().nextInt(60, 160);
			} else if (s.openAt >= 0 && s.age >= s.openAt) {
				display.text(OPEN);
				s.openAt = -1;
			}
			return false;
		});
	}
}
