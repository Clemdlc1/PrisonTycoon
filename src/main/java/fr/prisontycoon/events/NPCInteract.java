package fr.prisontycoon.events;

import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class NPCInteract implements Listener {

    private final PrisonTycoon plugin;

    public NPCInteract(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(NpcInteractEvent event) {
        Player player = event.getPlayer();

        try {
            // Vérifier si c'est un NPC du marché noir
            String npcName = event.getNpc().getData().getName();
            if (isBlackMarketNPC(npcName)) {

                // Debug log
                plugin.getPluginLogger().debug("§7Interaction avec NPC Marché Noir: " + npcName + " par " + player.getName());

                // Annuler les actions par défaut du NPC pour gérer nous-mêmes
                event.setCancelled(true);

                // Ouvrir le marché noir
                plugin.getBlackMarketManager().openBlackMarket(player);
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de l'interaction NPC:");
            e.printStackTrace();

            // Annuler l'event en cas d'erreur pour éviter des comportements imprévisibles
            event.setCancelled(true);
        }
    }

    /**
     * Vérifie si un NPC appartient au système de marché noir
     */
    private boolean isBlackMarketNPC(String npcName) {
        if (npcName == null) return false;

        return npcName.startsWith("blackmarket") ||
                npcName.contains("Marchand Noir") ||
                npcName.contains("RAID EN COURS") ||
                npcName.contains("Marchand Louche");
    }
}