package fr.prisontycoon;

import fr.prisontycoon.api.PrisonTycoonAPI;
import fr.prisontycoon.autominers.AutominerTask;
import fr.prisontycoon.boosts.BoostManager;
import fr.prisontycoon.commands.*;
import fr.prisontycoon.cristaux.CristalBonusHelper;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.enchantments.EnchantmentManager;
import fr.prisontycoon.enchantments.UniqueEnchantmentBookFactory;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import fr.prisontycoon.events.*;
import fr.prisontycoon.gui.*;
import fr.prisontycoon.managers.*;
import fr.prisontycoon.tasks.*;
import fr.prisontycoon.utils.ChatLogger;
import fr.prisontycoon.utils.Logger;
import fr.prisontycoon.vouchers.VoucherManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal PrisonTycoon
 * CORRIGÉ : Suppression de ScoreboardManager, utilisation du nouveau système
 */
public final class PrisonTycoon extends JavaPlugin {

    // Instance singleton du plugin
    private static PrisonTycoon instance;
    private ChatLogger chatLogger;

    // Managers principaux
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
    private PermissionManager permissionManager;
    private EnchantmentBookManager enchantmentBookManager;
    private ProfessionManager professionManager;
    private PrestigeManager prestigeManager; // NOUVEAU
    private ReputationManager reputationManager;
    private BlackMarketManager blackMarketManager;
    private WeaponArmorEnchantmentManager weaponArmorEnchantmentManager;
    private WeaponArmorEnchantGUI weaponArmorEnchantGUI;
    private UniqueEnchantmentBookFactory uniqueEnchantmentBookFactory;
    private VoucherManager voucherManager;
    private BoostManager boostManager;
    private BankManager bankManager;
    private CrateManager crateManager;
    private TankManager tankManager;
    private SellHandManager sellHandManager;

    private Logger logger;

    // GUIs séparés
    private EnchantmentMenu mainMenuGUI;
    private CategoryMenuGUI categoryMenuGUI;
    private EnchantmentUpgradeGUI enchantmentUpgradeGUI;
    private EnchantmentBookGUI enchantmentBookGUI;
    private PetsMenuGUI petsMenuGUI;
    private PickaxeRepairGUI pickaxeRepairGUI;
    private ContainerGUI containerGUI;
    private ContainerFilterGUI containerFilterGUI;
    private ProfessionRewardsGUI professionRewardsGUI;
    private PrestigeGUI prestigeGUI;
    private RankupCommand rankupCommand;
    private BoostGUI boostGUI;
    private BankGUI bankGUI;
    private CrateGUI crateGUI;
    private TankGUI tankGUI;

    //cristaux
    private CristalManager cristalManager;
    private CristalBonusHelper cristalBonusHelper;
    private CristalGUI cristalGUI;
    private ProfessionGUI professionGUI;


    // 3 tâches séparées
    private ActionBarTask actionBarTask;
    private ScoreboardTask scoreboardTask;
    private ChatTask chatTask;

    // Autres tâches
    private AutoSaveTask autoSaveTask;
    private CombustionDecayTask combustionDecayTask;
    private AutoUpgradeTask autoUpgradeTask;

    private AutominerManager autominerManager;
    private AutominerGUI autominerGUI;
    private AutominerEnchantGUI autominerEnchantGUI;
    private AutominerCondHeadGUI autominerCondHeadGUI;
    private AutominerTask autominerTask;
    private AutominerEnchantUpgradeGUI autominerEnchantUpgradeGUI;
    private DatabaseManager databaseManager;


    public static PrisonTycoon getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        logger = new Logger(this);

        logger.info("§a========================================");
        logger.info("§a    PrisonTycoon Plugin Startup");
        logger.info("§a========================================");

