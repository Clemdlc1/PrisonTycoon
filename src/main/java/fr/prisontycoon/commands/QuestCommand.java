package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.quests.QuestCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class QuestCommand implements CommandExecutor, TabCompleter {
    private final PrisonTycoon plugin;

    public QuestCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommande joueur uniquement.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            // Reset comme un changement de jour/semaine: re-tirage des deux catégories
            var qm = plugin.getQuestManager();
            var progress = qm.getProgress(player.getUniqueId());

            // Re-sélectionne 7 daily et 10 weekly et persiste
            try {
                var method = qm.getClass().getDeclaredMethod("selectActiveQuestsForCategory", fr.prisontycoon.quests.PlayerQuestProgress.class, QuestCategory.class, int.class);
                method.setAccessible(true);
                boolean changed = false;
                changed |= (boolean) method.invoke(qm, progress, QuestCategory.DAILY, 7);
                changed |= (boolean) method.invoke(qm, progress, QuestCategory.WEEKLY, 10);
                if (changed) {
                    qm.saveProgress(progress);
                }
            } catch (Exception ignored) {
                // fallback: juste sauvegarde
                qm.saveProgress(progress);
            }

            player.sendMessage("§aVos quêtes ont été réinitialisées et re-tirées !");
            return true;
        }
        plugin.getQuestsGUI().openMainMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            if ("reset".startsWith(args[0].toLowerCase())) {
                return java.util.List.of("reset");
            }
        }
        return Collections.emptyList();
    }
}


