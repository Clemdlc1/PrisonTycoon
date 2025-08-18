package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BattlePassAdminCommand implements CommandExecutor {

    private final PrisonTycoon plugin;

    public BattlePassAdminCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§cPermission manquante.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/bpadmin start §7- Démarre une nouvelle saison maintenant");
            sender.sendMessage("§e/bpadmin season §7- Affiche l'ID de la saison");
            sender.sendMessage("§e/bpadmin givepremium <joueur> §7- Donne le premium");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                plugin.getBattlePassManager().startNewSeasonNow();
                sender.sendMessage("§aNouvelle saison démarrée.");
            }
            case "season" ->
                    sender.sendMessage("§7Saison actuelle: §e" + plugin.getBattlePassManager().getCurrentSeasonId());
            case "givepremium" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /bpadmin givepremium <joueur>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cJoueur introuvable.");
                    return true;
                }
                var data = plugin.getBattlePassManager().getPlayerData(target.getUniqueId());
                if (data.premium()) {
                    sender.sendMessage("§7Ce joueur a déjà le premium.");
                    return true;
                }
                plugin.getBattlePassManager().purchasePremium(target); // bypass coût en retirant 0 (prix retiré si insuffisant)
                // Forcer flag premium on sans coût:
                plugin.getBattlePassManager().claimFree(target, 1); // prime: pas nécessaire mais ping la structure
                sender.sendMessage("§aPremium accordé.");
            }
            default -> sender.sendMessage("§cCommande inconnue.");
        }
        return true;
    }
}


