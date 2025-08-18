package fr.prisontycoon.utils;

/**
 * Enum listant des textures de têtes utiles pour les GUIs.
 * Les entrées peuvent être fournies soit sous forme d'URL de texture Minecraft
 * (<a href="https://textures.minecraft.net/texture/...">...</a>), soit de valeur Base64 complète,
 * soit d'un nom de compte MHF (ex: MHF_ArrowLeft).
 */
public enum HeadEnum {
    // Exemples de valeurs Base64 (issues de minecraft-heads.com)
    GUI_4("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTVhOTliNDFmMjZiZTdlODcwMjcwNTA4Zjc5Nzc2YTdkYWIyZjkzYzIyNTJiMzRlZDViMDMwNjU3ZDM1MmZhMyJ9fX0="),
    GUI_T("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDY1N2RjNDU1YjFlODE0NmYxZDkxYmFlZWU4NTIcharIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDY1N2RjNDU1YjFlODE0NmYxZDkxYmFlZWU4NTI4ZTkwYjlhY2JlZDJlYmUyODdkMmE3NTVlZjA3NWJlNjc5NCJ9fX0="),
    GLOBE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0ODUwMzFiMzdmMGQ4YTRmM2I3ODE2ZWI3MTdmMDNkZTg5YTg3ZjZhNDA2MDJhZWY1MjIyMWNkZmFmNzQ4OCJ9fX0="),
    OPEN_BOX("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjA5NzJjNDUzNmI1ZGFiYzgyNjE0ZjAzMjkwM2MzMjcxY2YyNmU2M2QxNTRjMGJmNzU2ZmMxNTkwNDg1ODJiNCJ9fX0=="),
    CLOSED_BOX("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmFiYzZlYWRhMjFhM2Q3MTdiMmNhZGQyNmU2Zjg1NzVkMzE4M2NmMGQwYWI1ZDVjNzgyOWQ5NTQ2ZGU1ZGZlOCJ9fX0="),
    // Flèches / navigation (MHF = comptes spéciaux Mojang)
    ARROW_LEFT("MHF_ArrowLeft", SourceType.MHF),
    ARROW_RIGHT("MHF_ArrowRight", SourceType.MHF),
    ARROW_UP("MHF_ArrowUp", SourceType.MHF),
    ARROW_DOWN("MHF_ArrowDown", SourceType.MHF),

    // Informations / actions génériques
    QUESTION("MHF_Question", SourceType.MHF),
    EXCLAMATION("MHF_Exclamation", SourceType.MHF),

    // Icones diverses pour menus
    STAR("https://textures.minecraft.net/texture/3e3be46a1f6f1f1b9b9d28d5bdb12a1e0df59c0d0e0d2cdbcb1a3d83f7a3e7f"),
    CHEST_GUI("https://textures.minecraft.net/texture/4a8f93b6df1a6b1a4a2f7d8c6e0b1c2d3e4f5061728394a5b6c7d8e9f0a1b2c3"),
    FUEL("https://textures.minecraft.net/texture/a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0fedcba9876543210"),
    ANVIL_HEAD("https://textures.minecraft.net/texture/7f1e2d3c4b5a69788776655443322110ffeeddccbbaa99887766554433221100"),
    CRAFTING("https://textures.minecraft.net/texture/2c1b3a5948372615049382716a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e0f1a2b3"),

    // Monstres et créatures MHF
    BLAZE("MHF_Blaze", SourceType.MHF),
    CAVE_SPIDER("MHF_CaveSpider", SourceType.MHF),
    CHICKEN("MHF_Chicken", SourceType.MHF),
    COW("MHF_Cow", SourceType.MHF),
    CREEPER("MHF_Creeper", SourceType.MHF),
    ENDERMAN("MHF_Enderman", SourceType.MHF),
    ENDER_DRAGON("MHF_EnderDragon", SourceType.MHF),
    EVOKER("MHF_Evoker", SourceType.MHF),
    GHAST("MHF_Ghast", SourceType.MHF),
    GOLEM("MHF_Golem", SourceType.MHF),
    HEROBRINE("MHF_Herobrine", SourceType.MHF),
    LAVA_SLIME("MHF_LavaSlime", SourceType.MHF),
    MUSHROOM_COW("MHF_MushroomCow", SourceType.MHF),
    OCELOT("MHF_Ocelot", SourceType.MHF),
    PIG("MHF_Pig", SourceType.MHF),
    PIG_ZOMBIE("MHF_PigZombie", SourceType.MHF),
    SHEEP("MHF_Sheep", SourceType.MHF),
    SKELETON("MHF_Skeleton", SourceType.MHF),
    SLIME("MHF_Slime", SourceType.MHF),
    SPIDER("MHF_Spider", SourceType.MHF),
    SHULKER("MHF_Shulker", SourceType.MHF),
    SQUID("MHF_Squid", SourceType.MHF),
    VILLAGER("MHF_Villager", SourceType.MHF),
    WITCH("MHF_Witch", SourceType.MHF),
    WITHER("MHF_Wither", SourceType.MHF),
    ZOMBIE("MHF_Zombie", SourceType.MHF),

