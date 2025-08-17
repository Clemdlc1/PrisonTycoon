package fr.prisontycoon.api;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.gangs.GangRole;
import fr.prisontycoon.managers.GangManager;
import fr.prisontycoon.managers.GlobalBonusManager;
import fr.prisontycoon.managers.MineManager;
import fr.prisontycoon.managers.PlayerDataManager;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * API Hook pour le système économique de PrisonTycoon
 * Permet aux autres plugins d'interagir avec l'économie via PlayerData
 */
public class PrisonTycoonAPI {

    private static PrisonTycoonAPI instance;
    private final PrisonTycoon plugin;
    private final PlayerDataManager playerDataManager;
    private final MineManager mineManager; // Ajout du MineManager
    private final GangManager gangManager; // Ajout du GangManager


    private PrisonTycoonAPI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerDataManager = plugin.getPlayerDataManager();
        this.mineManager = plugin.getMineManager(); // Initialisation du MineManager
        this.gangManager = plugin.getGangManager(); // Initialisation
    }

    /**
     * Initialise l'API (à appeler dans onEnable du plugin principal)
     */
    public static void initialize(PrisonTycoon plugin) {
        if (instance == null) {
            instance = new PrisonTycoonAPI(plugin);
            plugin.getPluginLogger().info("§aPrisonTycoonAPI initialisée et disponible pour les autres plugins.");
        }
    }

    /**
     * Récupère l'instance de l'API
     */
    public static PrisonTycoonAPI getInstance() {
        return instance;
    }

    /**
     * Vérifie si l'API est disponible
     */
    public static boolean isAvailable() {
        return instance != null;
    }

    // ===============================
    // MÉTHODES POUR LES COINS
    // ===============================

    /**
     * Ajoute des coins à un joueur
     *
     * @param player Le joueur (en ligne)
     * @param amount Le montant à ajouter
     * @return true si succès, false sinon
     */
    public boolean addCoins(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        playerData.addCoins(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Ajoute des coins à un joueur (hors ligne)
     *
     * @param playerId UUID du joueur
     * @param amount   Le montant à ajouter
     * @return true si succès, false sinon
     */
    public boolean addCoins(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.addCoins(amount);
        playerDataManager.saveSingleColumn(playerId, "coins");
        return true;
    }

    /**
     * Retire des coins à un joueur
     *
     * @param player Le joueur (en ligne)
     * @param amount Le montant à retirer
     * @return true si succès, false sinon
     */
    public boolean removeCoins(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        // Vérifier si le joueur a assez de coins
        if (playerData.getCoins() < amount) {
            return false;
        }

        playerData.removeCoins(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Retire des coins à un joueur (hors ligne)
     *
     * @param playerId UUID du joueur
     * @param amount   Le montant à retirer
     * @return true si succès, false sinon
     */
    public boolean removeCoins(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        // Vérifier si le joueur a assez de coins
        if (playerData.getCoins() < amount) {
            return false;
        }

        playerData.removeCoins(amount);
        playerDataManager.saveSingleColumn(playerId, "coins");
        return true;
    }

    /**
     * Récupère les coins d'un joueur
     *
     * @param player Le joueur (en ligne)
     * @return Le montant de coins
     */
    public long getCoins(Player player) {
        if (player == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        return playerData != null ? playerData.getCoins() : 0;
    }

    /**
     * Récupère les coins d'un joueur (hors ligne)
     *
     * @param playerId UUID du joueur
     * @return Le montant de coins
     */
    public long getCoins(UUID playerId) {
        if (playerId == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        return playerData != null ? playerData.getCoins() : 0;
    }

    /**
     * Définit les coins d'un joueur
     *
     * @param playerId UUID du joueur
     * @param amount   Le nouveau montant
     * @return true si succès
     */
    public boolean setCoins(UUID playerId, long amount) {
        if (playerId == null || amount < 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setCoins(amount);
        playerDataManager.markDirty(playerId);
        return true;
    }

    // ===============================
    // MÉTHODES POUR LES TOKENS
    // ===============================

    /**
     * Ajoute des tokens à un joueur
     */
    public boolean addTokens(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        playerData.addTokens(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Ajoute des tokens à un joueur (hors ligne)
     */
    public boolean addTokens(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.addTokens(amount);
        playerDataManager.saveSingleColumn(playerId, "tokens");
        return true;
    }

    /**
     * Retire des tokens à un joueur
     */
    public boolean removeTokens(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        boolean success = playerData.removeTokens(amount);
        if (success) {
            playerDataManager.markDirty(player.getUniqueId());
        }
        return success;
    }

    /**
     * Retire des tokens à un joueur (hors ligne)
     */
    public boolean removeTokens(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        boolean success = playerData.removeTokens(amount);
        if (success) {
            playerDataManager.saveSingleColumn(playerId, "tokens");
        }
        return success;
    }

    /**
     * Récupère les tokens d'un joueur
     */
    public long getTokens(Player player) {
        if (player == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        return playerData != null ? playerData.getTokens() : 0;
    }

    /**
     * Récupère les tokens d'un joueur (hors ligne)
     */
    public long getTokens(UUID playerId) {
        if (playerId == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        return playerData != null ? playerData.getTokens() : 0;
    }

    /**
     * Définit les tokens d'un joueur
     */
    public boolean setTokens(UUID playerId, long amount) {
        if (playerId == null || amount < 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setTokens(amount);
        playerDataManager.markDirty(playerId);
        return true;
    }

    // ===============================
    // MÉTHODES POUR LES BEACONS
    // ===============================

    /**
     * Ajoute des beacons à un joueur
     */
    public boolean addBeacons(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        playerData.addBeacons(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Ajoute des beacons à un joueur (hors ligne)
     */
    public boolean addBeacons(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.addBeacons(amount);
        playerDataManager.saveSingleColumn(playerId, "beacons");
        return true;
    }

    /**
     * Retire des beacons à un joueur
     */
    public boolean removeBeacons(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        // Vérifier si le joueur a assez de beacons
        if (playerData.getBeacons() < amount) {
            return false;
        }

        playerData.removeBeacon(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Retire des beacons à un joueur (hors ligne)
     */
    public boolean removeBeacons(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        // Vérifier si le joueur a assez de beacons
        if (playerData.getBeacons() < amount) {
            return false;
        }

        playerData.removeBeacon(amount);
        playerDataManager.saveSingleColumn(playerId, "beacons");
        return true;
    }

    /**
     * Récupère les beacons d'un joueur
     */
    public long getBeacons(Player player) {
        if (player == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        return playerData != null ? playerData.getBeacons() : 0;
    }

    /**
     * Récupère les beacons d'un joueur (hors ligne)
     */
    public long getBeacons(UUID playerId) {
        if (playerId == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        return playerData != null ? playerData.getBeacons() : 0;
    }

    /**
     * Définit les beacons d'un joueur
     */
    public boolean setBeacons(UUID playerId, long amount) {
        if (playerId == null || amount < 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setBeacons(amount);
        playerDataManager.saveSingleColumn(playerId, "beacons");
        return true;
    }

    // ===============================
    // MÉTHODES POUR L'EXPÉRIENCE
    // ===============================

    /**
     * Ajoute de l'expérience à un joueur
     */
    public boolean addExperience(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        playerData.addExperience(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Ajoute de l'expérience à un joueur (hors ligne)
     */
    public boolean addExperience(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.addExperience(amount);
        playerDataManager.saveSingleColumn(playerId, "experience");
        return true;
    }

    /**
     * Retire de l'expérience à un joueur
     */
    public boolean removeExperience(Player player, long amount) {
        if (player == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        // Vérifier si le joueur a assez d'expérience
        if (playerData.getExperience() < amount) {
            return false;
        }

        playerData.removeExperience(amount);
        playerDataManager.markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Retire de l'expérience à un joueur (hors ligne)
     */
    public boolean removeExperience(UUID playerId, long amount) {
        if (playerId == null || amount <= 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        // Vérifier si le joueur a assez d'expérience
        if (playerData.getExperience() < amount) {
            return false;
        }

        playerData.removeExperience(amount);
        playerDataManager.saveSingleColumn(playerId, "experience");
        return true;
    }

    /**
     * Récupère l'expérience d'un joueur
     */
    public long getExperience(Player player) {
        if (player == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(player.getUniqueId());
        return playerData != null ? playerData.getExperience() : 0;
    }

    /**
     * Récupère l'expérience d'un joueur (hors ligne)
     */
    public long getExperience(UUID playerId) {
        if (playerId == null) return 0;
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        return playerData != null ? playerData.getExperience() : 0;
    }

    /**
     * Définit l'expérience d'un joueur
     */
    public boolean setExperience(UUID playerId, long amount) {
        if (playerId == null || amount < 0) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setExperience(amount);
        playerDataManager.saveSingleColumn(playerId, "experience");
        return true;
    }

    // ===============================
    // MÉTHODES POUR LES PERMISSIONS
    // ===============================

    /**
     * Ajoute une permission custom à un joueur
     *
     * @param playerId   UUID du joueur
     * @param permission La permission à ajouter
     * @return true si succès
     */
    public boolean addPermission(UUID playerId, String permission) {
        if (playerId == null || permission == null || permission.isEmpty()) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.addPermission(permission);
        playerDataManager.markDirty(playerId);

        // Si le joueur est en ligne, appliquer la permission immédiatement
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            plugin.getPermissionManager().attachPermission(player, permission);
        }

        return true;
    }

    /**
     * Ajoute une permission custom à un joueur (en ligne)
     */
    public boolean addPermission(Player player, String permission) {
        if (player == null || permission == null || permission.isEmpty()) return false;

        boolean success = addPermission(player.getUniqueId(), permission);
        if (success && player.isOnline()) {
            plugin.getPermissionManager().attachPermission(player, permission);
        }
        return success;
    }

    /**
     * Retire une permission custom d'un joueur
     *
     * @param playerId   UUID du joueur
     * @param permission La permission à retirer
     * @return true si succès
     */
    public boolean removePermission(UUID playerId, String permission) {
        if (playerId == null || permission == null || permission.isEmpty()) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.removePermission(permission);
        playerDataManager.markDirty(playerId);

        // Si le joueur est en ligne, retirer la permission immédiatement
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            plugin.getPermissionManager().detachPermission(player, permission);
        }

        return true;
    }

    /**
     * Retire une permission custom d'un joueur (en ligne)
     */
    public boolean removePermission(Player player, String permission) {
        if (player == null || permission == null || permission.isEmpty()) return false;

        boolean success = removePermission(player.getUniqueId(), permission);
        if (success && player.isOnline()) {
            plugin.getPermissionManager().detachPermission(player, permission);
        }
        return success;
    }

    /**
     * Vérifie si un joueur a une permission custom stockée
     *
     * @param playerId   UUID du joueur
     * @param permission La permission à vérifier
     * @return true si le joueur a la permission
     */
    public boolean hasCustomPermission(UUID playerId, String permission) {
        if (playerId == null || permission == null) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        return playerData.hasCustomPermission(permission);
    }

    /**
     * Vérifie si un joueur a une permission (Bukkit + custom)
     *
     * @param player     Le joueur en ligne
     * @param permission La permission à vérifier
     * @return true si le joueur a la permission
     */
    public boolean hasPermission(Player player, String permission) {
        if (player == null || permission == null) return false;
        return player.hasPermission(permission);
    }

    /**
     * Récupère toutes les permissions custom d'un joueur
     *
     * @param playerId UUID du joueur
     * @return Set des permissions custom
     */
    public Set<String> getCustomPermissions(UUID playerId) {
        if (playerId == null) return new HashSet<>();

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return new HashSet<>();

        return playerData.getCustomPermissions();
    }

    /**
     * Définit toutes les permissions custom d'un joueur (remplace les existantes)
     *
     * @param playerId    UUID du joueur
     * @param permissions Set des nouvelles permissions
     * @return true si succès
     */
    public boolean setCustomPermissions(UUID playerId, Set<String> permissions) {
        if (playerId == null) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setCustomPermissions(permissions != null ? permissions : new HashSet<>());
        playerDataManager.markDirty(playerId);

        // Si le joueur est en ligne, recharger ses permissions
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            plugin.getPermissionManager().reloadPlayerPermissions(player);
        }

        return true;
    }

    /**
     * Recharge les permissions d'un joueur en ligne
     *
     * @param player Le joueur
     * @return true si succès
     */
    public boolean reloadPermissions(Player player) {
        if (player == null || !player.isOnline()) return false;

        plugin.getPermissionManager().reloadPlayerPermissions(player);
        return true;
    }

    // ===============================
    // MÉTHODES POUR LE PRESTIGE
    // ===============================

    /**
     * Récupère le niveau de prestige d'un joueur
     *
     * @param playerId UUID du joueur
     * @return Le niveau de prestige (0 si aucun)
     */
    public int getPrestigeLevel(UUID playerId) {
        if (playerId == null) return 0;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return 0;

        return playerData.getPrestigeLevel();
    }

    /**
     * Définit le niveau de prestige d'un joueur
     *
     * @param playerId UUID du joueur
     * @param level    Le nouveau niveau (0-50)
     * @return true si succès
     */
    public boolean setPrestigeLevel(UUID playerId, int level) {
        if (playerId == null || level < 0 || level > 50) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setPrestigeLevel(level);
        playerDataManager.markDirty(playerId);

        // Si le joueur est en ligne, recharger ses permissions
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            plugin.getPermissionManager().reloadPlayerPermissions(player);
        }

        return true;
    }

    /**
     * Vérifie si un joueur a débloqué une mine prestige
     *
     * @param playerId UUID du joueur
     * @param mineName Nom de la mine
     * @return true si débloquée
     */
    public boolean hasUnlockedPrestigeMine(UUID playerId, String mineName) {
        if (playerId == null || mineName == null) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        return playerData.hasUnlockedPrestigeMine(mineName);
    }

    /**
     * Débloque une mine prestige pour un joueur
     *
     * @param playerId UUID du joueur
     * @param mineName Nom de la mine
     * @return true si succès
     */
    public boolean unlockPrestigeMine(UUID playerId, String mineName) {
        if (playerId == null || mineName == null) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.unlockPrestigeMine(mineName);
        playerDataManager.markDirty(playerId);
        return true;
    }

    // ===============================
    // MÉTHODES POUR LE STATUT VIP
    // ===============================

    /**
     * Vérifie si un joueur est VIP
     *
     * @param playerId UUID du joueur
     * @return true si VIP
     */
    public boolean isVip(UUID playerId) {
        if (playerId == null) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        return playerData.isVip();
    }

    /**
     * Vérifie si un joueur est VIP (en ligne)
     */
    public boolean isVip(Player player) {
        if (player == null) return false;
        return isVip(player.getUniqueId());
    }

    /**
     * Définit le statut VIP d'un joueur
     *
     * @param playerId UUID du joueur
     * @param vip      true pour VIP, false pour retirer
     * @return true si succès
     */
    public boolean setVip(UUID playerId, boolean vip) {
        if (playerId == null) return false;

        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        if (playerData == null) return false;

        playerData.setVip(vip);
        playerDataManager.markDirty(playerId);

        // Si le joueur est en ligne, recharger ses permissions
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            plugin.getPermissionManager().reloadPlayerPermissions(player);
        }

        return true;
    }

    /**
     * Définit le statut VIP d'un joueur (en ligne)
     */
    public boolean setVip(Player player, boolean vip) {
        if (player == null) return false;
        return setVip(player.getUniqueId(), vip);
    }

    // ===============================
    // MÉTHODES UTILITAIRES
    // ===============================

    /**
     * Vérifie si un joueur a assez de coins
     */
    public boolean hasCoins(UUID playerId, long amount) {
        return getCoins(playerId) >= amount;
    }

    /**
     * Vérifie si un joueur a assez de tokens
     */
    public boolean hasTokens(UUID playerId, long amount) {
        return getTokens(playerId) >= amount;
    }

    /**
     * Vérifie si un joueur a assez de beacons
     */
    public boolean hasBeacons(UUID playerId, long amount) {
        return getBeacons(playerId) >= amount;
    }

    /**
     * Vérifie si un joueur a assez d'expérience
     */
    public boolean hasExperience(UUID playerId, long amount) {
        return getExperience(playerId) >= amount;
    }

    /**
     * Effectue une transaction avec des coins (débit/crédit atomique)
     *
     * @param fromPlayerId Joueur qui donne
     * @param toPlayerId   Joueur qui reçoit
     * @param amount       Montant à transférer
     * @return true si la transaction a réussi
     */
    public boolean transferCoins(UUID fromPlayerId, UUID toPlayerId, long amount) {
        if (fromPlayerId == null || toPlayerId == null || amount <= 0) return false;

        PlayerData fromData = playerDataManager.getPlayerData(fromPlayerId);
        PlayerData toData = playerDataManager.getPlayerData(toPlayerId);

        if (fromData == null || toData == null) return false;

        // Vérifier si le donneur a assez de coins
        if (fromData.getCoins() < amount) return false;

        // Transaction atomique
        fromData.removeCoins(amount);
        toData.addCoins(amount);

        // Marquer les deux comme modifiés
        playerDataManager.markDirty(fromPlayerId);
        playerDataManager.markDirty(toPlayerId);

        return true;
    }

    /**
     * Effectue une transaction avec des tokens
     */
    public boolean transferTokens(UUID fromPlayerId, UUID toPlayerId, long amount) {
        if (fromPlayerId == null || toPlayerId == null || amount <= 0) return false;

        PlayerData fromData = playerDataManager.getPlayerData(fromPlayerId);
        PlayerData toData = playerDataManager.getPlayerData(toPlayerId);

        if (fromData == null || toData == null) return false;

        // Transaction atomique avec rollback en cas d'erreur
        if (!fromData.removeTokens(amount)) return false;

        toData.addTokens(amount);

        // Marquer les deux comme modifiés
        playerDataManager.markDirty(fromPlayerId);
        playerDataManager.markDirty(toPlayerId);

        return true;
    }

    // ===============================
    // MÉTHODES POUR LES MINES (NOUVEAU)
    // ===============================

    /**
     * Récupère les données complètes d'une mine par son ID.
     *
     * @param mineId L'ID de la mine (ex: "a", "p1", "vip1")
     * @return L'objet MineData ou null si la mine n'existe pas.
     */
    public MineData getMine(String mineId) {
        return mineManager.getMine(mineId);
    }

    /**
     * Vérifie si une mine existe.
     *
     * @param mineId L'ID de la mine.
     * @return true si la mine existe, false sinon.
     */
    public boolean mineExists(String mineId) {
        return mineManager.mineExists(mineId);
    }

    /**
     * Récupère la collection de toutes les mines chargées.
     *
     * @return Une collection de tous les objets MineData.
     */
    public Collection<MineData> getAllMines() {
        return mineManager.getAllMines();
    }

    /**
     * Récupère la liste de toutes les mines, triées par ordre alphabétique de leur ID.
     *
     * @return Une liste triée d'objets MineData.
     */
    public List<MineData> getAllMinesSorted() {
        return mineManager.getAllMinesSorted();
    }

    /**
     * Récupère les noms (IDs) de toutes les mines.
     *
     * @return Un Set contenant tous les IDs des mines.
     */
    public Set<String> getMineNames() {
        return mineManager.getMineNames();
    }

    /**
     * Récupère le nombre total de mines chargées.
     *
     * @return Le nombre de mines.
     */
    public int getMineCount() {
        return mineManager.getMineCount();
    }

    /**
     * Récupère la liste des mines accessibles pour un joueur spécifique.
     *
     * @param player Le joueur concerné.
     * @return Une liste de MineData pour lesquelles le joueur a l'accès.
     */
    public List<MineData> getAccessibleMines(Player player) {
        return mineManager.getAccessibleMines(player);
    }

    /**
     * Récupère les mines d'un certain type (NORMAL, PRESTIGE, VIP).
     *
     * @param type Le type de mine à filtrer.
     * @return Une liste de MineData correspondant au type demandé.
     */
    public List<MineData> getMinesByType(MineData.MineType type) {
        return mineManager.getMinesByType(type);
    }

    /**
     * Recherche des mines dont le nom ou le nom d'affichage contient la requête.
     *
     * @param query Le texte à rechercher.
     * @return Une liste de MineData correspondant à la recherche.
     */
    public List<MineData> searchMines(String query) {
        return mineManager.searchMines(query);
    }

    /**
     * Vérifie si un joueur a l'autorisation d'accéder à une mine.
     *
     * @param player Le joueur.
     * @param mineId L'ID de la mine.
     * @return true si le joueur peut y accéder, false sinon.
     */
    public boolean canAccessMine(Player player, String mineId) {
        return mineManager.canAccessMine(player, mineId);
    }

    /**
     * Récupère la mine dans laquelle se trouve actuellement un joueur.
     *
     * @param player Le joueur.
     * @return L'objet MineData de la mine actuelle, ou null si le joueur n'est dans aucune mine.
     */
    public MineData getPlayerCurrentMine(Player player) {
        String mineId = mineManager.getPlayerCurrentMine(player);
        if (mineId != null) {
            return getMine(mineId);
        }
        return null;
    }

    /**
     * Téléporte un joueur au point de spawn d'une mine, si il y a accès.
     *
     * @param player Le joueur à téléporter.
     * @param mineId L'ID de la mine de destination.
     * @return true si la téléportation a réussi, false sinon (accès refusé, mine inexistante, etc.).
     */
    public boolean teleportToMine(Player player, String mineId) {
        return mineManager.teleportToMine(player, mineId);
    }


    // ===============================
    // MÉTHODES POUR LES GANGS
    // ===============================


    /**
     * Récupère le gang d'un joueur.
     *
     * @param playerId L'UUID du joueur.
     * @return L'objet Gang du joueur, ou null s'il n'est dans aucun gang.
     */
    public Gang getPlayerGang(UUID playerId) {
        PlayerData playerData = getPlayerData(playerId);
        if (playerData != null && playerData.getGangId() != null) {
            return gangManager.getGang(playerData.getGangId());
        }
        return null;
    }

    /**
     * Récupère un gang par son ID unique.
     *
     * @param gangId L'ID du gang.
     * @return L'objet Gang, ou null s'il n'existe pas.
     */
    public Gang getGangById(String gangId) {
        return gangManager.getGang(gangId);
    }

    /**
     * Récupère un gang par son nom (insensible à la casse).
     *
     * @param name Le nom du gang.
     * @return L'objet Gang, ou null s'il n'existe pas.
     */
    public Gang getGangByName(String name) {
        return gangManager.getGangByName(name);
    }

    /**
     * Récupère un gang par son tag (insensible à la casse).
     *
     * @param tag Le tag du gang.
     * @return L'objet Gang, ou null s'il n'existe pas.
     */
    public Gang getGangByTag(String tag) {
        return gangManager.getGangByTag(tag);
    }

    /**
     * Récupère la liste de tous les gangs existants.
     *
     * @return Une liste contenant tous les objets Gang.
     */
    public List<Gang> getAllGangs() {
        return gangManager.getAllGangs();
    }

    /**
     * Vérifie si un joueur est le chef de son gang.
     *
     * @param playerId L'UUID du joueur.
     * @return true si le joueur est le chef de son gang.
     */
    public boolean isGangLeader(UUID playerId) {
        Gang gang = getPlayerGang(playerId);
        return gang != null && gang.getLeader().equals(playerId);
    }

    /**
     * Obtient le rôle d'un joueur dans son gang.
     *
     * @param playerId L'UUID du joueur.
     * @return Le GangRole du joueur, ou null s'il n'est pas dans un gang.
     */
    public GangRole getPlayerGangRole(UUID playerId) {
        Gang gang = getPlayerGang(playerId);
        return gang != null ? gang.getMemberRole(playerId) : null;
    }

    /**
     * Récupère la liste des membres d'un gang.
     *
     * @param gang Le gang concerné.
     * @return Une map des membres avec leur UUID et leur rôle.
     */
    public Map<UUID, GangRole> getGangMembers(Gang gang) {
        return gang.getMembers();
    }

    /**
     * Dépose de l'argent depuis le solde d'un joueur vers la banque du gang.
     *
     * @param gang   Le gang cible.
     * @param player Le joueur qui dépose.
     * @param amount Le montant à déposer.
     * @return true si le dépôt a réussi.
     */
    public boolean depositToGangBank(Gang gang, Player player, long amount) {
        return gangManager.depositToBank(gang, player, amount);
    }

    /**
     * Récupère les données complètes d'un joueur
     */
    public PlayerData getPlayerData(UUID playerId) {
        if (playerId == null) return null;
        return playerDataManager.getPlayerData(playerId);
    }

    /**
     * Sauvegarde les données d'un joueur
     */
    public void savePlayerData(UUID playerId) {
        if (playerId != null) {
            playerDataManager.savePlayerNow(playerId);
        }
    }

    /**
     * Récupère la version de l'API
     */
    public String getAPIVersion() {
        return "1.0.0";
    }

    /**
     * Récupère le plugin principal
     */
    public PrisonTycoon getPlugin() {
        return plugin;
    }

    /**
     * Récupère le PlayerDataManager
     */
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    /**
     * Indique si un joueur est actuellement en combat (a été frappé par un autre joueur dans les 10 dernières secondes).
     *
     * @param player Le joueur à tester
     * @return true si le joueur est en combat, false sinon
     */
    public boolean isInCombat(Player player) {
        return plugin.getCombatManager().isInCombat(player);
    }

    public long getRemainingCombatSeconds(Player player) {
        return plugin.getCombatManager().getRemainingCombatSeconds(player);
    }

    public int getReputation(UUID playerId) {
        return plugin.getReputationManager().getReputation(playerId);
    }

    public void modifyReputation(UUID playerId, int reputation, String reason) {
        plugin.getReputationManager().modifyReputation(playerId, reputation, reason);
    }

    public String getActiveProfession(UUID playerId) {
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        return playerData.getActiveProfession();
    }

    public int getProfessionLevel(UUID playerId) {
        PlayerData playerData = playerDataManager.getPlayerData(playerId);
        return playerData.getProfessionLevel(playerData.getActiveProfession());
    }

    /**
     * Ajoute de l'XP métier (offline-friendly) au métier actif si correspond à professionId
     */
    public void addProfessionXP(UUID playerId, String professionId, int xp) {
        if (professionId == null || xp <= 0) return;
        plugin.getProfessionManager().addProfessionXP(playerId, professionId, xp);
    }

    public double getTotalBonusMultiplier(Player player, GlobalBonusManager.BonusCategory category) {
        return plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, category);
    }

    public fr.prisontycoon.managers.PermissionManager getPermissionManager() {
        return plugin.getPermissionManager();
    }

    public int AuctionHouseSlot(Player player) {
        return (int) plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.HDV_SLOT);
    }

    public boolean isLegendaryPickaxe(ItemStack item) {
        return plugin.getPickaxeManager().isLegendaryPickaxe(item);
    }

    public boolean isMainMenuHead(ItemStack item) {
        return plugin.getGUIManager().isMainMenuHead(item);
    }

    public ItemStack createKey(String keyType) {
        return plugin.getEnchantmentManager().createKey(keyType);
    }

    /**
     * Nombre maximum de slots d'imprimantes pour un joueur.
     * Base 10 + 1 par prestige + bonus via vouchers (printer_extra_slots) + bonus type de banque.
     */
    public int getMaxPrinterSlots(UUID playerId) {
        if (playerId == null) return 10;
        PlayerData data = playerDataManager.getPlayerData(playerId);
        if (data == null) return 10;

        int base = 10;
        int prestigeBonus = data.getPrestigeLevel();
        int voucherBonus = data.getPrinterExtraSlots();
        int bankBonus = data.getBankType() != null ? data.getBankType().getIslandPrintersBonus() : 0;
        return Math.max(0, base + prestigeBonus + voucherBonus + bankBonus);
    }

    public ItemStack createCristalVierge(int niveau) {
        return plugin.getCristalManager().createCristalViergeApi(niveau);
    }

    public ItemStack createAutominer(AutominerType type) {
        return plugin.getAutominerManager().createAutominer(type);
    }

    public ItemStack createContainer(int tier) {
        return plugin.getContainerManager().createContainer(tier);
    }

    public ItemStack createBoostItem(BoostType type, int durationMinutes, double bonusPercentage) {
        return plugin.getBoostManager().createBoostItem(type, durationMinutes, bonusPercentage);
    }

    public ItemStack createVoucher(VoucherType type, int tier) {
        return plugin.getVoucherManager().createVoucher(type, tier);
    }

    public ItemStack createUniqueEnchantmentBook(String enchantId) {
        return plugin.getUniqueEnchantmentBookFactory().createUniqueEnchantmentBook(enchantId);
    }

    public ItemStack createPhysicalEnchantmentBook(EnchantmentBookManager.EnchantmentBook book) {
        return plugin.getEnchantmentBookManager().createPhysicalEnchantmentBook(book);
    }

    // ===============================
    // API Tanks par monde
    // ===============================

    /**
     * Charge les tanks du monde donné (ne charge que ceux dont la location.world == worldName)
     */
    public void loadWorldTanks(String worldName) {
        plugin.getTankManager().loadWorldTanks(worldName);
    }

    /**
     * Sauvegarde tous les tanks actuellement en cache pour le monde donné
     */
    public void saveWorldTanks(String worldName) {
        plugin.getTankManager().saveWorldTanks(worldName);
    }

    /**
     * Décharge les tanks du monde donné depuis le cache et retire leurs nametags
     */
    public void unloadWorldTanks(String worldName) {
        plugin.getTankManager().unloadWorldTanks(worldName);
    }
}