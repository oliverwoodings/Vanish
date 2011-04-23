package com.echo28.bukkit.vanish;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityListener;

public class VanishEntityListener extends EntityListener {
	
	private final Vanish plugin;

	public VanishEntityListener(Vanish instance) {
		this.plugin = instance;
	}
	
	public void onEntityDamage(EntityDamageEvent event) {
		if(event.getEntity() instanceof Player) {
			Player victim = (Player) event.getEntity();
			if (plugin.invisible.contains(victim))
				event.setCancelled(true);
			if (event instanceof EntityDamageByEntityEvent) {
				EntityDamageByEntityEvent attackevent = (EntityDamageByEntityEvent) event;
				Entity attackentity = attackevent.getDamager();
				if (attackentity instanceof Player) {
					Player attacker = (Player) attackentity;
					if (plugin.invisible.contains(attacker)) {
						event.setCancelled(true);
						attacker.sendMessage("[Vanish] You are invisible, don't try to attack people!");
					}
				}
			}
		}
		
	}
	
}
