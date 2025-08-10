package fr.prisontycoon.enchantments;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.BlockValueData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire des enchantements custom
 * MODIFIÉ : KeyGreed déplacé dans SPECIAL, ajout de Jackhammer et SellGreed
 */
public class EnchantmentManager {

    private final PrisonTycoon plugin;
    private final Map<String, CustomEnchantment> enchantments;

    public EnchantmentManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantments = new HashMap<>();

        initializeEnchantments();
        plugin.getPluginLogger().info("§aEnchantmentManager initialisé avec " + enchantments.size() + " enchantements.");
    }

    /**
     * Initialise tous les enchantements custom
     */
    private void initializeEnchantments() {
        // CATÉGORIE ÉCONOMIQUE
        registerEnchantment(new TokenGreedEnchantment());
        registerEnchantment(new ExpGreedEnchantment());
        registerEnchantment(new MoneyGreedEnchantment());
        registerEnchantment(new AbondanceEnchantment());
        registerEnchantment(new CombustionEnchantment());
        registerEnchantment(new PetXpEnchantment());
        registerEnchantment(new SellGreedEnchantment());

        // CATÉGORIE UTILITÉS
        registerEnchantment(new EfficiencyEnchantment());
        registerEnchantment(new FortuneEnchantment());
        registerEnchantment(new DurabilityEnchantment());
        registerEnchantment(new JackpotEnchantment());
        registerEnchantment(new CohesionEnchantment());

        // CATÉGORIE MOBILITÉ
        registerEnchantment(new NightVisionEnchantment());
        registerEnchantment(new SpeedEnchantment());
        registerEnchantment(new HasteEnchantment());
        registerEnchantment(new JumpBoostEnchantment());
        registerEnchantment(new EscalatorEnchantment());
        registerEnchantment(new PlanneurEnchantment());

        // CATÉGORIE SPÉCIAUX
        registerEnchantment(new LuckEnchantment());
        registerEnchantment(new LaserEnchantment());
        registerEnchantment(new ExplosionEnchantment());
        registerEnchantment(new KeyGreedEnchantment());
        registerEnchantment(new JackhammerEnchantment());
        registerEnchantment(new HeritageEnchantment());
        registerEnchantment(new OpportunityFeverEnchantment());
    }

    /**
     * Enregistre un enchantement
     */
    private void registerEnchantment(CustomEnchantment enchantment) {
        enchantments.put(enchantment.getName(), enchantment);
    }

    /**
     * MODIFIÉ : Traite un bloc miné dans une mine avec tous les enchantements
     */
    public void processBlockMined(Player player, Location blockLocation, Material blockType, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute aux statistiques de minage
        playerData.addMinedBlock(blockType);

        // NOUVEAU : Jackpot — chance d'obtenir un voucher aléatoire en minant
        int jackpotLevel = playerData.getEnchantmentLevel("jackpot");
        if (jackpotLevel > 0) {
            double jChance = plugin.getConfigManager().getEnchantmentSetting("special.jackpot.base-chance", 0.0002) * jackpotLevel;
            if (ThreadLocalRandom.current().nextDouble() < jChance) {
                // Choix du type et du tier
                VoucherType[] types = VoucherType.values();
                VoucherType chosen = types[ThreadLocalRandom.current().nextInt(types.length)];
                int tier = Math.min(10, Math.max(1, 1 + jackpotLevel / 10));
                var item = plugin.getVoucherManager().createVoucher(chosen, tier);
                boolean added = plugin.getContainerManager().addItemToContainers(player, item);
                if (!added) {
                    player.getInventory().addItem(item);
                }
                player.sendMessage("§6🎁 Jackpot! §eVous recevez un " + chosen.getItemName() + " §7Tier §6" + tier);
            }
        }

        // NOUVEAU : Fièvre de l'Opportunité — si active et bloc ciblé, forcer un greed
        if (player.hasMetadata("opportunity_fever_until")) {
            long until = player.getMetadata("opportunity_fever_until").getFirst().asLong();
            if (System.currentTimeMillis() > until) {
                player.removeMetadata("opportunity_fever_until", plugin);
                player.removeMetadata("opportunity_fever_block", plugin);
            } else {
                String targetName = player.hasMetadata("opportunity_fever_block") ? player.getMetadata("opportunity_fever_block").getFirst().asString() : null;
                if (blockType.name().equalsIgnoreCase(targetName)) {
                    // Force un greed aléatoire (hors keygreed)
                    int r = ThreadLocalRandom.current().nextInt(3);
                    int lvl = switch (r) {
                        case 0 -> playerData.getEnchantmentLevel("token_greed");
                        case 1 -> playerData.getEnchantmentLevel("money_greed");
                        default -> playerData.getEnchantmentLevel("exp_greed");
                    };
                    if (lvl > 0) {
                        // Chance garantie => on appelle directement les traitements avec probas=1
                        final double cm = 1.0, am = 1.0;
                        BlockValueData bv = plugin.getConfigManager().getBlockValue(blockType);
                        if (r == 0) {
                            processTokenGreed(player, playerData, bv, lvl, 1.0, cm, am, false);
                        } else if (r == 1) {
                            processMoneyGreed(player, playerData, bv, lvl, 1.0, cm, am);
                        } else {
                            processExpGreed(player, playerData, bv, lvl, 1.0, cm, am);
                        }
                    }
                }
            }
        }

        processGreedEnchantments(player, playerData, blockType);

        if (isPlayerPickaxeBroken(player)) {
            addBlocksToInventory(player, blockType, 1, blockLocation);
            return;
        }
        // Applique Fortune
        int fortuneBlocks = calculateFortuneBlocks(player, playerData, blockType);
        int blocksToGive = fortuneBlocks + 1;
        addBlocksToInventory(player, blockType, blocksToGive, blockLocation);


        // Met à jour la combustion
        updateCombustion(player, playerData);

        // Traite les enchantements spéciaux (laser, explosion, jackhammer)
        processSpecialEnchantments(player, playerData, blockLocation, mineName);
        plugin.getEnchantmentBookManager().processMiningEnchantments(player, blockLocation);
        processKeyGreed(player, playerData);
        processAbondance(player, playerData);

        String activeProfession = playerData.getActiveProfession();
        if (activeProfession != null && activeProfession.equals("mineur")) {
            if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) == 0) {
                plugin.getProfessionManager().addProfessionXP(player, "mineur", 1);
            }
        }

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * MODIFIÉ : Traite les enchantements spéciaux (laser, explosion, jackhammer)
     */
    private void processSpecialEnchantments(Player player, PlayerData playerData, Location blockLocation, String mineName) {
        int laserLevel = playerData.getEnchantmentLevel("laser");
        int explosionLevel = playerData.getEnchantmentLevel("explosion");
        int jackhammerLevel = playerData.getEnchantmentLevel("jackhammer"); // CORRIGÉ : était "explosion"
        int feverLevel = playerData.getEnchantmentLevel("opportunity_fever");

        // Laser
        if (laserLevel > 0) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("special.laser.base-chance", 0.001) * laserLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateLaser(player, blockLocation, mineName, false);
            }
        }

        // Explosion
        if (explosionLevel > 0) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("special.explosion.base-chance", 0.0005) * explosionLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateExplosion(player, blockLocation, mineName);
            }
        }

        // NOUVEAU : Jackhammer
        if (jackhammerLevel > 0) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("special.jackhammer.base-chance", 0.0005) * jackhammerLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                activateJackhammer(player, blockLocation, mineName, false);
            }
        }

        // NOUVEAU : Fièvre de l'Opportunité — fenêtre où un type de bloc déclenche systématiquement un greed
        if (feverLevel > 0 && !player.hasMetadata("opportunity_fever_until")) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("special.opportunity_fever.base-chance", 0.0005) * feverLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                long until = System.currentTimeMillis() + 10_000L; // 10s
                player.setMetadata("opportunity_fever_until", new FixedMetadataValue(plugin, until));
                // Choisit le bloc ciblé (sauf BEACON)
                Material target = blockLocation.getBlock().getType();
                if (target == Material.BEACON) {
                    target = Material.STONE;
                }
                player.setMetadata("opportunity_fever_block", new FixedMetadataValue(plugin, target.name()));
                player.sendMessage("§6🔥 Fièvre de l'Opportunité! §e10s de greeds garantis sur §f" + target.name());
            }
        }
    }

    /**
     * NOUVEAU : Active l'effet Jackhammer (casse une couche)
     */
    private int activateJackhammer(Player player, Location center, String mineName, boolean isEcho) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return 0;
        var world = center.getWorld();
        if (world == null || !world.getName().equals(mineData.getWorldName())) return 0;

        // Choisir aléatoirement entre couche horizontale ou verticale
        boolean isHorizontal = ThreadLocalRandom.current().nextBoolean();

        int blocksDestroyed = 0;

        // Prépare une Location réutilisable pour éviter des allocations massives
        Location loc = new Location(world, 0, 0, 0);

        if (isHorizontal) {
            // Couche horizontale (même Y) - utilise directement les bornes de la mine
            final int y = Math.max(mineData.getMinY(), Math.min(center.getBlockY(), mineData.getMaxY()));
            final int minX = mineData.getMinX();
            final int maxX = mineData.getMaxX();
            final int minZ = mineData.getMinZ();
            final int maxZ = mineData.getMaxZ();

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var block = world.getBlockAt(x, y, z);
                    var crumbleData = block.getBlockData();
                    Material blockType = block.getType();
                    if (blockType != Material.AIR && blockType != Material.BEDROCK && blockType != Material.BEACON) {
                        block.setType(Material.AIR, false);
                        blocksDestroyed++;

                        // Localisation integer pour la suite des traitements
                        loc.setX(x);
                        loc.setY(y);
                        loc.setZ(z);
                        processBlockDestroyed(player, loc, blockType, mineName);

                        // Effets visuels: réutilise le BlockData existant
                        world.spawnParticle(Particle.BLOCK_CRUMBLE, x + 0.5, y + 0.5, z + 0.5,
                                3, 0.25, 0.25, 0.25, crumbleData);
                    }
                }
            }
        } else {
            // Couche verticale (même X ou Z, choisi aléatoirement)
            boolean sameX = ThreadLocalRandom.current().nextBoolean();

            if (sameX) {
                final int x = Math.max(mineData.getMinX(), Math.min(center.getBlockX(), mineData.getMaxX()));
                final int minY = mineData.getMinY();
                final int maxY = mineData.getMaxY();
                final int minZ = mineData.getMinZ();
                final int maxZ = mineData.getMaxZ();

                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        var block = world.getBlockAt(x, y, z);
                        var crumbleData = block.getBlockData();
                        Material blockType = block.getType();
                        if (blockType != Material.AIR && blockType != Material.BEDROCK && blockType != Material.BEACON) {
                            block.setType(Material.AIR, false);
                            blocksDestroyed++;

                            loc.setX(x);
                            loc.setY(y);
                            loc.setZ(z);
                            processBlockDestroyed(player, loc, blockType, mineName);

                            world.spawnParticle(Particle.BLOCK_CRUMBLE, x + 0.5, y + 0.5, z + 0.5,
                                    3, 0.25, 0.25, 0.25, crumbleData);
                        }
                    }
                }
            } else {
                final int z = Math.max(mineData.getMinZ(), Math.min(center.getBlockZ(), mineData.getMaxZ()));
                final int minX = mineData.getMinX();
                final int maxX = mineData.getMaxX();
                final int minY = mineData.getMinY();
                final int maxY = mineData.getMaxY();

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        var block = world.getBlockAt(x, y, z);
                        var crumbleData = block.getBlockData();
                        Material blockType = block.getType();
                        if (blockType != Material.AIR && blockType != Material.BEDROCK && blockType != Material.BEACON) {
                            block.setType(Material.AIR, false);
                            blocksDestroyed++;

                            loc.setX(x);
                            loc.setY(y);
                            loc.setZ(z);
                            processBlockDestroyed(player, loc, blockType, mineName);

                            world.spawnParticle(Particle.BLOCK_CRUMBLE, x + 0.5, y + 0.5, z + 0.5,
                                    3, 0.25, 0.25, 0.25, crumbleData);
                        }
                    }
                }
            }
        }

        // Son et effet pour l'activation (sans muter 'center')
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        final double cx = center.getX();
        final double cy = center.getY();
        final double cz = center.getZ();
        world.spawnParticle(Particle.EXPLOSION, cx + 0.5, cy + 0.5, cz + 0.5, 3, 2, 2, 2);

        // Gestion des échos seulement si ce n'est pas déjà un écho
        if (!isEcho) {
            // On appelle directement getEchoCount. Un seul appel, une seule vérification.
            int echoCount = plugin.getCristalBonusHelper().getEchoCount(player);

            if (echoCount > 0) {
                int echoBlocks = triggerEchos(player, center, echoCount, mineName, "jackhammer");
                player.sendMessage("§6⚡ §lJACKHAMMER §6avec " + echoCount + " écho(s) ! " +
                        "§e" + (blocksDestroyed + echoBlocks) + " blocs détruits au total !");
            } else {
                // Ce message s'affichera si getEchoCount a renvoyé 0
                player.sendMessage("§6⚡ §lJACKHAMMER §6activé ! §e" + blocksDestroyed + " blocs détruits !");
            }
        }
        return blocksDestroyed;
    }

    /**
     * NOUVEAU & CORRIGÉ : Déclenche les échos pour Laser/Explosion/Jackhammer et retourne le nombre total
     * de blocs détruits par ces échos. (Version exacte utilisateur)
     */
    private int triggerEchos(Player player, Location origin, int echoCount, String mineName, String enchantmentType) {
        int totalEchoBlocks = 0;
        for (int i = 0; i < echoCount; i++) {
            // Calculer direction aléatoire
            Vector randomDirection = new Vector(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 2
            ).normalize();

            Location echoLocation = origin.clone().add(randomDirection.multiply(3 + i));

            // Déclencher à nouveau l'enchantement à cette position (en mode écho)
            if ("laser".equalsIgnoreCase(enchantmentType)) {
                totalEchoBlocks += activateLaser(player, echoLocation, mineName, true);
            }

            if ("jackhammer".equalsIgnoreCase(enchantmentType)) {
                totalEchoBlocks += activateJackhammer(player, echoLocation, mineName, true);
            }
        }
        return totalEchoBlocks;
    }

    /**
     * Active l'effet laser
     */
    private int activateLaser(Player player, Location start, String mineName, boolean isEcho) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return 0;

        Vector direction = player.getLocation().getDirection();
        Location current = start.clone();
        int blocksDestroyed = 0;

        for (int i = 0; i < 20; i++) {
            current.add(direction);

            if (mineData.contains(current)) {
                var block = current.getBlock();
                var crumbleData = block.getBlockData();
                Material blockType = block.getType();
                if (blockType != Material.AIR && blockType != Material.BEDROCK && blockType != Material.BEACON) {
                    block.setType(Material.AIR, false);
                    blocksDestroyed++;
                    processBlockDestroyed(player, current, blockType, mineName);

                    // Effets visuels
                    current.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5, 2, 0.2, 0.2, 0.2, crumbleData);
                }
            } else {
                break;
            }
        }

        if (!isEcho) {
            int echoCount = plugin.getCristalBonusHelper().getEchoCount(player);

            if (echoCount > 0) {
                int echoBlocks = triggerEchos(player, start, echoCount, mineName, "laser");
                player.sendMessage("§c⚡ §lLASER §cavec " + echoCount + " écho(s) ! " +
                        "§e" + (blocksDestroyed + echoBlocks) + " blocs détruits au total !");
            } else {
                player.sendMessage("§c⚡ §lLASER §cactivé ! §e" + blocksDestroyed + " blocs détruits !");
            }
        }
        return blocksDestroyed;
    }

    /**
     * Active l'effet explosion
     */
    private void activateExplosion(Player player, Location center, String mineName) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) return;

        int blocksDestroyed = 0;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Location loc = center.clone().add(x, y, z);

                    if (mineData.contains(loc)) {
                        var block = loc.getBlock();
                        var crumbleData = block.getBlockData();
                        Material blockType = block.getType();
                        if (blockType != Material.AIR && blockType != Material.BEDROCK && blockType != Material.BEACON) {
                            block.setType(Material.AIR, false);
                            blocksDestroyed++;
                            processBlockDestroyed(player, loc, blockType, mineName);
                            center.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, 2, 0.2, 0.2, 0.2, crumbleData);
                        }
                    }
                }
            }
        }

        // Effets visuels et sonores
        center.getWorld().spawnParticle(Particle.EXPLOSION, center.add(0.5, 0.5, 0.5), 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

        player.sendMessage("§4💥 §lEXPLOSION §4déclenchée ! §e" + blocksDestroyed + " blocs détruits !");
    }

    /**
     * Retourne tous les enchantements
     */
    public Collection<CustomEnchantment> getAllEnchantments() {
        return enchantments.values();
    }

    /**
     * Retourne les enchantements par catégorie
     */
    public List<CustomEnchantment> getEnchantmentsByCategory(EnchantmentCategory category) {
        return enchantments.values().stream()
                .filter(ench -> ench.getCategory() == category)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Retourne un enchantement par nom
     */
    public CustomEnchantment getEnchantment(String name) {
        return enchantments.get(name);
    }

    /**
     * Vérifie si l'auto-upgrade est disponible
     */
    public boolean canUseAutoUpgrade(Player player) {
        return plugin.getVipManager().isVip(player.getUniqueId());
    }

    /**
     * Calcule les blocs bonus de Fortune
     */
    public int calculateFortuneBlocks(Player player, PlayerData playerData, Material blockType) {
        if (isPlayerPickaxeBroken(player)) {
            return 1; // Retourne 0 bloc bonus
        }

        int fortuneLevel = playerData.getEnchantmentLevel("fortune");

        // Pour chaque 100 niveaux, on gagne 1 bloc bonus garanti.
        int guaranteedBonus = fortuneLevel / 100;

        // Le reste (ex: niveau 250 -> 50) devient une probabilité d'obtenir un bloc supplémentaire.
        double chanceForExtra = (fortuneLevel % 100) / 100.0;

        int extraBlocks = 0;
        if (ThreadLocalRandom.current().nextDouble() < chanceForExtra) {
            extraBlocks = 1;
        }

        // Le total retourné correspond uniquement aux blocs BONUS.
        int baseBonusBlocks = guaranteedBonus + extraBlocks;

        // MODIFIÉ: Applique le bonus du cristal MineralGreed
        int finalBonusBlocks = plugin.getGlobalBonusManager().applyFortuneBonus(player, baseBonusBlocks);

        plugin.getPluginLogger().debug("Fortune " + fortuneLevel + " pour " + blockType.name() +
                ": " + finalBonusBlocks + " blocs bonus (" + baseBonusBlocks + " base + cristal)");

        return 1 + finalBonusBlocks;
    }

    /**
     * Vérifie si la pioche du joueur est cassée
     */
    public boolean isPlayerPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * MODIFIÉ : Ajoute plusieurs blocs à l'inventaire avec priorité aux conteneurs
     */
    private void addBlocksToInventory(Player player, Material material, int quantity, Location blockLocation) {
        if (quantity <= 0) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int actuallyAdded = 0;
        int remaining = quantity;

        if (plugin.getEnchantmentBookManager().isEnchantmentActive(player, "autosell")) {
            plugin.getEnchantmentBookManager().processAutoSell(player, material, quantity);
            String activeProfession = playerData.getActiveProfession();
            if ("commercant".equals(activeProfession)) {
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(1000) == 0) {
                    plugin.getProfessionManager().addProfessionXP(player, "commercant", 1);
                }
            }
            return;
        }

        // Gestion spéciale pour PlusValue (résolution paresseuse + cache mineName par joueur)
        if (!isPlayerPickaxeBroken(player) && plugin.getEnchantmentBookManager().isEnchantmentActive(player, "plusvalue")) {
            final long now = System.currentTimeMillis();
            final String cacheKeyName = "pt_cached_mine_name";
            final String cacheKeyTime = "pt_cached_mine_time";

            String mineName = null;
            Long lastTs = null;
            if (player.hasMetadata(cacheKeyName) && player.hasMetadata(cacheKeyTime)) {
                mineName = player.getMetadata(cacheKeyName).getFirst().asString();
                lastTs = player.getMetadata(cacheKeyTime).getFirst().asLong();
            }

            if (mineName == null || now - lastTs > 1500L) {
                mineName = plugin.getConfigManager().getPlayerMine(blockLocation);
                player.setMetadata(cacheKeyName, new FixedMetadataValue(plugin, mineName == null ? "" : mineName));
                player.setMetadata(cacheKeyTime, new FixedMetadataValue(plugin, now));
            }

            if (mineName != null && !mineName.isEmpty()) {
                material = plugin.getEnchantmentBookManager().getHighestValueBlockInMine(mineName);
            }
        }
        final Material effectiveMaterial = material;

        plugin.getPluginLogger().debugLazy(() -> "Tentative d'ajout de " + quantity + "x " + effectiveMaterial.name() + " pour " + player.getName());

        // Ajout prioritaire en conteneurs via un seul batch
        final ItemStack batch = new ItemStack(effectiveMaterial, remaining);
        final int added = plugin.getContainerManager().addItemsBatchToContainers(player, batch);
        if (added > 0) {
            actuallyAdded += added;
            remaining -= added;
            plugin.getPluginLogger().debugLazy(() -> added + "x " + effectiveMaterial.name() + " ajouté(s) au(x) conteneur(s)");
        }

        // Si il reste des items, tente de les ajouter à l'inventaire normal
        if (remaining > 0) {
            // Ajout à l’inventaire en un seul appel
            final ItemStack remainingStack = new ItemStack(effectiveMaterial, remaining);
            final Map<Integer, ItemStack> leftover = player.getInventory().addItem(remainingStack);

            if (leftover.isEmpty()) {
                actuallyAdded += remaining;
                final int finalRemaining = remaining;
                plugin.getPluginLogger().debugLazy(() -> finalRemaining + "x " + effectiveMaterial.name() + " ajoutés à l'inventaire normal");
            } else {
                // Calcul du nombre effectivement stocké
                int droppedCount = 0;
                for (ItemStack overflow : leftover.values()) {
                    droppedCount += overflow.getAmount();
                }
                int addedToInventory = Math.max(0, remaining - droppedCount);
                actuallyAdded += addedToInventory;

                // Message d’avertissement toutes les 30s max
                long now = System.currentTimeMillis();
                boolean shouldWarn = true;
                if (player.hasMetadata("inventory_full_warning")) {
                    long last = player.getMetadata("inventory_full_warning").getFirst().asLong();
                    shouldWarn = (now - last) > 30_000L;
                }
                if (shouldWarn) {
                    player.setMetadata("inventory_full_warning", new FixedMetadataValue(plugin, now));
                    player.sendMessage("§c⚠️ Inventaire et conteneurs pleins! " + droppedCount + " items droppés au sol.");
                    player.sendMessage("§e💡 Utilisez §a/sell all §epour vider vos conteneurs et inventaire!");
                }
            }
        }

        // NOUVEAU : Track les blocs ajoutés à l'inventaire/conteneurs
        if (actuallyAdded > 0) {
            playerData.addBlocksToInventory(actuallyAdded);
        }

        final int finalActuallyAdded = actuallyAdded;
        plugin.getPluginLogger().debugLazy(() -> "Blocs ajoutés au total: " + finalActuallyAdded + "/" + quantity + "x " + effectiveMaterial.name() +
                " (conteneurs + inventaire + droppés)");
    }

    /**
     * Traite les enchantements Greed
     */
    private void processGreedEnchantments(Player player, PlayerData playerData, Material blockType) {
        // Pré-calculs partagés
        final boolean pickaxeBroken = isPlayerPickaxeBroken(player);
        final int luckLevel = playerData.getEnchantmentLevel("luck");
        final int tokenGreedLevel = playerData.getEnchantmentLevel("token_greed");
        final int moneyGreedLevel = playerData.getEnchantmentLevel("money_greed");
        final int expGreedLevel = playerData.getEnchantmentLevel("exp_greed");

        if (tokenGreedLevel <= 0 && moneyGreedLevel <= 0 && expGreedLevel <= 0) {
            return; // aucun greed actif
        }

        final double baseCombustionMultiplier = playerData.getCombustionMultiplier();
        final double combustionMultiplier = plugin.getCristalBonusHelper().applyCombustionEfficiency(player, baseCombustionMultiplier);
        final double abundanceMultiplier = playerData.isAbundanceActive() ? 2.0 : 1.0;
        final BlockValueData blockValue = plugin.getConfigManager().getBlockValue(blockType);

        // Chances partagées
        final double baseChance = plugin.getConfigManager().getEnchantmentSetting("greed.base-chance", 0.05);
        final double luckBonus = luckLevel * plugin.getConfigManager().getEnchantmentSetting("greed.luck-bonus-per-level", 0.002);
        final double totalChance = baseChance + luckBonus;

        // NOUVEAU : Cohésion — multiplicateur de greed selon joueurs dans la mine
        int cohesionLevel = playerData.getEnchantmentLevel("cohesion");
        double cohesionMultiplier = 1.0;
        if (cohesionLevel > 0) {
            String mineId = plugin.getMineManager().getPlayerCurrentMine(player);
            if (mineId != null) {
                int active = plugin.getMineOverloadManager().getActiveMinersCount(mineId);
                // 1% par niveau par joueur actif, borné à +100%
                cohesionMultiplier += Math.min(1.0, active * (cohesionLevel * 0.01));
            }
        }

        // Si la pioche est cassée: seul TokenGreed peut s'appliquer (comportement existant)
        if (pickaxeBroken) {
            if (tokenGreedLevel > 0) {
                processTokenGreed(player, playerData, blockValue, tokenGreedLevel, totalChance, combustionMultiplier * cohesionMultiplier, abundanceMultiplier, true);
            }
            return;
        }

        if (tokenGreedLevel > 0) {
            processTokenGreed(player, playerData, blockValue, tokenGreedLevel, totalChance, combustionMultiplier * cohesionMultiplier, abundanceMultiplier, false);
        }
        if (moneyGreedLevel > 0) {
            processMoneyGreed(player, playerData, blockValue, moneyGreedLevel, totalChance, combustionMultiplier * cohesionMultiplier, abundanceMultiplier);
        }
        if (expGreedLevel > 0) {
            processExpGreed(player, playerData, blockValue, expGreedLevel, totalChance, combustionMultiplier * cohesionMultiplier, abundanceMultiplier);
        }
    }

    /**
     * Traite Token Greed
     */
    private void processTokenGreed(Player player, PlayerData playerData, BlockValueData blockValue,
                                   int tokenGreedLevel, double totalChance,
                                   double combustionMultiplier, double abundanceMultiplier,
                                   boolean pickaxeBroken) {
        if (ThreadLocalRandom.current().nextDouble() < totalChance) {
            long blockTokens = blockValue.tokens();
            long baseGains = Math.round((tokenGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.token-multiplier", 5) + blockTokens) * combustionMultiplier * abundanceMultiplier);

            long finalGains = plugin.getGlobalBonusManager().applyTokenBonus(player, baseGains);
            if (pickaxeBroken) {
                finalGains = (long) (finalGains * 0.05);
            }
            playerData.addTokensViaPickaxe(finalGains);
            playerData.addGreedTrigger();

            // Héritage: propage avec chance aux autres joueurs de la mine (sans boucler)
            handleHeritagePropagation(player, blockValue, combustionMultiplier, abundanceMultiplier, "token");
        }
    }

    /**
     * Traite Money Greed
     */
    private void processMoneyGreed(Player player, PlayerData playerData, BlockValueData blockValue,
                                   int moneyGreedLevel, double totalChance,
                                   double combustionMultiplier, double abundanceMultiplier) {
        if (ThreadLocalRandom.current().nextDouble() < totalChance) {
            long blockCoins = blockValue.coins();
            long baseGains = Math.round((moneyGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.money-multiplier", 10) + blockCoins * 2) * combustionMultiplier * abundanceMultiplier);

            long finalGains = plugin.getGlobalBonusManager().applyMoneyBonus(player, baseGains);
            playerData.addCoins(finalGains);
            playerData.addGreedTrigger();

            handleHeritagePropagation(player, blockValue, combustionMultiplier, abundanceMultiplier, "money");
        }
    }

    /**
     * Traite Exp Greed
     */
    private void processExpGreed(Player player, PlayerData playerData, BlockValueData blockValue,
                                 int expGreedLevel, double totalChance,
                                 double combustionMultiplier, double abundanceMultiplier) {
        if (ThreadLocalRandom.current().nextDouble() < totalChance) {
            long blockExp = blockValue.experience();
            long baseGains = Math.round((expGreedLevel * plugin.getConfigManager().getEnchantmentSetting("greed.exp-multiplier", 50) + blockExp * 3) * combustionMultiplier * abundanceMultiplier);
            long finalGains = plugin.getGlobalBonusManager().applyExperienceBonus(player, baseGains);
            playerData.addExperienceViaPickaxe(finalGains);
            playerData.addGreedTrigger();

            plugin.getEconomyManager().updateVanillaExpFromCustom(player, playerData.getExperience());

            handleHeritagePropagation(player, blockValue, combustionMultiplier, abundanceMultiplier, "exp");
        }
    }

    /**
     * Propage un greed à d'autres joueurs de la même mine selon l'enchantement Héritage.
     * Le greed hérité est marqué pour ne pas re-propager (évite les boucles).
     */
    private void handleHeritagePropagation(Player source, BlockValueData blockValue,
                                           double combustionMultiplier, double abundanceMultiplier,
                                           String greedType) {
        // Ne propage pas si l'effet vient déjà d'un héritage
        if (source.hasMetadata("heritage_copying")) {
            source.removeMetadata("heritage_copying", plugin);
            return;
        }

        String mineId = plugin.getMineManager().getPlayerCurrentMine(source);
        if (mineId == null) return;

        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.equals(source)) continue;
            String targetMine = plugin.getMineManager().getPlayerCurrentMine(target);
            if (!mineId.equals(targetMine)) continue;

            PlayerData td = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
            int heritageLevel = td.getEnchantmentLevel("heritage");
            if (heritageLevel <= 0) continue;

            double hChance = plugin.getConfigManager().getEnchantmentSetting("special.heritage.base-chance", 0.0005) * heritageLevel;
            if (ThreadLocalRandom.current().nextDouble() < hChance) {
                target.setMetadata("heritage_copying", new FixedMetadataValue(plugin, true));
                switch (greedType) {
                    case "token" -> processTokenGreed(target, td, blockValue, td.getEnchantmentLevel("token_greed"), 1.0, combustionMultiplier, abundanceMultiplier, false);
                    case "money" -> processMoneyGreed(target, td, blockValue, td.getEnchantmentLevel("money_greed"), 1.0, combustionMultiplier, abundanceMultiplier);
                    case "exp" -> processExpGreed(target, td, blockValue, td.getEnchantmentLevel("exp_greed"), 1.0, combustionMultiplier, abundanceMultiplier);
                }
                // Retire le flag après 1 tick
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> target.removeMetadata("heritage_copying", plugin), 1L);
            }
        }
    }

    /**
     * Traite Key Greed (maintenant dans SPECIAL)
     */
    private void processKeyGreed(Player player, PlayerData playerData) {
        int keyGreedLevel = playerData.getEnchantmentLevel("key_greed");
        if (keyGreedLevel <= 0) return;
        double chance = plugin.getConfigManager().getEnchantmentSetting("keys.base-chance", 0.0001) * keyGreedLevel;

        // On stocke temporairement un flag pour éviter les boucles
        boolean heritageTrigger = false;
        if (!player.hasMetadata("heritage_greed_copy")) {
            int heritageLevel = playerData.getEnchantmentLevel("heritage");
            if (heritageLevel > 0) {
                double hChance = plugin.getConfigManager().getEnchantmentSetting("special.heritage.base-chance", 0.0005) * heritageLevel;
                if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < hChance) {
                    heritageTrigger = true;
                    player.setMetadata("heritage_greed_copy", new FixedMetadataValue(plugin, true));
                }
            }
        }

        if (ThreadLocalRandom.current().nextDouble() < chance) {
            giveRandomKey(player);
            player.sendMessage("§6🗝️ Vous avez trouvé une clé de coffre !");
            playerData.addGreedTrigger();
        }

        if (heritageTrigger) {
            // Retire le flag immédiatement après un tick pour ne pas boucler
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> player.removeMetadata("heritage_greed_copy", plugin), 1L);
        }
    }

    private void processAbondance(Player player, PlayerData playerdata) {
        int abundanceLevel = playerdata.getEnchantmentLevel("abundance");
        if (abundanceLevel > 0 && !playerdata.isAbundanceActive() && !playerdata.isAbundanceOnCooldown()) {
            double chance = plugin.getConfigManager().getEnchantmentSetting("abundance.base-chance", 0.000001) * abundanceLevel;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                // MODIFIÉ: Calcule la durée avec le bonus du cristal
                int duration = plugin.getCristalBonusHelper().getAbondanceDuration(player, 60); // 60s de base

                playerdata.activateAbundance(duration * 1000L);
                player.sendMessage("§6🌟 ABONDANCE ACTIVÉE! §eGains doublés pendant " + duration + " secondes!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

                plugin.getPluginLogger().info("Abondance activée pour " + player.getName() +
                        " (niveau " + abundanceLevel + ", durée " + duration + "s)");
            }
        }
    }

    /**
     * Crée un ItemStack pour une clé d'un type donné.
     * La couleur de la clé est déterminée automatiquement à partir de son type.
     *
     * @param keyType Le type de la clé (ex: "Cristal", "Légendaire").
     * @return L'ItemStack de la clé configurée.
     */
    public ItemStack createKey(String keyType) {
        String keyColor = switch (keyType) {
            case "Cristal" -> "§d";
            case "Légendaire" -> "§6";
            case "Rare" -> "§5";
            case "Peu Commune" -> "§9";
            default -> // "Commune" et tout autre cas
                    "§f";
        };

        // Détermine la couleur en fonction du type de clé

        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta();

        plugin.getGUIManager().applyName(meta,keyColor + "Clé " + keyType);
        meta.setLore(Arrays.asList(
                "§7Clé de coffre " + keyColor + keyType,
                "§7Utilise cette clé pour ouvrir des coffres!"
        ));

        key.setItemMeta(meta);
        return key;
    }

    /**
     * MODIFIÉ : Donne une clé aléatoire au joueur en utilisant les conteneurs en priorité.
     * La méthode détermine le type de clé, la crée via createKey(), puis la donne au joueur.
     */
    private void giveRandomKey(Player player) {
        double rand = ThreadLocalRandom.current().nextDouble();
        String keyType;

        // CORRIGÉ : Création manuelle du Map au lieu d'utiliser getEnchantmentSetting avec Map
        Map<String, Double> keyProbabilities = new HashMap<>();
        keyProbabilities.put("cristal", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.cristal", 0.00005));
        keyProbabilities.put("legendaire", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.legendaire", 0.00995));
        keyProbabilities.put("rare", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.rare", 0.09));
        keyProbabilities.put("peu-commune", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.peu-commune", 0.20));
        keyProbabilities.put("commune", plugin.getConfigManager().getEnchantmentSetting("keys.probabilities.commune", 0.70));

        // Détermine le type de la clé à créer
        if (rand < keyProbabilities.get("cristal")) {
            keyType = "Cristal";
        } else if (rand < keyProbabilities.get("legendaire")) {
            keyType = "Légendaire";
        } else if (rand < keyProbabilities.get("rare")) {
            keyType = "Rare";
        } else if (rand < keyProbabilities.get("peu-commune")) {
            keyType = "Peu Commune";
        } else {
            keyType = "Commune";
        }

        ItemStack key = createKey(keyType);

        boolean addedToContainer = plugin.getContainerManager().addItemToContainers(player, key);

        if (addedToContainer) {
            // Le nom de l'item contient déjà la couleur et le type
            player.sendMessage("§e🗝️ " + key.getItemMeta().getDisplayName() + " §eajoutée à vos conteneurs!");
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.5f);
            plugin.getPluginLogger().debug("Clé " + keyType + " ajoutée au conteneur de " + player.getName());
        } else {
            // Pas de conteneur disponible, essaie l'inventaire normal
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(key);
                player.sendMessage("§e🗝️ " + key.getItemMeta().getDisplayName() + " §eobtenue!");
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.5f);
            } else {
                // Inventaire aussi plein, drop au sol
                player.getWorld().dropItemNaturally(player.getLocation(), key);
                player.sendMessage("§e🗝️ " + key.getItemMeta().getDisplayName() + " §edroppée au sol (inventaire et conteneurs pleins)!");
            }
        }
    }

    /**
     * Met à jour la combustion
     */
    private void updateCombustion(Player player, PlayerData playerData) {
        if (isPlayerPickaxeBroken(player)) {
            return;
        }

        int combustionLevel = playerData.getEnchantmentLevel("combustion");
        if (combustionLevel > 0) {
            int gainPerBlock = Math.max(1, combustionLevel / 10);
            playerData.updateCombustion(gainPerBlock);
        }
    }

    /**
     * MODIFIÉ : Traite un bloc CASSÉ par laser/explosion et applique Fortune
     */
    public void processBlockDestroyed(Player player, Location blockLocation, Material blockType, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute aux statistiques de destruction (CASSÉ)
        playerData.addDestroyedBlocks(1);

        // NOUVEAU: Applique Fortune sur les blocs cassés
        int blocksfortune = calculateFortuneBlocks(player, playerData, blockType);
        int blocksToGive = blocksfortune + 1;
        addBlocksToInventory(player, blockType, blocksToGive, blockLocation);

        // LIMITATION : Seuls Money/Token Greed s'appliquent sur les gains de base
        processGreedEnchantments(player, playerData, blockType);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * NOUVEAU : Désactive de force l'abondance et reset la combustion quand la pioche est cassée
     */
    public void forceDisableAbundanceAndResetCombustion(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        boolean changed = false;

        // Désactive l'abondance si elle est active
        if (playerData.isAbundanceActive()) {
            playerData.deactivateAbundance(); // Suppose que cette méthode existe dans PlayerData
            changed = true;

            plugin.getPluginLogger().info("Abondance forcément désactivée pour " + player.getName() + " (pioche cassée)");
        }

        // Reset la combustion si elle est active
        if (playerData.getCombustionLevel() > 0) {
            playerData.setCombustionLevel(0);
            changed = true;

            plugin.getPluginLogger().info("Combustion remise à zéro pour " + player.getName() + " (pioche cassée)");
        }

        // Marque les données comme modifiées si des changements ont été effectués
        if (changed) {
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        }
    }

    /**
     * CORRIGÉ : Améliore un enchantement avec calcul de coûts identique au GUI
     */
    public boolean upgradeEnchantment(Player player, String enchantmentName, int requestedLevels) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        CustomEnchantment enchantment = getEnchantment(enchantmentName);

        if (enchantment == null) {
            plugin.getPluginLogger().warning("Enchantement introuvable: " + enchantmentName);
            return false;
        }

        int currentLevel = playerData.getEnchantmentLevel(enchantmentName);
        int maxPossibleLevels = enchantment.getMaxLevel() - currentLevel;

        if (maxPossibleLevels <= 0) {
            player.sendMessage("§cNiveau maximum déjà atteint!");
            return false;
        }

        long availableTokens = playerData.getTokens();

        // CORRECTION : Utilise la même logique que le GUI pour calculer les niveaux réellement achetables
        int actualLevels = 0;
        long totalCost = 0;

        for (int i = 1; i <= Math.min(requestedLevels, maxPossibleLevels); i++) {
            long cost = enchantment.getUpgradeCost(currentLevel + i);
            if (totalCost + cost <= availableTokens) {
                totalCost += cost;
                actualLevels = i;
            } else {
                break;
            }
        }

        if (actualLevels <= 0) {
            return false;
        }

        // Effectue l'amélioration
        if (playerData.removeTokens(totalCost)) {
            playerData.setEnchantmentLevel(enchantmentName, currentLevel + actualLevels);

            player.sendMessage("§a✅ " + enchantment.getDisplayName() + " amélioré de " + actualLevels +
                    " niveau" + (actualLevels > 1 ? "x" : "") + " au niveau " + (currentLevel + actualLevels) +
                    " §7(-" + NumberFormatter.format(totalCost) + " tokens)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
            // Quêtes: améliorer un enchantement X fois
            plugin.getQuestManager().addProgress(player, fr.prisontycoon.quests.QuestType.UPGRADE_ENCHANTMENTS, actualLevels);
            return true;
        }

        return false;
    }
}

// NOUVEAU : Enchantement SellGreed
class SellGreedEnchantment implements CustomEnchantment {
    @Override
    public String getName() {
        return "sell_greed";
    }

    @Override
    public String getDisplayName() {
        return "Sell Greed";
    }

    @Override
    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    @Override
    public String getDescription() {
        return "Bonus de vente permanent";
    }

    @Override
    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getUpgradeCost(int level) {
        // Prix rééquilibré: croissance douce
        return Math.max(1000, Math.round(1000 * Math.pow(1.03, level)));
    }

    @Override
    public Material getDisplayMaterial() {
        return Material.GOLD_BLOCK;
    }
}

// NOUVEAU : Enchantement Jackhammer
class JackhammerEnchantment implements CustomEnchantment {
    @Override
    public String getName() {
        return "jackhammer";
    }

    @Override
    public String getDisplayName() {
        return "Jackhammer";
    }

    @Override
    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.SPECIAL;
    }

    @Override
    public String getDescription() {
        return "Chance de casser une couche entière";
    }

    @Override
    public int getMaxLevel() {
        return 2000;
    }

    @Override
    public long getUpgradeCost(int level) {
        return Math.round(10000 * Math.pow(level, 1.8));
    }

    @Override
    public Material getDisplayMaterial() {
        return Material.DIAMOND_PICKAXE;
    }
}

