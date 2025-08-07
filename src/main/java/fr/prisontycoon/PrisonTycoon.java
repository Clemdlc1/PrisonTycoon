package fr.prisontycoon;

import fr.custommobs.CustomMobsPlugin;
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
import fr.prisontycoon.utils.StartupBedrockReplacer;
import fr.prisontycoon.vouchers.VoucherManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal PrisonTycoon
 */
public final class PrisonTycoon extends JavaPlugin {

    // --- Champs Statiques & Instances ---
    private static PrisonTycoon instance;
    private CustomMobsPlugin customMobsPlugin = null;

    // --- Utilitaires Core ---
    private Logger logger;
    private ChatLogger chatLogger;

    // --- Managers ---
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private EconomyManager economyManager;
    private MineManager mineManager;
    private PickaxeManager pickaxeManager;
    private EnchantmentManager enchantmentManager;
    private EnchantmentBookManager enchantmentBookManager;
    private WeaponArmorEnchantmentManager weaponArmorEnchantmentManager;
    private UniqueEnchantmentBookFactory uniqueEnchantmentBookFactory;
    private ContainerManager containerManager;
    private NotificationManager notificationManager;
    private GlobalBonusManager globalBonusManager;
    private TabManager tabManager;
    private ModerationManager moderationManager;
    private PermissionManager permissionManager;
    private VipManager vipManager;
    private ProfessionManager professionManager;
    private PrestigeManager prestigeManager;
    private ReputationManager reputationManager;
    private BlackMarketManager blackMarketManager;
    private VoucherManager voucherManager;
    private BoostManager boostManager;
    private AutominerManager autominerManager;
    private BankManager bankManager;
    private CrateManager crateManager;
    private TankManager tankManager;
    private SellHandManager sellHandManager;
    private GangManager gangManager;
    private GUIManager guiManager;
    private OutpostManager outpostManager;
    private WarpManager warpManager;
    private CristalManager cristalManager;
    private CristalBonusHelper cristalBonusHelper;
    private HeadCollectionManager headCollectionManager;

    // --- GUIs ---
    private AutominerCondHeadGUI autominerCondHeadGUI;
    private AutominerEnchantGUI autominerEnchantGUI;
    private AutominerEnchantUpgradeGUI autominerEnchantUpgradeGUI;
    private AutominerGUI autominerGUI;
    private BankGUI bankGUI;
    private BoostGUI boostGUI;
    private CategoryMenuGUI categoryMenuGUI;
    private ContainerFilterGUI containerFilterGUI;
    private ContainerGUI containerGUI;
    private CrateGUI crateGUI;
    private CristalGUI cristalGUI;
    private EnchantmentBookGUI enchantmentBookGUI;
    private EnchantmentMenu mainMenuGUI;
    private MainMenuGUI mainNavigationGUI;
    private EnchantmentUpgradeGUI enchantmentUpgradeGUI;
    private GangGUI gangGUI;
    private OutpostGUI outpostGUI;
    private PetsMenuGUI petsMenuGUI;
    private PickaxeRepairGUI pickaxeRepairGUI;
    private PrestigeGUI prestigeGUI;
    private ProfessionGUI professionGUI;
    private ProfessionRewardsGUI professionRewardsGUI;
    private TankGUI tankGUI;
    private WarpGUI warpGUI;
    private WeaponArmorEnchantGUI weaponArmorEnchantGUI;
    private HeadCollectionGUI headCollectionGUI;

    // --- Tâches ---
    private ActionBarTask actionBarTask;
    private ScoreboardTask scoreboardTask;
    private ChatTask chatTask;
    private AutoSaveTask autoSaveTask;
    private CombustionDecayTask combustionDecayTask;
    private AutoUpgradeTask autoUpgradeTask;
    private AutominerTask autominerTask;
    private PickaxeContainerUpdateTask pickaxeContainerTask;

    //listeners
    private HeadCollectionListener headCollectionListener;


    // --- Commandes avec état ---
    private RankupCommand rankupCommand;

    public static PrisonTycoon getInstance() {
        return instance;
    }

    // ===============================================================================================
    // MÉTHODES DU CYCLE DE VIE DU PLUGIN (ENABLE/DISABLE)
    // ===============================================================================================

