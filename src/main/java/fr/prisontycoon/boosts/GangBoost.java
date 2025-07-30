
package fr.prisontycoon.boosts;

import fr.prisontycoon.gangs.GangBoostType;

import java.util.UUID;

public class GangBoost {

    private final GangBoostType type;
    private final double multiplier;
    private final long expirationTime;
    private final UUID activatorId;

    public GangBoost(GangBoostType type, double multiplier, long expirationTime, UUID activatorId) {
        this.type = type;
        this.multiplier = multiplier;
        this.expirationTime = expirationTime;
        this.activatorId = activatorId;
    }

    public GangBoostType getType() {
        return type;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public UUID getActivatorId() {
        return activatorId;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}