// MODIFIÉ : KeyGreed maintenant dans la catégorie SPECIAL
class KeyGreedEnchantment implements CustomEnchantment {
    @Override
    public String getName() {
        return "key_greed";
    }

    @Override
    public String getDisplayName() {
        return "Key Greed";
    }

    @Override
    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC; // MODIFIÉ : changé de ECONOMIC vers SPECIAL
    }

    @Override
    public String getDescription() {
        return "Chance d'obtenir des clés de coffres";
    }

    @Override
    public int getMaxLevel() {
        return 10;
    }

    @Override
    public long getUpgradeCost(int level) {
        return Math.round(500000 * Math.pow(level, 1.8));
    }

    @Override
    public Material getDisplayMaterial() {
        return Material.TRIPWIRE_HOOK;
    }
}

// Les autres classes d'enchantements restent inchangées...
class TokenGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "token_greed";
    }

    public String getDisplayName() {
        return "Token Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir des tokens bonus";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(5000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.GOLD_NUGGET;
    }
}

class ExpGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "exp_greed";
    }

    public String getDisplayName() {
        return "Exp Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir de l'expérience bonus";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(3000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.EXPERIENCE_BOTTLE;
    }
}

class MoneyGreedEnchantment implements CustomEnchantment {
    public String getName() {
        return "money_greed";
    }

