package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.PrivateMessageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ReplyCommand implements CommandExecutor {

	private final PrivateMessageManager pmManager;

	public ReplyCommand(PrisonTycoon plugin) {
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

		if (args.length < 1) {
			player.sendMessage("§cUsage: /" + label + " <message>");
			return true;
		}

		UUID last = pmManager.getLastConversation(player.getUniqueId());
		if (last == null) {
			player.sendMessage("§cAucun correspondant récent. Utilisez /m <joueur> <message> d'abord.");
			return true;
		}

		Player target = Bukkit.getPlayer(last);
		if (target == null || !target.isOnline()) {
			player.sendMessage("§cVotre correspondant récent est hors-ligne.");
			return true;
		}

		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			if (i > 0) sb.append(' ');
			sb.append(args[i]);
		}
		String message = sb.toString();

		String toSender = "§7[§fMoi §7➜ §f" + target.getName() + "§7] §r" + message;
		String toTarget = "§7[§f" + player.getName() + " §7➜ §fMoi§7] §r" + message;

		player.sendMessage(toSender);
		target.sendMessage(toTarget);

		pmManager.setLastConversation(player.getUniqueId(), target.getUniqueId());
		return true;
	}
}


