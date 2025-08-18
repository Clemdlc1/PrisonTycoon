package fr.prisontycoon.quests;

/**
 * Types de quêtes améliorés incluant les quêtes de temps de jeu
 * et les quêtes spéciales pour le Pass de Combat
 */
public enum QuestType {
    // Quêtes de combat existantes
    KILL_PLAYERS("Éliminer des joueurs", "joueur(s) éliminé(s)"),
    KILL_MONSTERS("Tuer des créatures", "créature(s) tuée(s)"),
    DEAL_DAMAGE("Infliger des dégâts", "point(s) de dégâts infligés"),

    // Quêtes de minage existantes
    MINE_BLOCKS("Miner des blocs", "bloc(s) miné(s)"),
    MINE_BEACONS("Miner des beacons", "beacon(s) miné(s)"),
    MINE_SPECIFIC_BLOCKS("Miner des blocs spécifiques", "bloc(s) spécifique(s) miné(s)"),
    BREAK_PICKAXE("User des pioches", "pioche(s) usée(s)"),

    // Quêtes économiques existantes
    EARN_MONEY("Gagner de l'argent", "coin(s) gagné(s)"),
    EARN_TOKENS("Gagner des tokens", "token(s) gagné(s)"),
    SPEND_MONEY("Dépenser de l'argent", "coin(s) dépensé(s)"),
    SELL_ITEMS("Vendre des objets", "objet(s) vendu(s)"),
    UPGRADE_ENCHANTMENTS("Améliorer des enchantements", "enchantement(s) amélioré(s)"),

    // Quêtes sociales existantes
    CHAT_MESSAGES("Envoyer des messages", "message(s) envoyé(s)"),
    JOIN_GANG("Rejoindre un gang", "gang rejoint"),
    HELP_PLAYERS("Aider des joueurs", "joueur(s) aidé(s)"),

    // Avant-poste
    CAPTURE_OUTPOST("Capturer l'avant-poste", "capture(s) réalisée(s)"),
    HOLD_OUTPOST_MINUTES("Occuper l'avant-poste (minutes)", "minute(s) en contrôle"),

    // Evénements CustomMobs
    WIN_SPONTANEOUS_EVENT("Gagner un événement spontané", "victoire(s) en événement"),
    PARTICIPATE_BOSS("Participer à un boss", "participation(s) à un boss"),

    // Economie/Objets
    USE_SELLHAND("Utiliser /sellhand", "sellhand(s) utilisé(s)"),
    BREAK_CONTAINER("Casser des conteneurs", "conteneur(s) cassé(s)"),

    // ========================================
    // NOUVELLES QUÊTES DE TEMPS DE JEU
    // ========================================

    PLAYTIME_MINUTES("Temps de jeu (minutes)", "minute(s) de jeu"),
    PLAYTIME_HOURS("Temps de jeu (heures)", "heure(s) de jeu"),
    PLAYTIME_DAILY("Temps de jeu quotidien", "minute(s) de jeu aujourd'hui"),
    PLAYTIME_WEEKLY("Temps de jeu hebdomadaire", "heure(s) de jeu cette semaine"),
    PLAYTIME_SESSION("Session de jeu continue", "minute(s) de session continue"),

    // ========================================
    // QUÊTES SPÉCIALES PASS DE COMBAT
    // ========================================

    // Quêtes de progression
    COMPLETE_DAILY_QUESTS("Compléter des quêtes journalières", "quête(s) journalière(s) complétée(s)"),
    COMPLETE_WEEKLY_QUESTS("Compléter des quêtes hebdomadaires", "quête(s) hebdomadaire(s) complétée(s)"),
    EARN_BATTLE_PASS_XP("Gagner de l'XP Battle Pass", "point(s) d'XP Battle Pass gagnés"),
    REACH_TIER("Atteindre un palier", "palier atteint"),

    // Quêtes d'activité intensive
    MINE_MARATHON("Marathon de minage", "bloc(s) minés en une session"),
    COMBAT_STREAK("Série de combats", "élimination(s) consécutive(s)"),
    EARNINGS_BURST("Explosion de gains", "coin(s) gagnés rapidement"),

    // Quêtes communautaires
    GANG_ACTIVITIES("Activités de gang", "activité(s) de gang réalisée(s)"),
    HELP_NEWBIES("Aider les nouveaux", "nouveau joueur aidé"),
    SOCIAL_INTERACTION("Interactions sociales", "interaction(s) sociale(s)"),

    // Quêtes d'exploration et découverte
    VISIT_WARPS("Visiter des téléporteurs", "téléporteur(s) visité(s)"),
    DISCOVER_AREAS("Découvrir des zones", "zone(s) découverte(s)"),
    USE_FEATURES("Utiliser des fonctionnalités", "fonctionnalité(s) utilisée(s)"),

    // Quêtes de collection et craft
    COLLECT_ITEMS("Collecter des objets", "objet(s) collecté(s)"),
    CRAFT_ITEMS("Fabriquer des objets", "objet(s) fabriqué(s)"),
    ENCHANT_ITEMS("Enchanter des objets", "objet(s) enchanté(s)"),

    // Quêtes de défis spéciaux
    LUCKY_EVENTS("Événements chanceux", "événement(s) chanceux réalisé(s)"),
    SKILL_MASTERY("Maîtrise de compétences", "niveau(x) de compétence atteint(s)"),
    RESOURCE_MANAGEMENT("Gestion des ressources", "ressource(s) optimisée(s)"),

    // ========================================
    // QUÊTES SAISONNIÈRES ET ÉVÉNEMENTS
    // ========================================

    SEASONAL_ACTIVITY("Activité saisonnière", "activité(s) saisonnière(s) réalisée(s)"),
    EVENT_PARTICIPATION("Participation à un événement", "événement(s) participé(s)"),
    CELEBRATION_TASKS("Tâches de célébration", "tâche(s) de célébration accomplie(s)"),

    // ========================================
    // QUÊTES DE PERFECTIONNEMENT
    // ========================================

    EFFICIENCY_CHALLENGE("Défi d'efficacité", "tâche(s) réalisée(s) efficacement"),
    CONSISTENCY_REWARD("Récompense de régularité", "jour(s) consécutif(s) d'activité"),
    MASTERY_DEMONSTRATION("Démonstration de maîtrise", "compétence(s) maîtrisée(s)");

    private final String description;
    private final String unitDescription;

    QuestType(String description, String unitDescription) {
        this.description = description;
        this.unitDescription = unitDescription;
    }

    public String getDescription() {
        return description;
    }

    public String getUnitDescription() {
        return unitDescription;
    }

    public String formatProgress(int current, int target) {
        return current + "/" + target + " " + unitDescription;
    }
}