    public String getDisplayName() {
        return "Money Greed";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Chance d'obtenir des coins bonus";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(4000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.GOLD_INGOT;
    }
}

class AbondanceEnchantment implements CustomEnchantment {
    public String getName() {
        return "abundance";
    }

    public String getDisplayName() {
        return "Abondance";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Chance de doubler tous les gains";
    }

    public int getMaxLevel() {
        return 100000;
    }

    public long getUpgradeCost(int level) {
        return Math.round(3000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.NETHER_STAR;
    }
}

class CombustionEnchantment implements CustomEnchantment {
    public String getName() {
        return "combustion";
    }

    public String getDisplayName() {
        return "Combustion";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Plus tu mines, plus tes gains augmentent";
    }

    public int getMaxLevel() {
        return 1000;
    }

    public long getUpgradeCost(int level) {
        return Math.round(5000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.FIRE_CHARGE;
    }
}

class PetXpEnchantment implements CustomEnchantment {
    public String getName() {
        return "pet_xp";
    }

    public String getDisplayName() {
        return "Pet XP";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.ECONOMIC;
    }

    public String getDescription() {
        return "Augmente l'expérience des pets";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(2000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.BONE;
    }
}

class EfficiencyEnchantment implements CustomEnchantment {
    public String getName() {
        return "efficiency";
    }

