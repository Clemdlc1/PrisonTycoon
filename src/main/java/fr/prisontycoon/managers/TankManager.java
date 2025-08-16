package fr.prisontycoon.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.TankData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.ChatColor;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TankManager {

	private final PrisonTycoon plugin;
	private final DatabaseManager databaseManager;
	private final Gson gson = new Gson();
	private final NamespacedKey tankKey;
	private final NamespacedKey tankIdKey;
	private final Map<String, TankData> tankCache = new ConcurrentHashMap<>();
	private final Map<Location, String> tankLocations = new ConcurrentHashMap<>();
	private final Map<String, ArmorStand> tankNameTags = new ConcurrentHashMap<>();
	private int nextTankId = 1;

	public TankManager(PrisonTycoon plugin) {
		this.plugin = plugin;
		this.databaseManager = plugin.getDatabaseManager();
		this.tankKey = new NamespacedKey(plugin, "tank");
		this.tankIdKey = new NamespacedKey(plugin, "tank_id");
		createTanksTable();
		loadTanks();
		startNameTagVisibilityTask();
	}

	private void createTanksTable() {
		String query = """
					CREATE TABLE IF NOT EXISTS tanks (
						id VARCHAR(255) PRIMARY KEY,
						owner VARCHAR(36),
						location TEXT,
						filters TEXT,
						prices TEXT,
						custom_name VARCHAR(255),
						total_items BIGINT,
						bills TEXT
					);
				""";
		try (Connection conn = databaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(query)) {
			ps.execute();
		} catch (SQLException e) {
			plugin.getLogger().severe("Could not create tanks table: " + e.getMessage());
		}
	}

	public boolean isTank(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return false;
		ItemMeta meta = item.getItemMeta();
		return meta.getPersistentDataContainer().has(tankKey, PersistentDataType.BOOLEAN) &&
				meta.getPersistentDataContainer().has(tankIdKey, PersistentDataType.STRING);
	}

	public boolean isTankBlock(Location location) {
		return tankLocations.containsKey(location) &&
				location.getBlock().getType() == Material.BARREL;
	}

	public TankData getTankAt(Location location) {
		String tankId = tankLocations.get(location);
		return tankId != null ? tankCache.get(tankId) : null;
	}

	public ItemStack createTank(Player owner) {
		String tankId = generateTankId();
		ItemStack tank = new ItemStack(Material.BARREL);
		ItemMeta meta = tank.getItemMeta();
		meta.getPersistentDataContainer().set(tankKey, PersistentDataType.BOOLEAN, true);
		meta.getPersistentDataContainer().set(tankIdKey, PersistentDataType.STRING, tankId);
		plugin.getGUIManager().applyName(meta, "§6⚡ Tank Automatique");
		plugin.getGUIManager().applyLore(meta, Arrays.asList(
				"§7Propriétaire: §e" + owner.getName(),
				"§7Capacité: §b" + NumberFormatter.format(TankData.MAX_CAPACITY) + " items",
				"§7Filtres: §cAucun",
				"§7Prix: §cNon configuré",
				"",
				"§7▸ Placer au sol pour activer",
				"§7▸ Shift + Clic droit (placé) pour configurer",
				"§7▸ Shift + Clic (placé) pour vendre avec Sell Hand"
		));
		tank.setItemMeta(meta);
		TankData tankData = new TankData(tankId, owner.getUniqueId());
		tankCache.put(tankId, tankData);
		saveTank(tankData);
		return tank;
	}

	public boolean placeTank(Location location, ItemStack tankItem, Player player) {
		if (!isTank(tankItem)) return false;
		String tankId = getTankId(tankItem);
		TankData tankData = tankCache.get(tankId);
		if (tankData == null) return false;
		if (isTankBlock(location)) {
			player.sendMessage("§c❌ Il y a déjà un tank à cette position!");
			return false;
		}
		tankData.setLocation(location);
		tankLocations.put(location.getBlock().getLocation(), tankId); // Utilisez location.getBlock().getLocation() pour une clé normalisée
		createNameTag(tankData);
		saveTank(tankData);
		player.sendMessage("§a✓ Tank placé! Les autres joueurs peuvent maintenant vendre leurs items.");
		player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);
		return true;
	}

	public ItemStack breakTank(Location location, Player player) {
		TankData tankData = getTankAt(location);
		if (tankData == null) return null;
		if (!tankData.getOwner().equals(player.getUniqueId())) {
			player.sendMessage("§c❌ Vous n'êtes pas le propriétaire de ce tank!");
			return null;
		}
		removeNameTag(tankData.getId());
		tankLocations.remove(location);
		tankData.setLocation(null);
		ItemStack tankItem = createTankItem(tankData);
		saveTank(tankData);
		player.sendMessage("§a✓ Tank récupéré!");
		player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
		return tankItem;
	}

	private ItemStack createTankItem(TankData tankData) {
		ItemStack tank = new ItemStack(Material.BARREL);
		ItemMeta meta = tank.getItemMeta();
		meta.getPersistentDataContainer().set(tankKey, PersistentDataType.BOOLEAN, true);
		meta.getPersistentDataContainer().set(tankIdKey, PersistentDataType.STRING, tankData.getId());
		updateTankItemDisplay(meta, tankData);
		tank.setItemMeta(meta);
		return tank;
	}

	private void createNameTag(TankData tankData) {
		if (!tankData.isPlaced()) return;
		Location nameTagLoc = tankData.getLocation().clone().add(0.5, 1.5, 0.5);
		ArmorStand mainNameTag = (ArmorStand) nameTagLoc.getWorld().spawnEntity(nameTagLoc, EntityType.ARMOR_STAND);
		setupNameTag(mainNameTag);
		ArmorStand customNameTag = null;
		if (tankData.hasCustomName()) {
			Location customLoc = nameTagLoc.clone().add(0, 0.3, 0);
			customNameTag = (ArmorStand) customLoc.getWorld().spawnEntity(customLoc, EntityType.ARMOR_STAND);
			setupNameTag(customNameTag);
			customNameTag.setCustomName(tankData.getCustomName());
		}
		updateNameTag(tankData, mainNameTag);
		tankNameTags.put(tankData.getId(), mainNameTag);
		if (customNameTag != null) {
			tankNameTags.put(tankData.getId() + "_custom", customNameTag);
		}
	}

	private void setupNameTag(ArmorStand nameTag) {
		nameTag.setVisible(false);
		nameTag.setGravity(false);
		nameTag.setCanPickupItems(false);
		nameTag.setCustomNameVisible(true);
		nameTag.setRemoveWhenFarAway(false);
		nameTag.setInvulnerable(true);
	}

	private void updateNameTag(TankData tankData, ArmorStand nameTag) {
		if (nameTag == null || nameTag.isDead()) return;
		String ownerName = plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName();
		String playerBalance = "0$";
		int totalBills = tankData.getBills().values().stream().mapToInt(Integer::intValue).sum();
		List<String> lines = Arrays.asList(
				"§6⚡ Tank de §e" + ownerName,
				"§7Solde propriétaire: §a" + playerBalance,
				"§7Items: §b" + NumberFormatter.format(tankData.getTotalItems()) + "§7/§b" + NumberFormatter.format(TankData.MAX_CAPACITY),
				(totalBills > 0 ? "§7Billets: §b" + NumberFormatter.format(totalBills) + " §7(§e" + tankData.getBills().size() + " tiers§7)" : "§7Billets: §cAucun"),
				"§7Prix: " + (tankData.getPrices().isEmpty() ? "§cAucun" : "§a" + tankData.getPrices().size() + " configurés")
		);
		nameTag.setCustomName(String.join("\n", lines));
	}

	public void updateTankNameTag(TankData tankData) {
		if (!tankData.isPlaced()) return;
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			ArmorStand mainNameTag = tankNameTags.get(tankData.getId());
			ArmorStand customNameTag = tankNameTags.get(tankData.getId() + "_custom");
			if (mainNameTag != null && !mainNameTag.isDead()) {
				updateNameTag(tankData, mainNameTag);
			}
			if (tankData.hasCustomName()) {
				if (customNameTag == null || customNameTag.isDead()) {
					Location customLoc = tankData.getLocation().clone().add(0.5, 1.8, 0.5);
					ArmorStand newCustomNameTag = (ArmorStand) customLoc.getWorld().spawnEntity(customLoc, EntityType.ARMOR_STAND);
					setupNameTag(newCustomNameTag);
					tankNameTags.put(tankData.getId() + "_custom", newCustomNameTag);
					customNameTag = newCustomNameTag;
				}
				customNameTag.setCustomName(tankData.getCustomName());
			} else {
				if (customNameTag != null && !customNameTag.isDead()) {
					customNameTag.remove();
					tankNameTags.remove(tankData.getId() + "_custom");
				}
			}
		});
	}

	private void removeNameTag(String tankId) {
		ArmorStand mainNameTag = tankNameTags.remove(tankId);
		ArmorStand customNameTag = tankNameTags.remove(tankId + "_custom");
		if (mainNameTag != null && !mainNameTag.isDead()) {
			mainNameTag.remove();
		}
		if (customNameTag != null && !customNameTag.isDead()) {
			customNameTag.remove();
		}
	}

	private void updateTankItemDisplay(ItemMeta meta, TankData tankData) {
		plugin.getGUIManager().applyName(meta, "§6⚡ Tank Automatique");
		List<String> lore = new ArrayList<>();
		lore.add("§7Propriétaire: §e" + plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName());
		if (tankData.hasCustomName()) {
			lore.add("§7Nom: §f" + tankData.getCustomName());
		}
		lore.addAll(Arrays.asList(
				"§7Capacité: §b" + NumberFormatter.format(tankData.getTotalItems()) + "§7/§b" + NumberFormatter.format(TankData.MAX_CAPACITY),
				"§7Filtres: " + (tankData.getFilters().isEmpty() ? "§cAucun" : "§a" + tankData.getFilters().size() + " matériaux"),
				"§7Prix configurés: " + (tankData.getPrices().isEmpty() ? "§cAucun" : "§a" + tankData.getPrices().size()),
				"",
				"§7▸ Placer au sol pour activer",
				"§7▸ Shift + Clic droit (placé) pour configurer",
				"§7▸ Shift + Clic (placé) pour vendre avec Sell Hand",
				"§7▸ Clic droit (placé) pour vendre ou voir les prix"
		));
		plugin.getGUIManager().applyLore(meta, lore);
	}

	public boolean sellToTank(Location tankLocation, Player seller, ItemStack itemToSell) {
		TankData tankData = getTankAt(tankLocation);
		if (tankData == null) return false;
		Material material = itemToSell.getType();
		if (!tankData.getFilters().contains(material)) {
			seller.sendMessage("§c❌ Ce tank n'accepte pas " + material.name().toLowerCase() + "!");
			return false;
		}
		if (!tankData.getPrices().containsKey(material)) {
			seller.sendMessage("§c❌ Aucun prix configuré pour " + material.name().toLowerCase() + "!");
			return false;
		}
		long pricePerItem = tankData.getPrices().get(material);
		if (pricePerItem <= 0) {
			seller.sendMessage("§c❌ Prix non configuré pour " + material.name().toLowerCase() + "!");
			return false;
		}
		int amount = itemToSell.getAmount();
		long totalPrice = pricePerItem * amount;
		Player owner = plugin.getServer().getPlayer(tankData.getOwner());
		if (tankData.canAddItems(amount)) {
			seller.sendMessage("§c❌ Le tank est plein!");
			return false;
		}
		tankData.addItems(material, amount);
		seller.sendMessage("§a✓ Vendu " + amount + "x " + material.name().toLowerCase() +
				" pour " + NumberFormatter.format(totalPrice) + "$!");
		seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
		if (owner != null && !owner.equals(seller)) {
			owner.sendMessage("§7📦 " + seller.getName() + " a vendu " + amount + "x " +
					material.name().toLowerCase() + " à votre tank pour " +
					NumberFormatter.format(totalPrice) + "$");
		}
		updateTankNameTag(tankData);
		saveTank(tankData);
		return true;
	}

	public TankData getTankData(ItemStack tank) {
		if (!isTank(tank)) return null;
		String tankId = getTankId(tank);
		return tankId != null ? tankCache.get(tankId) : null;
	}

	public String getTankId(ItemStack tank) {
		if (!isTank(tank)) return null;
		ItemMeta meta = tank.getItemMeta();
		return meta.getPersistentDataContainer().get(tankIdKey, PersistentDataType.STRING);
	}

	private String generateTankId() {
		String id;
		do {
			id = "TANK-" + String.format("%06d", nextTankId++);
		} while (tankCache.containsKey(id));
		return id;
	}

	private void loadTanks() {
		String query = "SELECT * FROM tanks";
		try (Connection conn = databaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(query);
			 ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				String id = rs.getString("id");
				UUID owner = UUID.fromString(rs.getString("owner"));
				TankData tankData = new TankData(id, owner);

				// Désérialiser la localisation et filtrer par monde Market
				String locationJson = rs.getString("location");
				if (locationJson != null) {
					Type locationMapType = new TypeToken<Map<String, Object>>() {
					}.getType();
					Map<String, Object> locationMap = gson.fromJson(locationJson, locationMapType);

					String worldName = (String) locationMap.get("world");
					if (worldName == null || !"Market".equalsIgnoreCase(worldName)) {
						// Ne charger que les tanks du monde Market
						continue;
					}
					double x = (double) locationMap.get("x");
					double y = (double) locationMap.get("y");
					double z = (double) locationMap.get("z");
					// Gérer le fait que yaw/pitch peuvent être stockés comme des Double
					float yaw = ((Number) locationMap.get("yaw")).floatValue();
					float pitch = ((Number) locationMap.get("pitch")).floatValue();

					Location location = new Location(plugin.getServer().getWorld(worldName), x, y, z, yaw, pitch);
					tankData.setLocation(location);
				}

				Type materialSetType = new TypeToken<Set<Material>>() {
				}.getType();
				String filtersJson = rs.getString("filters");
				if (filtersJson != null) {
					Set<Material> filters = gson.fromJson(filtersJson, materialSetType);
					tankData.getFilters().addAll(filters);
				}

				Type materialLongMapType = new TypeToken<Map<Material, Long>>() {
				}.getType();
				String pricesJson = rs.getString("prices");
				if (pricesJson != null) {
					Map<Material, Long> prices = gson.fromJson(pricesJson, materialLongMapType);
					tankData.getPrices().putAll(prices);
				}

				tankData.setCustomName(rs.getString("custom_name"));

				// Charger les billets
				String billsJson = rs.getString("bills");
				if (billsJson != null) {
					Type billsMapType = new TypeToken<Map<Integer, Integer>>() {}.getType();
					Map<Integer, Integer> bills = gson.fromJson(billsJson, billsMapType);
					if (bills != null) {
						for (Map.Entry<Integer, Integer> e : bills.entrySet()) {
							tankData.addBills(e.getKey(), e.getValue());
						}
					}
				}

				tankCache.put(id, tankData);
				if (tankData.isPlaced()) {
					tankLocations.put(tankData.getLocation(), id);
					createNameTag(tankData);
				}
			}
		} catch (SQLException e) {
			plugin.getLogger().severe("Could not load tanks from database: " + e.getMessage());
		}
	}

	// ====== Nouveau: gestion par monde ======
	public void loadWorldTanks(String worldName) {
		if (worldName == null || worldName.isEmpty()) return;
		String query = "SELECT * FROM tanks";
		try (Connection conn = databaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(query);
			 ResultSet rs = ps.executeQuery()) {

			while (rs.next()) {
				String id = rs.getString("id");
				UUID owner = UUID.fromString(rs.getString("owner"));
				TankData tankData = new TankData(id, owner);

				String locationJson = rs.getString("location");
				if (locationJson == null) continue;
				Type locationMapType = new TypeToken<Map<String, Object>>() {}.getType();
				Map<String, Object> locationMap = gson.fromJson(locationJson, locationMapType);
				String wn = (String) locationMap.get("world");
				if (wn == null || !worldName.equalsIgnoreCase(wn)) continue;

				double x = ((Number) locationMap.get("x")).doubleValue();
				double y = ((Number) locationMap.get("y")).doubleValue();
				double z = ((Number) locationMap.get("z")).doubleValue();
				float yaw = ((Number) locationMap.get("yaw")).floatValue();
				float pitch = ((Number) locationMap.get("pitch")).floatValue();
				Location location = new Location(plugin.getServer().getWorld(wn), x, y, z, yaw, pitch);
				tankData.setLocation(location);

				Type materialSetType = new TypeToken<Set<Material>>() {}.getType();
				String filtersJson = rs.getString("filters");
				if (filtersJson != null) {
					Set<Material> filters = gson.fromJson(filtersJson, materialSetType);
					tankData.getFilters().addAll(filters);
				}

				Type materialLongMapType = new TypeToken<Map<Material, Long>>() {}.getType();
				String pricesJson = rs.getString("prices");
				if (pricesJson != null) {
					Map<Material, Long> prices = gson.fromJson(pricesJson, materialLongMapType);
					tankData.getPrices().putAll(prices);
				}

				tankData.setCustomName(rs.getString("custom_name"));

				String billsJson = rs.getString("bills");
				if (billsJson != null) {
					Type billsMapType = new TypeToken<Map<Integer, Integer>>() {}.getType();
					Map<Integer, Integer> bills = gson.fromJson(billsJson, billsMapType);
					if (bills != null) {
						for (Map.Entry<Integer, Integer> e : bills.entrySet()) {
							tankData.addBills(e.getKey(), e.getValue());
						}
					}
				}

				tankCache.put(id, tankData);
				if (tankData.isPlaced()) {
					tankLocations.put(tankData.getLocation(), id);
					createNameTag(tankData);
				}
			}
		} catch (SQLException e) {
			plugin.getLogger().severe("Could not load world tanks: " + e.getMessage());
		}
	}

	public void saveWorldTanks(String worldName) {
		if (worldName == null || worldName.isEmpty()) return;
		tankCache.values().stream()
				.filter(t -> t.isPlaced() && t.getLocation() != null && t.getLocation().getWorld() != null)
				.filter(t -> worldName.equalsIgnoreCase(t.getLocation().getWorld().getName()))
				.forEach(this::saveTank);
	}

	public void unloadWorldTanks(String worldName) {
		if (worldName == null || worldName.isEmpty()) return;
		Iterator<Map.Entry<String, TankData>> it = tankCache.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, TankData> e = it.next();
			TankData t = e.getValue();
			if (t.isPlaced() && t.getLocation() != null && t.getLocation().getWorld() != null &&
					worldName.equalsIgnoreCase(t.getLocation().getWorld().getName())) {
				removeNameTag(t.getId());
				tankLocations.remove(t.getLocation());
				it.remove();
			}
		}
	}

	public void saveTank(TankData tankData) {
		String query = """
					INSERT INTO tanks (id, owner, location, filters, prices, custom_name, total_items, bills)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					ON CONFLICT (id) DO UPDATE SET
						owner = EXCLUDED.owner,
						location = EXCLUDED.location,
						filters = EXCLUDED.filters,
						prices = EXCLUDED.prices,
						custom_name = EXCLUDED.custom_name,
						total_items = EXCLUDED.total_items,
						bills = EXCLUDED.bills;
				""";
		try (Connection conn = databaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(query)) {

			ps.setString(1, tankData.getId());
			ps.setString(2, tankData.getOwner().toString());

			// Correction : Sérialiser la localisation manuellement
			Location loc = tankData.getLocation();
			if (loc != null) {
				Map<String, Object> locationMap = new HashMap<>();
				locationMap.put("world", loc.getWorld().getName());
				locationMap.put("x", loc.getX());
				locationMap.put("y", loc.getY());
				locationMap.put("z", loc.getZ());
				locationMap.put("yaw", loc.getYaw());
				locationMap.put("pitch", loc.getPitch());
				ps.setString(3, gson.toJson(locationMap));
			} else {
				ps.setString(3, null); // Gérer le cas où la localisation est nulle
			}

			ps.setString(4, gson.toJson(tankData.getFilters()));
			ps.setString(5, gson.toJson(tankData.getPrices()));
			ps.setString(6, tankData.getCustomName());
			ps.setLong(7, tankData.getTotalItems());

			// Sérialiser les billets
			ps.setString(8, gson.toJson(tankData.getBills()));

			ps.executeUpdate();
		} catch (SQLException e) {
			plugin.getLogger().severe("Could not save tank to database: " + e.getMessage());
		}
	}

	public Map<String, TankData> getTankCache() {
		return tankCache;
	}

	public NamespacedKey getTankIdKey() {
		return tankIdKey;
	}

	public void saveAllTanks() {
		for (TankData tankData : tankCache.values()) {
			saveTank(tankData);
		}
	}

	public List<TankData> getPlayerTanks(UUID playerUUID) {
		return tankCache.values().stream()
				.filter(tank -> tank.getOwner().equals(playerUUID))
				.toList();
	}

	public void removeTank(String tankId) {
		TankData tankData = tankCache.remove(tankId);
		if (tankData != null) {
			if (tankData.isPlaced()) {
				tankLocations.remove(tankData.getLocation());
				removeNameTag(tankId);
			}
		}
		String query = "DELETE FROM tanks WHERE id = ?";
		try (Connection conn = databaseManager.getConnection();
			 PreparedStatement ps = conn.prepareStatement(query)) {
			ps.setString(1, tankId);
			ps.executeUpdate();
		} catch (SQLException e) {
			plugin.getLogger().severe("Could not remove tank from database: " + e.getMessage());
		}
	}

	public void shutdown() {
		saveAllTanks();
		for (ArmorStand nameTag : tankNameTags.values()) {
			if (nameTag != null && !nameTag.isDead()) {
				nameTag.remove();
			}
		}
		tankNameTags.clear();
	}

	// === GESTION DE LA VISIBILITÉ DES NAMETAGS (20 blocs) ===
	private void startNameTagVisibilityTask() {
		plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			for (Map.Entry<String, ArmorStand> entry : new HashMap<>(tankNameTags).entrySet()) {
				String id = entry.getKey();
				ArmorStand as = entry.getValue();
				if (as == null || as.isDead()) continue;
				// clé "id" ou "id_custom" -> récupérer id réel
				String tankId = id.endsWith("_custom") ? id.substring(0, id.length() - 7) : id;
				TankData data = tankCache.get(tankId);
				if (data == null || !data.isPlaced() || data.getLocation().getWorld() == null) continue;
				boolean nearby = data.getLocation().getWorld().getNearbyPlayers(data.getLocation(), 20).iterator().hasNext();
				as.setCustomNameVisible(nearby);
			}
		}, 40L, 40L); // toutes les 2s
	}

	// === BILLETS: UTILITAIRES ===
	public boolean isBillItem(ItemStack item) {
		if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return false;
		ItemMeta meta = item.getItemMeta();
		if (!meta.hasLore()) return false;
		java.util.List<String> lore = meta.getLore();
		if (lore == null || lore.isEmpty()) return false;
		String line0 = ChatColor.stripColor(lore.get(0));
		if (line0 == null || !line0.startsWith("Tier ")) return false;
		// Vérifier la ligne d'instruction pour limiter les papiers renommés
		if (lore.size() >= 2) {
			String line1 = ChatColor.stripColor(lore.get(1));
			if (line1 == null || !line1.startsWith("Utiliser /sell")) return false;
		} else {
			return false;
		}
		// Tier doit être > 0
		return extractTierFromLore(lore) > 0;
	}

	public int getBillTier(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return -1;
		ItemMeta meta = item.getItemMeta();
		java.util.List<String> lore = meta.getLore();
		if (lore == null) return -1;
		return extractTierFromLore(lore);
	}

	public long getBillValue(int tier) {
		return plugin.getConfig().getLong("printers." + tier + ".value", tier * 10L);
	}

	public long getBillValueFromItem(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return -1L;
		java.util.List<String> lore = item.getItemMeta().getLore();
		if (lore == null || lore.isEmpty()) return -1L;
		return extractValueFromLore(lore);
	}

	private int extractTierFromLore(java.util.List<String> lore) {
		try {
			String line0 = ChatColor.stripColor(lore.get(0));
			if (line0 == null) return -1;
			int idx = line0.indexOf("Tier ");
			if (idx < 0) return -1;
			String after = line0.substring(idx + "Tier ".length());
			StringBuilder digits = new StringBuilder();
			for (char c : after.toCharArray()) {
				if (Character.isDigit(c)) digits.append(c); else break;
			}
			if (digits.length() == 0) return -1;
			return Integer.parseInt(digits.toString());
		} catch (Exception e) {
			return -1;
		}
	}

	private long extractValueFromLore(java.util.List<String> lore) {
		try {
			String line0 = ChatColor.stripColor(lore.get(0));
			if (line0 == null) return -1L;
			int open = line0.indexOf('(');
			int dollar = line0.indexOf('$', open + 1);
			if (open < 0 || dollar < 0) return -1L;
			String inside = line0.substring(open + 1, dollar); // e.g. 12345
			StringBuilder digits = new StringBuilder();
			for (char c : inside.toCharArray()) if (Character.isDigit(c)) digits.append(c);
			if (digits.length() == 0) return -1L;
			return Long.parseLong(digits.toString());
		} catch (Exception e) {
			return -1L;
		}
	}

	/**
	 * Crée un billet pour un tier donné avec le nouveau format
	 */
	public ItemStack createBillForTier(int tier) {
		// Papier avec valeur depuis printer.yml (chargé dans getConfig sous printers.*)
		long value = plugin.getConfig().getLong("printers." + tier + ".value", tier * 10L);
		ItemStack paper = new ItemStack(Material.PAPER);
		ItemMeta meta = paper.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(ChatColor.AQUA + "Billet Tier " + tier);
			List<String> lore = new ArrayList<>();
			lore.add(ChatColor.GRAY + "Tier " + tier + " (" + value + "$)");
			lore.add(ChatColor.GRAY + "Utiliser /sell ou un tank pour vendre vos billets");
			meta.setLore(lore);
			paper.setItemMeta(meta);
		}
		return paper;
	}
}