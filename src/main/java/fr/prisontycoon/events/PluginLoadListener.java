package fr.prisontycoon.events; // Assurez-vous que le package est correct

import fr.custommobs.CustomMobsPlugin;
import fr.prisontycoon.PrisonTycoon;
import fr.shop.PlayerShops;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public class PluginLoadListener implements Listener {

    private final PrisonTycoon prisonTycoon;

    public PluginLoadListener(PrisonTycoon prisonTycoon) {
        this.prisonTycoon = prisonTycoon;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String name = event.getPlugin().getName();

        if ("CustomMobs".equals(name)) {
            Plugin customMobs = Bukkit.getPluginManager().getPlugin("CustomMobs");
            if (customMobs instanceof CustomMobsPlugin) {
                prisonTycoon.setCustomMobsPlugin((CustomMobsPlugin) customMobs);
                prisonTycoon.getLogger().info("Successfully hooked into CustomMobs!");
            } else {
                prisonTycoon.getLogger().warning("CustomMobs was enabled, but the hook failed.");
            }
            return;
        }

        if ("PlayerShops".equals(name)) {
            Plugin ps = Bukkit.getPluginManager().getPlugin("PlayerShops");
            if (ps != null && ps.isEnabled()) {
                prisonTycoon.setPlayerShopsPlugin((PlayerShops) ps);
                prisonTycoon.getLogger().info("Successfully hooked into PlayerShops!");
            } else {
                prisonTycoon.getLogger().warning("PlayerShops was enabled, but the hook failed.");
            }
        }
    }
}