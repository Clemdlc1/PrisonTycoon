package fr.prisontycoon.tasks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.quests.QuestType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche périodique pour compter le temps de jeu et incrémenter la quête PLAYTIME_MINUTES
 */
public class PlaytimeTrackerTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private final java.util.Map<java.util.UUID, Integer> secondsAccumulated = new java.util.concurrent.ConcurrentHashMap<>();

    public PlaytimeTrackerTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            data.addPlaytimeMillis(1000L);
            // Progression de quête temps de jeu: +1 par minute (au plus)
            java.util.UUID id = player.getUniqueId();
            int s = secondsAccumulated.getOrDefault(id, 0) + 1;
            if (s >= 60) {
                // N'ajoute qu'1 minute par passage pour éviter les bursts si des ticks ont pris du retard
                secondsAccumulated.put(id, s - 60);
                plugin.getQuestManager().addProgress(player, QuestType.PLAYTIME_MINUTES, 1);
            } else {
                secondsAccumulated.put(id, s);
            }
        }
    }
}


