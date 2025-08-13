package fr.prisontycoon.quests;

import java.util.Map;

/**
 * Définition immuable d'une quête configurable.
 */
public class QuestDefinition {
    private final String id;
    private final QuestCategory category;
    private final QuestType type;
    private final int target;
    private final Map<String, Object> params;
    private final QuestRewards rewards;
    private final int battlePassPoints;

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

    public String getId() {
        return id;
    }

    public QuestCategory getCategory() {
        return category;
    }

    public QuestType getType() {
        return type;
    }

    public int getTarget() {
        return target;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public QuestRewards getRewards() {
        return rewards;
    }

    public int getBattlePassPoints() {
        return battlePassPoints;
    }
}