    public String getDisplayName() {
        return "Efficacité";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Réduit le temps de minage";
    }

    public int getMaxLevel() {
        return 200;
    }

    public long getUpgradeCost(int level) {
        return 10000L * level * level;
    }

    public Material getDisplayMaterial() {
        return Material.REDSTONE;
    }
}

class FortuneEnchantment implements CustomEnchantment {
    public String getName() {
        return "fortune";
    }

    public String getDisplayName() {
        return "Fortune";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Multiplie tous les drops";
    }

    public int getMaxLevel() {
        return Integer.MAX_VALUE;
    }

    public long getUpgradeCost(int level) {
        return Math.round(2000 * Math.pow(level, 1.6));
    }

    public Material getDisplayMaterial() {
        return Material.EMERALD;
    }
}

class DurabilityEnchantment implements CustomEnchantment {
    public String getName() {
        return "durability";
    }

    public String getDisplayName() {
        return "Solidité";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Augmente la durabilité";
    }

    public int getMaxLevel() {
        return 20;
    }

    public long getUpgradeCost(int level) {
        return Math.round(10000 * Math.pow(level, 5));
    }

    public Material getDisplayMaterial() {
        return Material.DIAMOND;
    }
}

class NightVisionEnchantment implements CustomEnchantment {
    public String getName() {
        return "night_vision";
    }

