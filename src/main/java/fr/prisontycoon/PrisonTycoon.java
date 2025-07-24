package fr.prisontycoon;

import com.earth2me.essentials.Essentials;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import fr.prisontycoon.GUI.*;
import fr.prisontycoon.autominers.AutominerTask;
import fr.prisontycoon.boosts.BoostManager;
import fr.prisontycoon.commands.*;
import fr.prisontycoon.cristaux.CristalBonusHelper;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.enchantments.EnchantmentManager;
import fr.prisontycoon.enchantments.UniqueEnchantmentBookFactory;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import fr.prisontycoon.events.*;
import fr.prisontycoon.managers.*;
import fr.prisontycoon.tasks.*;
import fr.prisontycoon.utils.ChatLogger;
import fr.prisontycoon.utils.Logger;
import fr.prisontycoon.vouchers.VoucherManager;
import net.ess3.api.IEssentials;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal PrisonTycoon - VERSION COMPLÈTE INTÉGRÉE
 * Intégration native avec LuckPerms, Vault, WorldGuard et EssentialsX
 *
 * @author PrisonTycoon Team
 * @version 1.0-INTEGRATED
 */
public final class PrisonTycoon extends JavaPlugin {

    // Instance singleton du plugin
    private static PrisonTycoon instance;

    // Logger personnalisé
    private Logger pluginLogger;
    private ChatLogger chatLogger;

    // === INTÉGRATIONS NATIVES ===
    private LuckPerms luckPermsAPI;
    private Economy vaultEconomy;
    private WorldGuardPlugin worldGuardPlugin;
    private IEssentials essentialsAPI;

    // États des intégrations
    private boolean luckPermsEnabled = false;
    private boolean vaultEnabled = false;
    private boolean worldGuardEnabled = false;
    private boolean essentialsEnabled = false;

    // === MANAGERS PRINCIPAUX ===
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private MineManager mineManager;
    private EnchantmentManager enchantmentManager;
    private PickaxeManager pickaxeManager;
    private EconomyManager economyManager;
    private NotificationManager notificationManager;
    private ContainerManager containerManager;
    private GlobalBonusManager globalBonusManager;
    private TabManager tabManager;
    private ModerationManager moderationManager;
    private VipManager vipManager;
    private PermissionManager permissionManager; // Manager intégré
    private EnchantmentBookManager enchantmentBookManager;
    private ProfessionManager professionManager;
    private PrestigeManager prestigeManager;
    private ReputationManager reputationManager;
    private BlackMarketManager blackMarketManager;
    private CristalManager cristalManager;
    private WeaponArmorEnchantmentManager weaponArmorEnchantmentManager;
    private VoucherManager voucherManager;
    private BoostManager boostManager;
    private AutominerManager autominerManager;

    // === UTILITAIRES ===
    private CristalBonusHelper cristalBonusHelper;
    private UniqueEnchantmentBookFactory uniqueEnchantmentBookFactory;

    // === INTERFACES GRAPHIQUES ===
    private EnchantmentMenu mainMenuGUI;
    private CategoryMenuGUI categoryMenuGUI;
    private EnchantmentUpgradeGUI enchantmentUpgradeGUI;
    private CristalGUI cristalGUI;
    private EnchantmentBookGUI enchantmentBookGUI;
    private PetsMenuGUI petsMenuGUI;
    private PickaxeRepairGUI pickaxeRepairGUI;
    private ContainerGUI containerGUI;
    private ContainerFilterGUI containerFilterGUI;
    private ProfessionGUI professionGUI;
    private ProfessionRewardsGUI professionRewardsGUI;
    private PrestigeGUI prestigeGUI;
    private BoostGUI boostGUI;
    private AutominerGUI autominerGUI;
    private AutominerEnchantGUI autominerEnchantGUI;
    private AutominerCondHeadGUI autominerCondHeadGUI;
    private AutominerEnchantUpgradeGUI autominerEnchantUpgradeGUI;
    private WeaponArmorEnchantGUI weaponArmorEnchantGUI;

    // === TÂCHES ASYNCHRONES ===
    private AutoSaveTask autoSaveTask;
    private AutominerTask autominerTask;
    private AutoUpgradeTask autoUpgradeTask;
    private CombustionDecayTask combustionDecayTask;
    private ActionBarTask actionBarTask;
    private ChatTask chatTask;
    private ScoreboardTask scoreboardTask;

    // Métriques bStats
    private Metrics metrics;

