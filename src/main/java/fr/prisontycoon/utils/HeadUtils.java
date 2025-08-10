package fr.prisontycoon.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utilitaires modernes (Paper/Adventure) pour créer des têtes custom.
 * Supporte: Base64, URL textures.minecraft.net et comptes MHF.
 */
public final class HeadUtils {

    private HeadUtils() {}

    /**
     * Crée une tête custom depuis une entrée du HeadEnum (Base64/URL/MHF).
     */
    public static ItemStack createHead(HeadEnum headEnum) {
        return switch (headEnum.getSourceType()) {
            case BASE64 -> createHeadFromBase64(headEnum.getValue(), headEnum.name());
            case URL -> createHeadFromUrl(headEnum.getValue(), headEnum.name());
            case MHF -> createMHFHead(headEnum.getValue(), headEnum.name());
        };
    }

    /**
     * Crée une tête custom pour un joueur (Offline inclus), avec nom d'affichage.
     */
    public static ItemStack createPlayerHead(UUID playerId, String displayNameLegacy) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(playerId);
            meta.setOwningPlayer(op);
            applyDisplayName(meta, displayNameLegacy);
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * Applique un displayName (Adventure, non italic).
     */
    public static void applyDisplayName(ItemMeta meta, String legacy) {
        if (meta == null || legacy == null) return;
        Component name = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
        meta.customName(name);
        meta.displayName(name);
    }

    /**
     * Crée une tête custom depuis une URL textures.minecraft.net.
     */
    public static ItemStack createHeadFromUrl(String texturesUrl, String displayName) {
        String json = '{' + "\"textures\":{\"SKIN\":{\"url\":\"" + texturesUrl + "\"}}}";
        String base64 = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return createHeadFromBase64(base64, displayName);
    }

    /**
     * Crée une tête custom depuis une chaîne Base64.
     */
    public static ItemStack createHeadFromBase64(String base64, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("head:" + base64).getBytes(StandardCharsets.UTF_8)), null);
        profile.setProperty(new ProfileProperty("textures", base64));
        meta.setPlayerProfile(profile);

        if (displayName != null && !displayName.isEmpty()) {
            applyDisplayName(meta, "§f" + displayName);
        }
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Crée une tête en utilisant un compte MHF (ex: MHF_ArrowLeft).
     */
    public static ItemStack createMHFHead(String mhfName, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            // Meilleur chemin: créer un profil par nom et tenter de le compléter (Paper)
            PlayerProfile profile = null;
            try {
                profile = Bukkit.createProfile(mhfName);
                try { profile.complete(true); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            if (profile != null && !profile.getProperties().isEmpty()) {
                meta.setPlayerProfile(profile);
            } else {
                // Fallback: utiliser OfflinePlayer (peut marcher en online-mode)
                OfflinePlayer op = Bukkit.getOfflinePlayer(mhfName);
                try {
                    PlayerProfile opProfile = op.getPlayerProfile();
                    if (opProfile != null && !opProfile.getProperties().isEmpty()) {
                        meta.setPlayerProfile(opProfile);
                    } else {
                        meta.setOwningPlayer(op);
                    }
                } catch (Throwable ignored) {
                    meta.setOwningPlayer(op);
                }
            }
            if (displayName != null) applyDisplayName(meta, "§f" + displayName);
            head.setItemMeta(meta);
        }
        return head;
    }
}


