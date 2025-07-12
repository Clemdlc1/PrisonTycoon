package fr.prisoncore.prisoncore.prisonTycoon;

import fr.prisoncore.prisoncore.prisonTycoon.commands.GiveTokensCommand;
import fr.prisoncore.prisoncore.prisonTycoon.commands.MineCommand;
import fr.prisoncore.prisoncore.prisonTycoon.commands.PickaxeCommand;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.EnchantmentManager;
import fr.prisoncore.prisoncore.prisonTycoon.events.*;
import fr.prisoncore.prisoncore.prisonTycoon.GUI.*;
import fr.prisoncore.prisoncore.prisonTycoon.managers.*;
import fr.prisoncore.prisoncore.prisonTycoon.tasks.ActionBarTask;
import fr.prisoncore.prisoncore.prisonTycoon.tasks.AutoSaveTask;
import fr.prisoncore.prisoncore.prisonTycoon.tasks.AutoUpgradeTask;
import fr.prisoncore.prisoncore.prisonTycoon.tasks.CombustionDecayTask;
import fr.prisoncore.prisoncore.prisonTycoon.utils.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal PrisonTycoon
 * CORRIGÉ : Avec ScoreboardManager et nouveaux GUIs séparés
 */
public final class PrisonTycoon extends JavaPlugin {

    // Instance singleton du plugin
    private static PrisonTycoon instance;

    // Managers principaux
    private ConfigManager configManager;
    private PlayerDataManager playerDataManager;
    private MineManager mineManager;
    private EnchantmentManager enchantmentManager;
    private PickaxeManager pickaxeManager;
    private EconomyManager economyManager;
    private ScoreboardManager scoreboardManager;
    private Logger logger;

    // NOUVEAU: GUIs séparés
    private MainMenuGUI mainMenuGUI;
    private CategoryMenuGUI categoryMenuGUI;
    private EnchantmentUpgradeGUI enchantmentUpgradeGUI;
    private CrystalsMenuGUI crystalsMenuGUI;
    private UniqueEnchantsMenuGUI uniqueEnchantsMenuGUI;
    private PetsMenuGUI petsMenuGUI;

    // Tasks asynchrones
    private ActionBarTask actionBarTask;
    private AutoSaveTask autoSaveTask;
    private CombustionDecayTask combustionDecayTask;
    private AutoUpgradeTask autoUpgradeTask;

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

            // NOUVEAU: Initialisation des GUIs
            initializeGUIs();

            // Enregistrement des événements
            registerEvents();

            // Enregistrement des commandes
            registerCommands();

            // Démarrage des tâches asynchrones
            startTasks();

            logger.info("§aPlugin PrisonTycoon activé avec succès!");
            logger.info("§7Fonctionnalités chargées:");
            logger.info("§7- Système de mines protégées");
            logger.info("§7- Pioche légendaire unique");
            logger.info("§7- 18 enchantements custom");
            logger.info("§7- Économie triple (coins/tokens/xp)");
            logger.info("§7- Interface graphique avancée");
            logger.info("§7- Auto-amélioration des enchantements");
            logger.info("§7- Scoreboard en temps réel");
            logger.info("§7- Notifications Greed hotbar");

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

            // Nettoyage des managers
            cleanupManagers();

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
     * Initialise tous les managers du plugin
     */
    private void initializeManagers() {
        logger.info("§7Initialisation des managers...");

        // Ordre d'initialisation important pour les dépendances
        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this);
        enchantmentManager = new EnchantmentManager(this);
        pickaxeManager = new PickaxeManager(this);
        mineManager = new MineManager(this);
        scoreboardManager = new ScoreboardManager(this);

        logger.info("§aTous les managers initialisés.");
    }

    /**
     * NOUVEAU: Initialise tous les GUIs
     */
    private void initializeGUIs() {
        logger.info("§7Initialisation des interfaces graphiques...");

        mainMenuGUI = new MainMenuGUI(this);
        categoryMenuGUI = new CategoryMenuGUI(this);
        enchantmentUpgradeGUI = new EnchantmentUpgradeGUI(this);
        crystalsMenuGUI = new CrystalsMenuGUI(this);
        uniqueEnchantsMenuGUI = new UniqueEnchantsMenuGUI(this);
        petsMenuGUI = new PetsMenuGUI(this);

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

        logger.info("§aÉvénements enregistrés.");
    }

    /**
     * Enregistre toutes les commandes
     */
    private void registerCommands() {
        logger.info("§7Enregistrement des commandes...");

        // Commandes joueur
        getCommand("pickaxe").setExecutor(new PickaxeCommand(this));
        getCommand("mine").setExecutor(new MineCommand(this));

        // Commandes admin
        getCommand("givetokens").setExecutor(new GiveTokensCommand(this));

        logger.info("§aCommandes enregistrées.");
    }

    /**
     * Démarre toutes les tâches asynchrones
     */
    private void startTasks() {
        logger.info("§7Démarrage des tâches asynchrones...");

        // Action bar et récapitulatif minute
        actionBarTask = new ActionBarTask(this);
        actionBarTask.runTaskTimerAsynchronously(this, 0L, 20L);

        // Sauvegarde automatique toutes les 5 minutes
        autoSaveTask = new AutoSaveTask(this);
        autoSaveTask.runTaskTimerAsynchronously(this, 6000L, 6000L);

        // Décroissance combustion toutes les secondes
        combustionDecayTask = new CombustionDecayTask(this);
        combustionDecayTask.runTaskTimer(this, 0L, 20L);

        // Auto-amélioration toutes les 10 secondes
        autoUpgradeTask = new AutoUpgradeTask(this);
        autoUpgradeTask.runTaskTimerAsynchronously(this, 200L, 200L);

        logger.info("§aTâches asynchrones démarrées (ActionBar, AutoSave, Combustion, AutoUpgrade).");
    }

    /**
     * Arrête toutes les tâches
     */
    private void stopTasks() {
        if (actionBarTask != null) actionBarTask.cancel();
        if (autoSaveTask != null) autoSaveTask.cancel();
        if (combustionDecayTask != null) combustionDecayTask.cancel();
        if (autoUpgradeTask != null) autoUpgradeTask.cancel();
    }

    /**
     * Nettoie tous les managers
     */
    private void cleanupManagers() {
        // Les managers se nettoient automatiquement
        // Cette méthode est prête pour d'éventuels nettoyages futurs
    }

    // Getters pour accès aux managers

    public static PrisonTycoon getInstance() {
        return instance;
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

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public Logger getPluginLogger() {
        return logger;
    }

    public AutoUpgradeTask getAutoUpgradeTask() {
        return autoUpgradeTask;
    }

    // NOUVEAU: Getters pour les GUIs

    public MainMenuGUI getMainMenuGUI() {
        return mainMenuGUI;
    }

    public CategoryMenuGUI getCategoryMenuGUI() {
        return categoryMenuGUI;
    }

    public EnchantmentUpgradeGUI getEnchantmentUpgradeGUI() {
        return enchantmentUpgradeGUI;
    }

    public CrystalsMenuGUI getCrystalsMenuGUI() {
        return crystalsMenuGUI;
    }

    public UniqueEnchantsMenuGUI getUniqueEnchantsMenuGUI() {
        return uniqueEnchantsMenuGUI;
    }

    public PetsMenuGUI getPetsMenuGUI() {
        return petsMenuGUI;
    }

    // SUPPRIMÉ: getEnchantmentGUI() - remplacé par les GUIs séparés
}