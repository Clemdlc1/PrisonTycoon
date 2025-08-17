package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du statut de combat (combat tag)
 * Un joueur est en combat s'il a été frappé par un autre joueur dans les 10 dernières secondes.
 */
public class CombatManager {

	private static final long COMBAT_TAG_DURATION_MS = 10_000L;

	private final PrisonTycoon plugin;
	private final Map<UUID, CombatTag> playerCombatTags = new ConcurrentHashMap<>();

	public CombatManager(PrisonTycoon plugin) {
		this.plugin = plugin;
	}

	public void tagPlayerInCombat(Player victim, Player attacker) {
		if (victim == null || attacker == null || victim.equals(attacker)) {
			return;
		}

		UUID victimId = victim.getUniqueId();
		long now = System.currentTimeMillis();
		CombatTag previous = playerCombatTags.get(victimId);
		boolean wasInCombat = previous != null && previous.expiresAtMs > now;

		CombatTag newTag = new CombatTag(attacker.getUniqueId(), now + COMBAT_TAG_DURATION_MS, now);
		playerCombatTags.put(victimId, newTag);

		if (!wasInCombat) {
			victim.sendMessage("§c⚠ Vous entrez en combat pour §e10s§c. Ne vous déconnectez pas!");
		}

		// Planifie un rappel pour message de fin de combat, lié au timestamp de tag
		long scheduledAt = newTag.taggedAtMs;
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			CombatTag current = playerCombatTags.get(victimId);
			if (current == null) return;
			long nowCheck = System.currentTimeMillis();
			// S'assurer qu'on traite le même cycle de tag et qu'il est expiré
			if (current.taggedAtMs == scheduledAt && nowCheck >= current.expiresAtMs) {
				playerCombatTags.remove(victimId);
				Player stillOnline = Bukkit.getPlayer(victimId);
				if (stillOnline != null && stillOnline.isOnline()) {
					stillOnline.sendMessage("§a✔ Vous n'êtes plus en combat.");
				}
			}
		}, ticksFromMs(COMBAT_TAG_DURATION_MS + 50));
	}

	public boolean isInCombat(Player player) {
		if (player == null) return false;
		CombatTag tag = playerCombatTags.get(player.getUniqueId());
		if (tag == null) return false;
		if (System.currentTimeMillis() <= tag.expiresAtMs) return true;
		playerCombatTags.remove(player.getUniqueId());
		return false;
	}

	/**
	 * Retourne le temps restant du combat en secondes (arrondi au supérieur).
	 */
	public long getRemainingCombatSeconds(Player player) {
		CombatTag tag = playerCombatTags.get(player.getUniqueId());
		if (tag == null) return 0L;
		long remainingMs = tag.expiresAtMs - System.currentTimeMillis();
		if (remainingMs <= 0) return 0L;
		return (remainingMs + 999L) / 1000L;
	}

	public UUID getLastAttackerId(Player player) {
		CombatTag tag = playerCombatTags.get(player.getUniqueId());
		return tag != null ? tag.attackerId : null;
	}

	public Player getLastAttackerOnline(Player player) {
		UUID attackerId = getLastAttackerId(player);
		return attackerId != null ? Bukkit.getPlayer(attackerId) : null;
	}

	public void clear(Player player) {
		if (player != null) {
			playerCombatTags.remove(player.getUniqueId());
		}
	}

	/**
	 * À appeler lors de la déconnexion du joueur.
	 * Si le joueur est en combat, considère que le dernier attaquant l'a tué
	 * (récompense quêtes killer et message serveur).
	 */
	public void handlePlayerQuit(Player quitter) {
		if (quitter == null) return;
		if (!isInCombat(quitter)) return;

		Player killer = getLastAttackerOnline(quitter);
		java.util.UUID attackerId = getLastAttackerId(quitter);
		String killerName = null;
		if (killer != null) {
			killerName = killer.getName();
			// Comptabilise la progression de quête "tuer des joueurs"
            plugin.getWeaponArmorEnchantmentManager().handlePlayerDeath(quitter, killer, null);
            plugin.getQuestManager().addProgress(killer, fr.prisontycoon.quests.QuestType.KILL_PLAYERS, 1);

		} else if (attackerId != null) {
			org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(attackerId);
			if (offline != null && offline.getName() != null) {
				killerName = offline.getName();
			}
		}

		if (killerName != null) {
			plugin.getServer().broadcastMessage("§c⚔ " + quitter.getName() + " s'est déconnecté en combat. §6" + killerName + " §7reçoit le kill.");
		} else {
			plugin.getServer().broadcastMessage("§c⚔ " + quitter.getName() + " s'est déconnecté en combat.");
		}

		// Tue le joueur (et attribue le kill à l'attaquant s'il est en ligne)
        quitter.setHealth(0.0);
        

		// Nettoie l'état de combat du joueur sortant
		clear(quitter);
	}

	private static long ticksFromMs(long ms) {
		return Math.max(1L, ms / 50L);
	}

	private static class CombatTag {
		final UUID attackerId;
		final long expiresAtMs;
		final long taggedAtMs;

		CombatTag(UUID attackerId, long expiresAtMs, long taggedAtMs) {
			this.attackerId = attackerId;
			this.expiresAtMs = expiresAtMs;
			this.taggedAtMs = taggedAtMs;
		}
	}
}


