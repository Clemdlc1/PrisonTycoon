package fr.prisontycoon.events; // Assurez-vous que le package est correct

import fr.custommobs.CustomMobsPlugin;
import fr.prisontycoon.PrisonTycoon;
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
        // On ne s'intéresse qu'au plugin "CustomMobs"
        if (!event.getPlugin().getName().equals("CustomMobs")) {
            return;
        }

        // Le plugin CustomMobs vient d'être activé, on exécute la logique du hook ici.
        Plugin customMobs = Bukkit.getPluginManager().getPlugin("CustomMobs");

        if (customMobs instanceof CustomMobsPlugin) {
            // La connexion est réussie, on l'enregistre dans la classe principale
            prisonTycoon.setCustomMobsPlugin((CustomMobsPlugin) customMobs);
            prisonTycoon.getLogger().info("Successfully hooked into CustomMobs!");
        } else {
            // Cette erreur ne devrait pas arriver, mais c'est une sécurité
            prisonTycoon.getLogger().warning("CustomMobs was enabled, but the hook failed.");
        }
    }
}