    public String getDisplayName() {
        return "Vision Nocturne";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Vision nocturne permanente";
    }

    public int getMaxLevel() {
        return 1;
    }

    public long getUpgradeCost(int level) {
        return 150000;
    }

    public Material getDisplayMaterial() {
        return Material.GOLDEN_CARROT;
    }
}

class SpeedEnchantment implements CustomEnchantment {
    public String getName() {
        return "speed";
    }

    public String getDisplayName() {
        return "Vitesse";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Augmente la vitesse de déplacement";
    }

    public int getMaxLevel() {
        return 3;
    }

    public long getUpgradeCost(int level) {
        return 100000L * level * level;
    }

    public Material getDisplayMaterial() {
        return Material.SUGAR;
    }
}

class HasteEnchantment implements CustomEnchantment {
    public String getName() {
        return "haste";
    }

    public String getDisplayName() {
        return "Célérité";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Effet Célérité permanent";
    }

    public int getMaxLevel() {
        return 10;
    }

    public long getUpgradeCost(int level) {
        return Math.round(500000 * Math.pow(level, 3));
    }

    public Material getDisplayMaterial() {
        return Material.BEACON;
    }
}

class JumpBoostEnchantment implements CustomEnchantment {
    public String getName() {
        return "jump_boost";
    }

