package fr.prisontycoon.quests;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.entity.Player;

/**
 * Récompenses d'une quête (beacons, xp métier actif, vouchers/boosters).
 */
public class QuestRewards {
    private final long beacons;
    private final int jobXp;
    private final VoucherType voucherType; // optionnel
    private final int voucherTier;         // optionnel
    private final BoostType boostType;     // optionnel
    private final int boostMinutes;        // optionnel
    private final double boostPercent;     // optionnel

    public QuestRewards(long beacons, int jobXp,
                        VoucherType voucherType, int voucherTier,
                        BoostType boostType, int boostMinutes, double boostPercent) {
        this.beacons = Math.max(0, beacons);
        this.jobXp = Math.max(0, jobXp);
        this.voucherType = voucherType;
        this.voucherTier = voucherTier;
        this.boostType = boostType;
        this.boostMinutes = boostMinutes;
        this.boostPercent = boostPercent;
    }

    public void grant(PrisonTycoon plugin, Player player) {
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        data.addBeacons(beacons);
        String active = data.getActiveProfession();
        if (jobXp > 0 && active != null) plugin.getProfessionManager().addProfessionXP(player, active, jobXp);
        if (voucherType != null) {
            var item = plugin.getVoucherManager().createVoucher(voucherType, Math.max(1, voucherTier));
            boolean added = plugin.getContainerManager().addItemToContainers(player, item);
            if (!added) player.getInventory().addItem(item);
        }
        if (boostType != null) {
            var item = plugin.getBoostManager().createBoostItem(boostType, Math.max(1, boostMinutes), Math.max(1.0, boostPercent));
            boolean added = plugin.getContainerManager().addItemToContainers(player, item);
            if (!added) player.getInventory().addItem(item);
        }
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    public long getBeacons() { return beacons; }
    public int getJobXp() { return jobXp; }
}