    // === GETTERS STATIQUES ===
    public static PrisonTycoon getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        // Initialise l'instance singleton
        instance = this;

        // Initialise le logger personnalisé
        pluginLogger = new Logger(this);
        chatLogger = new ChatLogger(this);

        pluginLogger.info("§a========================================");
        pluginLogger.info("§aPrisonTycoon v1.0-INTEGRATED - Démarrage");
        pluginLogger.info("§a========================================");

        try {
            // Phase 1: Configuration
            initializeConfig();

            // Phase 2: Intégrations externes (ordre critique!)
            setupExternalIntegrations();

            // Phase 3: Managers internes (avec intégrations)
            initializeManagers();

            // Phase 4: Interfaces utilisateur
            initializeGUIs();

            // Phase 5: Événements et commandes
            registerEvents();
            registerCommands();

            // Phase 6: Tâches asynchrones
            startTasks();

            // Phase 7: Métriques
            setupMetrics();

            pluginLogger.info("§a========================================");
            pluginLogger.info("§aPrisonTycoon démarré avec succès!");
            pluginLogger.info("§7- LuckPerms: " + (luckPermsEnabled ? "§aOUI" : "§cNON"));
            pluginLogger.info("§7- Vault: " + (vaultEnabled ? "§aOUI" : "§cNON"));
            pluginLogger.info("§7- WorldGuard: " + (worldGuardEnabled ? "§aOUI" : "§cNON"));
            pluginLogger.info("§7- EssentialsX: " + (essentialsEnabled ? "§aOUI" : "§cNON"));
            pluginLogger.info("§a========================================");

        } catch (Exception e) {
            pluginLogger.severe("§cErreur critique lors du démarrage!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        pluginLogger.info("§e========================================");
        pluginLogger.info("§ePrisonTycoon - Arrêt en cours...");
        pluginLogger.info("§e========================================");

        try {
            // Arrêt des tâches
            stopTasks();

            // Sauvegarde finale
            if (playerDataManager != null) {
                pluginLogger.info("§7Sauvegarde finale des données...");
                playerDataManager.saveAllPlayersAsync();
            }

            if (chatLogger != null) {
                chatLogger.shutdown();
                pluginLogger.info("§7ChatLogger arrêté");
            }

        } catch (Exception e) {
            pluginLogger.severe("§cErreur lors de l'arrêt:");
            e.printStackTrace();
        }

        pluginLogger.info("§e========================================");
    }

    /**
     * PHASE 1: Initialise la configuration
     */
    private void initializeConfig() {
        pluginLogger.info("§7Phase 1: Configuration...");

        saveDefaultConfig();
        configManager = new ConfigManager(this);

        pluginLogger.info("§aConfiguration chargée.");
    }

    /**
     * PHASE 2: Configure les intégrations externes - INTÉGRATION NATIVE!
     */
    private void setupExternalIntegrations() {
        pluginLogger.info("§7Phase 2: Intégrations natives...");

        // LuckPerms - INTÉGRATION NATIVE
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPermsAPI = LuckPermsProvider.get();
                luckPermsEnabled = true;
                pluginLogger.info("§a✓ LuckPerms intégré nativement");
            } catch (Exception e) {
                pluginLogger.warning("§c⚠ Erreur intégration LuckPerms: " + e.getMessage());
            }
        } else {
            pluginLogger.warning("§eLuckPerms non détecté - Système de permissions limité");
        }

