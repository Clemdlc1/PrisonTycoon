package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class VanishCommand implements CommandExecutor {

	private final PrisonTycoon plugin;
	private final Set<UUID> vanished = new HashSet<>();

	public VanishCommand(PrisonTycoon plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("§cCette commande doit être exécutée par un joueur.");
			return true;
		}
		if (!player.hasPermission("specialmine.moderator") && !player.hasPermission("specialmine.admin")) {
			player.sendMessage("§cVous n'avez pas la permission.");
			return true;
		}

		boolean toVanish = !vanished.contains(player.getUniqueId());
		if (toVanish) {
			vanished.add(player.getUniqueId());
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (p.equals(player)) continue;
				if (!p.hasPermission("specialmine.moderator") && !p.hasPermission("specialmine.admin")) {
					p.hidePlayer(plugin, player);
				}
			}
			player.sendMessage("§aVanish activé.");
		} else {
			vanished.remove(player.getUniqueId());
			for (Player p : Bukkit.getOnlinePlayers()) {
				if (p.equals(player)) continue;
				p.showPlayer(plugin, player);
			}
			player.sendMessage("§cVanish désactivé.");
		}
		return true;
	}
}


