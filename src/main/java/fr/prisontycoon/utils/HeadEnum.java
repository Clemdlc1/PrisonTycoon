package fr.prisontycoon.utils;

/**
 * Enum listant des textures de têtes utiles pour les GUIs.
 * Les entrées peuvent être fournies soit sous forme d'URL de texture Minecraft
 * (https://textures.minecraft.net/texture/...), soit de valeur Base64 complète,
 * soit d'un nom de compte MHF (ex: MHF_ArrowLeft).
 */
public enum HeadEnum {
    // Exemples de valeurs Base64 (issues de minecraft-heads.com)
    GUI_4("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTVhOTliNDFmMjZiZTdlODcwMjcwNTA4Zjc5Nzc2YTdkYWIyZjkzYzIyNTJiMzRlZDViMDMwNjU3ZDM1MmZhMyJ9fX0="),
    GUI_T("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDY1N2RjNDU1YjFlODE0NmYxZDkxYmFlZWU4NTIcharIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDY1N2RjNDU1YjFlODE0NmYxZDkxYmFlZWU4NTI4ZTkwYjlhY2JlZDJlYmUyODdkMmE3NTVlZjA3NWJlNjc5NCJ9fX0="),
    GLOBE("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjU0ODUwMzFiMzdmMGQ4YTRmM2I3ODE2ZWI3MTdmMDNkZTg5YTg3ZjZhNDA2MDJhZWY1MjIyMWNkZmFmNzQ4OCJ9fX0="),

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
    SQUID("MHF_Squid", SourceType.MHF),
    VILLAGER("MHF_Villager", SourceType.MHF),
    WITCH("MHF_Witch", SourceType.MHF),
    WITHER("MHF_Wither", SourceType.MHF),
    ZOMBIE("MHF_Zombie", SourceType.MHF);

    public enum SourceType { BASE64, URL, MHF }

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
}