        // Vault - INTÉGRATION NATIVE
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            try {
                RegisteredServiceProvider<Economy> economyProvider =
                        getServer().getServicesManager().getRegistration(Economy.class);
                if (economyProvider != null) {
                    vaultEconomy = economyProvider.getProvider();
                    vaultEnabled = true;
                    pluginLogger.info("§a✓ Vault intégré nativement (" + vaultEconomy.getName() + ")");
                }
            } catch (Exception e) {
                pluginLogger.warning("§c⚠ Erreur intégration Vault: " + e.getMessage());
            }
        } else {
            pluginLogger.warning("§eVault non détecté - Économie interne uniquement");
        }

        // WorldGuard - INTÉGRATION NATIVE
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                worldGuardPlugin = WorldGuardPlugin.inst();
                if (worldGuardPlugin != null) {
                    worldGuardEnabled = true;
                    pluginLogger.info("§a✓ WorldGuard intégré nativement");
                }
            } catch (Exception e) {
                pluginLogger.warning("§c⚠ Erreur intégration WorldGuard: " + e.getMessage());
            }
        } else {
            pluginLogger.warning("§eWorldGuard non détecté - Protection des zones désactivée");
        }

        // EssentialsX - INTÉGRATION NATIVE
        if (getServer().getPluginManager().getPlugin("Essentials") != null) {
            try {
                var essentialsPlugin = getServer().getPluginManager().getPlugin("Essentials");
                if (essentialsPlugin instanceof Essentials) {
                    essentialsAPI = (IEssentials) essentialsPlugin;
                    essentialsEnabled = true;
                    pluginLogger.info("§a✓ EssentialsX intégré nativement");
                }
            } catch (Exception e) {
                pluginLogger.warning("§c⚠ Erreur intégration EssentialsX: " + e.getMessage());
            }
        } else {
            pluginLogger.warning("§eEssentialsX non détecté - Fonctionnalités étendues désactivées");
        }
    }

    /**
     * PHASE 3: Initialise tous les managers avec intégrations
     */
    private void initializeManagers() {
        pluginLogger.info("§7Phase 3: Managers intégrés...");

        // Ordre critique pour les dépendances!
        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this); // Intégration Vault
        enchantmentManager = new EnchantmentManager(this);
        pickaxeManager = new PickaxeManager(this);
        mineManager = new MineManager(this); // Intégration WorldGuard
        notificationManager = new NotificationManager(this);
        containerManager = new ContainerManager(this);
        cristalManager = new CristalManager(this);
        cristalBonusHelper = new CristalBonusHelper(this);
        globalBonusManager = new GlobalBonusManager(this);
        tabManager = new TabManager(this); // Intégration LuckPerms
        moderationManager = new ModerationManager(this);
        vipManager = new VipManager(this); // Intégration LuckPerms
        permissionManager = new PermissionManager(this); // Intégration LuckPerms
        enchantmentBookManager = new EnchantmentBookManager(this);
        professionManager = new ProfessionManager(this);
        prestigeManager = new PrestigeManager(this); // Intégration LuckPerms
        reputationManager = new ReputationManager(this);
        blackMarketManager = new BlackMarketManager(this);
        weaponArmorEnchantmentManager = new WeaponArmorEnchantmentManager(this);
        uniqueEnchantmentBookFactory = new UniqueEnchantmentBookFactory(this);
        voucherManager = new VoucherManager(this);
        boostManager = new BoostManager(this);
        autominerManager = new AutominerManager(this);

        pluginLogger.info("§aTous les managers initialisés avec intégrations.");
    }

    /**
     * PHASE 4: Initialise les interfaces graphiques
     */
    private void initializeGUIs() {
        pluginLogger.info("§7Phase 4: Interfaces graphiques...");

        mainMenuGUI = new EnchantmentMenu(this);
        categoryMenuGUI = new CategoryMenuGUI(this);
        enchantmentUpgradeGUI = new EnchantmentUpgradeGUI(this);
        cristalGUI = new CristalGUI(this);
        enchantmentBookGUI = new EnchantmentBookGUI(this);
        petsMenuGUI = new PetsMenuGUI(this);
        pickaxeRepairGUI = new PickaxeRepairGUI(this);
        containerGUI = new ContainerGUI(this);
        containerFilterGUI = new ContainerFilterGUI(this);
        professionGUI = new ProfessionGUI(this);
        professionRewardsGUI = new ProfessionRewardsGUI(this);
        prestigeGUI = new PrestigeGUI(this);
        boostGUI = new BoostGUI(this);
        autominerGUI = new AutominerGUI(this);
        autominerEnchantGUI = new AutominerEnchantGUI(this);
        autominerCondHeadGUI = new AutominerCondHeadGUI(this);
        autominerEnchantUpgradeGUI = new AutominerEnchantUpgradeGUI(this);
        weaponArmorEnchantGUI = new WeaponArmorEnchantGUI(this);

        pluginLogger.info("§aInterfaces graphiques initialisées.");
    }

    /**
     * PHASE 5: Enregistre les événements
     */
    private void registerEvents() {
        pluginLogger.info("§7Phase 5: Événements...");

        var pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(new PlayerJoinQuitListener(this), this);
        pluginManager.registerEvents(new MiningListener(this), this);
        pluginManager.registerEvents(new PickaxeProtectionListener(this), this);
        pluginManager.registerEvents(new GUIListener(this), this);
        pluginManager.registerEvents(new MobilityEffectsListener(this), this);
        pluginManager.registerEvents(new ContainerListener(this), this);
        pluginManager.registerEvents(new CristalListener(this), this);
        pluginManager.registerEvents(new ChatListener(this), this);
        pluginManager.registerEvents(new PNJInteract(this), this);
        pluginManager.registerEvents(new WeaponArmorEnchantmentListener(this), this);
        pluginManager.registerEvents(new VoucherBoostListener(this), this);

        pluginLogger.info("§aÉvénements enregistrés.");
    }

    /**
     * PHASE 5: Enregistre les commandes
     */
    private void registerCommands() {
        pluginLogger.info("§7Enregistrement des commandes...");

        // Commandes joueur
        getCommand("pickaxe").setExecutor(new PickaxeCommand(this));
        getCommand("pickaxe").setTabCompleter(new PickaxeCommand(this));
        getCommand("mine").setExecutor(new MineCommand(this));
        getCommand("mine").setTabCompleter(new MineCommand(this));
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("sell").setTabCompleter(new SellCommand(this));
        getCommand("repair").setExecutor(new RepairCommand(this)); // NOUVELLE LIGNE
        getCommand("repair").setTabCompleter(new RepairCommand(this));

        getCommand("rankup").setExecutor(new RankupCommand(this));
        getCommand("rankup").setTabCompleter(new RankupCommand(this));

        // Commandes admin
        getCommand("givetokens").setExecutor(new GiveTokensCommand(this));
        getCommand("givetokens").setTabCompleter(new GiveTokensCommand(this));

        getCommand("conteneur").setExecutor(new ContainerCommand(this));
        getCommand("conteneur").setTabCompleter(new ContainerCommand(this));

        getCommand("cristal").setExecutor(new CristalCommand(this));
        getCommand("cristal").setTabCompleter(new CristalCommand(this));

        getCommand("adminchat").setExecutor(new AdminChatCommand(this));
        getCommand("adminchat").setTabCompleter(new AdminChatCommand(this));

        getCommand("vip").setExecutor(new VipCommand(this));
        getCommand("vip").setTabCompleter(new VipCommand(this));

        getCommand("invsee").setExecutor(new InvseeCommand(this));
        getCommand("invsee").setTabCompleter(new InvseeCommand(this));

        getCommand("metier").setExecutor(new MetierCommand(this));
        getCommand("metier").setTabCompleter(new MetierCommand(this));

        getCommand("enchantbook").setExecutor(new EnchantmentBookCommand(this));

        getCommand("prestige").setExecutor(new PrestigeCommand(this));
        getCommand("prestige").setTabCompleter(new PrestigeCommand(this));

        getCommand("rep").setExecutor(new ReputationCommand(this));
        getCommand("fbm").setExecutor(new BlackMarketCommand(this));

        getCommand("boost").setExecutor(new BoostCommand(this));
        getCommand("boost").setTabCompleter(new BoostCommand(this));
        getCommand("voucher").setExecutor(new VoucherCommand(this));
        getCommand("giveboost").setExecutor(new GiveBoostCommand(this));
        getCommand("giveboost").setTabCompleter(new GiveBoostCommand(this));

        getCommand("autominer").setExecutor(new AutominerCommand(this));
        getCommand("adminchat").setExecutor(new AdminChatCommand(this));

        pluginLogger.info("§aCommandes enregistrées.");
    }

    /**
     * PHASE 6: Démarre les tâches asynchrones
     */
    private void startTasks() {
        pluginLogger.info("§7Démarrage des tâches asynchrones...");

        // Récupère les intervalles depuis la config
        int actionBarInterval = getConfig().getInt("performance.task-intervals.action-bar-ticks", 5);
        int scoreboardInterval = getConfig().getInt("performance.task-intervals.scoreboard-ticks", 50);
        int chatInterval = getConfig().getInt("performance.task-intervals.chat-ticks", 1200);
        int autoSaveInterval = getConfig().getInt("performance.task-intervals.auto-save-ticks", 6000);
        int combustionInterval = getConfig().getInt("performance.task-intervals.combustion-ticks", 200);
        int autoUpgradeInterval = getConfig().getInt("performance.task-intervals.auto-upgrade-ticks", 200);

        // 3 tâches séparées avec nouveau système de notifications
        if (getConfig().getBoolean("notifications.action-bar.enabled", true)) {
            actionBarTask = new ActionBarTask(this);
            actionBarTask.runTaskTimerAsynchronously(this, 0L, actionBarInterval);
            pluginLogger.info("§7- ActionBarTask démarrée (nouveau système multi-notifications toutes les " + actionBarInterval + " ticks)");
        }

        if (getConfig().getBoolean("notifications.scoreboard.enabled", true)) {
            scoreboardTask = new ScoreboardTask(this);
            scoreboardTask.runTaskTimer(this, 0L, scoreboardInterval);
            pluginLogger.info("§7- ScoreboardTask démarrée (gestion intégrée toutes les " + scoreboardInterval + " ticks)");
        }

        if (getConfig().getBoolean("notifications.chat.enabled", true)) {
            chatTask = new ChatTask(this);
            chatTask.runTaskTimerAsynchronously(this, chatInterval, chatInterval);
            pluginLogger.info("§7- ChatTask démarrée (récapitulatif toutes les " + chatInterval + " ticks)");
        }

        // Autres tâches existantes
        autoSaveTask = new AutoSaveTask(this);
        autoSaveTask.runTaskTimerAsynchronously(this, autoSaveInterval, autoSaveInterval);
        pluginLogger.info("§7- AutoSaveTask démarrée (sauvegarde toutes les " + autoSaveInterval + " ticks)");

        combustionDecayTask = new CombustionDecayTask(this);
        combustionDecayTask.runTaskTimer(this, 0L, combustionInterval);
        pluginLogger.info("§7- CombustionDecayTask démarrée (décroissance corrigée toutes les " + combustionInterval + " ticks)");

        autoUpgradeTask = new AutoUpgradeTask(this);
        autoUpgradeTask.runTaskTimerAsynchronously(this, autoUpgradeInterval, autoUpgradeInterval);
        pluginLogger.info("§7- AutoUpgradeTask démarrée (toutes les " + autoUpgradeInterval + " ticks)");

        autominerTask = new AutominerTask(this);
        autominerTask.runTaskTimerAsynchronously(this, 20L, 20L);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (moderationManager != null) {
                moderationManager.cleanupExpiredSanctions();
            }
        }, 12000L, 12000L); // Toutes les 10 minutes
        pluginLogger.info("§7- Tâche de nettoyage automatique démarrée");

        // NOUVELLE TÂCHE: Nettoyage des anciens logs (optionnel)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (chatLogger != null) {
                chatLogger.cleanOldLogs(30); // Garde 30 jours de logs
            }
        }, 86400L, 86400L); // Tous les jours
        pluginLogger.info("§7- Tâche de nettoyage des logs démarrée");
    }

    /**
     * PHASE 7: Configure les métriques bStats
     */
    private void setupMetrics() {
        if (getConfig().getBoolean("advanced.metrics.enabled", true)) {
            pluginLogger.info("§7Phase 7: Métriques...");

            metrics = new Metrics(this, 12345); // Remplacez par votre ID bStats

            // Statistiques personnalisées
            metrics.addCustomChart(new Metrics.SimplePie("luckperms_integration",
                    () -> luckPermsEnabled ? "Enabled" : "Disabled"));
            metrics.addCustomChart(new Metrics.SimplePie("vault_integration",
                    () -> vaultEnabled ? "Enabled" : "Disabled"));
            metrics.addCustomChart(new Metrics.SimplePie("worldguard_integration",
                    () -> worldGuardEnabled ? "Enabled" : "Disabled"));
            metrics.addCustomChart(new Metrics.SimplePie("essentialsx_integration",
                    () -> essentialsEnabled ? "Enabled" : "Disabled"));

            pluginLogger.info("§aMétriques bStats configurées.");
        }
    }

    /**
     * Arrête toutes les tâches
     */
    private void stopTasks() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            pluginLogger.debug("ActionBarTask arrêtée");
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            pluginLogger.debug("ScoreboardTask arrêtée");
        }
        if (chatTask != null) {
            chatTask.cancel();
            pluginLogger.debug("ChatTask arrêtée");
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            pluginLogger.debug("AutoSaveTask arrêtée");
        }
        if (combustionDecayTask != null) {
            combustionDecayTask.cancel();
            pluginLogger.debug("CombustionDecayTask arrêtée");
        }
        if (autoUpgradeTask != null) {
            autoUpgradeTask.cancel();
            pluginLogger.debug("AutoUpgradeTask arrêtée");
        }
        if (moderationManager != null) {
            moderationManager.cleanupExpiredSanctions();
            pluginLogger.info("§7ModerationManager nettoyé");
        }
        if (permissionManager != null) {
            permissionManager.cleanup();
        }
        if (autominerTask != null) {
            autominerTask.cancel();
        }
    }

    /**
     * Nettoie les intégrations externes
     */
    private void cleanupIntegrations() {
        // Rien de spécial à nettoyer car intégrations natives
        luckPermsAPI = null;
        vaultEconomy = null;
        worldGuardPlugin = null;
        essentialsAPI = null;
    }

    // === GETTERS INTÉGRATIONS NATIVES ===
    public LuckPerms getLuckPermsAPI() {
        return luckPermsAPI;
    }

    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    public WorldGuardPlugin getWorldGuardPlugin() {
        return worldGuardPlugin;
    }

    public IEssentials getEssentialsAPI() {
        return essentialsAPI;
    }

    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public boolean isEssentialsEnabled() {
        return essentialsEnabled;
    }

    // === GETTERS MANAGERS (conservés pour compatibilité) ===
    public Logger getPluginLogger() {
        return pluginLogger;
    }

    public ChatLogger getChatLogger() {
        return chatLogger;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public MineManager getMineManager() {
        return mineManager;
    }

    public EnchantmentManager getEnchantmentManager() {
        return enchantmentManager;
    }

    public PickaxeManager getPickaxeManager() {
        return pickaxeManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    public ContainerManager getContainerManager() {
        return containerManager;
    }

    public GlobalBonusManager getGlobalBonusManager() {
        return globalBonusManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    public VipManager getVipManager() {
        return vipManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    public EnchantmentBookManager getEnchantmentBookManager() {
        return enchantmentBookManager;
    }

    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    public PrestigeManager getPrestigeManager() {
        return prestigeManager;
    }

    public ReputationManager getReputationManager() {
        return reputationManager;
    }

    public BlackMarketManager getBlackMarketManager() {
        return blackMarketManager;
    }

    public CristalManager getCristalManager() {
        return cristalManager;
    }

    public WeaponArmorEnchantmentManager getWeaponArmorEnchantmentManager() {
        return weaponArmorEnchantmentManager;
    }

    public VoucherManager getVoucherManager() {
        return voucherManager;
    }

    public BoostManager getBoostManager() {
        return boostManager;
    }

    public AutominerManager getAutominerManager() {
        return autominerManager;
    }

    // === GETTERS UTILITAIRES ===
    public CristalBonusHelper getCristalBonusHelper() {
        return cristalBonusHelper;
    }

    public UniqueEnchantmentBookFactory getUniqueEnchantmentBookFactory() {
        return uniqueEnchantmentBookFactory;
    }

    // === GETTERS GUI ===
    public EnchantmentMenu getMainMenuGUI() {
        return mainMenuGUI;
    }

    public CategoryMenuGUI getCategoryMenuGUI() {
        return categoryMenuGUI;
    }

    public EnchantmentUpgradeGUI getEnchantmentUpgradeGUI() {
        return enchantmentUpgradeGUI;
    }

    public CristalGUI getCristalGUI() {
        return cristalGUI;
    }

    public EnchantmentBookGUI getEnchantmentBookGUI() {
        return enchantmentBookGUI;
    }

    public PetsMenuGUI getPetsMenuGUI() {
        return petsMenuGUI;
    }

    public PickaxeRepairGUI getPickaxeRepairGUI() {
        return pickaxeRepairGUI;
    }

    public ContainerGUI getContainerGUI() {
        return containerGUI;
    }

    public ContainerFilterGUI getContainerFilterGUI() {
        return containerFilterGUI;
    }

    public ProfessionGUI getProfessionGUI() {
        return professionGUI;
    }

    public ProfessionRewardsGUI getProfessionRewardsGUI() {
        return professionRewardsGUI;
    }

    public PrestigeGUI getPrestigeGUI() {
        return prestigeGUI;
    }

    public BoostGUI getBoostGUI() {
        return boostGUI;
    }

    public AutominerGUI getAutominerGUI() {
        return autominerGUI;
    }

    public AutominerEnchantGUI getAutominerEnchantGUI() {
        return autominerEnchantGUI;
    }

    public AutominerCondHeadGUI getAutominerCondHeadGUI() {
        return autominerCondHeadGUI;
    }

    public AutominerEnchantUpgradeGUI getAutominerEnchantUpgradeGUI() {
        return autominerEnchantUpgradeGUI;
    }

    public WeaponArmorEnchantGUI getWeaponArmorEnchantGUI() {
        return weaponArmorEnchantGUI;
    }
}