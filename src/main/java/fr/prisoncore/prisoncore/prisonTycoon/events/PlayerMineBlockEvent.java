package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList; /**
 * Événement déclenché quand un joueur mine un bloc dans une mine
 */
public class PlayerMineBlockEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PlayerData playerData;
    private final org.bukkit.Location blockLocation;
    private final org.bukkit.Material blockType;
    private final String mineName;
    private final long coinsGained;
    private final long tokensGained;
    private final long experienceGained;

    public PlayerMineBlockEvent(Player player, PlayerData playerData,
                                org.bukkit.Location blockLocation, org.bukkit.Material blockType,
                                String mineName, long coinsGained, long tokensGained, long experienceGained) {
        this.player = player;
        this.playerData = playerData;
        this.blockLocation = blockLocation;
        this.blockType = blockType;
        this.mineName = mineName;
        this.coinsGained = coinsGained;
        this.tokensGained = tokensGained;
        this.experienceGained = experienceGained;
    }

    // Getters
    public Player getPlayer() { return player; }
    public PlayerData getPlayerData() { return playerData; }
    public org.bukkit.Location getBlockLocation() { return blockLocation; }
    public org.bukkit.Material getBlockType() { return blockType; }
    public String getMineName() { return mineName; }
    public long getCoinsGained() { return coinsGained; }
    public long getTokensGained() { return tokensGained; }
    public long getExperienceGained() { return experienceGained; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
