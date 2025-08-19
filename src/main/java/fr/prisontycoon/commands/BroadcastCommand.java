package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BroadcastCommand implements CommandExecutor {

	private final PrisonTycoon plugin;

	public BroadcastCommand(PrisonTycoon plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if (!sender.hasPermission("specialmine.admin")) {
			sender.sendMessage("§cVous n'avez pas la permission.");
			return true;
		}
		if (args.length == 0) {
			sender.sendMessage("§cUsage: /broadcast <message>");
			return true;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			if (i > 0) sb.append(' ');
			sb.append(args[i]);
		}
		String formatted = "§6§l[Annonce] §r" + sb;
		for (Player p : plugin.getServer().getOnlinePlayers()) {
			p.sendMessage(formatted);
		}
		plugin.getLogger().info("[Annonce] " + sb);
		return true;
	}
}


