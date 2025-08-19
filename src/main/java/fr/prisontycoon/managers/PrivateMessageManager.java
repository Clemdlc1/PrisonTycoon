package fr.prisontycoon.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service simple pour gérer les dernières conversations privées (/m et /r)
 */
public class PrivateMessageManager {

	private final Map<UUID, UUID> lastConversationPartnerByPlayer = new ConcurrentHashMap<>();

	public PrivateMessageManager(Object ignored) {
		// Aucun état externe requis pour l'instant
	}

	/**
	 * Définit les derniers correspondants entre deux joueurs (dans les deux sens)
	 */
	public void setLastConversation(UUID senderId, UUID targetId) {
		if (senderId == null || targetId == null) return;
		lastConversationPartnerByPlayer.put(senderId, targetId);
		lastConversationPartnerByPlayer.put(targetId, senderId);
	}

	/**
	 * Récupère le dernier correspondant d'un joueur
	 */
	public UUID getLastConversation(UUID playerId) {
		if (playerId == null) return null;
		return lastConversationPartnerByPlayer.get(playerId);
	}

	/**
	 * Efface le correspondant d'un joueur (ex: déconnexion)
	 */
	public void clear(UUID playerId) {
		if (playerId == null) return;
		lastConversationPartnerByPlayer.remove(playerId);
	}
}