        try {
            // Initialisation de la configuration
            initializeConfig();

            // Initialisation des managers
            initializeManagers();

            // Initialisation des GUIs
            initializeGUIs();

            // Enregistrement des événements
            registerEvents();

            // Enregistrement des commandes
            registerCommands();

            PrisonTycoonAPI.initialize(this);

            chatLogger = new ChatLogger(this);

            // Démarrage des tâches asynchrones
            startTasks();
            tabManager.startTabUpdater();

            logger.info("§aPlugin PrisonTycoon activé avec succès!");
            logger.info("§7Fonctionnalités chargées:");
            logger.info("§7- Système de mines protégées");
            logger.info("§7- 18 enchantements custom");
            logger.info("§7- Distinction blocs minés/cassés");
            logger.info("§7- Restrictions hors mine");
            logger.info("§7- Économie (coins/tokens/xp/beacons)");
            logger.info("§7- Interface graphique avancée");
            logger.info("§7- Auto-amélioration des enchantements");
            logger.info("§7- Système de notifications intelligent multi-types");
            logger.info("§7- ScoreboardTask intégré");

        } catch (Exception e) {
            logger.severe("§cErreur lors de l'activation du plugin:");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        logger.info("§cArrêt du plugin PrisonTycoon...");

        try {
            // Arrêt des tâches
            stopTasks();

            // Sauvegarde de toutes les données joueurs
            if (playerDataManager != null) {
                playerDataManager.saveAllPlayersSync();
                logger.info("§aDonnées joueurs sauvegardées.");
            }

            if (chatLogger != null) {
                chatLogger.shutdown();
                logger.info("§7ChatLogger arrêté");
            }

            if (databaseManager != null) {
                databaseManager.close();
                logger.info("§7Database connection closed");
            }

        } catch (Exception e) {
            logger.severe("§cErreur lors de la désactivation:");
            e.printStackTrace();
        }

        logger.info("§aPlugin PrisonTycoon désactivé correctement.");
    }

    /**
     * Initialise le système de configuration
     */
    private void initializeConfig() {
        logger.info("§7Initialisation de la configuration...");
        configManager = new ConfigManager(this);

        // Sauvegarde du fichier de config par défaut si inexistant
        saveDefaultConfig();

        logger.info("§aConfiguration chargée.");
    }

    /**
     * CORRIGÉ : Initialise tous les managers du plugin sans ScoreboardManager
     */
    private void initializeManagers() {
        logger.info("§7Initialisation des managers...");

        // Ordre d'initialisation important pour les dépendances
        databaseManager = new DatabaseManager(this);
        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this);
        enchantmentManager = new EnchantmentManager(this);
        pickaxeManager = new PickaxeManager(this);
        mineManager = new MineManager(this);
        notificationManager = new NotificationManager(this); // NOUVEAU : Système amélioré
        containerManager = new ContainerManager(this);
        cristalManager = new CristalManager(this);
        cristalBonusHelper = new CristalBonusHelper(this);
        globalBonusManager = new GlobalBonusManager(this);
        tabManager = new TabManager(this);
        moderationManager = new ModerationManager(this);
        vipManager = new VipManager(this);
        permissionManager = new PermissionManager(this); // NOUVEAU !
        enchantmentBookManager = new EnchantmentBookManager(this);
        professionManager = new ProfessionManager(this);
        prestigeManager = new PrestigeManager(this);
        reputationManager = new ReputationManager(this);
        blackMarketManager = new BlackMarketManager(this);
        weaponArmorEnchantmentManager = new WeaponArmorEnchantmentManager(this);
        weaponArmorEnchantGUI = new WeaponArmorEnchantGUI(this);
        uniqueEnchantmentBookFactory = new UniqueEnchantmentBookFactory(this);
        voucherManager = new VoucherManager(this);
        boostManager = new BoostManager(this);
        autominerManager = new AutominerManager(this);
        bankManager = new BankManager(this);
        crateManager = new CrateManager(this);
        tankManager = new TankManager(this);
        sellHandManager = new SellHandManager(this);


