package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.crates.CrateType;
import fr.prisontycoon.data.ContainerData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire principal des crates - Gère l'ouverture, les animations et les récompenses
 */
public class CrateManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey keyTypeKey;

    // Cache des positions des crates configurées
    private final Map<Location, CrateType> crateLocations;

    // Cache des joueurs en cours d'ouverture de crate (pour éviter le spam)
    private final Set<UUID> playersOpening;

    // Cache temporaire des animations en cours
    private final Map<UUID, BukkitRunnable> activeAnimations;

    // NOUVEAU: Cache pour les hologrammes des crates (nametags)
    private final Map<Location, List<ArmorStand>> crateHolograms;

    // NOUVEAU: Tâche pour les effets de particules permanents
    private BukkitRunnable permanentEffectTask;


    public CrateManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.keyTypeKey = new NamespacedKey(plugin, "key_type");
        this.crateLocations = new ConcurrentHashMap<>();
        this.playersOpening = ConcurrentHashMap.newKeySet();
        this.activeAnimations = new ConcurrentHashMap<>();
        this.crateHolograms = new ConcurrentHashMap<>(); // Initialisation

        loadCrateLocations();
        startPermanentEffects(); // Démarrage des animations permanentes
        plugin.getPluginLogger().info("§aCrateManager initialisé avec " + crateLocations.size() + " crates.");
    }

    /**
     * Charge les positions des crates depuis la configuration
     */
    private void loadCrateLocations() {
        // Nettoie les anciens hologrammes avant de recharger
        crateHolograms.values().forEach(list -> list.forEach(ArmorStand::remove));
        crateHolograms.clear();

        if (!plugin.getConfig().contains("crates.locations")) {
            plugin.getPluginLogger().warning("Aucune configuration de crates trouvée. Création d'exemples...");
            createDefaultCrateConfig();
            return;
        }

        var cratesSection = plugin.getConfig().getConfigurationSection("crates.locations");
        if (cratesSection == null) return;

        for (String crateName : cratesSection.getKeys(false)) {
            try {
                String worldName = cratesSection.getString(crateName + ".world");
                int x = cratesSection.getInt(crateName + ".x");
                int y = cratesSection.getInt(crateName + ".y");
                int z = cratesSection.getInt(crateName + ".z");
                String crateTypeStr = cratesSection.getString(crateName + ".type");

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getPluginLogger().warning("Monde " + worldName + " non trouvé pour la crate " + crateName);
                    continue;
                }

                Location loc = new Location(world, x, y, z);
                assert crateTypeStr != null;
                CrateType crateType = CrateType.valueOf(crateTypeStr.toUpperCase().replace(" ", "_"));

                crateLocations.put(loc, crateType);
                createCrateHolograms(loc, crateType); // Crée le nametag au-dessus de la crate

                // Place un bloc physique si nécessaire
                Block block = loc.getBlock();
                if (block.getType() != Material.BEACON) {
                    block.setType(Material.BEACON);
                }

                plugin.getPluginLogger().debug("Crate " + crateName + " chargée: " + crateType + " à " + loc);

            } catch (Exception e) {
                plugin.getPluginLogger().severe("Erreur lors du chargement de la crate " + crateName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Crée une configuration par défaut des crates
     */
    private void createDefaultCrateConfig() {
        plugin.getConfig().set("crates.locations.crate_commune.world", "world");
        plugin.getConfig().set("crates.locations.crate_commune.x", 0);
        plugin.getConfig().set("crates.locations.crate_commune.y", 64);
        plugin.getConfig().set("crates.locations.crate_commune.z", 0);
        plugin.getConfig().set("crates.locations.crate_commune.type", "COMMUNE");

        plugin.getConfig().set("crates.locations.crate_rare.world", "world");
        plugin.getConfig().set("crates.locations.crate_rare.x", 10);
        plugin.getConfig().set("crates.locations.crate_rare.y", 64);
        plugin.getConfig().set("crates.locations.crate_rare.z", 0);
        plugin.getConfig().set("crates.locations.crate_rare.type", "RARE");

        plugin.saveConfig();
        plugin.getPluginLogger().info("Configuration des crates créée. Rechargez le plugin pour l'appliquer.");
    }

    // ... (Les méthodes isKey, getKeyType, countKeys, consumeKey, etc. restent inchangées)

    /**
     * Vérifie si un item est une clé
     */
    public boolean isKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK || !item.hasItemMeta()) return false;
        return item.getItemMeta().getDisplayName().contains("Clé");
    }

    /**
     * Récupère le type de clé depuis un ItemStack
     */
    public String getKeyType(ItemStack item) {
        if (!isKey(item)) return null;

        String displayName = item.getItemMeta().getDisplayName();

        // Extraction du type depuis le nom d'affichage
        if (displayName.contains("Cristal")) return "Cristal";
        if (displayName.contains("Légendaire")) return "Légendaire";
        if (displayName.contains("Rare")) return "Rare";
        if (displayName.contains("Peu Commune")) return "Peu Commune";
        if (displayName.contains("Commune")) return "Commune";

        return null;
    }

    /**
     * Compte le nombre de clés d'un type spécifique dans l'inventaire
     */
    public int countKeys(Player player, String keyType) {
        int count = 0;

        // Compte dans l'inventaire
        for (ItemStack item : player.getInventory().getContents()) {
            if (isKey(item) && keyType.equals(getKeyType(item))) {
                assert item != null;
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Consomme une clé d'un type spécifique (d'abord dans l'inventaire, puis dans les conteneurs)
     */
    public boolean consumeKey(Player player, String keyType) {
        // D'abord, essaie de consommer dans l'inventaire
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isKey(item) && keyType.equals(getKeyType(item))) {
                assert item != null;
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
                }
                return false;
            }
        }

        // Sinon, consomme dans les conteneurs
        return !consumeKeyFromContainers(player, keyType);
    }

    /**
     * Consomme une clé dans les conteneurs du joueur
     */
    private boolean consumeKeyFromContainers(Player player, String keyType) {
        ContainerManager containerManager = plugin.getContainerManager();
        if (containerManager == null) return false;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack containerItem = player.getInventory().getItem(i);
            if (containerManager.isContainer(containerItem)) {
                ContainerData data = containerManager.loadDataFromItem(containerItem);
                if (data == null) continue;

                // Cherche la clé dans ce conteneur
                for (Map.Entry<ItemStack, Integer> entry : data.getContents().entrySet()) {
                    ItemStack storedItem = entry.getKey();
                    Integer amount = entry.getValue();

                    if (isKey(storedItem) && keyType.equals(getKeyType(storedItem))) {
                        if (amount > 1) {
                            entry.setValue(amount - 1);
                        } else {
                            data.getContents().remove(storedItem);
                        }

                        // Sauvegarde le conteneur modifié
                        // Utilise une méthode publique ou force la mise à jour
                        try {
                            // Essaie d'utiliser une méthode publique de sauvegarde
                            containerManager.loadDataFromItem(containerItem); // Force le cache
                            player.getInventory().setItem(i, containerItem);
                        } catch (Exception e) {
                            // Si ça ne marche pas, on met à jour quand même l'inventaire
                            player.getInventory().setItem(i, containerItem);
                        }
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Vérifie si le joueur peut ouvrir une crate à cette position
     */
    public boolean canOpenCrate(Player player, Location location, CrateType crateType) {
        // Vérifie si le joueur n'est pas déjà en train d'ouvrir une crate
        if (playersOpening.contains(player.getUniqueId())) {
            return false;
        }

        // Vérifie si le joueur a la clé correspondante
        String requiredKeyType = crateType.getDisplayName();
        return countKeys(player, requiredKeyType) > 0;
    }

    /**
     * Ouvre une crate avec animation
     */
    public void openCrateWithAnimation(Player player, Location location, CrateType crateType) {
        if (!canOpenCrate(player, location, crateType)) {
            player.sendMessage("§cVous n'avez pas de clé " + crateType.getColor() + crateType.getDisplayName() + " §cpour ouvrir cette crate!");
            return;
        }

        String requiredKeyType = crateType.getDisplayName();

        // Consomme la clé
        if (consumeKey(player, requiredKeyType)) {
            player.sendMessage("§cErreur lors de la consommation de la clé!");
            return;
        }

        // Ajoute le joueur à la liste des joueurs en cours d'ouverture
        playersOpening.add(player.getUniqueId());

        // Lance l'animation
        startOpeningAnimation(player, location, crateType);
    }

    /**
     * Ouvre toutes les clés disponibles jusqu'à ce que l'inventaire soit plein
     */
    public void openAllKeys(Player player, Location location, CrateType crateType) {
        String requiredKeyType = crateType.getDisplayName();
        int availableKeys = countKeys(player, requiredKeyType);

        if (availableKeys == 0) {
            player.sendMessage("§cVous n'avez aucune clé " + crateType.getColor() + crateType.getDisplayName() + "!");
            return;
        }

        playersOpening.add(player.getUniqueId());

        player.sendMessage("§6✨ Ouverture de " + availableKeys + " clés " + crateType.getColor() + crateType.getDisplayName() + "§6...");

        int opened = 0;
        List<ItemStack> rewards = new ArrayList<>();

        while (opened < availableKeys && player.getInventory().firstEmpty() != -1) {
            if (consumeKey(player, requiredKeyType)) break;

            CrateType.CrateReward reward = crateType.selectRandomReward();
            ItemStack rewardItem = crateType.convertRewardToItem(reward, plugin);

            if (rewardItem != null) {
                rewards.add(rewardItem);
                giveRewardToPlayer(player, reward, rewardItem);
            }

            opened++;
        }

        playersOpening.remove(player.getUniqueId());

        // Effets visuels finaux
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        spawnFireworks(location, 3);

        player.sendMessage("§a✅ " + opened + " crates ouvertes! " + (availableKeys - opened > 0 ?
                "§7(" + (availableKeys - opened) + " non ouvertes - inventaire plein)" : ""));
    }

    /**
     * Lance l'animation d'ouverture de crate
     */
    private void startOpeningAnimation(Player player, Location location, CrateType crateType) {
        // Titre d'ouverture
        player.sendTitle("§6✨ OUVERTURE DE CRATE ✨",
                crateType.getColor() + "Crate " + crateType.getDisplayName(),
                10, 60, 20);

        // Son d'ouverture
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);

        // Animation de particules pendant l'ouverture
        BukkitRunnable animation = new BukkitRunnable() {
            final int maxTicks = 80; // 4 secondes
            final List<CrateType.CrateReward> allRewards = crateType.getAllRewards();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    // Animation terminée, donne la récompense finale
                    CrateType.CrateReward finalReward = crateType.selectRandomReward();
                    ItemStack rewardItem = crateType.convertRewardToItem(finalReward, plugin);

                    // Effets finaux spectaculaires
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    spawnFireworks(location, 2);

                    // Titre de récompense
                    player.sendTitle("§6🎉 RÉCOMPENSE GAGNÉE! 🎉",
                            getRewardDisplayText(finalReward),
                            10, 40, 10);

                    // Donne la récompense
                    giveRewardToPlayer(player, finalReward, rewardItem);

                    // Nettoie
                    playersOpening.remove(player.getUniqueId());
                    activeAnimations.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }

                // Animation en cours - fait défiler les récompenses
                if (ticks % 10 == 0) { // Toutes les 0.5 secondes
                    CrateType.CrateReward displayReward = allRewards.get(ticks / 10 % allRewards.size());

                    // Action bar avec la récompense actuelle
                    player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§6➤ " + getRewardDisplayText(displayReward)));

                    // Particules colorées
                    spawnOpeningParticles(location, crateType);

                    // Sons rythmés
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f + (ticks * 0.01f));
                }

                ticks++;
            }
        };

        activeAnimations.put(player.getUniqueId(), animation);
        animation.runTaskTimer(plugin, 0L, 1L);
    }

    // ... (Les méthodes getRewardDisplayText et giveRewardToPlayer restent inchangées)

    /**
     * Génère du texte d'affichage pour une récompense
     */
    private String getRewardDisplayText(CrateType.CrateReward reward) {
        return switch (reward.getType()) {
            case CONTAINER -> "§eConteneur Tier " + reward.getContainerTier();
            case KEY -> "§bClé " + reward.getKeyType();
            case CRISTAL_VIERGE -> "§dCristal Niveau " + reward.getRandomAmount();
            case LIVRE_UNIQUE -> "§5Livre " + reward.getBookType();
            case AUTOMINER -> "§7Autominer " + reward.getAutominerType();
            case VOUCHER -> {
                // Si le type de voucher correspond à PrinterSlot, afficher équivalent imprimante
                try {
                    String vt = reward.getVoucherType();
                    if (vt != null && vt.equalsIgnoreCase("PRINTER_SLOT")) {
                        yield "§6Imprimante (slot)";
                    }
                } catch (Throwable ignored) {}
                yield "§eVoucher " + reward.getVoucherType();
            }
            case BOOST -> "§cBoost " + reward.getBoostType() + " x" + reward.getBoostMultiplier();
            case FORGE_BLUEPRINT -> "§7Plan de forge " + reward.getBlueprintTier();
        };
    }

    /**
     * Donne la récompense au joueur selon son type
     */
    private void giveRewardToPlayer(Player player, CrateType.CrateReward reward, ItemStack rewardItem) {
        if (rewardItem != null) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(rewardItem);
                player.sendMessage("§a✅ " + rewardItem.getItemMeta().getDisplayName() + " §aajouté à votre inventaire!");
            } else {
                // Inventaire plein, drop l'item
                player.getWorld().dropItemNaturally(player.getLocation(), rewardItem);
                player.sendMessage("§e⚠ Inventaire plein! L'item a été lâché au sol.");
            }
            return;
        }

        // Conversion de récompense de type "slot voucher": applique +1 PrinterExtraSlot (max 100)
        try {
            if (reward.getType() == fr.prisontycoon.crates.CrateType.RewardType.VOUCHER) {
                String vt = reward.getVoucherType();
                if (vt != null && vt.equalsIgnoreCase("PRINTER_SLOT")) {
                    var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    int current = data.getPrinterExtraSlots();
                    if (current < 100) {
                        data.setPrinterExtraSlots(Math.min(100, current + 1));
                        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
                        plugin.getPlayerDataManager().saveSingleColumn(player.getUniqueId(), "printer_extra_slots");
                        player.sendMessage("§a✅ +1 slot d'imprimante (" + data.getPrinterExtraSlots() + "/100)");
                    } else {
                        player.sendMessage("§e⚠ Vous avez déjà atteint la limite de 100 slots d'imprimantes.");
                    }
                }
            }
        } catch (Throwable ignored) {}
    }


    /**
     * Génère des particules colorées pour l'animation d'ouverture
     */
    private void spawnOpeningParticles(Location location, CrateType crateType) {
        World world = location.getWorld();
        if (world == null) return;

        Particle particle = switch (crateType) {
            case VOTE -> Particle.EGG_CRACK;
            case COMMUNE -> Particle.HAPPY_VILLAGER;
            case PEU_COMMUNE -> Particle.ENCHANT;
            case RARE -> Particle.PORTAL;
            case LEGENDAIRE -> Particle.TOTEM_OF_UNDYING;
            case CRISTAL -> Particle.END_ROD;
        };

        // Cercle de particules autour de la crate
        for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 8) {
            double x = location.getX() + 0.5 + Math.cos(angle) * 1.5;
            double z = location.getZ() + 0.5 + Math.sin(angle) * 1.5;
            double y = location.getY() + 1.0;

            world.spawnParticle(particle, x, y, z, 3, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /**
     * Lance des feux d'artifice
     */
    private void spawnFireworks(Location location, int count) {
        World world = location.getWorld();
        if (world == null) return;

        for (int i = 0; i < count; i++) {
            world.spawnParticle(Particle.FIREWORK,
                    location.clone().add(0.5, 1.5, 0.5),
                    50, 1.0, 1.0, 1.0, 0.1);
        }
    }

    // ==============================================================================
    // NOUVELLES MÉTHODES POUR LES EFFETS PERMANENTS ET LES HOLOGRAMMES
    // ==============================================================================

    /**
     * Crée les hologrammes (nametags) au-dessus d'une crate.
     */
    private void createCrateHolograms(Location location, CrateType crateType) {
        World world = location.getWorld();
        if (world == null) return;
        Location hologramCenter = location.clone().add(0.5, 1.65, 0.5);

        // Vérifie si des ArmorStands caractéristiques d'un hologramme existent déjà dans une petite zone
        Collection<Entity> nearbyEntities = world.getNearbyEntities(hologramCenter, 0.5, 0.5, 0.5);
        for (Entity entity : nearbyEntities) {
            if (entity instanceof ArmorStand as) {
                if (as.isMarker() && as.isCustomNameVisible()) {
                    return;
                }
            }
        }
        removeCrateHolograms(location);
        List<ArmorStand> holograms = new ArrayList<>();

        // Ligne 1: Nom de la crate
        Location line1Loc = location.clone().add(0.5, 1.8, 0.5);
        ArmorStand line1 = spawnHologramLine(line1Loc, crateType.getColor() + "§lCrate " + crateType.getDisplayName());
        if (line1 != null) holograms.add(line1);

        // Ligne 2: Instruction
        Location line2Loc = line1Loc.clone().subtract(0, 0.3, 0);
        ArmorStand line2 = spawnHologramLine(line2Loc, "§eClic-droit pour ouvrir");
        if (line2 != null) holograms.add(line2);

        crateHolograms.put(location, holograms);
    }

    /**
     * Fait apparaître une seule ligne de texte flottant (ArmorStand).
     */
    private ArmorStand spawnHologramLine(Location location, String text) {
        World world = location.getWorld();
        if (world == null) return null;

        ArmorStand hologram = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
        hologram.setGravity(false);
        hologram.setCanPickupItems(false);
        hologram.setCustomName(ChatColor.translateAlternateColorCodes('&', text));
        hologram.setCustomNameVisible(true);
        hologram.setVisible(false);
        hologram.setInvulnerable(true);
        hologram.setMarker(true); // Empêche l'interaction et la hitbox (version 1.8+)

        return hologram;
    }

    /**
     * Supprime les hologrammes associés à une location de crate.
     */
    private void removeCrateHolograms(Location location) {
        if (crateHolograms.containsKey(location)) {
            crateHolograms.get(location).forEach(ArmorStand::remove);
            crateHolograms.remove(location);
        }
    }

    /**
     * Démarre une tâche qui affiche des particules en continu sur toutes les crates.
     */
    public void startPermanentEffects() {
        if (permanentEffectTask != null) {
            permanentEffectTask.cancel();
        }

        permanentEffectTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<Location, CrateType> entry : crateLocations.entrySet()) {
                    spawnPermanentParticles(entry.getKey(), entry.getValue());
                }
            }
        };
        // Exécute toutes les 20 ticks (1 seconde) avec un délai initial de 2 secondes
        permanentEffectTask.runTaskTimer(plugin, 40L, 20L);
    }

    /**
     * Génère des particules pour l'effet d'ambiance permanent.
     */
    private void spawnPermanentParticles(Location location, CrateType crateType) {
        World world = location.getWorld();
        if (world == null) return;

        Color particleColor = getParticleColor(crateType);
        Particle.DustOptions dustOptions = new Particle.DustOptions(particleColor, 1.0f);
        world.spawnParticle(Particle.DUST, location.clone().add(0.5, 0.7, 0.5), 3, 0.3, 0.2, 0.3, dustOptions);
    }

    private Color getParticleColor(CrateType crateType) {
        return switch (crateType) {
            case VOTE -> Color.WHITE; // Blanc
            case COMMUNE -> Color.LIME; // Vert
            case PEU_COMMUNE -> Color.BLUE; // Bleu
            case RARE -> Color.PURPLE; // Violet
            case LEGENDAIRE -> Color.ORANGE; // Orange
            case CRISTAL -> Color.FUCHSIA; // Rose/Magenta
        };
    }

    // ==============================================================================

    /**
     * Récupère le type de crate à une position donnée
     */
    public CrateType getCrateTypeAtLocation(Location location) {
        return crateLocations.get(location);
    }

    /**
     * Vérifie si une position contient une crate
     */
    public boolean isCrateLocation(Location location) {
        return crateLocations.containsKey(location);
    }

    /**
     * Arrête toutes les animations et nettoie les entités (hologrammes).
     * Doit être appelée lors du onDisable du plugin.
     */
    public void shutdown() {
        // Arrête les animations d'ouverture en cours
        for (BukkitRunnable animation : activeAnimations.values()) {
            animation.cancel();
        }
        activeAnimations.clear();
        playersOpening.clear();

        // Arrête la tâche des effets permanents
        if (permanentEffectTask != null) {
            permanentEffectTask.cancel();
        }

        // Supprime tous les hologrammes du monde
        for (List<ArmorStand> hologramList : crateHolograms.values()) {
            hologramList.forEach(ArmorStand::remove);
        }
        crateHolograms.clear();
        plugin.getPluginLogger().info("CrateManager nettoyé (animations et hologrammes arrêtés).");
    }

    // Getters
    public Map<Location, CrateType> getCrateLocations() {
        return new HashMap<>(crateLocations);
    }

    public NamespacedKey getKeyTypeKey() {
        return keyTypeKey;
    }
}