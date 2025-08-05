package fr.prisontycoon.utils; // ou le package de votre choix

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.function.mask.BlockMask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Gère le remplacement unique de la bedrock dans une zone définie au démarrage du serveur.
 * Utilise FAWE pour des performances optimales.
 */
public class StartupBedrockReplacer {

    private final PrisonTycoon plugin;

    public StartupBedrockReplacer(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Exécute le remplacement de la bedrock de manière asynchrone.
     */
    public void executeReplacement() {
        World caveWorld = Bukkit.getWorld("Cave");

        plugin.getPluginLogger().info("Lancement du remplacement de la bedrock dans la zone définie du monde 'Cave'...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Material replacementMaterial = Material.BEACON;

                int x1 = 63;
                int y1 = 31;
                int z1 = -48;
                int x2 = -95;
                int y2 = -35;
                int z2 = 160;

                com.sk89q.worldedit.world.World faweWorld = BukkitAdapter.adapt(caveWorld);

                BlockVector3 pos1 = BlockVector3.at(x1, y1, z1);
                BlockVector3 pos2 = BlockVector3.at(x2, y2, z2);
                CuboidRegion region = new CuboidRegion(faweWorld, pos1, pos2);

                BlockState replacementState = BukkitAdapter.adapt(replacementMaterial.createBlockData());

                try (EditSession editSession = WorldEdit.getInstance().newEditSession(faweWorld)) {
                    // Masque pour ne remplacer QUE la bedrock
                    BlockMask mask = new BlockMask(editSession);
                    mask.add(Objects.requireNonNull(BlockTypes.BEDROCK).getDefaultState());

                    int blocksAffected = editSession.replaceBlocks(region, mask, replacementState);
                    plugin.getLogger().info("Remplacement de la bedrock terminé. " + blocksAffected + " blocs ont été modifiés.");
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Une erreur majeure est survenue lors du remplacement de la bedrock avec FAWE.", e);
            }
        });
    }
}