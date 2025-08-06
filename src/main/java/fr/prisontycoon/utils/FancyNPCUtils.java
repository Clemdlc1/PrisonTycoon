package fr.prisontycoon.utils;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcData;
import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Location;

import java.io.File;
import java.util.UUID;

/**
 * Utilitaire pour créer et gérer des NPCs avec FancyNPCs
 */
public class FancyNPCUtils {

    private final PrisonTycoon plugin;
    private final File skinsFolder;
    private boolean isFancyNPCsEnabled = false;

    public FancyNPCUtils(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.skinsFolder = new File(plugin.getDataFolder(), "npc_skins");

        // Créer le dossier des skins s'il n'existe pas
        if (!skinsFolder.exists()) {
            skinsFolder.mkdirs();
            plugin.getPluginLogger().info("§7Dossier npc_skins créé : " + skinsFolder.getAbsolutePath());
        }

        checkFancyNPCsAvailability();
    }

    /**
     * Vérifie si FancyNPCs est disponible
     */
    private void checkFancyNPCsAvailability() {
        try {
            Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
            if (plugin.getServer().getPluginManager().isPluginEnabled("FancyNpcs")) {
                isFancyNPCsEnabled = true;
                plugin.getPluginLogger().info("§a✅ FancyNPCs disponible pour les utilitaires NPC");
            }
        } catch (ClassNotFoundException e) {
            isFancyNPCsEnabled = false;
            plugin.getPluginLogger().warning("§eFancyNPCs non disponible - Mode dégradé pour les NPCs");
        }
    }

    /**
     * Crée un NPC FancyNPCs
     */
    public Npc createNPC(String npcId, String displayName, Location location) {
        if (!isFancyNPCsEnabled) {
            plugin.getPluginLogger().warning("§eTentative de création NPC sans FancyNPCs : " + npcId);
            return null;
        }

        try {
            // Créer les données du NPC
            NpcData npcData = new NpcData(npcId, UUID.randomUUID(), location);

            // Configuration de base
            npcData.setDisplayName(displayName);
            npcData.setShowInTab(false);
            npcData.setCollidable(false);

            // Appliquer le skin si disponible
            String skinPath = getSkinForNPC(npcId);
            if (skinPath != null) {
                npcData.setSkin(skinPath);
                plugin.getPluginLogger().debug("§7Skin appliqué pour " + npcId + " : " + skinPath);
            } else {
                // Skin par défaut
                npcData.setSkin("MHF_Villager");
            }

            // Créer le NPC
            Npc npc = FancyNpcsPlugin.get().getNpcAdapter().apply(npcData);

            // Enregistrer et créer
            FancyNpcsPlugin.get().getNpcManager().registerNpc(npc);
            npc.create();
            npc.spawnForAll();

            plugin.getPluginLogger().debug("§aNPC créé avec succès : " + npcId);
            return npc;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de la création du NPC " + npcId + " : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Supprime un NPC FancyNPCs
     */
    public void removeNPC(Npc npc) {
        if (!isFancyNPCsEnabled || npc == null) {
            return;
        }

        try {
            npc.removeForAll();
            FancyNpcsPlugin.get().getNpcManager().removeNpc(npc);
            plugin.getPluginLogger().debug("§7NPC supprimé : " + npc.getData().getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lors de la suppression du NPC : " + e.getMessage());
        }
    }

    /**
     * Met à jour l'apparence d'un NPC
     */
    public void updateNPCAppearance(Npc npc, String displayName, String npcId) {
        if (!isFancyNPCsEnabled || npc == null) {
            return;
        }

        try {
            NpcData data = npc.getData();
            data.setDisplayName(displayName);

            // Mettre à jour le skin si nécessaire
            String skinPath = getSkinForNPC(npcId);
            if (skinPath != null) {
                data.setSkin(skinPath);
            }

            // Respawn pour appliquer les changements
            npc.removeForAll();
            npc.spawnForAll();

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lors de la mise à jour du NPC : " + e.getMessage());
        }
    }

    /**
     * Cherche le skin pour un NPC donné
     * Le fichier doit être dans le dossier npc_skins/ et se nommer {npcId}.png
     */
    private String getSkinForNPC(String npcId) {

        File skinFileInFancyNPCsFolder = new File("plugins/FancyNpcs/skins/", npcId + ".png");
        if (skinFileInFancyNPCsFolder.exists()) {
            return npcId + ".png";
        }
        return null; // Aucun skin trouvé
    }

    /**
     * Vérifie si FancyNPCs est activé
     */
    public boolean isFancyNPCsEnabled() {
        return isFancyNPCsEnabled;
    }

    /**
     * Obtient le dossier des skins
     */
    public File getSkinsFolder() {
        return skinsFolder;
    }

    /**
     * Liste tous les skins disponibles
     */
    public String[] getAvailableSkins() {
        File[] files = skinsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) return new String[0];

        String[] skins = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String fileName = files[i].getName();
            skins[i] = fileName.substring(0, fileName.lastIndexOf('.'));
        }
        return skins;
    }
}