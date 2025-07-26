package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.crates.CrateType;
import fr.prisontycoon.gui.CrateGUI;
import fr.prisontycoon.managers.CrateManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Listener pour gérer toutes les interactions avec les crates
 */
public class CrateListener implements Listener {

    private final PrisonTycoon plugin;
    private final CrateManager crateManager;
    private final CrateGUI crateGUI;

    // Cache pour éviter le spam de clics
    private final ConcurrentMap<Player, Long> lastInteraction;

    // Délai minimum entre les interactions (en millisecondes)
    private static final long INTERACTION_COOLDOWN = 1000; // 1 seconde

    public CrateListener(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.crateManager = plugin.getCrateManager();
        this.crateGUI = plugin.getCrateGUI();
        this.lastInteraction = new ConcurrentHashMap<>();

        // Enregistre les événements
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Démarre la tâche d'effets visuels pour les crates
        startCrateEffectsTask();

        plugin.getPluginLogger().info("§aCrateListener initialisé.");
    }

    /**
     * Gère les interactions avec les crates (clic droit, clic gauche, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        // Vérifie si un bloc a été cliqué
        if (clickedBlock == null) return;

        Location blockLocation = clickedBlock.getLocation();

        // Vérifie si c'est une crate
        if (!crateManager.isCrateLocation(blockLocation)) return;

        // Annule l'événement par défaut
        event.setCancelled(true);

        // Vérifie le cooldown
        if (!checkInteractionCooldown(player)) {
            player.sendMessage("§cAttendez avant d'interagir à nouveau avec cette crate!");
            return;
        }

        CrateType crateType = crateManager.getCrateTypeAtLocation(blockLocation);
        if (crateType == null) {
            player.sendMessage("§cErreur: Type de crate non reconnu!");
            return;
        }

        // Gestion selon l'action
        Action action = event.getAction();
        boolean isShiftClick = player.isSneaking();

        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (isShiftClick) {
                // Shift + Clic droit = Ouvre toutes les clés
                handleOpenAllKeys(player, blockLocation, crateType);
            } else {
                // Clic droit = Ouvre avec animation
                handleSingleCrateOpen(player, blockLocation, crateType);
            }
        } else if (action == Action.LEFT_CLICK_BLOCK) {
            // Clic gauche = Affiche les récompenses
            handleShowRewards(player, crateType);
        }
    }

    /**
     * Gère l'ouverture d'une seule crate avec animation
     */
    private void handleSingleCrateOpen(Player player, Location location, CrateType crateType) {
        String requiredKeyType = crateType.getDisplayName();

        // Vérification de l'espace dans l'inventaire
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) { // Inventaire principal (sans armor/off-hand)
            if (player.getInventory().getItem(i) == null) {
                emptySlots++;
            }
        }

