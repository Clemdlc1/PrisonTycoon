package fr.prisontycoon.tasks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.AutominerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Random;

public class AutominerTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private final AutominerManager autominerManager;
    private final Random random = new Random();

    public AutominerTask(PrisonTycoon plugin, AutominerManager autominerManager) {
        this.plugin = plugin;
        this.autominerManager = autominerManager;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPlayerAutominers(player);
        }
    }

    private void processPlayerAutominers(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!playerData.isAutominersRunning() || playerData.getActiveAutominers().isEmpty()) {
            return;
        }

        // Fuel consumption is handled once per minute in AutominerManager
        // This task handles the mining process every second

        for (String autominerUuid : playerData.getActiveAutominers()) {
            AutominerData autominer = findAutominerDataByUuid(player, autominerUuid);
            if (autominer != null) {
                processSingleAutominer(player, autominer, playerData);
            }
        }
    }

    private void processSingleAutominer(Player player, AutominerData autominer, PlayerData playerData) {
        // Each autominer "breaks" one block per second
        String world = playerData.getAutominerWorld();
        MineData mineData = plugin.getMineManager().getMine("mine-" + world);
        if (mineData == null) {
            return; // Invalid world
        }
        Map<Material, Double> blockComposition = mineData.getBlockComposition();

        if (blockComposition == null || blockComposition.isEmpty()) {
            return; // Invalid world or composition
        }

        Material minedBlock = getRandomMaterial(blockComposition);
        int quantity = 1;

        // Apply Fortune
        if (autominer.getTotalFortune() > 0 && random.nextInt(100) < autominer.getTotalFortune()) {
            quantity += random.nextInt(autominer.getTotalFortune() / 10 + 1) + 1;
        }

        // Add to storage
        if (!playerData.isStorageFull()) {
            playerData.addStoredBlocks(minedBlock, quantity);
        }

        // Apply Greed enchants
        applyGreedEnchants(player, autominer, playerData);
    }

    private void applyGreedEnchants(Player player, AutominerData autominer, PlayerData playerData) {
        // MoneyGreed
        if (autominer.getTotalMoneyBonus() > 0 && random.nextInt(100) < autominer.getTotalMoneyBonus()) {
            long moneyBonus = 10 + random.nextInt(autominer.getTotalMoneyBonus());
            plugin.getEconomyManager().addCoins(player, moneyBonus);
        }

        // TokenGreed
        if (autominer.getTotalTokenBonus() > 0 && random.nextInt(100) < autominer.getTotalTokenBonus()) {
            long tokenBonus = 1 + random.nextInt(autominer.getTotalTokenBonus() / 10 + 1);
            plugin.getEconomyManager().addTokens(player, tokenBonus);
        }

        // ExpGreed
        if (autominer.getTotalExpBonus() > 0 && random.nextInt(100) < autominer.getTotalExpBonus()) {
            int expBonus = 5 + random.nextInt(autominer.getTotalExpBonus());
            player.giveExp(expBonus);
        }

        // KeyGreed
        if (autominer.getEnchantmentLevel("keygreed") > 0 && random.nextInt(1000) < autominer.getEnchantmentLevel("keygreed")) {
            // Generate a key
            ItemStack key = createKey("Commune"); // Example, can be improved
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(key);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), key);
            }
        }
    }

    private AutominerData findAutominerDataByUuid(Player player, String uuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                AutominerData data = AutominerData.fromItemStack(item, autominerManager.getUuidKey(), autominerManager.getTypeKey(), autominerManager.getEnchantKey(), autominerManager.getCristalKey());
                if (data != null && data.getUuid().equals(uuid)) {
                    return data;
                }
            }
        }
        return null; // Autominer not found in inventory
    }

    private Material getRandomMaterial(Map<Material, Double> blockComposition) {
        double totalWeight = 0.0;
        for (double weight : blockComposition.values()) {
            totalWeight += weight;
        }

        double randomValue = random.nextDouble() * totalWeight;
        for (Map.Entry<Material, Double> entry : blockComposition.entrySet()) {
            randomValue -= entry.getValue();
            if (randomValue <= 0.0) {
                return entry.getKey();
            }
        }

        return Material.STONE; // Fallback
    }

    public ItemStack createKey(String keyType) {
        String keyColor;

        // Détermine la couleur en fonction du type de clé
        switch (keyType) {
            case "Cristal":
                keyColor = "§d";
                break;
            case "Légendaire":
                keyColor = "§6";
                break;
            case "Rare":
                keyColor = "§5";
                break;
            case "Peu Commune":
                keyColor = "§9";
                break;
            default: // "Commune" et tout autre cas
                keyColor = "§f";
                break;
        }

        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta();

        meta.setDisplayName(keyColor + "Clé " + keyType);
        meta.setLore(java.util.Arrays.asList(
                "§7Clé de coffre " + keyColor + keyType,
                "§7Utilise cette clé pour ouvrir des coffres!"
        ));

        key.setItemMeta(meta);
        return key;
    }
}
