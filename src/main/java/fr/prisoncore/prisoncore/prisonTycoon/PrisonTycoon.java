package fr.prisoncore.prisoncore.prisonTycoon;

import fr.prisoncore.prisoncore.prisonTycoon.commands.*;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.EnchantmentManager;
import fr.prisoncore.prisoncore.prisonTycoon.events.*;
import fr.prisoncore.prisoncore.prisonTycoon.GUI.*;
import fr.prisoncore.prisoncore.prisonTycoon.managers.*;
import fr.prisoncore.prisoncore.prisonTycoon.tasks.*;
import fr.prisoncore.prisoncore.prisonTycoon.utils.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin principal PrisonTycoon
 * CORRIGÉ : Suppression de ScoreboardManager, utilisation du nouveau système
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
    private NotificationManager notificationManager;
    private ContainerManager containerManager;
    private Logger logger;

    // SUPPRIMÉ : ScoreboardManager (maintenant intégré dans ScoreboardTask)

    // GUIs séparés
    private EnchantmentMenu mainMenuGUI;
    private CategoryMenuGUI categoryMenuGUI;
    private EnchantmentUpgradeGUI enchantmentUpgradeGUI;
    private CrystalsMenuGUI crystalsMenuGUI;
    private UniqueEnchantsMenuGUI uniqueEnchantsMenuGUI;
    private PetsMenuGUI petsMenuGUI;
    private PickaxeRepairGUI pickaxeRepairGUI;
    private ContainerGUI containerGUI;
    private ContainerFilterGUI containerFilterGUI;



    // 3 tâches séparées
    private ActionBarTask actionBarTask;
    private ScoreboardTask scoreboardTask;
    private ChatTask chatTask;

    // Autres tâches
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

            // Initialisation des GUIs
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
            logger.info("§7- Pioche légendaire immobile (slot 1)");
            logger.info("§7- 18 enchantements custom");
            logger.info("§7- Distinction blocs minés/cassés");
            logger.info("§7- Restrictions hors mine");
            logger.info("§7- Économie triple (coins/tokens/xp)");
            logger.info("§7- Interface graphique avancée");
            logger.info("§7- Auto-amélioration des enchantements");
            logger.info("§7- Système de notifications intelligent multi-types");
            logger.info("§7- ScoreboardTask intégré (sans ScoreboardManager)");

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
     * CORRIGÉ : Initialise tous les managers du plugin sans ScoreboardManager
     */
    private void initializeManagers() {
        logger.info("§7Initialisation des managers...");

        // Ordre d'initialisation important pour les dépendances
        playerDataManager = new PlayerDataManager(this);
        economyManager = new EconomyManager(this);
        enchantmentManager = new EnchantmentManager(this);
        pickaxeManager = new PickaxeManager(this);
        mineManager = new MineManager(this);
        notificationManager = new NotificationManager(this); // NOUVEAU : Système amélioré
        containerManager = new ContainerManager(this);


        // SUPPRIMÉ : scoreboardManager (maintenant dans ScoreboardTask)

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
        crystalsMenuGUI = new CrystalsMenuGUI(this);
        uniqueEnchantsMenuGUI = new UniqueEnchantsMenuGUI(this);
        petsMenuGUI = new PetsMenuGUI(this);
        pickaxeRepairGUI = new PickaxeRepairGUI(this);
        containerGUI = new ContainerGUI(this);
        containerFilterGUI = new ContainerFilterGUI(this);

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
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("repair").setExecutor(new RepairCommand(this)); // NOUVELLE LIGNE


        // Commandes admin
        getCommand("givetokens").setExecutor(new GiveTokensCommand(this));
        getCommand("prisontycoon").setExecutor(new PrisonTycoonCommand(this));
        getCommand("conteneur").setExecutor(new ContainerCommand(this));


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
        int combustionInterval = getConfig().getInt("performance.task-intervals.combustion-ticks", 20);
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
        logger.info("§7- CombustionDecayTask démarrée (décroissance corrigée toutes les " + combustionInterval + " ticks)");

        autoUpgradeTask = new AutoUpgradeTask(this);
        autoUpgradeTask.runTaskTimerAsynchronously(this, autoUpgradeInterval, autoUpgradeInterval);
        logger.info("§7- AutoUpgradeTask démarrée (toutes les " + autoUpgradeInterval + " ticks)");

        logger.info("§aTâches asynchrones démarrées avec succès (nouveau système).");
    }

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

    public CrystalsMenuGUI getCrystalsMenuGUI() {
        return crystalsMenuGUI;
    }

    public UniqueEnchantsMenuGUI getUniqueEnchantsMenuGUI() {
        return uniqueEnchantsMenuGUI;
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

}