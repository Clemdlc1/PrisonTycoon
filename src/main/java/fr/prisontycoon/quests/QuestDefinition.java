package fr.prisontycoon.quests;

import java.util.Map;

/**
 * Définition immuable d'une quête configurable.
 */
public record QuestDefinition(String id, QuestCategory category, QuestType type, int target, Map<String, Object> params,
                              QuestRewards rewards, int battlePassPoints) {
    public QuestDefinition(String id, QuestCategory category, QuestType type, int target,
                           Map<String, Object> params, QuestRewards rewards, int battlePassPoints) {
        this.id = id;
        this.category = category;
        this.type = type;
        this.target = Math.max(1, target);
        this.params = params;
        this.rewards = rewards;
        this.battlePassPoints = Math.max(0, battlePassPoints);
    }
}


