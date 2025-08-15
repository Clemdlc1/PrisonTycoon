package fr.skyblock;

import fr.skyblock.commands.IslandCommand;
import fr.skyblock.commands.IslandAdminCommand;
import fr.skyblock.listeners.MenuListener;
import fr.skyblock.listeners.PlayerListener;
import fr.skyblock.listeners.IslandListener;
import fr.skyblock.managers.*;
import fr.skyblock.hooks.PrisonTycoonHook;

import org.bukkit.plugin.java.JavaPlugin;
import org.mvplugins.multiverse.core.MultiverseCoreApi;

public final class CustomSkyblock extends JavaPlugin {

    private static CustomSkyblock instance;
    private DatabaseManager databaseManager;
    private WorldManager worldManager;
    private IslandManager islandManager;
    private MenuManager menuManager;
    private EconomyManager economyManager;
    private SchematicManager schematicManager;
    private InvitationManager invitationManager;
    private TaskManager taskManager;
    private WarpManager warpManager;
    private MultiverseCoreApi multiverseCoreApi;
    private PrisonTycoonHook prisonTycoonHook;
    private IslandUpgradeManager islandUpgradeManager;
    private DepositBoxManager depositBoxManager;
    private PrinterManager printerManager;


    // Configuration
    private int defaultIslandSize = 50;
    private int maxIslandSize = 200;

    @Override
    public void onEnable() {
        instance = this;

        // Sauvegarde de la configuration par défaut
        saveDefaultConfig();
        loadConfig();

        // Vérification de Multiverse Core AVANT d'initialiser les managers qui en dépendent
        if (!setupMultiverse()) {
            getLogger().severe("Multiverse Core non trouvé ! Le plugin se désactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialisation des managers
        this.databaseManager = new DatabaseManager(this);
        this.economyManager = new EconomyManager(this);
        this.worldManager = new WorldManager(this);
        this.schematicManager = new SchematicManager(this);
        this.invitationManager = new InvitationManager(this);
        this.islandManager = new IslandManager(this);
        this.menuManager = new MenuManager(this);
        this.warpManager = new WarpManager(this);
        this.prisonTycoonHook = new PrisonTycoonHook(this);
        this.islandUpgradeManager = new IslandUpgradeManager(this);
        this.depositBoxManager = new DepositBoxManager(this);
        this.printerManager = new PrinterManager(this);

        // Vérification du hook PrisonTycoon
        if (!prisonTycoonHook.isEnabled()) {
            getLogger().severe("Hook PrisonTycoon non activé ! Le plugin se désactive.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Enregistrement des commandes
        getCommand("island").setExecutor(new IslandCommand(this));
        getCommand("isadmin").setExecutor(new IslandAdminCommand(this));

        // Enregistrement des listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new IslandListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);


        // Démarrer les tâches périodiques
        this.taskManager = new TaskManager(this);

        getLogger().info("CustomSkyblock activé avec succès !");
    }

    @Override
    public void onDisable() {
        if (taskManager != null) {
            taskManager.stopAllTasks();
        }
        if (worldManager != null) {
            worldManager.saveAllIslandWorlds();
        }
        if (economyManager != null) {
            economyManager.saveAllBalances();
        }

        if (databaseManager != null) {
            databaseManager.saveAll();
            databaseManager.close();
        }
        getLogger().info("CustomSkyblock désactivé !");
    }

    private void loadConfig() {
        defaultIslandSize = getConfig().getInt("island.default-size", 50);
        maxIslandSize = getConfig().getInt("island.max-size", 200);
    }

    private boolean setupMultiverse() {
        if (getServer().getPluginManager().getPlugin("Multiverse-Core") == null) {
            return false;
        }
        try {
            this.multiverseCoreApi = MultiverseCoreApi.get();
        } catch (IllegalStateException e) {
            getLogger().severe("Multiverse-Core API could not be loaded!");
            return false;
        }
        return this.multiverseCoreApi != null;
    }

    // Getters
    public static CustomSkyblock getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public IslandManager getIslandManager() {
        return islandManager;
    }

    public MenuManager getMenuManager() {
        return menuManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public SchematicManager getSchematicManager() {
        return schematicManager;
    }

    public InvitationManager getInvitationManager() {
        return invitationManager;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public IslandUpgradeManager getIslandUpgradeManager() {
        return islandUpgradeManager;
    }
    
    public DepositBoxManager getDepositBoxManager() {
        return depositBoxManager;
    }
    
    public PrinterManager getPrinterManager() {
        return printerManager;
    }

    public MultiverseCoreApi getMultiverseCoreApi() {
        return multiverseCoreApi;
    }

    public int getDefaultIslandSize() {
        return defaultIslandSize;
    }

    public int getMaxIslandSize() {
        return maxIslandSize;
    }

    public PrisonTycoonHook getPrisonTycoonHook() {
        return prisonTycoonHook;
    }
}