        logger.info("§aTous les managers initialisés (sans ScoreboardManager).");
    }

    /**
     * Initialise tous les GUIs
     */
    private void initializeGUIs() {
        logger.info("§7Initialisation des interfaces graphiques...");

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
        bankGUI = new BankGUI(this);
        crateGUI = new CrateGUI(this);
        tankGUI = new TankGUI(this);


        logger.info("§aInterfaces graphiques initialisées.");
    }

    /**
     * Enregistre tous les listeners d'événements
     */
    private void registerEvents() {
        logger.info("§7Enregistrement des événements...");

        var pluginManager = getServer().getPluginManager();

        // Événements de base
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
        pluginManager.registerEvents(new CrateListener(this), this);
        pluginManager.registerEvents(new TankListener(this), this);

        logger.info("§aÉvénements enregistrés.");
    }

    /**
     * Enregistre toutes les commandes
     */
    private void registerCommands() {
        logger.info("§7Enregistrement des commandes...");

        // Commandes joueur
        getCommand("pickaxe").setExecutor(new PickaxeCommand(this));
        getCommand("pickaxe").setTabCompleter(new PickaxeCommand(this));
        getCommand("mine").setExecutor(new MineCommand(this));
        getCommand("mine").setTabCompleter(new MineCommand(this));
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("sell").setTabCompleter(new SellCommand(this));
        getCommand("repair").setExecutor(new RepairCommand(this)); // NOUVELLE LIGNE
        getCommand("repair").setTabCompleter(new RepairCommand(this));

        this.rankupCommand = new RankupCommand(this);
        getCommand("rankup").setExecutor(this.rankupCommand);
        getCommand("rankup").setTabCompleter(this.rankupCommand);

        // Commandes admin
        getCommand("givetokens").setExecutor(new GiveTokensCommand(this));
        getCommand("givetokens").setTabCompleter(new GiveTokensCommand(this));
        getCommand("prisontycoon").setExecutor(new PrisonTycoonCommand(this));
        getCommand("prisontycoon").setTabCompleter(new PrisonTycoonCommand(this));
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

        getCommand("bank").setExecutor(new BankCommand(this));
        getCommand("bank").setTabCompleter(new BankCommand(this));

        getCommand("tankadmin").setExecutor(new TankAdminCommand(this));
        getCommand("tankadmin").setTabCompleter(new TankAdminCommand(this));


        logger.info("§aCommandes enregistrées.");
    }

    /**
     * CORRIGÉ : Démarre toutes les tâches avec le nouveau système
     */
    private void startTasks() {
        logger.info("§7Démarrage des tâches asynchrones...");

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
            logger.info("§7- ActionBarTask démarrée (nouveau système multi-notifications toutes les " + actionBarInterval + " ticks)");
        }

        if (getConfig().getBoolean("notifications.scoreboard.enabled", true)) {
            scoreboardTask = new ScoreboardTask(this);
            scoreboardTask.runTaskTimer(this, 0L, scoreboardInterval);
            logger.info("§7- ScoreboardTask démarrée (gestion intégrée toutes les " + scoreboardInterval + " ticks)");
        }

        if (getConfig().getBoolean("notifications.chat.enabled", true)) {
            chatTask = new ChatTask(this);
            chatTask.runTaskTimerAsynchronously(this, chatInterval, chatInterval);
            logger.info("§7- ChatTask démarrée (récapitulatif toutes les " + chatInterval + " ticks)");
        }

        // Autres tâches existantes
        autoSaveTask = new AutoSaveTask(this);
        autoSaveTask.runTaskTimerAsynchronously(this, autoSaveInterval, autoSaveInterval);
        logger.info("§7- AutoSaveTask démarrée (sauvegarde toutes les " + autoSaveInterval + " ticks)");

        combustionDecayTask = new CombustionDecayTask(this);
        combustionDecayTask.runTaskTimer(this, 0L, combustionInterval);
        logger.info("§7- CombustionDecayTask démarrée (décroissance toutes les " + combustionInterval + " ticks)");

        autoUpgradeTask = new AutoUpgradeTask(this);
        autoUpgradeTask.runTaskTimerAsynchronously(this, autoUpgradeInterval, autoUpgradeInterval);
        logger.info("§7- AutoUpgradeTask démarrée (toutes les " + autoUpgradeInterval + " ticks)");

        autominerTask = new AutominerTask(this);
        autominerTask.runTaskTimerAsynchronously(this, 20L, 20L);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (moderationManager != null) {
                moderationManager.cleanupExpiredSanctions();
            }
        }, 12000L, 12000L); // Toutes les 10 minutes
        logger.info("§7- Tâche de nettoyage automatique démarrée");

        // NOUVELLE TÂCHE: Nettoyage des anciens logs (optionnel)
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (chatLogger != null) {
                chatLogger.cleanOldLogs(30); // Garde 30 jours de logs
            }
        }, 86400L, 86400L); // Tous les jours
        logger.info("§7- Tâche de nettoyage des logs démarrée");

        logger.info("§aTâches asynchrones démarrées avec succès (nouveau système).");
    }

    // Getters pour accès aux managers

    /**
     * Arrête toutes les tâches
     */
    private void stopTasks() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            logger.debug("ActionBarTask arrêtée");
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            logger.debug("ScoreboardTask arrêtée");
        }
        if (chatTask != null) {
            chatTask.cancel();
            logger.debug("ChatTask arrêtée");
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            logger.debug("AutoSaveTask arrêtée");
        }
        if (combustionDecayTask != null) {
            combustionDecayTask.cancel();
            logger.debug("CombustionDecayTask arrêtée");
        }
        if (autoUpgradeTask != null) {
            autoUpgradeTask.cancel();
            logger.debug("AutoUpgradeTask arrêtée");
        }
        if (moderationManager != null) {
            moderationManager.cleanupExpiredSanctions();
            logger.info("§7ModerationManager nettoyé");
        }
        if (permissionManager != null) {
            permissionManager.cleanup();
        }
        if (autominerTask != null) {
            autominerTask.cancel();
            getPluginLogger().info("§7Tâche d'automineur arrêtée.");
        }
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

    public Logger getPluginLogger() {
        return logger;
    }

    public AutoUpgradeTask getAutoUpgradeTask() {
        return autoUpgradeTask;
    }

    // Getters pour les tâches séparées

    public ActionBarTask getActionBarTask() {
        return actionBarTask;
    }

    public ScoreboardTask getScoreboardTask() {
        return scoreboardTask;
    }

    public ChatTask getChatTask() {
        return chatTask;
    }

    // Getters pour les GUIs

    public EnchantmentMenu getMainMenuGUI() {
        return mainMenuGUI;
    }

    public CategoryMenuGUI getCategoryMenuGUI() {
        return categoryMenuGUI;
    }

    public EnchantmentUpgradeGUI getEnchantmentUpgradeGUI() {
        return enchantmentUpgradeGUI;
    }

    public EnchantmentBookManager getEnchantmentBookManager() {
        return enchantmentBookManager;
    }

    public EnchantmentBookGUI getEnchantmentBookGUI() {
        return enchantmentBookGUI;
    }

    public PetsMenuGUI getPetsMenuGUI() {
        return petsMenuGUI;
    }

    public PickaxeRepairGUI getPickaxeRepairMenu() {
        return pickaxeRepairGUI;
    }

    /**
     * Getter pour le manager des conteneurs
     */
    public ContainerManager getContainerManager() {
        return containerManager;
    }

    /**
     * Getter pour le GUI des conteneurs
     */
    public ContainerGUI getContainerGUI() {
        return containerGUI;
    }

    public ContainerFilterGUI getContainerFilterGUI() {
        return containerFilterGUI;
    }

    public RankupCommand getRankupCommand() {
        return rankupCommand;
    }

    public CristalManager getCristalManager() {
        return cristalManager;
    }

    public CristalBonusHelper getCristalBonusHelper() {
        return cristalBonusHelper;
    }

    public CristalGUI getCristalGUI() {
        return cristalGUI;
    }

    public GlobalBonusManager getGlobalBonusManager() {
        return globalBonusManager;
    }

    public TabManager getTabManager() {
        return tabManager;
    }

    /**
     * Obtient le gestionnaire de modération
     */
    public ModerationManager getModerationManager() {
        return moderationManager;
    }

    /**
     * Obtient le gestionnaire de logs du chat
     */
    public ChatLogger getChatLogger() {
        return chatLogger;
    }

    /**
     * Obtient le gestionnaire des VIP
     */
    public VipManager getVipManager() {
        return vipManager;
    }

    public PermissionManager getPermissionManager() {
        return permissionManager;
    }

    /**
     * Obtient le gestionnaire des métiers
     */
    public ProfessionManager getProfessionManager() {
        return professionManager;
    }

    /**
     * Obtient l'interface graphique des métiers
     */
    public ProfessionGUI getProfessionGUI() {
        return professionGUI;
    }

    /**
     * Obtient l'interface graphique des métiers
     */
    public ProfessionRewardsGUI getProfessionRewardsGUI() {
        return professionRewardsGUI;
    }

    public PrestigeManager getPrestigeManager() {
        return prestigeManager;
    }

    public PrestigeGUI getPrestigeGUI() {
        return prestigeGUI;
    }

    public ReputationManager getReputationManager() {
        return reputationManager;
    }

    public BlackMarketManager getBlackMarketManager() {
        return blackMarketManager;
    }

    public WeaponArmorEnchantmentManager getWeaponArmorEnchantmentManager() {
        return weaponArmorEnchantmentManager;
    }

    public WeaponArmorEnchantGUI getWeaponArmorEnchantGUI() {
        return weaponArmorEnchantGUI;
    }

    public UniqueEnchantmentBookFactory getUniqueEnchantmentBookFactory() {
        return uniqueEnchantmentBookFactory;
    }

    public VoucherManager getVoucherManager() {
        return voucherManager;
    }

    public BoostManager getBoostManager() {
        return boostManager;
    }

    public BoostGUI getBoostGUI() {
        return boostGUI;
    }

    public AutominerManager getAutominerManager() {
        return autominerManager;
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

    public BankManager getBankManager() {
        return bankManager;
    }

    public BankGUI getBankGUI() {
        return bankGUI;
    }

    /**
     * Obtient le gestionnaire des crates
     */
    public CrateManager getCrateManager() {
        return crateManager;
    }

    /**
     * Obtient l'interface graphique des crates
     */
    public CrateGUI getCrateGUI() {
        return crateGUI;
    }

    public TankManager getTankManager() {
        return tankManager;
    }

    public TankGUI getTankGUI() {
        return tankGUI;
    }

    public SellHandManager getSellHandManager() {
        return sellHandManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}

