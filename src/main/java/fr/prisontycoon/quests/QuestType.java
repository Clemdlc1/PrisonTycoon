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
    MINE_BEACONS("Miner des beacons", "Miner des beacon(s)"),
    MINE_SPECIFIC_BLOCKS("Miner des blocs spécifiques", "bloc(s) spécifique(s) miné(s)"),
    BREAK_PICKAXE("User des pioches", "pioche(s) usée(s)"),

    // Quêtes économiques existantes
    EARN_MONEY("Gagner de l'argent", "coin(s) gagné(s)"),
    EARN_TOKENS("Gagner des tokens", "token(s) gagné(s)"),
    SPEND_MONEY("Dépenser de l'argent", "coin(s) dépensé(s)"),
    SELL_ITEMS("Vendre des objets", "objet(s) vendu(s)"),
    UPGRADE_ENCHANTMENTS("up","ench"),

    // Quêtes sociales existantes
    CHAT_MESSAGES("Envoyer des messages", "message(s) envoyé(s)"),
    JOIN_GANG("Rejoindre un gang", "gang rejoint"),
    HELP_PLAYERS("Aider des joueurs", "joueur(s) aidé(s)"),

    // Avant-poste
    CAPTURE_OUTPOST("",""),
    HOLD_OUTPOST_MINUTES("",""),

    // Evénements CustomMobs
    WIN_SPONTANEOUS_EVENT("",""),
    PARTICIPATE_BOSS("",""),

    // Economie/Objets
    USE_SELLHAND("",""),
    BREAK_CONTAINER("",""),

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

    /**
     * Vérifie si ce type de quête est lié au temps de jeu
     */
    public boolean isPlayTimeQuest() {
        return this == PLAYTIME_MINUTES || this == PLAYTIME_HOURS ||
                this == PLAYTIME_DAILY || this == PLAYTIME_WEEKLY ||
                this == PLAYTIME_SESSION;
    }

    /**
     * Vérifie si ce type de quête est spécifique au Battle Pass
     */
    public boolean isBattlePassQuest() {
        return this == COMPLETE_DAILY_QUESTS || this == COMPLETE_WEEKLY_QUESTS ||
                this == EARN_BATTLE_PASS_XP || this == REACH_TIER ||
                this == MINE_MARATHON || this == COMBAT_STREAK ||
                this == EARNINGS_BURST || this == GANG_ACTIVITIES ||
                this == HELP_NEWBIES || this == SOCIAL_INTERACTION ||
                this == VISIT_WARPS || this == DISCOVER_AREAS ||
                this == USE_FEATURES || this == COLLECT_ITEMS ||
                this == CRAFT_ITEMS || this == ENCHANT_ITEMS ||
                this == LUCKY_EVENTS || this == SKILL_MASTERY ||
                this == RESOURCE_MANAGEMENT || this == SEASONAL_ACTIVITY ||
                this == EVENT_PARTICIPATION || this == CELEBRATION_TASKS ||
                this == EFFICIENCY_CHALLENGE || this == CONSISTENCY_REWARD ||
                this == MASTERY_DEMONSTRATION;
    }

    /**
     * Vérifie si ce type de quête peut être quotidien
     */
    public boolean canBeDaily() {
        return this != REACH_TIER && this != JOIN_GANG &&
                this != HELP_NEWBIES && !this.name().contains("WEEKLY");
    }

    /**
     * Vérifie si ce type de quête peut être hebdomadaire
     */
    public boolean canBeWeekly() {
        return this != PLAYTIME_DAILY && this != PLAYTIME_SESSION;
    }

    /**
     * Obtient les valeurs cibles recommandées selon la catégorie de quête
     */
    public int getRecommendedTarget(QuestCategory category) {
        return switch (this) {
            // Temps de jeu
            case PLAYTIME_MINUTES -> category == QuestCategory.DAILY ? 30 : 180;
            case PLAYTIME_HOURS -> category == QuestCategory.WEEKLY ? 5 : 2;
            case PLAYTIME_DAILY -> 60; // 1 heure par jour
            case PLAYTIME_WEEKLY -> 10; // 10 heures par semaine
            case PLAYTIME_SESSION -> 45; // 45 minutes consécutives

            // Combat
            case KILL_PLAYERS -> category == QuestCategory.DAILY ? 5 : 25;
            case KILL_MONSTERS -> category == QuestCategory.DAILY ? 20 : 100;
            case DEAL_DAMAGE -> category == QuestCategory.DAILY ? 1000 : 5000;

            // Minage
            case MINE_BLOCKS -> category == QuestCategory.DAILY ? 500 : 2500;
            case MINE_SPECIFIC_BLOCKS -> category == QuestCategory.DAILY ? 100 : 500;
            case MINE_MARATHON -> 1000; // Session intensive

            // Économie
            case EARN_MONEY -> category == QuestCategory.DAILY ? 10000 : 50000;
            case EARN_TOKENS -> category == QuestCategory.DAILY ? 500 : 2500;
            case SPEND_MONEY -> category == QuestCategory.DAILY ? 5000 : 25000;

            // Social
            case CHAT_MESSAGES -> category == QuestCategory.DAILY ? 10 : 50;
            case HELP_NEWBIES -> 1;
            case SOCIAL_INTERACTION -> category == QuestCategory.DAILY ? 5 : 20;

            // Battle Pass spécifiques
            case COMPLETE_DAILY_QUESTS -> category == QuestCategory.WEEKLY ? 5 : 3;
            case COMPLETE_WEEKLY_QUESTS -> 1;
            case EARN_BATTLE_PASS_XP -> category == QuestCategory.DAILY ? 100 : 500;
            case REACH_TIER -> 1;

            // Activités diverses
            case VISIT_WARPS -> category == QuestCategory.DAILY ? 3 : 10;
            case COLLECT_ITEMS -> category == QuestCategory.DAILY ? 50 : 200;
            case CRAFT_ITEMS -> category == QuestCategory.DAILY ? 10 : 50;
            case ENCHANT_ITEMS -> category == QuestCategory.DAILY ? 3 : 15;

            // Défis
            case COMBAT_STREAK -> 5;
            case EARNINGS_BURST -> 25000;
            case EFFICIENCY_CHALLENGE -> category == QuestCategory.DAILY ? 1 : 5;
            case CONSISTENCY_REWARD -> 7; // 7 jours consécutifs

            // Par défaut
            default -> category == QuestCategory.DAILY ? 10 : 50;
        };
    }

    /**
     * Obtient les points Battle Pass recommandés pour ce type de quête
     */
    public int getBattlePassPoints(QuestCategory category) {
        if (category == QuestCategory.PASS) {
            return switch (this) {
                // Quêtes spéciales Pass = plus de points
                case MINE_MARATHON, COMBAT_STREAK, EARNINGS_BURST -> 100;
                case PLAYTIME_SESSION, EFFICIENCY_CHALLENGE -> 75;
                case REACH_TIER, MASTERY_DEMONSTRATION -> 150;
                case CONSISTENCY_REWARD -> 200;

                // Quêtes longues
                case PLAYTIME_WEEKLY -> 80;
                case COMPLETE_WEEKLY_QUESTS -> 120;

                // Quêtes moyennes
                case PLAYTIME_DAILY, EARN_BATTLE_PASS_XP -> 50;
                case GANG_ACTIVITIES, SOCIAL_INTERACTION -> 40;

                // Quêtes courtes
                default -> 30;
            };
        }

        // Quêtes normales (daily/weekly)
        return switch (category) {
            case DAILY -> isPlayTimeQuest() ? 25 : 20;
            case WEEKLY -> isPlayTimeQuest() ? 60 : 50;
            default -> 10;
        };
    }
}