    public String getDisplayName() {
        return "Saut";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Augmente la hauteur de saut";
    }

    public int getMaxLevel() {
        return 5;
    }

    public long getUpgradeCost(int level) {
        return Math.round(75000 * Math.pow(level, 3));
    }

    public Material getDisplayMaterial() {
        return Material.RABBIT_FOOT;
    }
}

class EscalatorEnchantment implements CustomEnchantment {
    public String getName() {
        return "escalator";
    }

    public String getDisplayName() {
        return "Escalateur";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.MOBILITY;
    }

    public String getDescription() {
        return "Téléportation vers la surface";
    }

    public int getMaxLevel() {
        return 1;
    }

    public long getUpgradeCost(int level) {
        return 200000;
    }

    public Material getDisplayMaterial() {
        return Material.ENDER_PEARL;
    }
}

class LuckEnchantment implements CustomEnchantment {
    public String getName() {
        return "luck";
    }

    public String getDisplayName() {
        return "Chance";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.UTILITY;
    }

    public String getDescription() {
        return "Augmente les chances des Greeds";
    }

    public int getMaxLevel() {
        return 500;
    }

    public long getUpgradeCost(int level) {
        return Math.round(3000 * Math.pow(level, 1.5));
    }

    public Material getDisplayMaterial() {
        return Material.RABBIT_FOOT;
    }
}

class LaserEnchantment implements CustomEnchantment {
    public String getName() {
        return "laser";
    }

