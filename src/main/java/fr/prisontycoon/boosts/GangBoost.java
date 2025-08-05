package fr.prisontycoon.boosts;

import fr.prisontycoon.gangs.GangBoostType;

import java.util.UUID;

public record GangBoost(GangBoostType type, double multiplier, long expirationTime, UUID activatorId) {

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTime;
    }
}
