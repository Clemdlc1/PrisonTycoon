package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.DepositBoxManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Commande /depot - Vendre les billets de l'inventaire
 */
public class DepotCommand implements CommandExecutor {

    private final PrisonTycoon plugin;
    private final DepositBoxManager depositBoxManager;

    public DepotCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.depositBoxManager = plugin.getDepositBoxManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c❌ Cette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        if (args.length > 0) {
            sendHelpMessage(player);
            return true;
        }

        // Traiter l'inventaire du joueur
        depositBoxManager.processPlayerInventory(player);

        return true;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6▬▬▬▬▬▬▬▬▬▬ §lCOMMANDE DÉPÔT §6▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e/depot §7- Vend tous les billets de votre inventaire");
        player.sendMessage("");
        player.sendMessage("§7Cette commande vend automatiquement tous les billets");
        player.sendMessage("§7de votre inventaire et vous rend les autres items.");
        player.sendMessage("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}