    // Pets dédiés (alias vers MHF ou textures uniques)
    PET_GARGOUILLE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTMwYjYwYzY2YjgyNWFhMTAwZmQ0YzZlYjYzOWY1NjA2ZjcwNzc2MTg0MmQ5N2VlZDBhYWFkODNlMmY5NjZjZiJ9fX0="),
    PET_VOUIVRE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzFjNDhiOTFkZmI2ZDZkNjc2MTg5ZThmMjZmYzFkZGNkNjNiNzhiZGFhMjAyYThjM2FkYWUyNDBhYWJhMjE4NiJ9fX0="),
    PET_KELPIE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2UzZDk1ODlkMjM2YWVjODg3ZDJiMzJmZDhkM2I5MTcwMGMyNWI4MzhmY2MwNzhmZjg3NmEwYTg1NzFmZmQ3OCJ9fX0="),
    PET_CAIT_SITH("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTBlZWFhODY5ZjUzZmE5ODkxOThmNTU5NTUyMGFlYzkzOTU1MDlhYmE5OTM1OTZhODY2NTRiM2EwZjZjYTRhNiJ9fX0="),
    PET_BLACK_SHUCK("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOThhZjAzOTllNTgyY2ZmN2NmY2Q2M2I3YjkzODYxMTcyZmY1NDhjYWUyYzk0NTk3OTQ5ZmIyOGUyN2U3ZTg0YSJ9fX0="),
    PET_NEREIDE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzc3OWJkZDk2MzRiYWNmMTNiOTYxMWFjOTIwODc2Y2JjMGU3YjNjNTBkZmYzZDg0MWE0ZjBmZWRmYzA4OTI4YyJ9fX0="),
    PET_GEVAUDAN("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjBkYWY1NWNkOGI3Y2ExNDViNGZiNGE5OTRmODRmMDhmNTRmN2YwMWViMzZhNGMwOTk1NmE5ZTIwZDRhNTRiZSJ9fX0="),

    PET_GRIFFON("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VjODIzYWI5NTdjMzViODZhMjY1YmYxOTM5OWNlMTE3NzdlNDZjMmM1NjNjNjNhOWU5OTRmNTJjOTMyOSJ9fX0="),
    PET_BASILIC("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTE5ZjdiNWJmNGY0ZGVlNjU1Y2M3ZTc5MTBhMDEwNjVhZjcwN2M3M2I4MGIzNjljMDJjMmEyODQwZjE0ODMxYSJ9fX0="),
    PET_SELKIE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzE4MzY5YTVlMmU5NTQ0MTFhYmZlOTE0YjM5NDIwMjc0ZTBiMzlmYTY0YjFiMDgwNGQ2MDMxZjMxODlkYzM2ZiJ9fX0="),
    PET_TARASQUE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWFlOTAzNzEzN2I2Mjg4NDJhMjkzODBmODI4YzI2ZWM2NjE4NjY0YzM4MDJjOTk0NWY0YzAxYTk0M2FhNDBlOSJ9fX0="),
    PET_FARFADET("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2Q3YTQxZjAzOTQxODlkMmNhZmRjZTY1ODkzZTdhMDI0YjcxMTJjNThhMWVlMWZkZmViMWMwYWQ1Y2NhOWNmYyJ9fX0="),

    PET_LICORNE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzBiMzNiN2EzYjI0Zjc2YmQ4MGE2NWMzYTkzM2U3MTg4YmUzNTg0ODczNDJiZTE0M2MyMmE3NTgwYjRkMDBlMSJ9fX0="),
    PET_SPHINX("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFiMDZjZjQ2MmRkN2U0YzgyYjE3NDlmZGM0YzhlODg5NTgwNjg5ZWQ3Y2ExZjdlYWViODQ3ZTFlZmVjM2NkNiJ9fX0="),
    PET_MORRIGAN("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDA5MmRjMDg1NWQ5M2UzMGM3YWExYzc1OGMyYjY1MDNiNDE2MjBlZTQwZDNlZDc3NDQzMmIzZTAwOThhM2JjNyJ9fX0="),
    PET_CERNUNNOS("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTZlMjY0YWU3OTU1YWQyMWQ3ODBmN2FhNDk4ZjhhMDQ2N2M3ZTU4MjJjMGVmMTU3NmQyOWIwMGY4ODdlZWE2NyJ9fX0="),
    PET_HIPPOGRIFFE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2U4NzJmNGRiYjJkMWU3ZTMwZjhmMzRlODY5NjNiZGI4YmQzYjkxYWQyODcxNjUwMDBjNzNhNDBlYmRjMTBkNyJ9fX0="),

    PET_FENRIR("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjFiYTQ5ODUzYTA0YzZmZGIwMTJiN2E3OWEwZjczODNjZDgyMTg0ZDY3MTE3OTE1MmIwZmU5OTI4MDdjMjY0MSJ9fX0="),
    PET_KRAKEN("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTllZTIxNjE4NWM1NmUyOTFjOGVkZjQ3NTdiMWI3NTc3ZGY1ZjNmZDRiZmQzYTBjNzYwODRjODFkMjcwIn19fQ=="),
    PET_TARASQUE_ROYALE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWI3OGY3Njc0ZDEwNjMyNTUwNzBmZmZiMmVkYzFmMmI5MzE0ZmU2ODEzYTllYWI4NDQ0YzAzNTNhNzIzZDNkMiJ9fX0=");

    private final String value;
    private final SourceType sourceType;

    HeadEnum(String base64OrUrl) {
        this.value = base64OrUrl;
        // Heuristique: si la valeur commence par "eyJ" c'est très probablement du Base64 JSON
        // sinon si elle commence par http(s) → URL, sinon → MHF (nom de joueur)
        if (base64OrUrl.startsWith("http://") || base64OrUrl.startsWith("https://")) {
            this.sourceType = SourceType.URL;
        } else if (base64OrUrl.startsWith("eyJ")) {
            this.sourceType = SourceType.BASE64;
        } else {
            this.sourceType = SourceType.MHF;
        }
    }

    HeadEnum(String value, SourceType type) {
        this.value = value;
        this.sourceType = type;
    }

    public String getValue() {
        return value;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public enum SourceType {BASE64, URL, MHF}
}