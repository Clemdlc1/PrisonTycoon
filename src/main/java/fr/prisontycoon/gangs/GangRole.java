package fr.prisontycoon.gangs;

/**
 * Énumération des rôles dans un gang
 */
public enum GangRole {

    CHEF("Chef", "§6👑 ", "§6", 3),
    OFFICIER("Officier", "§e⭐ ", "§e", 2),
    MEMBRE("Membre", "§7• ", "§7", 1);

    private final String displayName;
    private final String chatPrefix;
    private final String color;
    private final int hierarchy;

    GangRole(String displayName, String chatPrefix, String color, int hierarchy) {
        this.displayName = displayName;
        this.chatPrefix = chatPrefix;
        this.color = color;
        this.hierarchy = hierarchy;
    }

    /**
     * Parse un nom de rôle en GangRole
     */
    public static GangRole fromString(String name) {
        if (name == null) return null;

        try {
            return GangRole.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Essaie avec les noms d'affichage
            for (GangRole role : values()) {
                if (role.displayName.equalsIgnoreCase(name)) {
                    return role;
                }
            }
            return null;
        }
    }

    /**
     * Nom d'affichage du rôle
     */
    public String getDisplayName() {
        return color + displayName;
    }

    /**
     * Nom d'affichage coloré complet
     */
    public String getFormattedDisplayName() {
        return color + chatPrefix + displayName;
    }

    /**
     * Préfixe utilisé dans le chat du gang
     */
    public String getChatPrefix() {
        return chatPrefix;
    }

    /**
     * Couleur du rôle
     */
    public String getColor() {
        return color;
    }

    /**
     * Niveau hiérarchique (plus élevé = plus de permissions)
     */
    public int getHierarchy() {
        return hierarchy;
    }

    /**
     * Vérifie si ce rôle peut effectuer des actions sur un autre rôle
     */
    public boolean canActOn(GangRole other) {
        return this.hierarchy > other.hierarchy;
    }

    /**
     * Vérifie si ce rôle peut inviter des membres
     */
    public boolean canInvite() {
        return this == CHEF || this == OFFICIER;
    }

    /**
     * Vérifie si ce rôle peut exclure des membres
     */
    public boolean canKick() {
        return this == CHEF || this == OFFICIER;
    }

    /**
     * Vérifie si ce rôle peut promouvoir/rétrograder
     */
    public boolean canPromoteDemote() {
        return this == CHEF;
    }

    /**
     * Vérifie si ce rôle peut modifier les paramètres du gang
     */
    public boolean canManageSettings() {
        return this == CHEF || this == OFFICIER;
    }

    /**
     * Vérifie si ce rôle peut améliorer le gang
     */
    public boolean canUpgrade() {
        return this == CHEF;
    }

    /**
     * Vérifie si ce rôle peut acheter des talents
     */
    public boolean canBuyTalents() {
        return this == CHEF;
    }

    /**
     * Vérifie si ce rôle peut transférer le leadership
     */
    public boolean canTransferLeadership() {
        return this == CHEF;
    }

    /**
     * Vérifie si ce rôle peut dissoudre le gang
     */
    public boolean canDisband() {
        return this == CHEF;
    }

    /**
     * Vérifie si ce rôle peut renommer le gang
     */
    public boolean canRename() {
        return this == CHEF;
    }

    /**
     * Obtient le rôle suivant dans la hiérarchie (pour les promotions)
     */
    public GangRole getNextRank() {
        return switch (this) {
            case MEMBRE -> OFFICIER;
            case OFFICIER -> CHEF;
            case CHEF -> CHEF; // Le chef ne peut pas être promu
        };
    }

    /**
     * Obtient le rôle précédent dans la hiérarchie (pour les rétrogradations)
     */
    public GangRole getPreviousRank() {
        return switch (this) {
            case CHEF -> OFFICIER;
            case OFFICIER -> MEMBRE;
            case MEMBRE -> MEMBRE; // Le membre ne peut pas être rétrogradé
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}