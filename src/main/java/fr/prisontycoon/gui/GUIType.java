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
    GANG_NO_GANG("gang_no_gang"),
    GANG_LIST("gang_list"),
    GANG_INFO("gang_info"),
    GANG_MEMBERS("gang_members"),
    GANG_UPGRADES("gang_upgrades"),
    GANG_TALENTS("gang_talents"),
    GANG_SHOP("gang_shop"),
    GANG_SETTINGS("gang_settings"),
    BANNER_CREATOR("banner_creator"),
    BANK_MAIN("bank_main"),
    BANK_TYPE_SELECTOR("bank_type_selector"),
    INVESTMENT_MENU("investment_menu"),
    CONTAINER_CONFIG("container_config"),
    CONTAINER_FILTER("container_filter"),
    TANK_CONFIG("tank_config"),
    TANK_PRICES("tank_prices"),
    OUTPOST_MAIN("outpost_main"),
    OUTPOST_SKINS("outpost_skins"),
    WARP_MENU("warp_menu"),
    WARP_MINES_MENU("warp_mines_menu"),
    HEAD_COLLECTION("head_collection"),
    CRATE_MANAGEMENT("crate_management"),
    BOOK_SHOP_SELECTOR("book_shop_selector"),
    BOOK_SHOP_PICKAXE("book_shop_pickaxe"),
    BOOK_SHOP_WEAPON_ARMOR("book_shop_weapon_armor"),
    QUESTS_MENU("quests_menu"),
    BATTLE_PASS_MENU("battle_pass_menu"),
    BATTLE_PASS_REWARDS("battle_pass_rewards"),
    QUESTS_DAILY("quests_daily"),
    QUESTS_WEEKLY("quests_weekly"),
    BLOCK_COLLECTOR("block_collector"),
    BLOCK_COLLECTOR_STATS("block_collector_stats"),
    BLOCK_COLLECTOR_LEADERBOARD("block_collector_leaderboard"),
    ADVANCEMENT_QUESTS("advancement_quests"),
    QUEST_STATISTICS("quest_statistics"),
    QUEST_REWARDS_HISTORY("quest_rewards_history"),
    DAILY_REWARD("daily_reward"),
    FORGE_MAIN("forge_main"),
    BATTLE_PASS_MAIN_MENU("battle_pass_main_menu"),
    SHOP_MAIN("shop_main"),
    SHOP_CATEGORY("shop_category"),
    SHOP_QUANTITY("shop_quantity"),
    SHOP_PVP("shop_pvp"),
    SHOP_BLOCKS("shop_blocks"),
    SHOP_FOOD("shop_food"),
    SHOP_TOOLS("shop_tools"),
    SHOP_MISC("shop_misc"),
    SHOP_REDSTONE("shop_redstone"),
    SHOP_DECORATION("shop_decoration"),
    SHOP_FARMING("shop_farming"),
    SHOP_PRINTERS("shop_printers"),

    FORGE_RECIPE("forge_recipe");

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