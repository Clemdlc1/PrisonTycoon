package fr.prisontycoon.gui;

/**
 * Énumération pour identifier chaque type de GUI de manière unique
 * Remplace l'identification par title.contains()
 */
public enum GUIType {
    MAIN_MENU("main_menu"),
    ENCHANTMENT_MENU("enchantment_menu"),
    CATEGORY_ENCHANT("category_economiques"),
    ENCHANTMENT_UPGRADE("enchantment_upgrade"),
    CRISTAL_MANAGEMENT("cristal_management"),
    ENCHANTMENT_BOOK("enchantment_book"),
    BOOK_SHOP("book_shop"),
    PETS_MENU("pets_menu"),
    PICKAXE_REPAIR("pickaxe_repair"),
    PROFESSION_MAIN("profession_main"),
    PROFESSION_TALENTS("profession_talents"),
    PROFESSION_REWARDS("profession_rewards"),
    PRESTIGE_MENU("prestige_menu"),
    BLACK_MARKET("black_market"),
    WEAPON_ARMOR_ENCHANT("weapon_armor_enchant"),
    BOOST_MENU("boost_menu"),
    AUTOMINER_MAIN("autominer_main"),
    AUTOMINER_UPGRADE("autominer_upgrade"),
    AUTOMINER_STORAGE("autominer_storage"),
    AUTOMINER_FUEL("autominer_fuel"),
    AUTOMINER_CONDENSATION("autominer_condensation"),
    AUTOMINER_ENCHANT("autominer_enchant"),
    GANG_MAIN("gang_main"),
    GANG_MANAGEMENT("gang_management"),
    GANG_BANNER_CREATOR("gang_banner_creator"),
    BANK_MAIN("bank_main"),
    INVESTMENT_MENU("investment_menu"),
    CONTAINER_CONFIG("container_config"),
    CONTAINER_FILTER("container_filter"),
    TANK_CONFIG("tank_config"),
    TANK_PRICES("tank_prices"),
    OUTPOST_MAIN("outpost_main"),
    OUTPOST_SKINS("outpost_skins"),
    CRATE_MANAGEMENT("crate_management");

    private final String id;

    GUIType(String id) {
        this.id = id;
    }

    public static GUIType fromId(String id) {
        for (GUIType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    public String getId() {
        return id;
    }
}