    public String getDisplayName() {
        return "Laser";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.SPECIAL;
    }

    public String getDescription() {
        return "Chance de miner en ligne";
    }

    public int getMaxLevel() {
        return 2000;
    }

    public long getUpgradeCost(int level) {
        return 20000L * level * level;
    }

    public Material getDisplayMaterial() {
        return Material.END_ROD;
    }
}

class ExplosionEnchantment implements CustomEnchantment {
    public String getName() {
        return "explosion";
    }

    public String getDisplayName() {
        return "Explosion";
    }

    public EnchantmentCategory getCategory() {
        return EnchantmentCategory.SPECIAL;
    }

    public String getDescription() {
        return "Chance de créer une explosion";
    }

    public int getMaxLevel() {
        return 100;
    }

    public long getUpgradeCost(int level) {
        return Math.round(25000 * Math.pow(level, 1.05));
    }

    public Material getDisplayMaterial() {
        return Material.TNT;
    }
}

// Jackpot: chance de recevoir un voucher aléatoire
class JackpotEnchantment implements CustomEnchantment {
    @Override public String getName() { return "jackpot"; }
    @Override public String getDisplayName() { return "§6Jackpot"; }
    @Override public EnchantmentCategory getCategory() { return EnchantmentCategory.SPECIAL; }
    @Override public String getDescription() { return "Chance d'obtenir un coupon aléatoire en minant"; }
    @Override public int getMaxLevel() { return 10000; }
    @Override public long getUpgradeCost(int level) { return Math.max(10, Math.round(5 * Math.pow(1.02, level))); }
    @Override public Material getDisplayMaterial() { return Material.PAPER; }
}

// Cohésion: multiplicateur de greeds selon joueurs dans la mine
class CohesionEnchantment implements CustomEnchantment {
    @Override public String getName() { return "cohesion"; }
    @Override public String getDisplayName() { return "§aCohésion"; }
    @Override public EnchantmentCategory getCategory() { return EnchantmentCategory.SPECIAL; }
    @Override public String getDescription() { return "Augmente les greeds selon les joueurs présents dans la mine"; }
    @Override public int getMaxLevel() { return 10000; }
    @Override public long getUpgradeCost(int level) { return Math.max(10, Math.round(3 * Math.pow(1.015, level))); }
    @Override public Material getDisplayMaterial() { return Material.PLAYER_HEAD; }
}

// Héritage: chance de copier le greed d'un autre joueur
class HeritageEnchantment implements CustomEnchantment {
    @Override public String getName() { return "heritage"; }
    @Override public String getDisplayName() { return "§dHéritage"; }
    @Override public EnchantmentCategory getCategory() { return EnchantmentCategory.SPECIAL; }
    @Override public String getDescription() { return "Chance de déclencher un greed lorsqu'un autre joueur en déclenche un"; }
    @Override public int getMaxLevel() { return 500; }
    @Override public long getUpgradeCost(int level) { return Math.max(100, Math.round(250 * Math.pow(1.03, level))); }
    @Override public Material getDisplayMaterial() { return Material.TOTEM_OF_UNDYING; }
}

// Fièvre de l'Opportunité: fenêtre de 10s de greeds garantis sur un bloc
class OpportunityFeverEnchantment implements CustomEnchantment {
    @Override public String getName() { return "opportunity_fever"; }
    @Override public String getDisplayName() { return "§eFièvre de l'Opportunité"; }
    @Override public EnchantmentCategory getCategory() { return EnchantmentCategory.UTILITY; }
    @Override public String getDescription() { return "Chance de 10s pendant lesquelles un bloc déclenche toujours un greed"; }
    @Override public int getMaxLevel() { return 10000; }
    @Override public long getUpgradeCost(int level) { return Math.max(25, Math.round(4 * Math.pow(1.02, level))); }
    @Override public Material getDisplayMaterial() { return Material.CLOCK; }
}

// Planneur: Chute lente en tombant (annulable en sneak)
class PlanneurEnchantment implements CustomEnchantment {
    @Override public String getName() { return "planneur"; }
    @Override public String getDisplayName() { return "§bPlanneur"; }
    @Override public EnchantmentCategory getCategory() { return EnchantmentCategory.MOBILITY; }
    @Override public String getDescription() { return "Applique chute lente en tombant; sneak pour l'annuler"; }
    @Override public int getMaxLevel() { return 2; }
    @Override public long getUpgradeCost(int level) { return level == 0 ? 1000 : 5000; }
    @Override public Material getDisplayMaterial() { return Material.FEATHER; }
}