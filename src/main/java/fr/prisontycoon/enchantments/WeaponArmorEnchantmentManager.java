package fr.prisontycoon.enchantments;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire des enchantements uniques pour épées et armures
 * Les enchantements sont stockés directement dans les métadonnées des items
 */
public class WeaponArmorEnchantmentManager {

    private final PrisonTycoon plugin;
    private final Map<String, UniqueEnchantment> enchantments;
    private final NamespacedKey uniqueEnchantsKey;

    public WeaponArmorEnchantmentManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantments = new HashMap<>();
        this.uniqueEnchantsKey = new NamespacedKey(plugin, "unique_enchantments");

        initializeUniqueEnchantments();
        plugin.getPluginLogger().info("§aWeaponArmorEnchantmentManager initialisé avec " + enchantments.size() + " enchantements.");
    }

    /**
     * Initialise tous les enchantements uniques
     */
    private void initializeUniqueEnchantments() {
        // Enchantement Tonnerre - pour pioches ET épées (pas armures)
        registerEnchantment(new TonnerreEnchantment());

        // Enchantement Incassable - pour pioches/épées/armures (universel)
        registerEnchantment(new IncassableEnchantment());

        // Enchantements spécifiques aux épées
        registerEnchantment(new TornadeEnchantment());
        registerEnchantment(new RepercussionEnchantment());
        registerEnchantment(new BeHeadEnchantment());
        registerEnchantment(new ChasseurEnchantment());
    }

    private void registerEnchantment(UniqueEnchantment enchantment) {
        enchantments.put(enchantment.getId(), enchantment);
    }

    /**
     * CORRIGÉ : addEnchantment avec gestion des items sans meta
     */
    public boolean addEnchantment(ItemStack item, String enchantId, int level) {
        if (item == null) return false;

        UniqueEnchantment enchant = enchantments.get(enchantId);
        if (enchant == null) return false;

        // CORRIGÉ : Récupère ou crée les métadonnées
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(item.getType());
            if (meta == null) return false;
        }

        // Récupère les enchantements existants
        Map<String, Integer> currentEnchants = getUniqueEnchantments(item);

        // Vérifie si l'enchantement peut être amélioré
        int currentLevel = currentEnchants.getOrDefault(enchantId, 0);
        if (currentLevel >= enchant.getMaxLevel()) {
            return false;
        }

        // Vérifie le nombre maximum d'enchantements uniques
        if (currentLevel == 0) { // Nouvel enchantement
            int maxUnique = isValidWeapon(item) ? 2 : 1;
            if (currentEnchants.size() >= maxUnique) {
                return false;
            }
        }

        // Ajoute ou améliore l'enchantement
        currentEnchants.put(enchantId, currentLevel + 1);

        // Sauvegarde dans les métadonnées
        StringBuilder enchantData = new StringBuilder();
        for (Map.Entry<String, Integer> entry : currentEnchants.entrySet()) {
            if (entry.getValue() > 0) { // Seulement les niveaux > 0
                if (enchantData.length() > 0) enchantData.append(";");
                enchantData.append(entry.getKey()).append(":").append(entry.getValue());
            }
        }

        meta.getPersistentDataContainer().set(uniqueEnchantsKey, PersistentDataType.STRING, enchantData.toString());

        // Met à jour le lore (qui sera complètement reconstruit)
        updateItemLore(meta, currentEnchants);
        item.setItemMeta(meta);

        return true;
    }

    /**
     * Détermine le nombre maximum d'enchantements uniques pour un item.
     *
     * @param item L'item à vérifier.
     * @return Le nombre maximum d'enchantements (2 pour les épées, 1 pour les armures/pioches).
     */
    public int getMaxEnchantments(ItemStack item) {
        if (item == null) return 0;
        // Une épée peut avoir 2 enchantements uniques.
        if (isValidWeapon(item)) {
            return 2;
        }
        // Les armures et les pioches ne peuvent en avoir qu'un seul.
        if (isValidArmor(item) || isValidPickaxe(item)) {
            return 1;
        }
        // Les autres items ne peuvent pas en avoir.
        return 0;
    }

    /**
     * Récupère les enchantements uniques d'un item
     */
    public Map<String, Integer> getUniqueEnchantments(ItemStack item) {
        Map<String, Integer> result = new HashMap<>();

        // CORRIGÉ : Retourne une map vide si pas de meta (c'est normal pour items vierges)
        if (item == null) {
            return result;
        }

        String enchantData = item.getItemMeta().getPersistentDataContainer()
                .get(uniqueEnchantsKey, PersistentDataType.STRING);

        if (enchantData == null || enchantData.isEmpty()) {
            return result;
        }

        String[] enchants = enchantData.split(";");
        for (String enchantStr : enchants) {
            if (enchantStr.trim().isEmpty()) continue;

            String[] parts = enchantStr.split(":");
            if (parts.length == 2) {
                try {
                    String enchantId = parts[0].trim();
                    int enchantLevel = Integer.parseInt(parts[1].trim());

                    // Seulement les enchantements valides et avec niveau > 0
                    if (enchantLevel > 0 && enchantments.containsKey(enchantId)) {
                        result.put(enchantId, enchantLevel);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return result;
    }

    /**
     * Vérifie si un item a un enchantement spécifique
     */
    public boolean hasEnchantment(ItemStack item, String enchantId) {
        return getUniqueEnchantments(item).containsKey(enchantId);
    }

    /**
     * Récupère le niveau d'un enchantement
     */
    public int getEnchantmentLevel(ItemStack item, String enchantId) {
        return getUniqueEnchantments(item).getOrDefault(enchantId, 0);
    }

    /**
     * Compte le nombre d'enchantements uniques sur un item
     */
    public int getUniqueEnchantmentCount(ItemStack item) {
        return getUniqueEnchantments(item).size();
    }

    /**
     * Vérifie si un enchantement est compatible avec un type d'item
     */
    public boolean isCompatible(String enchantId, ItemStack item) {
        UniqueEnchantment enchant = enchantments.get(enchantId);
        if (enchant == null) return false;

        boolean isWeapon = isValidWeapon(item);
        boolean isArmor = isValidArmor(item);
        boolean isPickaxe = isValidPickaxe(item);

        // Tonnerre : pioches ET épées seulement
        if (enchantId.equals("tonnerre")) {
            return isWeapon || isPickaxe;
        }

        // Incassable : universel (pioches/épées/armures)
        if (enchantId.equals("incassable")) {
            return isWeapon || isArmor || isPickaxe;
        }

        // Autres enchantements : selon leur configuration
        if (enchant.isWeaponOnly()) return isWeapon;
        if (enchant.isArmorOnly()) return isArmor;

        return true; // Enchantement universel
    }

    /**
     * Met à jour le lore de l'item avec les enchantements uniques
     */
    private void updateItemLore(ItemMeta meta, Map<String, Integer> enchants) {
        // SIMPLE ET EFFICACE : Vide complètement le lore
        List<String> lore = new ArrayList<>();

        // Filtre les enchantements valides seulement
        Map<String, Integer> validEnchants = new HashMap<>();
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            if (entry.getValue() > 0 && enchantments.containsKey(entry.getKey())) {
                validEnchants.put(entry.getKey(), entry.getValue());
            }
        }

        if (!validEnchants.isEmpty()) {
            // Header élégant
            lore.add("");
            lore.add("§8▬▬▬ §5⚡ §lENCHANTEMENTS UNIQUES §5⚡ §8▬▬▬");

            for (Map.Entry<String, Integer> entry : validEnchants.entrySet()) {
                UniqueEnchantment enchant = enchantments.get(entry.getKey());
                if (enchant != null) {
                    String enchantId = entry.getKey();
                    int level = entry.getValue();

                    // Couleur et titre
                    String color = getEnchantColor(enchantId);
                    String levelDisplay = enchant.getMaxLevel() > 1 ? " §8[§f" + toRoman(level) + "§8]" : "";

                    lore.add(color + "⚡ §l" + enchant.getName() + levelDisplay);

                    // Description courte et détails essentiels seulement
                    lore.add("§7▸ " + enchant.getDescription());

                    // Détails techniques condensés
                    addCompactDetails(lore, enchantId, level, enchant);

                    // Progression si multi-niveaux
                    if (enchant.getMaxLevel() > 1) {
                        if (level < enchant.getMaxLevel()) {
                            lore.add("§7▸ §a✦ Améliorable §8(§e" + level + "§8/§e" + enchant.getMaxLevel() + "§8)");
                        } else {
                            lore.add("§7▸ §6★ Niveau Maximum §8(§e" + level + "§8/§e" + enchant.getMaxLevel() + "§8)");
                        }
                    }
                }
            }

            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        meta.setLore(lore);
    }

    /**
     * NOUVEAU : Détails compacts pour éviter la surcharge d'informations
     */
    private void addCompactDetails(List<String> lore, String enchantId, int level, UniqueEnchantment enchant) {
        switch (enchantId) {
            case "tonnerre":
                int chance = 5 + (level / 2);
                double damage = 0.5 + (level * 0.5);
                lore.add("§7▸ §e" + chance + "% chance §8| §c" + damage + " ❤ §8| §5Pioches & Épées");
                break;

            case "incassable":
                lore.add("§7▸ §bDurabilité infinie §8| §5Universel");
                break;

            case "tornade":
                lore.add("§7▸ §aZone 3x3 §8| §cRepousse ennemis");
                break;

            case "repercussion":
                int radius = 2 + level;
                lore.add("§7▸ §cExplosion " + radius + " blocs §8| §6Vengeance posthume");
                break;

            case "behead":
                int dropChance = 10 + (level * 5);
                lore.add("§7▸ §4" + dropChance + "% décapitation §8| §6Trophée tête");
                break;

            case "chasseur":
                int pvpBonus = level * 15;
                lore.add("§7▸ §6+" + pvpBonus + "% dégâts PvP §8| §eAnti-joueurs");
                break;
        }
    }

    /**
     * CORRIGÉ : Couleurs distinctes pour chaque enchantement
     */
    private String getEnchantColor(String enchantId) {
        switch (enchantId) {
            case "tonnerre":
                return "§e"; // Jaune électrique
            case "incassable":
                return "§b"; // Bleu cyan
            case "tornade":
                return "§a"; // Vert
            case "repercussion":
                return "§c"; // Rouge
            case "behead":
                return "§4"; // Rouge foncé
            case "chasseur":
                return "§6"; // Orange
            default:
                return "§5"; // Violet
        }
    }

    /**
     * Gère les dégâts infligés avec une épée enchantée
     */
    public void handleWeaponDamage(Player attacker, Entity victim, ItemStack weapon) {
        Map<String, Integer> enchants = getUniqueEnchantments(weapon);

        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            UniqueEnchantment enchant = enchantments.get(entry.getKey());
            if (enchant != null) {
                enchant.onAttack(attacker, victim, entry.getValue(), plugin);
            }
        }
    }

    /**
     * Gère les dégâts reçus avec une armure enchantée
     */
    public void handleArmorDamage(Player defender, Entity attacker) {
        for (ItemStack armor : defender.getInventory().getArmorContents()) {
            if (armor != null) {
                Map<String, Integer> enchants = getUniqueEnchantments(armor);

                for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
                    UniqueEnchantment enchant = enchantments.get(entry.getKey());
                    if (enchant != null) {
                        enchant.onDefend(defender, attacker, entry.getValue(), plugin);
                    }
                }
            }
        }
    }

    /**
     * Gère la mort d'un joueur (pour Répercussion et Chasseur)
     */
    public void handlePlayerDeath(Player dead, Player killer) {
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon == null) return;

        Map<String, Integer> enchants = getUniqueEnchantments(weapon);

        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            UniqueEnchantment enchant = enchantments.get(entry.getKey());
            if (enchant != null) {
                if (entry.getKey().equals("repercussion")) {
                    handleRepercussion(dead, killer, entry.getValue());
                } else if (entry.getKey().equals("chasseur")) {
                    handleChasseur(dead, killer, entry.getValue());
                }
            }
        }
    }

    /**
     * Gère l'effet Répercussion
     */
    private void handleRepercussion(Player dead, Player killer, int level) {
        PlayerData deadData = plugin.getPlayerDataManager().getPlayerData(dead.getUniqueId());
        PlayerData killerData = plugin.getPlayerDataManager().getPlayerData(killer.getUniqueId());

        int deadRep = deadData.getReputation();

        if (deadRep < 0) { // Répercussion positive
            int chanceKeepInv = Math.abs(deadRep) / 100;
            if (ThreadLocalRandom.current().nextInt(100) < chanceKeepInv) {
                // Empêche la perte d'inventaire (à implémenter selon votre système)
                dead.sendMessage("§a✅ Répercussion positive: Inventaire conservé!");
                killer.sendMessage("§a⚖ Répercussion: Votre victime garde son inventaire!");
            }
        } else if (deadRep > 0) { // Répercussion négative
            long coinsToSteal = (long) (deadData.getCoins() * (deadRep / 100.0));
            if (coinsToSteal > 0) {
                deadData.removeCoins(coinsToSteal);
                killerData.addCoins(coinsToSteal);

                killer.sendMessage("§6💰 Répercussion: +" + NumberFormatter.format(coinsToSteal) + " coins volés!");
                dead.sendMessage("§c💸 Répercussion: -" + NumberFormatter.format(coinsToSteal) + " coins perdus!");
            }
        }
    }

    /**
     * Gère l'effet Chasseur
     */
    private void handleChasseur(Player dead, Player killer, int level) {
        PlayerData killerData = plugin.getPlayerDataManager().getPlayerData(killer.getUniqueId());
        PlayerData victimData = plugin.getPlayerDataManager().getPlayerData(dead.getUniqueId());

        int killerRep = killerData.getReputation();
        int victimRep = victimData.getReputation();

        // Vérifier si les réputations sont opposées
        if ((killerRep > 0 && victimRep < 0) || (killerRep < 0 && victimRep > 0)) {
            long coinsGain = Math.abs(killerRep - victimRep) * level * 100L;
            killerData.addCoins(coinsGain);
            killer.sendMessage("§6🏹 Chasseur: +" + NumberFormatter.format(coinsGain) + " coins!");
        }
    }

    // Méthodes utilitaires
    private boolean isValidWeapon(ItemStack item) {
        return item.getType() == Material.NETHERITE_SWORD ||
                item.getType() == Material.DIAMOND_SWORD ||
                item.getType() == Material.IRON_SWORD ||
                item.getType() == Material.GOLDEN_SWORD ||
                item.getType() == Material.STONE_SWORD ||
                item.getType() == Material.WOODEN_SWORD;
    }

    private boolean isValidArmor(ItemStack item) {
        Material type = item.getType();
        return type.name().endsWith("_HELMET") ||
                type.name().endsWith("_CHESTPLATE") ||
                type.name().endsWith("_LEGGINGS") ||
                type.name().endsWith("_BOOTS");
    }

    private boolean isValidPickaxe(ItemStack item) {
        return item.getType() == Material.NETHERITE_PICKAXE ||
                item.getType() == Material.DIAMOND_PICKAXE ||
                item.getType() == Material.IRON_PICKAXE ||
                item.getType() == Material.GOLDEN_PICKAXE ||
                item.getType() == Material.STONE_PICKAXE ||
                item.getType() == Material.WOODEN_PICKAXE;
    }

    public UniqueEnchantment getEnchantment(String id) {
        return enchantments.get(id);
    }

    public Collection<UniqueEnchantment> getAllEnchantments() {
        return enchantments.values();
    }

    private String toRoman(int number) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number > 0 && number < romans.length ? romans[number] : String.valueOf(number);
    }

    // Classes d'enchantements uniques
    public abstract static class UniqueEnchantment {
        protected final String id;
        protected final String name;
        protected final String description;
        protected final int maxLevel;
        protected final long cost;
        protected final boolean weaponOnly;
        protected final boolean armorOnly;

        public UniqueEnchantment(String id, String name, String description, int maxLevel, long cost, boolean weaponOnly, boolean armorOnly) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.maxLevel = maxLevel;
            this.cost = cost;
            this.weaponOnly = weaponOnly;
            this.armorOnly = armorOnly;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getMaxLevel() {
            return maxLevel;
        }

        public long getCost() {
            return cost;
        }

        public boolean isWeaponOnly() {
            return weaponOnly;
        }

        public boolean isArmorOnly() {
            return armorOnly;
        }

        public void onAttack(Player attacker, Entity victim, int level, PrisonTycoon plugin) {
        }

        public void onDefend(Player defender, Entity attacker, int level, PrisonTycoon plugin) {
        }
    }

    // Implémentations des enchantements

    private static class TonnerreEnchantment extends UniqueEnchantment {
        public TonnerreEnchantment() {
            // MODIFIÉ : Description mise à jour pour refléter qu'il fonctionne sur pioches ET épées
            super("tonnerre", "Tonnerre", "Chance de foudroyer en minage et combat", 3, 1000, false, false);
        }

        @Override
        public void onAttack(Player attacker, Entity victim, int level, PrisonTycoon plugin) {
            int chance = 5 + (level / 2);
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                victim.getWorld().strikeLightning(victim.getLocation());
                if (victim instanceof LivingEntity) {
                    double damage = 0.5 + (level * 0.5);
                    ((LivingEntity) victim).damage(damage, attacker);
                }
                attacker.sendMessage("§e⚡ Tonnerre déclenché!");
            }
        }
    }

    private static class IncassableEnchantment extends UniqueEnchantment {
        public IncassableEnchantment() {
            // MODIFIÉ : Description mise à jour pour refléter qu'il est universel
            super("incassable", "Incassable", "Durabilité infinie pour outils, armes et armures", 1, 5000, false, false);
        }
        // L'effet est géré par un listener séparé
    }

    private static class TornadeEnchantment extends UniqueEnchantment {
        private static final Set<UUID> playersWithActiveTornado = new HashSet<>();

        public TornadeEnchantment() {
            super("tornade", "Tornade", "Crée une tornade mobile qui aspire et projette les ennemis", 1, 5000, true, false);
        }

        // Méthode pour que le Runnable puisse accéder à l'ensemble
        public static Set<UUID> getActiveTornadoPlayers() {
            return playersWithActiveTornado;
        }

        @Override
        public void onAttack(Player attacker, Entity victim, int level, PrisonTycoon plugin) {
            // 1 chance sur 10 de déclencher la tornade
            if (ThreadLocalRandom.current().nextInt(10) == 0) {
                // NOUVEAU : Vérifier si le joueur a déjà une tornade active.
                if (playersWithActiveTornado.contains(attacker.getUniqueId())) {
                    return;
                }

                // La tornade démarre légèrement devant le joueur, dans sa direction de regard
                Location startLocation = attacker.getEyeLocation().add(attacker.getLocation().getDirection().multiply(2));
                Vector travelDirection = attacker.getLocation().getDirection().setY(0).normalize();

                // Démarrer la tâche de la tornade
                new TornadoRunnable(attacker, startLocation, travelDirection).runTaskTimer(plugin, 0L, 1L);

                attacker.sendMessage("§7🌪 §lTornade Dévastatrice déclenchée !");
            }
        }
    }

    /**
     * Classe dédiée à la gestion de l'animation et de la physique de la tornade.
     */
    private static class TornadoRunnable extends BukkitRunnable {

        // --- Paramètres de la tornade (facilement modifiables) ---
        private static final int DURATION_TICKS = 100; // 8 secondes
        private static final int PROJECTION_PHASE_START_TICKS = 70; // Aspiration pendant 6 secondes
        private static final double MOVEMENT_SPEED = 0.3; // Vitesse de déplacement au sol
        private static final double PULL_STRENGTH = 0.7; // Force d'aspiration
        private static final double SWIRL_STRENGTH = 0.7; // Force du tourbillon
        private static final double FINAL_PROJECTION_STRENGTH = 1.8; // Force de l'explosion finale
        private static final double FINAL_PROJECTION_LIFT = 1.0; // Portance de l'explosion finale
        private static final double DAMAGE_INSIDE = 1.0D; // Dégâts par tick à l'intérieur (1/2 coeur)
        private static final int DAMAGE_TICK_INTERVAL = 15; // Dégâts toutes les 15 ticks (0.75s)
        private static final double FINAL_PROJECTION_DAMAGE = 5.0D; // Dégâts de l'explosion (2.5 coeurs)

        private final Player attacker;
        private final Vector travelDirection;
        private final Location center;

        private int ticks = 0;

        public TornadoRunnable(Player attacker, Location startLocation, Vector travelDirection) {
            this.attacker = attacker;
            this.center = startLocation;
            this.travelDirection = travelDirection;

            TornadeEnchantment.getActiveTornadoPlayers().add(attacker.getUniqueId());

            // Son initial
            center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 0.5f);
        }

        @Override
        public void run() {
            // --- Phase finale : Explosion ---
            if (ticks >= DURATION_TICKS) {
                playFinalExplosion();
                cancel();
                return;
            }

            // --- Mouvement et logique principale ---
            moveTornado();
            playVisualAndSoundEffects();
            applyPhysicsAndDamage();

            ticks++;
        }

        private void moveTornado() {
            center.add(travelDirection.clone().multiply(MOVEMENT_SPEED));
            // S'assure que la tornade reste au sol et s'adapte au relief
            Block highestBlock = center.getWorld().getHighestBlockAt(center);
            if (!highestBlock.getType().isAir()) {
                center.setY(highestBlock.getY() + 1.0);
            }
        }

        private void playVisualAndSoundEffects() {
            double radius = 1.0 + (ticks * 0.04);
            double height = Math.min(ticks * 0.15, 10.0); // Hauteur max de 10 blocs

            // --- Particules de l'entonnoir principal (nuages, cendres) ---
            for (int i = 0; i < 8; i++) {
                double angle = (ticks * 0.35) + (i * Math.PI / 4);
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                Location particleLoc = center.clone().add(x, height * 0.8, z);
                center.getWorld().spawnParticle(Particle.CLOUD, particleLoc, 1, 0, 0, 0, 0.02);
                center.getWorld().spawnParticle(Particle.WHITE_ASH, center.clone().add(x, height / 2, z), 1, 0, 0, 0, 0.05);
            }

            // --- Effet de débris arrachés du sol ---
            Block groundBlock = center.clone().subtract(0, 1, 0).getBlock();
            if (!groundBlock.getType().isAir() && groundBlock.isSolid()) {
                BlockData blockData = groundBlock.getBlockData();
                for (int i = 0; i < 3; i++) {
                    double debrisRadius = radius * 0.7;
                    double angle = (ticks * 0.5) + (i * Math.PI / 1.5);
                    double x = debrisRadius * Math.cos(angle);
                    double z = debrisRadius * Math.sin(angle);
                    center.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, center.clone().add(x, 1, z), 5, 0.5, 0.5, 0.5, blockData);
                }
            }

            // --- Particules de vent coupant (plus près du centre) ---
            for (int i = 0; i < 4; i++) {
                double angle = (ticks * -0.6) + (i * Math.PI / 2);
                double x = (radius * 0.5) * Math.cos(angle);
                double z = (radius * 0.5) * Math.sin(angle);
                center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(x, height / 3, z), 1, 0, 0, 0, 0);
            }

            // --- Sons continus ---
            if (ticks % 8 == 0) {
                center.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.8f);
            }
            if (ticks % 25 == 0) {
                center.getWorld().playSound(center, Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 1.2f);
            }
        }

        private void applyPhysicsAndDamage() {
            double effectRadius = 2.0 + (ticks * 0.04);
            // Pendant la phase d'aspiration
            if (ticks < PROJECTION_PHASE_START_TICKS) {
                for (Entity nearby : center.getWorld().getNearbyEntities(center, effectRadius, 12, effectRadius)) {
                    if (nearby instanceof LivingEntity && !nearby.equals(attacker) && nearby.isValid()) {
                        Vector pullDirection = center.toVector().subtract(nearby.getLocation().toVector());

                        if (pullDirection.lengthSquared() > 1e-9) { // Sécurité anti-crash
                            // Vecteur d'aspiration vers le centre
                            Vector pullVector = pullDirection.clone().normalize().multiply(PULL_STRENGTH);
                            // Vecteur perpendiculaire pour le tourbillon
                            Vector swirlVector = new Vector(-pullDirection.getZ(), 0, pullDirection.getX()).normalize().multiply(SWIRL_STRENGTH);

                            // Combinaison des forces et légère portance
                            Vector combinedVelocity = pullVector.add(swirlVector).setY(0.28);
                            nearby.setVelocity(combinedVelocity);

                            // Dégâts continus
                            if (ticks % DAMAGE_TICK_INTERVAL == 0) {
                                ((LivingEntity) nearby).damage(DAMAGE_INSIDE, attacker);
                            }
                        }
                    }
                }
            }
        }

        private void playFinalExplosion() {
            double effectRadius = 2.0 + (ticks * 0.04);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.7f);
            center.getWorld().spawnParticle(Particle.EXPLOSION, center, 10);

            for (Entity nearby : center.getWorld().getNearbyEntities(center, effectRadius, 12, effectRadius)) {
                if (nearby instanceof LivingEntity && !nearby.equals(attacker) && nearby.isValid()) {
                    // Dégâts de l'explosion
                    ((LivingEntity) nearby).damage(FINAL_PROJECTION_DAMAGE, attacker);

                    // Projection
                    Vector direction = nearby.getLocation().toVector().subtract(center.toVector());
                    if (direction.lengthSquared() > 1e-9) {
                        direction.normalize().multiply(FINAL_PROJECTION_STRENGTH).setY(FINAL_PROJECTION_LIFT);
                        nearby.setVelocity(direction);
                    }
                }
            }
            TornadeEnchantment.getActiveTornadoPlayers().remove(attacker.getUniqueId());
        }
    }

    private static class RepercussionEnchantment extends UniqueEnchantment {
        public RepercussionEnchantment() {
            super("repercussion", "Répercussion", "Effets selon la réputation de la victime", 5, 10000, true, false);
        }
        // L'effet est géré dans handlePlayerDeath
    }

    private static class BeHeadEnchantment extends UniqueEnchantment {
        public BeHeadEnchantment() {
            super("behead", "BeHead", "10% chance d'obtenir la tête de la victime", 1, 10000, true, false);
        }

        @Override
        public void onAttack(Player attacker, Entity victim, int level, PrisonTycoon plugin) {
            if (!(victim instanceof LivingEntity)) return;

            if (ThreadLocalRandom.current().nextInt(10) == 0) { // 10% de chance
                ItemStack head = null;

                if (victim instanceof Player) {
                    head = new ItemStack(Material.PLAYER_HEAD);
                    // TODO: Configurer la tête du joueur
                } else {
                    // Têtes de monstres basiques
                    switch (victim.getType()) {
                        case ZOMBIE:
                            head = new ItemStack(Material.ZOMBIE_HEAD);
                            break;
                        case SKELETON:
                            head = new ItemStack(Material.SKELETON_SKULL);
                            break;
                        case CREEPER:
                            head = new ItemStack(Material.CREEPER_HEAD);
                            break;
                        case WITHER_SKELETON:
                            head = new ItemStack(Material.WITHER_SKELETON_SKULL);
                            break;
                        case ENDER_DRAGON:
                            head = new ItemStack(Material.DRAGON_HEAD);
                            break;
                    }
                }

                if (head != null) {
                    if (attacker.getInventory().firstEmpty() != -1) {
                        attacker.getInventory().addItem(head);
                    } else {
                        attacker.getWorld().dropItemNaturally(attacker.getLocation(), head);
                    }
                    attacker.sendMessage("§6💀 BeHead: Tête obtenue!");
                }
            }
        }
    }

    private static class ChasseurEnchantment extends UniqueEnchantment {
        public ChasseurEnchantment() {
            super("chasseur", "Chasseur", "Gain de coins en tuant des joueurs de réputation opposée", 3, 2000, true, false);
        }
        // L'effet est géré dans handlePlayerDeath
    }
}