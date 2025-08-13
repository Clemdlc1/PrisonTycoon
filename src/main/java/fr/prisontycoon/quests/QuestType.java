package fr.prisontycoon.quests;

/**
 * Types de quêtes supportés.
 */
public enum QuestType {
    // PvP / PvE
    KILL_PLAYERS,
    KILL_MONSTERS,

    // Minage Beacons (via MiningListener onBlockDamage/onBlockBreak)
    MINE_BEACONS,

    // Enchantements
    UPGRADE_ENCHANTMENTS,

    // Avant-poste
    CAPTURE_OUTPOST,
    HOLD_OUTPOST_MINUTES,

    // Evénements CustomMobs
    WIN_SPONTANEOUS_EVENT,
    PARTICIPATE_BOSS,

    // Economie/Objets
    USE_SELLHAND,
    BREAK_CONTAINER,

    // Temps de jeu (minutes)
    PLAYTIME_MINUTES
}


