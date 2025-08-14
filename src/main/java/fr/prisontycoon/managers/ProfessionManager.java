package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Gestionnaire du système de métiers
 * Gère les métiers, leurs niveaux, XP et talents
 */
public class ProfessionManager {

    private final PrisonTycoon plugin;
    private final Map<String, Profession> professions;

    public ProfessionManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.professions = new HashMap<>();
        initializeProfessions();
        plugin.getPluginLogger().info("§aProfessionManager initialisé avec " + professions.size() + " métiers.");
    }

    /**
     * Initialise les 3 métiers selon la documentation
     */
    private void initializeProfessions() {
        // Métier Mineur
        professions.put("mineur", new Profession("mineur", "§7⛏ §lMineur", "§7Maître de l'Extraction",
                "Optimisé pour l'extraction de ressources", Arrays.asList(
                new ProfessionTalent("exp_greed", "Exp Greed+", "Bonus d'expérience",
                        new int[]{2, 5, 10, 15, 25, 35, 50, 75, 125, 200}),
                new ProfessionTalent("token_greed", "Token Greed+", "Bonus de tokens",
                        new int[]{2, 5, 10, 15, 25, 35, 50, 75, 125, 200}),
                new ProfessionTalent("money_greed", "Money Greed+", "Bonus d'argent",
                        new int[]{2, 5, 10, 15, 25, 35, 50, 75, 125, 200})
        )));

        // Métier Commerçant
        professions.put("commercant", new Profession("commercant", "§6💰 §lCommerçant", "§6Maître de l'Économie",
                "Excellence dans la valorisation des ventes et exploitation des marchés", Arrays.asList(
                new ProfessionTalent("negotiations", "Négociations", "Augmentation nombre de générateurs",
                        new int[]{2, 5, 10, 15, 20, 25, 30, 35, 45, 60}),
                new ProfessionTalent("vitrines_sup", "Vitrines Sup.", "Slots HDV supplémentaires",
                        new int[]{0, 1, 1, 1, 1, 1, 1, 2, 2, 2}),
                new ProfessionTalent("sell_boost", "SellBoost", "Augmentation prix de vente",
                        new int[]{2, 5, 10, 15, 25, 35, 50, 75, 125, 200})
        )));

        // Métier Guerrier
        professions.put("guerrier", new Profession("guerrier", "§c⚔ §lGuerrier", "§cMaître du Combat",
                "Spécialisé dans le PvP, contrôle de l'avant-poste et équipement de combat", Arrays.asList(
                new ProfessionTalent("soldes", "Soldes", "Réduction prix marchand PvP",
                        new int[]{2, 5, 10, 15, 25, 35, 45, 55, 70, 85}),
                new ProfessionTalent("garde", "Garde", "Augmentation gains avant-poste",
                        new int[]{5, 15, 30, 50, 75, 125, 200, 300, 500, 750}),
                new ProfessionTalent("beacon_multiplier", "Bonus Minage Beacon", "Multiplicateur de beacons",
                        new int[]{1, 1, 1, 2, 2, 2, 3, 3, 3, 4}) // x1, x1.5, x2, x3 selon le niveau
        )));
    }

    /**
     * Vérifie si un joueur peut débloquer les métiers (rang F)
     */
    public boolean canUnlockProfessions(Player player) {
        String currentRank = plugin.getMineManager().getCurrentRank(player);
        return currentRank.charAt(0) >= 'f';
    }

    /**
     * Notifie un joueur qu'il peut débloquer les métiers
     */
    public void notifyProfessionUnlock(Player player) {
        if (canUnlockProfessions(player)) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (playerData.getActiveProfession() == null) {
                player.sendMessage("");
                player.sendMessage("§e🎯 §lFélicitations ! Vous avez débloqué le système de métiers !");
                player.sendMessage("§7Utilisez §a/metier §7pour choisir votre premier métier gratuitement.");
                player.sendMessage("");
            }
        }
    }

    /**
     * Définit le métier actif d'un joueur (premier choix gratuit)
     */
    public boolean setActiveProfession(Player player, String professionId) {
        if (!professions.containsKey(professionId.toLowerCase())) {
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentProfession = playerData.getActiveProfession();

        // Premier choix gratuit
        if (currentProfession == null) {
            playerData.setActiveProfession(professionId.toLowerCase());
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            Profession prof = professions.get(professionId.toLowerCase());
            player.sendMessage("§a✅ Vous avez choisi le métier §e" + prof.displayName() + " §a!");
            player.sendMessage("§7" + prof.description());
            return true;
        }

        return false; // Changement payant géré par /changemetier
    }

    /**
     * Change le métier actif (payant, cooldown)
     */
    public boolean changeProfession(Player player, String professionId) {
        if (!professions.containsKey(professionId.toLowerCase())) {
            player.sendMessage("§cMétier invalide ! Métiers disponibles: mineur, commercant, guerrier");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentProfession = playerData.getActiveProfession();

        if (currentProfession == null) {
            player.sendMessage("§cVous devez d'abord choisir un métier avec /metier");
            return false;
        }

        if (currentProfession.equals(professionId.toLowerCase())) {
            player.sendMessage("§cVous avez déjà ce métier actif !");
            return false;
        }

        // Vérification du cooldown (24h)
        long lastChange = playerData.getLastProfessionChange();
        long cooldownTime = 24 * 60 * 60 * 1000; // 24h en ms
        long timeLeft = (lastChange + cooldownTime) - System.currentTimeMillis();

        if (timeLeft > 0) {
            long hoursLeft = timeLeft / (60 * 60 * 1000);
            long minutesLeft = (timeLeft % (60 * 60 * 1000)) / (60 * 1000);
            player.sendMessage("§cCooldown actif ! Temps restant: §e" + hoursLeft + "h " + minutesLeft + "m");
            return false;
        }

        // Vérification du coût (5000 beacons)
        if (playerData.getBeacons() < 5000) {
            player.sendMessage("§cIl vous faut §e5000 beacons §cpour changer de métier !");
            player.sendMessage("§7Vous avez: §e" + NumberFormatter.format(playerData.getBeacons()) + " beacons");
            return false;
        }

        // Effectue le changement
        playerData.removeBeacon(5000);
        playerData.setActiveProfession(professionId.toLowerCase());
        playerData.setLastProfessionChange(System.currentTimeMillis());
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        Profession prof = professions.get(professionId.toLowerCase());
        player.sendMessage("§a✅ Métier changé vers §e" + prof.displayName() + " §a!");
        player.sendMessage("§7Coût: §c-5000 beacons");

        return true;
    }

    /**
     * Ajoute de l'XP métier à un joueur
     */
    public void addProfessionXP(Player player, String professionId, int xp) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null || !activeProfession.equals(professionId)) {
            return; // XP uniquement pour le métier actif
        }

        int currentXP = playerData.getProfessionXP(professionId);
        int currentLevel = playerData.getProfessionLevel(professionId);
        int newXP = currentXP + xp;

        playerData.setProfessionXP(professionId, newXP);

        // Vérification de montée de niveau
        int newLevel = calculateLevelFromXP(newXP);
        if (newLevel > currentLevel) {
            playerData.setProfessionLevel(professionId, newLevel);
            onProfessionLevelUp(player, professionId, newLevel);
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Ajoute de l'XP métier à un joueur en utilisant uniquement l'UUID (compatible hors-ligne)
     */
    public void addProfessionXP(UUID playerId, String professionId, int xp) {
        if (xp <= 0 || professionId == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null || !activeProfession.equals(professionId)) {
            return; // XP uniquement pour le métier actif
        }

        int currentXP = playerData.getProfessionXP(professionId);
        int currentLevel = playerData.getProfessionLevel(professionId);
        int newXP = currentXP + xp;

        playerData.setProfessionXP(professionId, newXP);

        // Vérification de montée de niveau (sans messages si le joueur est hors-ligne)
        int newLevel = calculateLevelFromXP(newXP);
        if (newLevel > currentLevel) {
            playerData.setProfessionLevel(professionId, newLevel);
        }

        plugin.getPlayerDataManager().markDirty(playerId);
        plugin.getPlayerDataManager().savePlayerData(playerId); // Sauvegarde
    }

    /**
     * Calcule le niveau basé sur l'XP (progression exponentielle)
     */
    private int calculateLevelFromXP(int xp) {
        int level = 1;
        int required = 100;
        int totalRequired = 0;

        while (level < 10 && xp >= totalRequired + required) {
            totalRequired += required;
            level++;
            required = required * 3; // Progression exponentielle
        }

        return level;
    }

    /**
     * Calcule l'XP requis pour le prochain niveau
     */
    public int getXPForNextLevel(int currentLevel) {
        if (currentLevel >= 10) return 0;

        int required = 100;
        int totalRequired = 0;

        for (int i = 1; i < currentLevel; i++) {
            totalRequired += required;
            required = required * 3;
        }

        return totalRequired + required;
    }

    /**
     * Gère la montée de niveau d'un métier
     */
    private void onProfessionLevelUp(Player player, String professionId, int newLevel) {
        Profession profession = professions.get(professionId);
        if (profession == null) return;

        player.sendMessage("");
        player.sendMessage("§e🎯 §lMétier: Niveau supérieur !");
        player.sendMessage("§7" + profession.displayName() + " §7→ §eNiveau " + newLevel);

        player.sendMessage("§7Récupérez votre récompense et débloqué un talent via /metier");

        player.sendMessage("");

        // Notification de déblocage: /repair pour Guerrier niveau 5+
        if ("guerrier".equalsIgnoreCase(professionId) && newLevel >= 5) {
            player.sendMessage("§6🔧 §eVous avez débloqué la commande §a/repair§e (répare l'item en main, cooldown 3h)!");
        }
    }

    /**
     * Active un talent (dépense d'XP joueur) - CORRIGÉ
     */
    public boolean activateTalent(Player player, String talentId, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) {
            player.sendMessage("§cVous devez avoir un métier actif !");
            return false;
        }

        Profession profession = professions.get(activeProfession);
        if (profession == null) return false;

        ProfessionTalent talent = profession.getTalent(talentId);
        if (talent == null) {
            player.sendMessage("§cTalent introuvable !");
            return false;
        }

        // Vérification du niveau de métier requis
        int professionLevel = playerData.getProfessionLevel(activeProfession);
        if (professionLevel < level) {
            player.sendMessage("§cNiveau de métier requis: §e" + level);
            player.sendMessage("§7Votre niveau actuel: §c" + professionLevel);
            return false;
        }

        // Vérification du niveau actuel du talent
        int currentTalentLevel = playerData.getTalentLevel(activeProfession, talentId);
        if (currentTalentLevel >= level) {
            player.sendMessage("§cVous avez déjà ce niveau de talent ou supérieur !");
            return false;
        }

        // NOUVELLE VÉRIFICATION: Doit avoir le niveau précédent (sauf pour le niveau 1)
        if (level > 1 && currentTalentLevel < level - 1) {
            player.sendMessage("§cVous devez d'abord activer le niveau " + (level - 1) + " !");
            player.sendMessage("§7Niveau actuel: §e" + currentTalentLevel);
            return false;
        }

        // Calcul du coût (exponentiel)
        long cost = calculateTalentCost(level);
        if (playerData.getExperience() < cost) {
            player.sendMessage("§cCoût en XP: §e" + NumberFormatter.format(cost));
            player.sendMessage("§7Votre XP: §c" + NumberFormatter.format(playerData.getExperience()));
            return false;
        }

        // Active le talent
        playerData.removeExperience(cost);
        playerData.setTalentLevel(activeProfession, talentId, level);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ Talent activé: §e" + talent.displayName() + " §7niveau §e" + level);
        player.sendMessage("§7Coût: §c-" + NumberFormatter.format(cost) + " XP");

        return true;
    }

    /**
     * Active un niveau de kit (dépense d'XP joueur) - CORRIGÉ
     */
    public boolean activateKit(Player player, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) {
            player.sendMessage("§cVous devez avoir un métier actif !");
            return false;
        }

        // Vérification du niveau de métier requis
        int professionLevel = playerData.getProfessionLevel(activeProfession);
        if (professionLevel < level) {
            player.sendMessage("§cNiveau de métier requis: §e" + level);
            player.sendMessage("§7Votre niveau actuel: §c" + professionLevel);
            return false;
        }

        // Vérification du niveau actuel du kit
        int currentKitLevel = playerData.getKitLevel(activeProfession);
        if (currentKitLevel >= level) {
            player.sendMessage("§cVous avez déjà ce niveau de kit ou supérieur !");
            return false;
        }

        // NOUVELLE VÉRIFICATION: Doit avoir le niveau précédent (sauf pour le niveau 1)
        if (level > 1 && currentKitLevel < level - 1) {
            player.sendMessage("§cVous devez d'abord activer le kit niveau " + (level - 1) + " !");
            player.sendMessage("§7Niveau actuel: §e" + currentKitLevel);
            return false;
        }

        // Calcul du coût (différent des talents)
        long cost = calculateKitCost(level);
        if (playerData.getExperience() < cost) {
            player.sendMessage("§cCoût en XP: §e" + NumberFormatter.format(cost));
            player.sendMessage("§7Votre XP: §c" + NumberFormatter.format(playerData.getExperience()));
            return false;
        }

        // Active le kit
        playerData.removeExperience(cost);
        playerData.setKitLevel(activeProfession, level);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ Kit amélioré: §6Niveau §e" + level);
        player.sendMessage("§7Coût: §c-" + NumberFormatter.format(cost) + " XP");

        return true;
    }

    /**
     * Calcule le coût d'un talent (exponentiel)
     */
    private long calculateTalentCost(int level) {
        return (long) (10000 * Math.pow(2, level - 1));
    }

    /**
     * NOUVEAU: Calcule le coût d'un kit (progression différente)
     */
    private long calculateKitCost(int level) {
        return (long) (20000 * Math.pow(1.8, level - 1)); // 2000, 3600, 6480, etc.
    }

    /**
     * Obtient un métier par son ID
     */
    public Profession getProfession(String professionId) {
        return professions.get(professionId.toLowerCase());
    }

    /**
     * Obtient tous les métiers
     */
    public Collection<Profession> getAllProfessions() {
        return professions.values();
    }

    // Classes internes

    /**
     * Représente un métier
     */
    public record Profession(String id, String displayName, String title, String description,
                             List<ProfessionTalent> talents) {

        public ProfessionTalent getTalent(String talentId) {
            return talents.stream().filter(t -> t.id().equals(talentId)).findFirst().orElse(null);
        }
    }

    /**
     * Représente un talent de métier
     *
     * @param values Valeurs pour les niveaux 1-10
     */
    public record ProfessionTalent(String id, String displayName, String description, int[] values) {

        public int getValueAtLevel(int level) {
            if (level < 1 || level > values.length) return 0;
            return values[level - 1];
        }
    }
}