        if (emptySlots == 0) {
            player.sendMessage("§c❌ Votre inventaire est plein! Libérez de l'espace avant d'ouvrir des crates.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.2f);
            return;
        }

        int keyCount = crateManager.countKeys(player, requiredKeyType);

        if (keyCount == 0) {
            player.sendMessage("§c❌ Vous n'avez aucune clé " + crateType.getColor() +
                    crateType.getDisplayName() + " §cpour ouvrir cette crate!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        // Confirmation avec le nombre de clés
        player.sendMessage("§6🔑 Ouverture de la crate " + crateType.getColor() +
                crateType.getDisplayName() + "§6... §7(" + keyCount + " clé(s) restante(s))");

        // Ouvre la crate
        crateManager.openCrateWithAnimation(player, location, crateType);

        // Effets visuels supplémentaires
        spawnOpeningEffects(location, crateType);
    }

    /**
     * Gère l'ouverture de toutes les clés disponibles
     */
    private void handleOpenAllKeys(Player player, Location location, CrateType crateType) {
        String requiredKeyType = crateType.getDisplayName();
        int keyCount = crateManager.countKeys(player, requiredKeyType);

        if (keyCount == 0) {
            player.sendMessage("§c❌ Vous n'avez aucune clé " + crateType.getColor() +
                    crateType.getDisplayName() + " §cpour cette crate!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        // Vérification de l'espace dans l'inventaire
        int emptySlots = 0;
        for (int i = 0; i < 36; i++) { // Inventaire principal (sans armor/off-hand)
            if (player.getInventory().getItem(i) == null) {
                emptySlots++;
            }
        }

        if (emptySlots == 0) {
            player.sendMessage("§c❌ Votre inventaire est plein! Libérez de l'espace avant d'ouvrir plusieurs crates.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.2f);
            return;
        }

        // Confirmation dramatique
        player.sendTitle("§6⚡ OUVERTURE MASSIVE ⚡",
                "§e" + keyCount + " clés " + crateType.getColor() + crateType.getDisplayName(),
                10, 30, 10);

        player.sendMessage("§6🔥 Ouverture de §e" + keyCount + " §6clés " + crateType.getColor() +
                crateType.getDisplayName() + "§6!");
        player.sendMessage("§7💡 L'ouverture s'arrêtera si votre inventaire se remplit.");

        // Ouvre toutes les clés
        crateManager.openAllKeys(player, location, crateType);

        // Effets visuels massifs
        spawnMassiveOpeningEffects(location, crateType, keyCount);
    }

    /**
     * Gère l'affichage des récompenses possibles
     */
    private void handleShowRewards(Player player, CrateType crateType) {
        player.sendMessage("§6📋 Ouverture du menu des récompenses pour la crate " +
                crateType.getColor() + crateType.getDisplayName() + "§6...");

        // Ouvre le GUI des récompenses
        crateGUI.openRewardsGUI(player, crateType);
    }

    /**
     * Vérifie le cooldown d'interaction pour éviter le spam
     */
    private boolean checkInteractionCooldown(Player player) {
        long currentTime = System.currentTimeMillis();
        Long lastTime = lastInteraction.get(player);

        if (lastTime != null && (currentTime - lastTime) < INTERACTION_COOLDOWN) {
            return false;
        }

        lastInteraction.put(player, currentTime);
        return true;
    }

    /**
     * Génère des effets visuels pour l'ouverture d'une crate
     */
    private void spawnOpeningEffects(Location location, CrateType crateType) {
        if (location.getWorld() == null) return;

        // Particules selon le type de crate
        Particle particle = switch (crateType) {
            case COMMUNE -> Particle.HAPPY_VILLAGER;
            case PEU_COMMUNE -> Particle.ENCHANT;
            case RARE -> Particle.PORTAL;
            case LEGENDAIRE -> Particle.FIREWORK;
            case CRISTAL -> Particle.END_ROD;
        };

        // Explosion de particules
        location.getWorld().spawnParticle(particle,
                location.clone().add(0.5, 1.0, 0.5),
                30, 0.5, 0.5, 0.5, 0.1);

        // Particules en spirale
        new BukkitRunnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks > 20) { // 1 seconde
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 3; i++) {
                    double x = location.getX() + 0.5 + Math.cos(angle + i * Math.PI * 2 / 3) * 1.0;
                    double z = location.getZ() + 0.5 + Math.sin(angle + i * Math.PI * 2 / 3) * 1.0;
                    double y = location.getY() + 1.0 + (ticks * 0.05);

                    location.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
                }

                angle += Math.PI / 10;
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Génère des effets visuels massifs pour l'ouverture de plusieurs crates
     */
    private void spawnMassiveOpeningEffects(Location location, CrateType crateType, int keyCount) {
        if (location.getWorld() == null) return;

        // Effets selon l'intensité (nombre de clés)
        int intensity = Math.min(keyCount, 20); // Limité à 20 pour les performances

        // Explosion initiale
        location.getWorld().spawnParticle(Particle.EXPLOSION,
                location.clone().add(0.5, 1.0, 0.5),
                intensity / 4, 1.0, 1.0, 1.0, 0);

        // Animation prolongée
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = intensity * 2;

            @Override
            public void run() {
                if (ticks > maxTicks) {
                    // Explosion finale
                    location.getWorld().spawnParticle(Particle.FIREWORK,
                            location.clone().add(0.5, 2.0, 0.5),
                            50, 2.0, 2.0, 2.0, 0.2);
                    this.cancel();
                    return;
                }

                // Particules continues
                Particle particle = switch (crateType) {
                    case COMMUNE -> Particle.HAPPY_VILLAGER;
                    case PEU_COMMUNE -> Particle.ENCHANT;
                    case RARE -> Particle.PORTAL;
                    case LEGENDAIRE -> Particle.FIREWORK;
                    case CRISTAL -> Particle.END_ROD;
                };

                // Cercles de particules
                for (double radius = 1.0; radius <= 3.0; radius += 1.0) {
                    for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 6) {
                        double x = location.getX() + 0.5 + Math.cos(angle) * radius;
                        double z = location.getZ() + 0.5 + Math.sin(angle) * radius;
                        double y = location.getY() + 1.0 + Math.sin(ticks * 0.3) * 0.5;

                        location.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * Démarre la tâche d'effets visuels continus pour les crates
     */
    private void startCrateEffectsTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Effets ambiants pour toutes les crates
                for (Location crateLocation : crateManager.getCrateLocations().keySet()) {
                    if (crateLocation.getWorld() == null) continue;

                    // Vérifie s'il y a des joueurs à proximité (optimisation)
                    boolean hasNearbyPlayers = crateLocation.getWorld().getPlayers().stream()
                            .anyMatch(p -> p.getLocation().distance(crateLocation) <= 10);

                    if (!hasNearbyPlayers) continue;

                    CrateType crateType = crateManager.getCrateTypeAtLocation(crateLocation);
                    if (crateType == null) continue;

                    // Particules ambiantes subtiles
                    Particle ambientParticle = switch (crateType) {
                        case COMMUNE -> Particle.HAPPY_VILLAGER;
                        case PEU_COMMUNE -> Particle.ENCHANT;
                        case RARE -> Particle.PORTAL;
                        case LEGENDAIRE -> Particle.FIREWORK;
                        case CRISTAL -> Particle.END_ROD;
                    };

                    // Particules flottantes douces
                    if (Math.random() < 0.3) { // 30% de chance
                        double x = crateLocation.getX() + 0.3 + Math.random() * 0.4;
                        double y = crateLocation.getY() + 1.0 + Math.random() * 0.5;
                        double z = crateLocation.getZ() + 0.3 + Math.random() * 0.4;

                        crateLocation.getWorld().spawnParticle(ambientParticle, x, y, z, 1, 0, 0, 0, 0);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // Toutes les 2 secondes
    }

    /**
     * Empêche la destruction des blocs de crates
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();

        if (crateManager.isCrateLocation(blockLocation)) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            CrateType crateType = crateManager.getCrateTypeAtLocation(blockLocation);

            player.sendMessage("§c❌ Vous ne pouvez pas détruire cette crate " +
                    (crateType != null ? crateType.getColor() + crateType.getDisplayName() : "inconnue") + "§c!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
    }

    /**
     * Empêche la pose de blocs sur les emplacements de crates
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();

        if (crateManager.isCrateLocation(blockLocation)) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            player.sendMessage("§c❌ Vous ne pouvez pas placer de bloc à cet emplacement!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
    }

    /**
     * Nettoie le cache lors de la déconnexion d'un joueur
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        lastInteraction.remove(player);

        // Ferme le GUI de crate s'il est ouvert
        if (crateGUI.hasOpenGUI(player)) {
            crateGUI.closeGUI(player);
        }
    }

    /**
     * Message d'accueil sur les crates lors de la connexion
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Message d'information sur les crates (avec délai pour éviter le spam au login)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    int totalCrates = crateManager.getCrateLocations().size();
                    if (totalCrates > 0) {
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                        player.sendMessage("§6✨ Bienvenue! Il y a §e" + totalCrates + " crates §6disponibles sur le serveur!");
                        player.sendMessage("§7💡 Utilisez vos clés pour obtenir des récompenses incroyables!");
                        player.sendMessage("§7🔍 §eClic gauche §7= Voir récompenses | §eClic droit §7= Ouvrir");
                        player.sendMessage("§7⚡ §eShift + Clic droit §7= Ouvrir toutes les clés");
                        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    }
                }
            }
        }.runTaskLater(plugin, 60L); // 3 secondes après la connexion
    }

    /**
     * Nettoie les ressources lors de l'arrêt
     */
    public void cleanup() {
        lastInteraction.clear();
        if (crateGUI != null) {
            crateGUI.closeAllGuis();
        }
    }
}