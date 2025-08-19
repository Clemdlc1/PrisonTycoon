package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.PrivateMessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class MessageCommand implements CommandExecutor {

	private final PrivateMessageManager pmManager;

	public MessageCommand(PrisonTycoon plugin) {
		this.pmManager = plugin.getPrivateMessageManager();
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("§cCette commande doit être exécutée par un joueur.");
			return true;
		}

		// Bloque si le joueur est mute
		if (PrisonTycoon.getInstance().getModerationManager().isMuted(player.getUniqueId())) {
			player.sendMessage("§c🔇 Vous êtes muté! Vous ne pouvez pas envoyer de message.");
			return true;
		}

		if (args.length < 2) {
			player.sendMessage("§cUsage: /" + label + " <joueur> <message>");
			return true;
		}

		Player target = Bukkit.getPlayerExact(args[0]);
		if (target == null || !target.isOnline()) {
			player.sendMessage("§cJoueur introuvable ou hors-ligne.");
			return true;
		}
		if (target.getUniqueId().equals(player.getUniqueId())) {
			player.sendMessage("§cVous ne pouvez pas vous envoyer un message à vous-même.");
			return true;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < args.length; i++) {
			if (i > 1) sb.append(' ');
			sb.append(args[i]);
		}
		String message = sb.toString();

		String toSender = "§7[§fMoi §7➜ §f" + target.getName() + "§7] §r" + message;
		String toTarget = "§7[§f" + player.getName() + " §7➜ §fMoi§7] §r" + message;

		player.sendMessage(toSender);
		target.sendMessage(toTarget);

		// Mémorise dernier correspondant
		pmManager.setLastConversation(player.getUniqueId(), target.getUniqueId());
		return true;
	}
}


