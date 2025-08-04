package fr.prisontycoon.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.gangs.GangBoostType;
import fr.prisontycoon.gangs.GangRole;
import fr.prisontycoon.gangs.GangTalent;
import fr.prisontycoon.utils.PatternAdapter;
import org.bukkit.Bukkit;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire principal du système de gangs
 */
public class GangManager {

    private final PrisonTycoon plugin;
    private final Map<String, Gang> gangs = new ConcurrentHashMap<>(); // gangId -> Gang
    private final Map<String, String> gangNameToId = new ConcurrentHashMap<>(); // nom -> id
    private final Map<String, String> gangTagToId = new ConcurrentHashMap<>(); // tag -> id
    private final Map<UUID, String> pendingInvitations = new ConcurrentHashMap<>(); // playerId -> gangId
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Pattern.class, new PatternAdapter())
            .create();    private final Map<String, GangTalent> talents = new ConcurrentHashMap<>();

    // Coûts des niveaux de gang (index 0 = création, index 1 = niveau 2, etc.)
    private final long[] LEVEL_COSTS = {
            10_000L,        // Création (niveau 1)
            50_000_000L,    // Niveau 2
            150_000_000L,   // Niveau 3
            500_000_000L,   // Niveau 4
            1_500_000_000L, // Niveau 5
            5_000_000_000L, // Niveau 6
            15_000_000_000L,// Niveau 7
            40_000_000_000L,// Niveau 8
            100_000_000_000L,// Niveau 9
            250_000_000_000L // Niveau 10
    };

    public GangManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        initializeTalents();
        loadAllGangs();
        startAutoSaveTask();
    }

    /**
     * Initialise les talents de gang disponibles
     */
    private void initializeTalents() {
        // SellBoost talents
        for (int i = 1; i <= 10; i++) {
            long cost = 5_000_000L * (long) Math.pow(2, i - 1); // 5M, 10M, 20M, 40M, etc.
            if (i >= 8) cost *= 5; // Coûts plus élevés pour les niveaux 8-10

            talents.put("sell_boost_" + i, new GangTalent(
                    "sell_boost_" + i,
                    "Boost Vente +" + i + "%",
                    "Augmente les prix de vente de " + i + "%",
                    cost,
                    i, // niveau de gang requis
                    "sell_boost",
                    i
            ));
        }

        // Gang Collectif talents (places)
        int[] placeRequiredLevels = {2, 4, 6, 8, 10};
        long[] placeCosts = {25_000_000L, 50_000_000L, 75_000_000L, 100_000_000L, 100_000_000L};

        for (int i = 0; i < 5; i++) {
            talents.put("gang_collectif_" + (i + 1), new GangTalent(
                    "gang_collectif_" + (i + 1),
                    "Gang Collectif +" + (i + 1),
                    "Ajoute " + (i + 1) + " place(s) supplémentaire(s) au gang",
                    placeCosts[i],
                    placeRequiredLevels[i],
                    "gang_collectif",
                    i + 1
            ));
        }

        // Beacon Multiplier talents
        int[] beaconRequiredLevels = {3, 5, 7, 9, 10};
        double[] beaconMultipliers = {1.25, 1.5, 1.75, 2.0, 2.5};
        long[] beaconCosts = {100_000_000L, 150_000_000L, 200_000_000L, 250_000_000L, 200_000_000L};

        for (int i = 0; i < 5; i++) {
            talents.put("beacon_multiplier_" + (i + 1), new GangTalent(
                    "beacon_multiplier_" + (i + 1),
                    "Multiplicateur Beacon x" + beaconMultipliers[i],
                    "Multiplie les gains de beacons en mine PvP par " + beaconMultipliers[i],
                    beaconCosts[i],
                    beaconRequiredLevels[i],
                    "beacon_multiplier",
                    beaconMultipliers[i]
            ));
        }
    }

    /**
     * Charge tous les gangs depuis la base de données
     */
    private void loadAllGangs() {
        createGangTable();

        String query = "SELECT * FROM gangs";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Gang gang = loadGangFromResultSet(rs);
                if (gang != null) {
                    gangs.put(gang.getId(), gang);
                    gangNameToId.put(gang.getName().toLowerCase(), gang.getId());
                    gangTagToId.put(gang.getTag().toLowerCase(), gang.getId());
                }
            }

            plugin.getPluginLogger().info("Chargé " + gangs.size() + " gangs depuis la base de données.");

        } catch (SQLException e) {
            plugin.getPluginLogger().severe("Erreur lors du chargement des gangs: " + e.getMessage());
        }
    }

    /**
     * Crée la table des gangs si elle n'existe pas
     */
    private void createGangTable() {
        String query = """
                    CREATE TABLE IF NOT EXISTS gangs (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(16) NOT NULL UNIQUE,
                        tag VARCHAR(6) NOT NULL UNIQUE,
                        leader VARCHAR(36) NOT NULL,
                        members TEXT,
                        level INT DEFAULT 1,
                        bank_balance BIGINT DEFAULT 0,
                        creation_date BIGINT,
                        description TEXT,
                        talents TEXT,
                        banner_patterns TEXT
                    )
                """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getPluginLogger().severe("Erreur lors de la création de la table gangs: " + e.getMessage());
        }
    }

    /**
     * Charge un gang depuis un ResultSet
     */
    private Gang loadGangFromResultSet(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String name = rs.getString("name");
        String tag = rs.getString("tag");
        UUID leader = UUID.fromString(rs.getString("leader"));
        int level = rs.getInt("level");
        long bankBalance = rs.getLong("bank_balance");
        long creationDate = rs.getLong("creation_date");
        String description = rs.getString("description");

        Gang gang = new Gang(id, name, tag, leader, creationDate);
        gang.setLevel(level);
        gang.setBankBalance(bankBalance);
        gang.setDescription(description);

        // Charger les membres
        String membersJson = rs.getString("members");
        if (membersJson != null && !membersJson.isEmpty()) {
            Map<UUID, GangRole> members = gson.fromJson(membersJson,
                    new com.google.gson.reflect.TypeToken<Map<UUID, GangRole>>() {
                    }.getType());
            if (members != null) {
                gang.setMembers(members);
            }
        }

        // Charger les talents
        String talentsJson = rs.getString("talents");
        if (talentsJson != null && !talentsJson.isEmpty()) {
            Map<String, Integer> talents = gson.fromJson(talentsJson,
                    new com.google.gson.reflect.TypeToken<Map<String, Integer>>() {
                    }.getType());
            if (talents != null) {
                gang.setTalents(talents);
            }
        }

        // Charger les motifs de bannière
        String bannerJson = rs.getString("banner_patterns");
        if (bannerJson != null && !bannerJson.isEmpty()) {
            try {
                List<org.bukkit.block.banner.Pattern> patterns = gson.fromJson(bannerJson,
                        new com.google.gson.reflect.TypeToken<List<org.bukkit.block.banner.Pattern>>() {
                        }.getType());
                gang.setBannerPatterns(patterns);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors du chargement des motifs de bannière pour " + name + ": " + e.getMessage());
            }
        }

        return gang;
    }

    /**
     * Sauvegarde un gang dans la base de données
     */
    public void saveGang(Gang gang) {
        String query = """
                    INSERT INTO gangs (id, name, tag, leader, members, level, bank_balance, creation_date, description, talents, banner_patterns)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        name = EXCLUDED.name,
                        tag = EXCLUDED.tag,
                        leader = EXCLUDED.leader,
                        members = EXCLUDED.members,
                        level = EXCLUDED.level,
                        bank_balance = EXCLUDED.bank_balance,
                        description = EXCLUDED.description,
                        talents = EXCLUDED.talents,
                        banner_patterns = EXCLUDED.banner_patterns
                """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, gang.getId());
            ps.setString(2, gang.getName());
            ps.setString(3, gang.getTag());
            ps.setString(4, gang.getLeader().toString());
            ps.setString(5, gson.toJson(gang.getMembers()));
            ps.setInt(6, gang.getLevel());
            ps.setLong(7, gang.getBankBalance());
            ps.setLong(8, gang.getCreationDate());
            ps.setString(9, gang.getDescription());
            ps.setString(10, gson.toJson(gang.getTalents()));
            ps.setString(11, gang.getBannerPatterns() != null ? gson.toJson(gang.getBannerPatterns()) : null);

            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getPluginLogger().severe("Erreur lors de la sauvegarde du gang " + gang.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Crée un nouveau gang
     */
    public boolean createGang(Player leader, String name, String tag) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(leader.getUniqueId());

        // Vérifications
        if (playerData.getGangId() != null) {
            leader.sendMessage("§c❌ Vous êtes déjà dans un gang!");
            return false;
        }

        if (playerData.getBeacons() < 10000) {
            leader.sendMessage("§c❌ Vous n'avez pas assez de beacons! (10,000 requis)");
            return false;
        }

        if (gangExists(name, tag)) {
            leader.sendMessage("§c❌ Un gang avec ce nom ou tag existe déjà!");
            return false;
        }

        // Retirer les beacons
        playerData.removeBeacon(10000);

        // Créer le gang
        String gangId = UUID.randomUUID().toString();
        Gang gang = new Gang(gangId, name, tag, leader.getUniqueId(), System.currentTimeMillis());
        gang.addMember(leader.getUniqueId(), GangRole.CHEF);

        // Ajouter aux maps
        gangs.put(gangId, gang);
        gangNameToId.put(name.toLowerCase(), gangId);
        gangTagToId.put(tag.toLowerCase(), gangId);

        // Mettre à jour les données du joueur
        playerData.setGangId(gangId);
        plugin.getPlayerDataManager().markDirty(leader.getUniqueId());

        // Sauvegarder
        saveGang(gang);

        plugin.getPluginLogger().info("Gang créé: " + name + " [" + tag + "] par " + leader.getName());
        return true;
    }

    /**
     * Vérifie si un gang existe avec ce nom ou tag
     */
    public boolean gangExists(String name, String tag) {
        return gangNameToId.containsKey(name.toLowerCase()) ||
                gangTagToId.containsKey(tag.toLowerCase());
    }

    /**
     * Invite un joueur dans un gang
     */
    public boolean invitePlayer(Gang gang, Player inviter, Player target) {
        PlayerData targetData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());

        // Vérifications
        if (targetData.getGangId() != null) {
            inviter.sendMessage("§c❌ " + target.getName() + " est déjà dans un gang!");
            return false;
        }

        if (gang.getMembers().size() >= gang.getMaxMembers()) {
            inviter.sendMessage("§c❌ Le gang est plein! (" + gang.getMaxMembers() + " membres max)");
            return false;
        }

        if (pendingInvitations.containsKey(target.getUniqueId())) {
            inviter.sendMessage("§c❌ " + target.getName() + " a déjà une invitation en attente!");
            return false;
        }

        // Créer l'invitation
        pendingInvitations.put(target.getUniqueId(), gang.getId());
        targetData.setGangInvitation(gang.getId());
        plugin.getPlayerDataManager().markDirty(target.getUniqueId());

        // Programmer l'expiration de l'invitation (5 minutes)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingInvitations.remove(target.getUniqueId()) != null) {
                    targetData.setGangInvitation(null);
                    plugin.getPlayerDataManager().markDirty(target.getUniqueId());

                    if (target.isOnline()) {
                        target.sendMessage("§c⏰ Votre invitation du gang " + gang.getName() + " a expiré.");
                    }
                }
            }
        }.runTaskLater(plugin, 20L * 60 * 5); // 5 minutes

        return true;
    }

    /**
     * Accepte une invitation de gang
     */
    public boolean acceptInvite(Player player) {
        UUID playerId = player.getUniqueId();
        String gangId = pendingInvitations.remove(playerId);

        if (gangId == null) {
            player.sendMessage("§c❌ Aucune invitation en attente!");
            return false;
        }

        Gang gang = gangs.get(gangId);
        if (gang == null) {
            player.sendMessage("§c❌ Le gang n'existe plus!");
            return false;
        }

        if (gang.getMembers().size() >= gang.getMaxMembers()) {
            player.sendMessage("§c❌ Le gang est maintenant plein!");
            return false;
        }

        // Ajouter le joueur au gang
        gang.addMember(playerId, GangRole.MEMBRE);

        // Mettre à jour les données du joueur
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        playerData.setGangId(gangId);
        playerData.setGangInvitation(null);
        plugin.getPlayerDataManager().markDirty(playerId);

        // Sauvegarder
        saveGang(gang);

        // Messages
        player.sendMessage("§a✅ Vous avez rejoint le gang §e" + gang.getName() + " §7[§e" + gang.getTag() + "§7]§a!");
        gang.broadcast("§a🎉 " + player.getName() + " §aa rejoint le gang!", player);

        plugin.getPluginLogger().info(player.getName() + " a rejoint le gang " + gang.getName());
        return true;
    }

    /**
     * Refuse une invitation de gang
     */
    public boolean denyInvite(Player player) {
        UUID playerId = player.getUniqueId();
        String gangId = pendingInvitations.remove(playerId);

        if (gangId == null) {
            return false;
        }

        // Nettoyer les données du joueur
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        playerData.setGangInvitation(null);
        plugin.getPlayerDataManager().markDirty(playerId);

        return true;
    }

    /**
     * Retire un joueur d'un gang
     */
    public boolean removePlayer(Gang gang, UUID playerId) {
        if (!gang.getMembers().containsKey(playerId)) {
            return false;
        }

        // Retirer du gang
        gang.removeMember(playerId);

        // Mettre à jour les données du joueur
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        playerData.setGangId(null);
        plugin.getPlayerDataManager().markDirty(playerId);

        // Sauvegarder
        saveGang(gang);

        return true;
    }

    /**
     * Exclut un joueur d'un gang
     */
    public boolean kickPlayer(Gang gang, Player kicker, String targetName) {
        UUID targetId = getPlayerUUIDByName(targetName);
        if (targetId == null) {
            kicker.sendMessage("§c❌ Joueur introuvable: " + targetName);
            return false;
        }

        if (!gang.getMembers().containsKey(targetId)) {
            kicker.sendMessage("§c❌ " + targetName + " n'est pas membre du gang!");
            return false;
        }

        if (targetId.equals(gang.getLeader())) {
            kicker.sendMessage("§c❌ Impossible d'exclure le chef du gang!");
            return false;
        }

        GangRole kickerRole = gang.getMemberRole(kicker.getUniqueId());
        GangRole targetRole = gang.getMemberRole(targetId);

        // Un officier ne peut pas exclure un autre officier
        if (kickerRole == GangRole.OFFICIER && targetRole == GangRole.OFFICIER) {
            kicker.sendMessage("§c❌ Vous ne pouvez pas exclure un autre officier!");
            return false;
        }

        // Retirer le joueur
        removePlayer(gang, targetId);

        // Notifier le gang
        gang.broadcast("§c" + targetName + " §ca été exclu du gang par §e" + kicker.getName() + "§c.", kicker);

        plugin.getPluginLogger().info(targetName + " a été exclu du gang " + gang.getName() + " par " + kicker.getName());
        return true;
    }

    /**
     * Promeut un joueur en officier
     */
    public boolean promotePlayer(Gang gang, String targetName) {
        UUID targetId = getPlayerUUIDByName(targetName);
        if (targetId == null) {
            return false;
        }

        GangRole currentRole = gang.getMemberRole(targetId);
        if (currentRole != GangRole.MEMBRE) {
            return false;
        }

        // Vérifier le nombre d'officiers (max 4)
        long officierCount = gang.getMembers().values().stream()
                .filter(role -> role == GangRole.OFFICIER)
                .count();

        if (officierCount >= 4) {
            return false; // Maximum 4 officiers
        }

        gang.setMemberRole(targetId, GangRole.OFFICIER);
        saveGang(gang);
        return true;
    }

    /**
     * Rétrograde un officier en membre
     */
    public boolean demotePlayer(Gang gang, String targetName) {
        UUID targetId = getPlayerUUIDByName(targetName);
        if (targetId == null) {
            return false;
        }

        GangRole currentRole = gang.getMemberRole(targetId);
        if (currentRole != GangRole.OFFICIER) {
            return false;
        }

        gang.setMemberRole(targetId, GangRole.MEMBRE);
        saveGang(gang);
        return true;
    }

    /**
     * Transfère le leadership du gang
     */
    public boolean transferLeadership(Gang gang, String targetName) {
        UUID targetId = getPlayerUUIDByName(targetName);
        if (targetId == null) {
            return false;
        }

        if (!gang.getMembers().containsKey(targetId)) {
            return false;
        }

        UUID oldLeader = gang.getLeader();

        // Transférer le leadership
        gang.setLeader(targetId);
        gang.setMemberRole(targetId, GangRole.CHEF);
        gang.setMemberRole(oldLeader, GangRole.OFFICIER);

        saveGang(gang);
        return true;
    }

    /**
     * Dissout un gang
     */
    public boolean disbandGang(Gang gang) {
        // Retirer tous les membres
        for (UUID memberId : new HashSet<>(gang.getMembers().keySet())) {
            PlayerData memberData = plugin.getPlayerDataManager().getPlayerData(memberId);
            memberData.setGangId(null);
            plugin.getPlayerDataManager().markDirty(memberId);
        }

        // Retirer des maps
        gangs.remove(gang.getId());
        gangNameToId.remove(gang.getName().toLowerCase());
        gangTagToId.remove(gang.getTag().toLowerCase());

        // Supprimer de la base de données
        deleteGangFromDatabase(gang.getId());

        // Notifier
        gang.broadcast("§c💥 Le gang a été dissous par le chef!", null);

        plugin.getPluginLogger().info("Gang dissous: " + gang.getName());
        return true;
    }

    /**
     * Renomme un gang
     */
    public boolean renameGang(Gang gang, String newName) {
        if (newName.length() < 3 || newName.length() > 16) {
            return false;
        }

        if (!newName.matches("[a-zA-Z0-9_]+")) {
            return false;
        }

        if (gangNameToId.containsKey(newName.toLowerCase())) {
            return false;
        }

        Player leader = Bukkit.getPlayer(gang.getLeader());
        if (leader == null) {
            return false;
        }

        PlayerData leaderData = plugin.getPlayerDataManager().getPlayerData(leader.getUniqueId());
        if (leaderData.getBeacons() < 5000) {
            leader.sendMessage("§c❌ Renommer un gang coûte 5,000 beacons!");
            return false;
        }

        // Retirer les beacons
        leaderData.removeBeacon(5000);

        // Renommer
        String oldName = gang.getName();
        gangNameToId.remove(oldName.toLowerCase());
        gang.setName(newName);
        gangNameToId.put(newName.toLowerCase(), gang.getId());

        saveGang(gang);
        return true;
    }

    /**
     * Dépose de l'argent dans la banque du gang
     */
    public boolean depositToBank(Gang gang, Player player, long amount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getCoins() < amount) {
            player.sendMessage("§c❌ Vous n'avez pas assez de coins!");
            return false;
        }

        // Retirer les coins du joueur
        playerData.removeCoins(amount);

        // Ajouter à la banque du gang
        gang.setBankBalance(gang.getBankBalance() + amount);
        saveGang(gang);

        return true;
    }

    /**
     * Améliore le niveau du gang
     */
    public boolean upgradeGang(Gang gang) {
        if (gang.getLevel() >= 10) {
            return false; // Niveau maximum
        }

        long cost = getUpgradeCost(gang.getLevel() + 1);
        if (gang.getBankBalance() < cost) {
            return false; // Fonds insuffisants
        }

        // Déduire le coût
        gang.setBankBalance(gang.getBankBalance() - cost);
        gang.setLevel(gang.getLevel() + 1);

        saveGang(gang);

        // Notifier les membres
        gang.broadcast("§6🎉 Le gang a été amélioré au niveau §e" + gang.getLevel() + "§6!", null);

        return true;
    }

    /**
     * Achète un talent de gang
     */
    public boolean buyTalent(Gang gang, Player buyer, String talentId) {
        GangTalent talent = talents.get(talentId);
        if (talent == null) {
            return false;
        }

        // Vérifier les prérequis
        if (gang.getLevel() < talent.getRequiredGangLevel()) {
            buyer.sendMessage("§c❌ Niveau de gang insuffisant! (Requis: " + talent.getRequiredGangLevel() + ")");
            return false;
        }

        if (gang.getBankBalance() < talent.getCost()) {
            buyer.sendMessage("§c❌ Fonds insuffisants dans la banque du gang!");
            return false;
        }

        // Vérifier si déjà acheté
        if (gang.getTalents().containsKey(talentId)) {
            buyer.sendMessage("§c❌ Ce talent est déjà acheté!");
            return false;
        }

        // Acheter le talent
        gang.setBankBalance(gang.getBankBalance() - talent.getCost());
        gang.getTalents().put(talentId, talent.getLevel());

        saveGang(gang);

        // Notifier
        gang.broadcast("§5🎯 Nouveau talent acheté: §e" + talent.getName() + " §5par §e" + buyer.getName() + "§5!", null);

        return true;
    }

    /**
     * Active un boost temporaire de gang
     */
    public boolean activateGangBoost(Gang gang, Player activator, GangBoostType boostType, int tier) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(activator.getUniqueId());

        long cost = boostType.getCosts()[tier - 1];
        if (playerData.getBeacons() < cost) {
            activator.sendMessage("§c❌ Vous n'avez pas assez de beacons!");
            return false;
        }

        // Vérifier le cooldown et limite quotidienne
        if (!gang.canActivateBoost(boostType)) {
            activator.sendMessage("§c❌ Ce boost est en cooldown ou la limite quotidienne est atteinte!");
            return false;
        }

        // Retirer les beacons
        playerData.removeBeacon(cost);

        // Activer le boost
        gang.activateBoost(boostType, tier, activator.getUniqueId());
        saveGang(gang);

        // Appliquer le boost via BoostManager
        plugin.getBoostManager().addGangBoost(gang, boostType, tier);

        // Notifier
        String[] multipliers = {"1.5x", "2x", "3x"};
        gang.broadcast("§6⚡ Boost " + boostType.getDisplayName() + " " + multipliers[tier - 1] +
                " §6activé par §e" + activator.getName() + "§6!", null);

        return true;
    }

    // Méthodes utilitaires

    /**
     * Obtient le coût d'amélioration pour un niveau donné
     */
    public long getUpgradeCost(int level) {
        if (level < 1 || level > 10) return 0;
        return LEVEL_COSTS[level - 1];
    }

    /**
     * Obtient les avantages d'un niveau de gang
     */
    public List<String> getLevelBenefits(int level) {
        List<String> benefits = new ArrayList<>();

        switch (level) {
            case 1 -> benefits.add("§7• Gang constitué");
            case 2 -> {
                benefits.add("§7• +3% prix de vente");
                benefits.add("§7• Accès Guerre des Gangs");
                benefits.add("§7• +1 place membre (total 4)");
            }
            case 3 -> {
                benefits.add("§7• +5% prix de vente");
                benefits.add("§7• +1 place membre (total 5)");
            }
            case 4 -> benefits.add("§7• +10% prix de vente");
            case 5 -> {
                benefits.add("§7• +15% prix de vente");
                benefits.add("§7• Parcelle de Gang");
                benefits.add("§7• +1 place membre (total 6)");
            }
            case 6 -> {
                benefits.add("§7• +25% prix de vente");
                benefits.add("§7• +1 place membre (total 7)");
            }
            case 7 -> benefits.add("§7• +35% prix de vente");
            case 8 -> {
                benefits.add("§7• +50% prix de vente");
                benefits.add("§7• Extension Parcelle");
                benefits.add("§7• +1 place membre (total 8)");
            }
            case 9 -> {
                benefits.add("§7• +75% prix de vente");
                benefits.add("§7• +1 place membre (total 9)");
            }
            case 10 -> {
                benefits.add("§7• +100% prix de vente");
                benefits.add("§7• Cosmétiques de Gang");
                benefits.add("§7• +1 place membre (total 10)");
            }
        }

        return benefits;
    }

    /**
     * Obtient le bonus de vente pour un niveau donné
     */
    public int getSellBonus(int level) {
        int[] bonuses = {0, 3, 5, 10, 15, 25, 35, 50, 75, 100};
        if (level < 1 || level > 10) return 0;
        return bonuses[level - 1];
    }

    /**
     * Calcule le multiplicateur de vente total d'un gang
     */
    public double getTotalSellMultiplier(Gang gang) {
        double multiplier = 1.0;

        // Bonus de niveau
        multiplier += getSellBonus(gang.getLevel()) / 100.0;

        // Bonus des talents
        for (Map.Entry<String, Integer> entry : gang.getTalents().entrySet()) {
            if (entry.getKey().startsWith("sell_boost_")) {
                multiplier += entry.getValue() / 100.0;
            }
        }

        return multiplier;
    }

    /**
     * Obtient le multiplicateur de beacons d'un gang
     */
    public double getBeaconMultiplier(Gang gang) {
        double multiplier = 1.0;

        for (Map.Entry<String, Integer> entry : gang.getTalents().entrySet()) {
            if (entry.getKey().startsWith("beacon_multiplier_")) {
                GangTalent talent = talents.get(entry.getKey());
                if (talent != null) {
                    multiplier = Math.max(multiplier, talent.getValue());
                }
            }
        }

        return multiplier;
    }

    // Getters
    public Gang getGang(String gangId) {
        return gangs.get(gangId);
    }

    public Gang getGangByName(String name) {
        String gangId = gangNameToId.get(name.toLowerCase());
        return gangId != null ? gangs.get(gangId) : null;
    }

    public Gang getGangByTag(String tag) {
        String gangId = gangTagToId.get(tag.toLowerCase());
        return gangId != null ? gangs.get(gangId) : null;
    }

    public List<Gang> getAllGangs() {
        return new ArrayList<>(gangs.values());
    }

    public List<String> getAllGangNames() {
        return gangs.values().stream()
                .map(Gang::getName)
                .toList();
    }

    public GangTalent getTalent(String talentId) {
        return talents.get(talentId);
    }

    public Collection<GangTalent> getAllTalents() {
        return talents.values();
    }

    // Méthodes utilitaires privées

    private UUID getPlayerUUIDByName(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }
        return null;
    }

    private void deleteGangFromDatabase(String gangId) {
        String query = "DELETE FROM gangs WHERE id = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, gangId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getPluginLogger().severe("Erreur lors de la suppression du gang " + gangId + ": " + e.getMessage());
        }
    }

    /**
     * Tâche de sauvegarde automatique
     */
    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Gang gang : gangs.values()) {
                    saveGang(gang);
                }
                plugin.getPluginLogger().debug("Sauvegarde automatique des gangs effectuée.");
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60 * 5, 20L * 60 * 5); // Toutes les 5 minutes
    }

    /**
     * Fermeture du manager
     */
    public void shutdown() {
        // Sauvegarder tous les gangs
        for (Gang gang : gangs.values()) {
            saveGang(gang);
        }
        plugin.getPluginLogger().info("GangManager fermé, " + gangs.size() + " gangs sauvegardés.");
    }
}