    @Override
    public void onEnable() {
        instance = this;
        logger = new Logger(this);

        logger.info("§a========================================");
        logger.info("§a    PrisonTycoon Plugin Startup");
        logger.info("§a========================================");

        try {
            // Initialisations dans l'ordre de dépendance
            initializeConfig();
            initializeManagers();
            initializeGUIs();
            registerEvents();
            registerCommands();

            PrisonTycoonAPI.initialize(this);
            chatLogger = new ChatLogger(this);

            // Démarrage des tâches
            startTasks();
            tabManager.startTabUpdater();

            logger.info("§aPlugin PrisonTycoon activé avec succès!");
            logger.info("§7Fonctionnalités chargées: Système de mines, enchantements, économie, GUI, etc.");

        } catch (Exception e) {
            logger.severe("§cErreur critique lors de l'activation du plugin:");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        logger.info("§cArrêt du plugin PrisonTycoon...");

        try {
            stopTasks();

            if (playerDataManager != null) {
                playerDataManager.saveAllPlayersSync();
                logger.info("§aDonnées joueurs sauvegardées.");
            }
            if (chatLogger != null) {
                chatLogger.shutdown();
                logger.info("§7ChatLogger arrêté.");
            }
            if (databaseManager != null) {
                databaseManager.close();
                logger.info("§7Connexion à la base de données fermée.");
            }
            if (blackMarketManager != null) {
                blackMarketManager.shutdown();
                logger.info("§7BlackMarketManager fermé.");
            }
        } catch (Exception e) {
            logger.severe("§cErreur lors de la désactivation du plugin:");
            e.printStackTrace();
        }

        logger.info("§aPlugin PrisonTycoon désactivé correctement.");
    }

    // ===============================================================================================
    // MÉTHODES D'INITIALISATION
    // ===============================================================================================

    private void initializeConfig() {
        logger.info("§7Initialisation de la configuration...");
        configManager = new ConfigManager(this);
        saveDefaultConfig();
        logger.info("§aConfiguration chargée.");
    }

    private void initializeManagers() {
        logger.info("§7Initialisation des managers...");

        // L'ordre est important pour les dépendances
        databaseManager = new DatabaseManager(this);
        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this);
        enchantmentManager = new EnchantmentManager(this);
        pickaxeManager = new PickaxeManager(this);
        mineManager = new MineManager(this);
        notificationManager = new NotificationManager(this);
        containerManager = new ContainerManager(this);
        cristalManager = new CristalManager(this);
        cristalBonusHelper = new CristalBonusHelper(this);
        globalBonusManager = new GlobalBonusManager(this);
        tabManager = new TabManager(this);
        moderationManager = new ModerationManager(this);
        vipManager = new VipManager(this);
        permissionManager = new PermissionManager(this);
        enchantmentBookManager = new EnchantmentBookManager(this);
        professionManager = new ProfessionManager(this);
        prestigeManager = new PrestigeManager(this);
        reputationManager = new ReputationManager(this);
        blackMarketManager = new BlackMarketManager(this);
        weaponArmorEnchantmentManager = new WeaponArmorEnchantmentManager(this);
        uniqueEnchantmentBookFactory = new UniqueEnchantmentBookFactory(this);
        voucherManager = new VoucherManager(this);
        boostManager = new BoostManager(this);
        autominerManager = new AutominerManager(this);
        bankManager = new BankManager(this);
        crateManager = new CrateManager(this);
        tankManager = new TankManager(this);
        sellHandManager = new SellHandManager(this);
        gangManager = new GangManager(this);
        outpostManager = new OutpostManager(this);
        warpManager = new WarpManager(this);
        headCollectionManager = new HeadCollectionManager(this);
        guiManager = new GUIManager(this);

        logger.info("§aTous les managers ont été initialisés.");
    }

    private void initializeGUIs() {
        logger.info("§7Initialisation des interfaces graphiques...");

        mainMenuGUI = new EnchantmentMenu(this);
        mainNavigationGUI = new MainMenuGUI(this);
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
        gangGUI = new GangGUI(this);
        outpostGUI = new OutpostGUI(this);
        warpGUI = new WarpGUI(this);
        weaponArmorEnchantGUI = new WeaponArmorEnchantGUI(this);
        headCollectionGUI = new HeadCollectionGUI(this);

        logger.info("§aInterfaces graphiques initialisées.");
    }

    private void registerEvents() {
        logger.info("§7Enregistrement des événements...");

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerJoinQuitListener(this), this);
        pluginManager.registerEvents(new MiningListener(this), this);
        pluginManager.registerEvents(new PickaxeProtectionListener(this), this);
        pluginManager.registerEvents(new GUIListener(this), this);
        pluginManager.registerEvents(new MobilityEffectsListener(this), this);
        pluginManager.registerEvents(new ContainerListener(this), this);
        pluginManager.registerEvents(new CristalListener(this), this);
        pluginManager.registerEvents(new ChatListener(this), this);
        pluginManager.registerEvents(new NPCInteract(this), this);
        pluginManager.registerEvents(new WeaponArmorEnchantmentListener(this), this);
        pluginManager.registerEvents(new VoucherBoostListener(this), this);
        pluginManager.registerEvents(new CrateListener(this), this);
        pluginManager.registerEvents(new TankListener(this), this);
        pluginManager.registerEvents(new OutpostListener(this), this);
        pluginManager.registerEvents(new PluginLoadListener(this), this);
        this.headCollectionListener = new HeadCollectionListener(this);
        pluginManager.registerEvents(this.headCollectionListener, this);
        logger.info("§aÉvénements enregistrés.");
    }

    /**
     * Enregistre une commande et ses alias en utilisant un seul objet handler.
     * Cette méthode vérifie automatiquement si le handler implémente CommandExecutor et/ou TabCompleter.
     *
     * @param handler L'objet qui gère la commande (doit implémenter au moins CommandExecutor).
     * @param commandNames Le nom principal de la commande, suivi de tous ses alias.
     */
    private void registerCommand(Object handler, String... commandNames) {
        // S'assurer que le handler est au moins un CommandExecutor
        if (!(handler instanceof CommandExecutor executor)) {
            if (commandNames.length > 0) {
                logger.warning("§cTentative d'enregistrer '" + commandNames[0] + "' avec un handler qui n'est pas un CommandExecutor.");
            }
            return;
        }

        TabCompleter completer = (handler instanceof TabCompleter) ? (TabCompleter) handler : null;

        for (String commandName : commandNames) {
            PluginCommand command = getCommand(commandName);
            if (command != null) {
                command.setExecutor(executor);
                if (completer != null) {
                    command.setTabCompleter(completer);
                }
            } else {
                logger.warning("§cLa commande '" + commandName + "' n'a pas pu être trouvée. Assurez-vous qu'elle est déclarée dans votre fichier plugin.yml !");
            }
        }
    }

    /**
     * Enregistre toutes les commandes du plugin de manière optimisée.
     */
    private void registerCommands() {
        logger.info("§7Enregistrement des commandes...");

        // Commandes où la même classe gère l'exécution et la complétion
        registerCommand(new PickaxeCommand(this), "pickaxe");
        registerCommand(new MineCommand(this), "adminmine");
        registerCommand(new SellCommand(this), "sell");
        registerCommand(new RepairCommand(this), "repair");
        registerCommand(new HeadCollectionCommand(this), "collection");
        this.rankupCommand = new RankupCommand(this);
        registerCommand(rankupCommand, "rankup");

        registerCommand(new GiveTokensCommand(this), "givetokens");
        registerCommand(new PrisonTycoonCommand(this), "prisontycoon");
        registerCommand(new ContainerCommand(this), "conteneur");
        registerCommand(new CristalCommand(this), "cristal");
        registerCommand(new AdminChatCommand(this), "adminchat");
        registerCommand(new VipCommand(this), "vip");
        registerCommand(new InvseeCommand(this), "invsee");
        registerCommand(new MetierCommand(this), "metier");
        registerCommand(new PrestigeCommand(this), "prestige");
        registerCommand(new BoostCommand(this), "boost");
        registerCommand(new GiveBoostCommand(this), "giveboost");
        registerCommand(new BankCommand(this), "bank");
        registerCommand(new TankAdminCommand(this), "tankadmin");

        // Commandes avec alias
        registerCommand(new GangCommand(this), "gang", "g");
        registerCommand(new WarpCommand(this), "warp", "mine", "spawn");
        registerCommand(new MenuCommand(this), "menu");

        // Commandes n'ayant pas de TabCompleter (la méthode gère cela automatiquement)
        registerCommand(new EnchantmentBookCommand(this), "enchantbook");
        registerCommand(new ReputationCommand(this), "rep");
        registerCommand(new BlackMarketCommand(this), "fbm");
        registerCommand(new VoucherCommand(this), "voucher");
        registerCommand(new AutominerCommand(this), "autominer");
        registerCommand(new OutpostCommand(this), "ap");

        logger.info("§aCommandes enregistrées.");
    }

    // ===============================================================================================
    // GESTION DES TÂCHES
    // ===============================================================================================

    private void startTasks() {
        logger.info("§7Démarrage des tâches asynchrones...");

        new StartupBedrockReplacer(this).executeReplacement();

        int actionBarInterval = getConfig().getInt("performance.task-intervals.action-bar-ticks", 5);
        int scoreboardInterval = getConfig().getInt("performance.task-intervals.scoreboard-ticks", 50);
        int chatInterval = getConfig().getInt("performance.task-intervals.chat-ticks", 1200);
        int autoSaveInterval = getConfig().getInt("performance.task-intervals.auto-save-ticks", 6000);
        int combustionInterval = getConfig().getInt("performance.task-intervals.combustion-ticks", 200);
        int autoUpgradeInterval = getConfig().getInt("performance.task-intervals.auto-upgrade-ticks", 200);

        if (getConfig().getBoolean("notifications.action-bar.enabled", true)) {
            actionBarTask = new ActionBarTask(this);
            actionBarTask.runTaskTimerAsynchronously(this, 0L, actionBarInterval);
        }

        if (getConfig().getBoolean("notifications.scoreboard.enabled", true)) {
            scoreboardTask = new ScoreboardTask(this);
            scoreboardTask.runTaskTimer(this, 0L, scoreboardInterval);
        }

        if (getConfig().getBoolean("notifications.chat.enabled", true)) {
            chatTask = new ChatTask(this);
            chatTask.runTaskTimerAsynchronously(this, chatInterval, chatInterval);
        }

        autoSaveTask = new AutoSaveTask(this);
        autoSaveTask.runTaskTimerAsynchronously(this, autoSaveInterval, autoSaveInterval);

        combustionDecayTask = new CombustionDecayTask(this);
        combustionDecayTask.runTaskTimer(this, 0L, combustionInterval);

        autoUpgradeTask = new AutoUpgradeTask(this);
        autoUpgradeTask.runTaskTimerAsynchronously(this, autoUpgradeInterval, autoUpgradeInterval);

        autominerTask = new AutominerTask(this);
        autominerTask.runTaskTimerAsynchronously(this, 20L, 20L);

        pickaxeContainerTask = new PickaxeContainerUpdateTask(this);
        pickaxeContainerTask.runTaskTimerAsynchronously(this, 20L, 60L);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (moderationManager != null) moderationManager.cleanupExpiredSanctions();
        }, 12000L, 12000L); // Chaque 10 minutes

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (chatLogger != null) chatLogger.cleanOldLogs(30); // Garde 30 jours
        }, 86400L, 86400L); // Chaque jour

        logger.info("§aTâches asynchrones démarrées.");
    }

    private void stopTasks() {
        logger.info("§7Arrêt des tâches...");
        getServer().getScheduler().cancelTasks(this);

        // Actions de nettoyage supplémentaires si nécessaire
        if (gangManager != null) gangManager.shutdown();
        if (permissionManager != null) permissionManager.cleanup();
        logger.info("§aToutes les tâches ont été arrêtées.");
    }

    // ===============================================================================================
    // GETTERS & SETTERS
    // ===============================================================================================

    // --- Core & Utils ---
    public Logger getPluginLogger() { return logger; }
    public ChatLogger getChatLogger() { return chatLogger; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }

    // --- Plugin Intégration ---
    public CustomMobsPlugin getCustomMobsPlugin() { return this.customMobsPlugin; }
    public void setCustomMobsPlugin(CustomMobsPlugin customMobsPlugin) { this.customMobsPlugin = customMobsPlugin; }

    // --- Managers ---
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public MineManager getMineManager() { return mineManager; }
    public EnchantmentManager getEnchantmentManager() { return enchantmentManager; }
    public PickaxeManager getPickaxeManager() { return pickaxeManager; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public ContainerManager getContainerManager() { return containerManager; }
    public GlobalBonusManager getGlobalBonusManager() { return globalBonusManager; }
    public TabManager getTabManager() { return tabManager; }
    public ModerationManager getModerationManager() { return moderationManager; }
    public VipManager getVipManager() { return vipManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public EnchantmentBookManager getEnchantmentBookManager() { return enchantmentBookManager; }
    public ProfessionManager getProfessionManager() { return professionManager; }
    public PrestigeManager getPrestigeManager() { return prestigeManager; }
    public ReputationManager getReputationManager() { return reputationManager; }
    public BlackMarketManager getBlackMarketManager() { return blackMarketManager; }
    public WeaponArmorEnchantmentManager getWeaponArmorEnchantmentManager() { return weaponArmorEnchantmentManager; }
    public UniqueEnchantmentBookFactory getUniqueEnchantmentBookFactory() { return uniqueEnchantmentBookFactory; }
    public VoucherManager getVoucherManager() { return voucherManager; }
    public BoostManager getBoostManager() { return boostManager; }
    public AutominerManager getAutominerManager() { return autominerManager; }
    public BankManager getBankManager() { return bankManager; }
    public CrateManager getCrateManager() { return crateManager; }
    public TankManager getTankManager() { return tankManager; }
    public SellHandManager getSellHandManager() { return sellHandManager; }
    public GangManager getGangManager() { return gangManager; }
    public GUIManager getGUIManager() { return guiManager; }
    public OutpostManager getOutpostManager() { return outpostManager; }
    public WarpManager getWarpManager() { return warpManager; }
    public CristalManager getCristalManager() { return cristalManager; }
    public CristalBonusHelper getCristalBonusHelper() { return cristalBonusHelper; }
    public HeadCollectionManager getHeadCollectionManager() { return headCollectionManager; }
    // --- Tâches ---
    public AutoUpgradeTask getAutoUpgradeTask() { return autoUpgradeTask; }
    public ActionBarTask getActionBarTask() { return actionBarTask; }
    public ScoreboardTask getScoreboardTask() { return scoreboardTask; }

    // --- GUIs ---
    public EnchantmentMenu getMainMenuGUI() { return mainMenuGUI; }
    public MainMenuGUI getMainNavigationGUI() { return mainNavigationGUI; }
    public CategoryMenuGUI getCategoryMenuGUI() { return categoryMenuGUI; }
    public EnchantmentUpgradeGUI getEnchantmentUpgradeGUI() { return enchantmentUpgradeGUI; }
    public EnchantmentBookGUI getEnchantmentBookGUI() { return enchantmentBookGUI; }
    public PetsMenuGUI getPetsMenuGUI() { return petsMenuGUI; }
    public PickaxeRepairGUI getPickaxeRepairMenu() { return pickaxeRepairGUI; }
    public ContainerGUI getContainerGUI() { return containerGUI; }
    public ContainerFilterGUI getContainerFilterGUI() { return containerFilterGUI; }
    public CristalGUI getCristalGUI() { return cristalGUI; }
    public ProfessionGUI getProfessionGUI() { return professionGUI; }
    public ProfessionRewardsGUI getProfessionRewardsGUI() { return professionRewardsGUI; }
    public PrestigeGUI getPrestigeGUI() { return prestigeGUI; }
    public BoostGUI getBoostGUI() { return boostGUI; }
    public AutominerGUI getAutominerGUI() { return autominerGUI; }
    public AutominerEnchantGUI getAutominerEnchantGUI() { return autominerEnchantGUI; }
    public AutominerCondHeadGUI getAutominerCondHeadGUI() { return autominerCondHeadGUI; }
    public AutominerEnchantUpgradeGUI getAutominerEnchantUpgradeGUI() { return autominerEnchantUpgradeGUI; }
    public BankGUI getBankGUI() { return bankGUI; }
    public CrateGUI getCrateGUI() { return crateGUI; }
    public TankGUI getTankGUI() { return tankGUI; }
    public GangGUI getGangGUI() { return gangGUI; }
    public OutpostGUI getOutpostGUI() { return outpostGUI; }
    public WarpGUI getWarpGUI() { return warpGUI; }
    public WeaponArmorEnchantGUI getWeaponArmorEnchantGUI() { return weaponArmorEnchantGUI; }
    public HeadCollectionGUI getHeadCollectionGUI() { return headCollectionGUI; }

    // --- Commandes ---
    public RankupCommand getRankupCommand() { return rankupCommand; }

    // --- Listeners ---
    public HeadCollectionListener getHeadCollectionListener() { return headCollectionListener; }
}