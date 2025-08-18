package fr.prisontycoon.pets;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.GlobalBonusManager;
import fr.prisontycoon.utils.HeadEnum;
import fr.prisontycoon.utils.HeadUtils;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service cœur du système de pets :
 * - stockage/sérialisation dans PlayerData (colonne custom via PlayerDataManager)
 * - gestion croissance/XP et duplicatas
 * - calcul des bonus (en %), puis application via GlobalBonusManager (pour les catégories mappées)
 * - utilitaires GUI (icônes de pets via HeadUtils)
 * - visuels améliorés avec nametags colorés et particules de synergie
 */
public class PetService {
	private final PrisonTycoon plugin;
	private static final long XP_PER_GROWTH = 100L; // base pour COMMUN; rareté applique un multiplicateur

	// NOTE (écarts visuels): ajustez ces constantes pour régler le triangle vertical isocèle
	// PET_BASE_HEIGHT: hauteur de base au-dessus du joueur
	// PET_TRIANGLE_TOP_OFFSET: décalage vertical du sommet (apex)
	// PET_TRIANGLE_BASE_OFFSET: décalage vertical de la base (deux pets du bas)
	// PET_TRIANGLE_SIDE_OFFSET: écart horizontal gauche/droite des deux pets du bas
	private static final double PET_BASE_HEIGHT = 1.6;
	private static final double PET_TRIANGLE_TOP_OFFSET = 0.8;
	private static final double PET_TRIANGLE_BASE_OFFSET = 0.4;
	private static final double PET_TRIANGLE_SIDE_OFFSET = 2.0;
	private final NamespacedKey petBoxTierKey;
	private final NamespacedKey petFoodTierKey;
	private final Map<UUID, List<ArmorStand>> petStands = new ConcurrentHashMap<>();
	private final Map<UUID, List<ArmorStand>> petNameTags = new ConcurrentHashMap<>();
	private BukkitTask visualTask;
	private BukkitTask particleTask;

	// Définition des synergies pour les particules
	private static final Map<String, List<String>> SYNERGIES = new HashMap<>();
	
	static {
		SYNERGIES.put("commerce", Arrays.asList("fenrir", "licorne", "griffon"));
		SYNERGIES.put("savoirs", Arrays.asList("sphinx", "hippogriffe", "kelpie"));
		SYNERGIES.put("machineries", Arrays.asList("tarasque_royale", "tarasque", "blackshuck"));
		SYNERGIES.put("richesses", Arrays.asList("basilic", "vouivre", "kraken"));
		SYNERGIES.put("opportunites", Arrays.asList("morrigan", "cernunnos", "farfadet_r"));
	}

	public PetService(PrisonTycoon plugin) {
		this.plugin = plugin;
		this.petBoxTierKey = new NamespacedKey(plugin, "pet_box_tier");
		this.petFoodTierKey = new NamespacedKey(plugin, "pet_food_tier");
		startVisualTask();
		startParticleTask();
	}

	// ====== Données sérialisables ======
	public static class PetData {
		public String id;       // id dans PetRegistry
		public int growth;      // 0..50
		public long xp;         // xp courante vers prochaine croissance
		public boolean equipped;
	}

	// ====== Slots publics ======
	public int getUnlockedSlots(Player player) {
		return plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getUnlockedPetSlots();
	}

	public int addUnlockedPetSlots(Player player, int delta) {
		var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
		int before = data.getUnlockedPetSlots();
		int after = Math.max(1, Math.min(3, before + delta));
		if (after != before) {
			data.setUnlockedPetSlots(after);
			plugin.getPlayerDataManager().markDirty(player.getUniqueId());
			player.sendMessage("§6🐾 Slots de compagnons débloqués: §e" + after + "§7/§e3");
		}
		return after;
	}

	public void setUnlockedPetSlots(Player player, int slots) {
		var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
		int clamped = Math.max(1, Math.min(3, slots));
		data.setUnlockedPetSlots(clamped);
		plugin.getPlayerDataManager().markDirty(player.getUniqueId());
		player.sendMessage("§6🐾 Slots de compagnons réglés sur: §e" + clamped + "§7/§e3");
	}

	// ====== Accès ======
	public Map<String, PetData> getPlayerPets(UUID playerId) {
		var data = plugin.getPlayerDataManager().getPlayerData(playerId);
		return data != null ? data.getPets() : new HashMap<>();
	}

	public void savePlayerPets(UUID playerId, Map<String, PetData> pets) {
		var data = plugin.getPlayerDataManager().getPlayerData(playerId);
		if (data != null) {
			data.setPets(pets);
			plugin.getPlayerDataManager().markDirty(playerId);
		}
	}

	// ====== Logique ======
	public double computePlayerBonusPercent(Player player, GlobalBonusManager.BonusCategory category) {
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		double total = 0.0;

		int equippedCount = 0;
		for (PetData pd : pets.values()) {
			if (!pd.equipped) continue;
			var def = PetRegistry.get(pd.id);
			if (def == null) continue;
			// Filtre catégorie mappée
			if (def.effectType().getMappedCategory() == category) {
				total += def.basePerGrowthPercent() * pd.growth;
			}
			equippedCount++;
		}

		// Synergies (linéaires) : on utilise la somme des croissances des 3 pets équipés
		if (equippedCount == 3) {
			int sumGrowth = pets.values().stream().filter(p -> p.equipped).mapToInt(p -> p.growth).sum();
			switch (category) {
				case SELL_BONUS -> {
					// Synergie Commerce: +0.20 Sell par sumGrowth
					if (hasEquipped(pets, "fenrir", "licorne", "griffon")) total += 0.20 * sumGrowth;
				}
				case TOKEN_BONUS -> {
					// Synergie Commerce: +0.25 TokensGreed par sumGrowth
					if (hasEquipped(pets, "fenrir", "licorne", "griffon")) total += 0.25 * sumGrowth;
				}
				case EXPERIENCE_BONUS -> {
					// Synergie Savoirs: +0.20 XP joueur par sumGrowth
					if (hasEquipped(pets, "sphinx", "hippogriffe", "kelpie")) total += 0.20 * sumGrowth;
				}
				case JOB_XP_BONUS -> {
					// Synergie Savoirs: +0.25 Pet XP par sumGrowth (sur Job XP ici on n'ajoute rien)
				}
				case FORTUNE_BONUS -> {
					// Synergie Machineries: +0.0 sur Fortune (non défini ici)
				}
				case BEACON_MULTIPLIER -> {
					// Synergie Richesses: +0.10 Beacons par sumGrowth
					if (hasEquipped(pets, "basilic", "vouivre", "kraken")) total += 0.10 * sumGrowth;
				}
				case OUTPOST_BONUS -> {
					// Synergie Richesses: +0.20 Gain avant-poste par sumGrowth
					if (hasEquipped(pets, "basilic", "vouivre", "kraken")) total += 0.20 * sumGrowth;
				}
				default -> {
				}
			}
		}
		return total;
	}

	private boolean hasEquipped(Map<String, PetData> pets, String a, String b, String c) {
		return isEquipped(pets, a) && isEquipped(pets, b) && isEquipped(pets, c);
	}

	private boolean isEquipped(Map<String, PetData> pets, String id) {
		PetData p = pets.get(id);
		return p != null && p.equipped;
	}

	// GUI Helpers (icônes)
	public ItemStack getHeadFor(PetDefinition pet) {
		HeadEnum head;
		String id = pet.id();
		switch (id) {
			case "gargouille" -> head = HeadEnum.PET_GARGOUILLE;
			case "vouivre" -> head = HeadEnum.PET_VOUIVRE;
			case "kelpie" -> head = HeadEnum.PET_KELPIE;
			case "caitsith" -> head = HeadEnum.PET_CAIT_SITH;
			case "blackshuck" -> head = HeadEnum.PET_BLACK_SHUCK;
			case "nereide" -> head = HeadEnum.PET_NEREIDE;
			case "gevaudan_c" -> head = HeadEnum.PET_GEVAUDAN;
			case "griffon" -> head = HeadEnum.PET_GRIFFON;
			case "basilic" -> head = HeadEnum.PET_BASILIC;
			case "selkie" -> head = HeadEnum.PET_SELKIE;
			case "tarasque" -> head = HeadEnum.PET_TARASQUE;
			case "farfadet_r" -> head = HeadEnum.PET_FARFADET;
			case "licorne" -> head = HeadEnum.PET_LICORNE;
			case "sphinx" -> head = HeadEnum.PET_SPHINX;
			case "morrigan" -> head = HeadEnum.PET_MORRIGAN;
			case "cernunnos" -> head = HeadEnum.PET_CERNUNNOS;
			case "hippogriffe" -> head = HeadEnum.PET_HIPPOGRIFFE;
			case "fenrir" -> head = HeadEnum.PET_FENRIR;
			case "kraken" -> head = HeadEnum.PET_KRAKEN;
			case "tarasque_royale" -> head = HeadEnum.PET_TARASQUE_ROYALE;
            default -> head = HeadEnum.PET_GARGOUILLE;
		}
		return HeadUtils.createHead(head);
	}

	/**
	 * Retourne le bonus utilitaire (en %) pour un type d'effet non mappé à GlobalBonusManager
	 * (PROC_PICKAXE, AUTOMINER_EFFICIENCY, PICKAXE_WEAR, KEYS_CHANCE).
	 */
	public double computeUtilityBonusPercent(Player player, PetEffectType effectType) {
		if (effectType == null) return 0.0;
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		double total = 0.0;
		
		int equippedCount = 0;
		for (PetData pd : pets.values()) {
			if (!pd.equipped) continue;
			var def = PetRegistry.get(pd.id);
			if (def == null) continue;
			if (def.effectType() == effectType) {
				total += def.basePerGrowthPercent() * pd.growth;
			}
			equippedCount++;
		}
		
		// Synergies utilitaires si 3 pets équipés
		if (equippedCount == 3) {
			int sumGrowth = pets.values().stream().filter(p -> p.equipped).mapToInt(p -> p.growth).sum();
			switch (effectType) {
				case AUTOMINER_FUEL_CONSUMPTION -> {
					// Synergie Machineries: +0.25 Consommation autominer par sumGrowth
					if (hasEquipped(pets, "tarasque_royale", "tarasque", "blackshuck")) total += 0.25 * sumGrowth;
				}
				case PICKAXE_WEAR -> {
					// Synergie Machineries: -0.05 Usure pickaxe par sumGrowth
					if (hasEquipped(pets, "tarasque_royale", "tarasque", "blackshuck")) total += 0.05 * sumGrowth;
				}
				case PROC_PICKAXE -> {
					// Synergie Opportunités: +0.05 Proc enchants par sumGrowth
					if (hasEquipped(pets, "morrigan", "cernunnos", "farfadet_r")) total += 0.05 * sumGrowth;
				}
				case KEYS_CHANCE -> {
					// Synergie Opportunités: +0.15 Chance clés par sumGrowth
					if (hasEquipped(pets, "morrigan", "cernunnos", "farfadet_r")) total += 0.15 * sumGrowth;
				}
				default -> {
				}
			}
		}
		
		return total;
	}

	/**
	 * Indique si un pet est équipé par le joueur.
	 */
	public boolean isEquipped(Player player, String petId) {
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		PetData p = pets.get(petId);
		return p != null && p.equipped;
	}

	/**
	 * Donne un pet au joueur. Si déjà possédé, ajoute +1 croissance (max 50). Renvoie true si succès.
	 */
	public boolean grantPet(Player player, String petId) {
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		var def = PetRegistry.get(petId);
		if (def == null) return false;
		PetData pd = pets.get(petId);
		if (pd == null) {
			pd = new PetData();
			pd.id = petId;
			pd.growth = 0;
			pd.xp = 0L;
			pd.equipped = false;
			pets.put(petId, pd);
		} else {
			if (pd.growth < 50) {
				pd.growth += 1; // doublon => croissance +1
			} else {
				// Croissance max: convertir en nourriture (tier selon rareté)
				int foodTier = switch (def.rarity()) {
					case COMMON -> 1;
					case RARE -> 2;
					case EPIC, MYTHIC -> 3;
				};
				ItemStack food = createFoodItem(foodTier, 1);
				HashMap<Integer, ItemStack> left = player.getInventory().addItem(food);
				if (!left.isEmpty()) {
					for (ItemStack is : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), is);
				}
				player.sendMessage("§6🐾 Croissance max atteinte: converti en §eNourriture T" + foodTier + "§6 ✨");
			}
		}
		savePlayerPets(pid, pets);
		return true;
	}

	/**
	 * Ouvre une boîte de pet d'un tier (1..3) et accorde un pet aléatoire en fonction des probabilités de rareté.
	 * Retourne la définition du pet accordé, sinon null.
	 */
	public PetDefinition openPetBox(Player player, int tier) {
		PetRarity target = rollRarityForTier(tier);
		if (target == null) return null;
		// Filtrer les pets de cette rareté
		List<PetDefinition> list = new ArrayList<>();
		for (var def : PetRegistry.all()) if (def.rarity() == target) list.add(def);
		if (list.isEmpty()) return null;
		PetDefinition won = list.get(new Random().nextInt(list.size()));
		if (grantPet(player, won.id())) {
			player.sendMessage("§6🐾 Vous obtenez le compagnon §e" + won.displayName() + "§6 !");
		}
		return won;
	}

	private PetRarity rollRarityForTier(int tier) {
		// Poids par tier (C,R,E,M)
		double[] weights = switch (Math.max(1, Math.min(3, tier))) {
			case 1 -> new double[]{70, 25, 5, 0};
			case 2 -> new double[]{45, 35, 18, 2};
			default -> new double[]{25, 30, 30, 15};
		};
		double r = Math.random() * (weights[0] + weights[1] + weights[2] + weights[3]);
		double acc = 0.0;
		if ((acc += weights[0]) >= r) return PetRarity.COMMON;
		if ((acc += weights[1]) >= r) return PetRarity.RARE;
		if ((acc += weights[2]) >= r) return PetRarity.EPIC;
		return PetRarity.MYTHIC;
	}

	// ====== Items de boîtes de pets ======
	public ItemStack createPetBoxItem(int tier) {
		tier = Math.max(1, Math.min(3, tier));
		ItemStack box = new ItemStack(Material.PLAYER_HEAD);
		// Utilise une jolie tête générique
		box = HeadUtils.createHead(HeadEnum.CHEST_GUI);
		ItemMeta meta = box.getItemMeta();
		if (meta != null) {
			String name = switch (tier) {
				case 1 -> "§a📦 Boîte de Compagnon — §lTier 1";
				case 2 -> "§9📦 Boîte de Compagnon — §lTier 2";
				default -> "§6📦 Boîte de Compagnon — §lTier 3";
			};
			plugin.getGUIManager().applyName(meta, name);
			plugin.getGUIManager().applyLore(meta, List.of(
				"§7Ouvre pour obtenir un compagnon (tier " + tier + ")",
				"§8Clic-droit pour ouvrir"
			));
			PersistentDataContainer pdc = meta.getPersistentDataContainer();
			pdc.set(petBoxTierKey, PersistentDataType.INTEGER, tier);
			box.setItemMeta(meta);
		}
		return box;
	}

	// ====== Nourriture de pets ======
	public ItemStack createFoodItem(int tier, int amount) {
		int clamped = Math.max(1, Math.min(3, tier));
		Material mat = switch (clamped) { case 1 -> Material.SWEET_BERRIES; case 2 -> Material.GLOW_BERRIES; default -> Material.GOLDEN_CARROT; };
		ItemStack food = new ItemStack(mat, Math.max(1, amount));
		ItemMeta meta = food.getItemMeta();
		if (meta != null) {
			String name = switch (clamped) { case 1 -> "§a🍖 Nourriture de compagnon — §lT1"; case 2 -> "§9🍖 Nourriture de compagnon — §lT2"; default -> "§6🍖 Nourriture de compagnon — §lT3"; };
			int xpUnit = getFoodXp(clamped);
			plugin.getGUIManager().applyName(meta, name);
			plugin.getGUIManager().applyLore(meta, List.of(
				"§7Glissez sur un compagnon",
				"§7pour lui donner §e" + xpUnit + "§7 XP"
			));
			meta.getPersistentDataContainer().set(petFoodTierKey, PersistentDataType.INTEGER, clamped);
			food.setItemMeta(meta);
		}
		return food;
	}

	public boolean isPetFood(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return false;
		Integer t = item.getItemMeta().getPersistentDataContainer().get(petFoodTierKey, PersistentDataType.INTEGER);
		return t != null && t >= 1 && t <= 3;
	}

	public int getPetFoodTier(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return 0;
		Integer t = item.getItemMeta().getPersistentDataContainer().get(petFoodTierKey, PersistentDataType.INTEGER);
		return t != null ? t : 0;
	}

	public boolean applyFoodToPet(Player player, String petId, ItemStack foodStack) {
		if (foodStack == null || !isPetFood(foodStack)) return false;
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		PetData pd = pets.get(petId);
		if (pd == null) return false;
		PetDefinition def = PetRegistry.get(petId);
		if (def == null) return false;
		if (pd.growth >= 50) {
			player.sendMessage("§cCe compagnon est déjà au niveau de croissance maximal.");
			return false;
		}
		int tier = getPetFoodTier(foodStack);
		int xpPerLevel = (int) Math.max(1, Math.round(XP_PER_GROWTH * def.rarity().getXpScale()));
		int unitXp = getFoodXp(tier);
		int units = Math.max(1, foodStack.getAmount());
		long totalXpGain = (long) unitXp * (long) units;
		long beforeXp = pd.xp;
		int beforeGrowth = pd.growth;
		pd.xp = Math.max(0, pd.xp) + totalXpGain;
		int leveled = 0;
		while (pd.xp >= xpPerLevel && pd.growth < 50) {
			pd.xp -= xpPerLevel;
			pd.growth += 1;
			leveled++;
		}
		savePlayerPets(pid, pets);
		foodStack.setAmount(0);
		try { player.setItemOnCursor(foodStack); } catch (Throwable ignored) {}
		player.sendMessage("§a🍖 Vous nourrissez §f" + def.displayName() + "§a: §e+" + totalXpGain + " XP §7(§ex" + units + "§7)" + (leveled > 0 ? " §7(§a+" + leveled + " croissance§7)" : ""));
		player.playSound(player.getLocation(), Sound.ENTITY_FOX_EAT, 0.7f, 1.2f);
		return true;
	}

	private int getFoodXp(int tier) {
		return switch (Math.max(1, Math.min(3, tier))) { case 1 -> 25; case 2 -> 75; default -> 200; };
	}

	public boolean isPetBox(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return false;
		PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
		return pdc.has(petBoxTierKey, PersistentDataType.INTEGER);
	}

	public int getPetBoxTier(ItemStack item) {
		if (item == null || !item.hasItemMeta()) return 0;
		PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
		Integer val = pdc.get(petBoxTierKey, PersistentDataType.INTEGER);
		return val != null ? val : 0;
	}

	// ====== Progression ======
	public void gainPetXP(Player player, int xpAmount) {
		if (xpAmount <= 0) return;
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		if (pets.isEmpty()) return;
		
		// Appliquer le bonus de synergie Pet XP (Savoirs)
		double bonusPercent = getPetXpSynergyBonusPercent(player); // en points de %
		if (bonusPercent > 0) {
			xpAmount = (int) Math.max(1, Math.round(xpAmount * (1.0 + (bonusPercent / 100.0))));
		}
		
		boolean changed = false;
		for (PetData pd : pets.values()) {
			if (!pd.equipped) continue;
			PetDefinition def = PetRegistry.get(pd.id);
			if (def == null) continue;
			long beforeXp = pd.xp;
			int beforeGrowth = pd.growth;
			int xpPerLevel = (int) Math.max(1, Math.round(XP_PER_GROWTH * def.rarity().getXpScale()));
			pd.xp = Math.max(0, pd.xp) + xpAmount;
			int gained = 0;
			while (pd.xp >= xpPerLevel && pd.growth < 50) {
				pd.xp -= xpPerLevel;
				pd.growth += 1;
				gained++;
			}
			if (pd.growth >= 50) pd.xp = 0;
			if (gained > 0) {
				changed = true;
				String rarityColor = switch (def.rarity()) { case COMMON->"§f"; case RARE->"§5"; case EPIC->"§d"; case MYTHIC->"§6"; };
				player.sendMessage("§a🐾 Votre compagnon " + rarityColor + def.displayName() + " §aest plus puissant: §e+" + gained + " §7croissance (" + beforeGrowth + "→" + pd.growth + ")");
				player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.8f);
			}
		}
		if (changed) savePlayerPets(pid, pets);
	}
	
	private double getPetXpSynergyBonusPercent(Player player) {
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		int equippedCount = (int) pets.values().stream().filter(p -> p.equipped).count();
		if (equippedCount != 3) return 0.0;
		if (!hasEquipped(pets, "sphinx", "hippogriffe", "kelpie")) return 0.0;
		int sumGrowth = pets.values().stream().filter(p -> p.equipped).mapToInt(p -> p.growth).sum();
		return 0.25 * sumGrowth; // +0.25% par point de croissance au total
	}

	public boolean toggleEquip(Player player, String petId) {
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		PetData pd = pets.get(petId);
		if (pd == null) return false; // non possédé
		if (pd.equipped) {
			pd.equipped = false;
			savePlayerPets(pid, pets);
			refreshVisuals(player);
			player.sendMessage("§c🐾 Vous déséquipez §7" + Optional.ofNullable(PetRegistry.get(petId)).map(PetDefinition::displayName).orElse(petId));
			return true;
		}
		// Vérifie slots disponibles (actuellement 1 slot débloqué)
		int equipped = (int) pets.values().stream().filter(p -> p.equipped).count();
		int unlockedSlots = plugin.getPlayerDataManager().getPlayerData(pid).getUnlockedPetSlots();
		if (equipped >= unlockedSlots) {
			return false;
		}
		pd.equipped = true;
		savePlayerPets(pid, pets);
		refreshVisuals(player);
		player.sendMessage("§a🐾 Vous équipez §f" + Optional.ofNullable(PetRegistry.get(petId)).map(PetDefinition::displayName).orElse(petId));
		// Message synergie si activée
		List<String> synergies = getActiveSynergyNames(player);
		if (!synergies.isEmpty()) {
			player.sendMessage("§d✦ Synergie active: §f" + String.join("§7, §f", synergies));
		}
		return true;
	}

	public boolean isOwned(Player player, String petId) {
		return getPlayerPets(player.getUniqueId()).containsKey(petId);
	}

	// ====== Visuel amélioré (suivi en triangle avec nametags) ======
	private void startVisualTask() {
		if (visualTask != null) return;
		visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				// Ne pas afficher les visuels si le joueur est dans le monde "Cave"
				if ("Cave".equals(player.getWorld().getName())) {
					clearVisuals(player);
					continue;
				}
				
				Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
				boolean hasAnyEquipped = pets.values().stream().anyMatch(p -> p.equipped);
				if (hasAnyEquipped) {
					refreshVisuals(player);
				} else {
					clearVisuals(player);
				}
			}
			cleanupOrphanedStands();
		}, 0L, 5L);
	}

	private void startParticleTask() {
		if (particleTask != null) return;
		particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				// Ne pas afficher les particules si le joueur est dans le monde "Cave"
				if ("Cave".equals(player.getWorld().getName())) {
					continue;
				}
				
				if (hasActiveSynergy(player)) {
					spawnSynergyParticles(player);
				}
			}
		}, 0L, 60L); // Toutes les 3 secondes
	}

	public void refreshVisuals(Player player) {
		// Ne pas afficher les visuels si le joueur est dans le monde "Cave"
		if ("Cave".equals(player.getWorld().getName())) {
			clearVisuals(player);
			return;
		}
		
		List<PetDefinition> equipped = getEquippedPetDefinitions(player);
		if (equipped.isEmpty()) {
			clearVisuals(player);
			return;
		}

		Map<String, PetData> playerPets = getPlayerPets(player.getUniqueId());
		int count = Math.min(equipped.size(), 3);
		
		List<ArmorStand> stands = petStands.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
		List<ArmorStand> nameTags = petNameTags.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

		// Ajuster le nombre de stands
		adjustStandsCount(stands, count, player.getWorld());
		adjustStandsCount(nameTags, count, player.getWorld());

		// Positions triangle vertical isocèle (axé sur l'axe Y). Pas de rotation yaw.
		// Sommet au-dessus, base en dessous, espacement réglable via constantes.
		double[][] relOffsets = {
			{0.0, PET_TRIANGLE_TOP_OFFSET},
			{-PET_TRIANGLE_SIDE_OFFSET, -PET_TRIANGLE_BASE_OFFSET},
			{ PET_TRIANGLE_SIDE_OFFSET, -PET_TRIANGLE_BASE_OFFSET}
		};

		Location base = player.getLocation().clone();
		World world = base.getWorld();
		if (world == null) return;

		// Rotation avec l'orientation du joueur (yaw)
		double yawRad = Math.toRadians(base.getYaw());
		double cos = Math.cos(yawRad);
		double sin = Math.sin(yawRad);

		for (int i = 0; i < count; i++) {
			PetDefinition def = equipped.get(i);
			PetData petData = playerPets.get(def.id());
			
			// Position du pet (x horizontal, y vertical) centrée sur le joueur
			double offsetX = relOffsets[i][0];
			double offsetY = relOffsets[i][1];
			// Tourner autour de l'axe Y selon le yaw du joueur
			double localZ = 0.0;
			double rx = offsetX * cos - localZ * sin;
			double rz = offsetX * sin + localZ * cos;
			Location petLocation = base.clone().add(rx, PET_BASE_HEIGHT + offsetY, rz);
			Location nameTagLocation = petLocation.clone().add(0, 0.6, 0);

			// ArmorStand pour le pet (tête)
			ArmorStand petStand = stands.get(i);
			updatePetStand(petStand, def, petLocation);

			// ArmorStand pour le nametag
			ArmorStand nameTagStand = nameTags.get(i);
			updateNameTagStand(nameTagStand, def, petData, nameTagLocation);
		}
	}

	private void adjustStandsCount(List<ArmorStand> stands, int targetCount, World world) {
		// Supprimer les stands en trop
		while (stands.size() > targetCount) {
			ArmorStand toRemove = stands.remove(stands.size() - 1);
			if (toRemove != null && !toRemove.isDead()) {
				toRemove.remove();
			}
		}

		// Ajouter des stands si nécessaire
		while (stands.size() < targetCount) {
			ArmorStand newStand = world.spawn(new Location(world, 0, 0, 0), ArmorStand.class, as -> {
				as.setMarker(true);
				as.setInvisible(true);
				as.setSmall(true);
				as.setBasePlate(false);
				as.setArms(false);
				as.setGravity(false);
				as.setCustomNameVisible(false); // Sera activé selon le type
				try { as.setCollidable(false); } catch (Throwable ignored) {}
			});
			stands.add(newStand);
		}
	}

	private void updatePetStand(ArmorStand stand, PetDefinition def, Location location) {
		if (stand == null || stand.isDead()) return;
		
		// Mettre la tête du pet
		ItemStack head = getHeadFor(def);
		try {
			if (stand.getEquipment() != null) {
				stand.getEquipment().setHelmet(head);
			}
		} catch (Throwable ignored) {}
		
		stand.teleport(location);
		stand.setCustomNameVisible(false); // Pas de nom sur le pet lui-même
	}

	private void updateNameTagStand(ArmorStand nameTag, PetDefinition def, PetData petData, Location location) {
		if (nameTag == null || nameTag.isDead() || petData == null) return;
		
		// Couleur selon la rareté
		String rarityColor = getRarityColor(def.rarity());
		String petName = rarityColor + def.displayName().replaceAll("§[0-9a-fk-or]", "");
		String growthInfo = "§7[Niv. " + petData.growth + "]";
		
		nameTag.setCustomName(petName + " " + growthInfo);
		nameTag.setCustomNameVisible(true);
		nameTag.teleport(location);
		
		// Retirer l'équipement du nametag (pas de tête)
		try {
			if (nameTag.getEquipment() != null) {
				nameTag.getEquipment().setHelmet(null);
			}
		} catch (Throwable ignored) {}
	}

	public void clearVisuals(Player player) {
		// Nettoyer les pets
		List<ArmorStand> stands = petStands.remove(player.getUniqueId());
		if (stands != null) {
			for (ArmorStand as : stands) {
				if (as != null && !as.isDead()) as.remove();
			}
		}
		
		// Nettoyer les nametags
		List<ArmorStand> nameTags = petNameTags.remove(player.getUniqueId());
		if (nameTags != null) {
			for (ArmorStand as : nameTags) {
				if (as != null && !as.isDead()) as.remove();
			}
		}
	}

	private void cleanupOrphanedStands() {
		Iterator<Map.Entry<UUID, List<ArmorStand>>> it = petStands.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, List<ArmorStand>> e = it.next();
			Player p = Bukkit.getPlayer(e.getKey());
			if (p == null || !p.isOnline()) {
				List<ArmorStand> list = e.getValue();
				if (list != null) {
					for (ArmorStand as : list) {
						if (as != null && !as.isDead()) as.remove();
					}
				}
				it.remove();
			}
		}
		
		// Même chose pour les nametags
		Iterator<Map.Entry<UUID, List<ArmorStand>>> nameIt = petNameTags.entrySet().iterator();
		while (nameIt.hasNext()) {
			Map.Entry<UUID, List<ArmorStand>> e = nameIt.next();
			Player p = Bukkit.getPlayer(e.getKey());
			if (p == null || !p.isOnline()) {
				List<ArmorStand> list = e.getValue();
				if (list != null) {
					for (ArmorStand as : list) {
						if (as != null && !as.isDead()) as.remove();
					}
				}
				nameIt.remove();
			}
		}
	}

	// ====== Système de particules pour les synergies ======
	private boolean hasActiveSynergy(Player player) {
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		List<String> equippedIds = pets.values().stream()
			.filter(pd -> pd.equipped)
			.map(pd -> pd.id)
			.collect(Collectors.toList());

		if (equippedIds.size() != 3) return false;

		for (List<String> synergyPets : SYNERGIES.values()) {
			if (equippedIds.containsAll(synergyPets)) {
				return true;
			}
		}
		return false;
	}

	private void spawnSynergyParticles(Player player) {
		List<ArmorStand> stands = petStands.get(player.getUniqueId());
		if (stands == null || stands.size() != 3) return;

		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		List<String> equippedIds = pets.values().stream()
			.filter(pd -> pd.equipped)
			.map(pd -> pd.id)
			.collect(Collectors.toList());

		// Déterminer le type de synergie pour choisir les particules
		Particle particleType = Particle.HAPPY_VILLAGER; // Par défaut
		
		if (equippedIds.containsAll(SYNERGIES.get("commerce"))) {
			particleType = Particle.HAPPY_VILLAGER; // Jaune/or pour commerce
		} else if (equippedIds.containsAll(SYNERGIES.get("savoirs"))) {
			particleType = Particle.ENCHANT; // Bleu pour savoirs
		} else if (equippedIds.containsAll(SYNERGIES.get("machineries"))) {
			particleType = Particle.SMOKE; // Gris pour machineries
		} else if (equippedIds.containsAll(SYNERGIES.get("richesses"))) {
			particleType = Particle.HEART; // Vert pour richesses
		} else if (equippedIds.containsAll(SYNERGIES.get("opportunites"))) {
			particleType = Particle.PORTAL; // Violet pour opportunités
		}

		// Générer des particules autour de chaque pet (à la hauteur des pets)
		for (ArmorStand stand : stands) {
			if (stand != null && !stand.isDead()) {
				Location loc = stand.getLocation().clone();
				World world = loc.getWorld();
				if (world != null) {
					// Particules en cercle autour du pet
					for (int i = 0; i < 8; i++) {
						double angle = (Math.PI * 2 * i) / 8;
						double x = Math.cos(angle) * 0.5;
						double z = Math.sin(angle) * 0.5;
						Location particleLoc = loc.clone().add(x, 1.0, z);
						
						world.spawnParticle(particleType, particleLoc, 1, 0.1, 0.1, 0.1, 0);
					}
				}
			}
		}
	}

	// ====== Méthodes utilitaires ======
	private String getRarityColor(PetRarity rarity) {
		return switch (rarity) {
			case COMMON -> "§f";
			case RARE -> "§5";
			case EPIC -> "§d";
			case MYTHIC -> "§6";
		};
	}

	private List<PetDefinition> getEquippedPetDefinitions(Player player) {
		Map<String, PetData> map = getPlayerPets(player.getUniqueId());
		if (map.isEmpty()) return Collections.emptyList();
		
		List<String> ids = new ArrayList<>();
		for (Map.Entry<String, PetData> e : map.entrySet()) {
			if (e.getValue().equipped) ids.add(e.getKey());
		}
		
		if (ids.isEmpty()) return Collections.emptyList();
		ids.sort(String::compareToIgnoreCase);
		
		List<PetDefinition> result = new ArrayList<>();
		for (String id : ids) {
			PetDefinition def = PetRegistry.get(id);
			if (def != null) result.add(def);
		}
		return result;
	}

	// ====== Méthodes d'administration/debug ======
	public void forceEquipPet(Player player, String petId) {
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		
		// Créer le pet s'il n'existe pas
		PetData pd = pets.get(petId);
		if (pd == null) {
			if (!grantPet(player, petId)) return;
			pets = getPlayerPets(pid); // Recharger
			pd = pets.get(petId);
		}
		
		if (pd != null) {
			pd.equipped = true;
			savePlayerPets(pid, pets);
			refreshVisuals(player);
		}
	}

	public void setPetGrowth(Player player, String petId, int growth) {
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		PetData pd = pets.get(petId);
		if (pd != null) {
			pd.growth = Math.max(0, Math.min(50, growth));
			savePlayerPets(pid, pets);
		}
	}

	public void setPetXP(Player player, String petId, long xp) {
		UUID pid = player.getUniqueId();
		Map<String, PetData> pets = getPlayerPets(pid);
		PetData pd = pets.get(petId);
		if (pd != null) {
			pd.xp = Math.max(0, xp);
			savePlayerPets(pid, pets);
		}
	}

	// ====== Cleanup ======
	public void shutdown() {
		if (visualTask != null) {
			visualTask.cancel();
			visualTask = null;
		}
		
		if (particleTask != null) {
			particleTask.cancel();
			particleTask = null;
		}
		
		// Nettoyer tous les ArmorStands
		for (List<ArmorStand> stands : petStands.values()) {
			for (ArmorStand as : stands) {
				if (as != null && !as.isDead()) as.remove();
			}
		}
		
		for (List<ArmorStand> nameTags : petNameTags.values()) {
			for (ArmorStand as : nameTags) {
				if (as != null && !as.isDead()) as.remove();
			}
		}
		
		petStands.clear();
		petNameTags.clear();
	}

	// ====== Statistiques et informations ======
	public int getTotalOwnedPets(Player player) {
		return getPlayerPets(player.getUniqueId()).size();
	}

	public int getEquippedPetsCount(Player player) {
		return (int) getPlayerPets(player.getUniqueId()).values().stream()
			.filter(pd -> pd.equipped)
			.count();
	}

	public int getTotalGrowth(Player player) {
		return getPlayerPets(player.getUniqueId()).values().stream()
			.mapToInt(pd -> pd.growth)
			.sum();
	}

	public long getTotalXP(Player player) {
		return getPlayerPets(player.getUniqueId()).values().stream()
			.mapToLong(pd -> pd.xp)
			.sum();
	}

	public Map<PetRarity, Long> getRarityDistribution(Player player) {
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		return pets.values().stream()
			.collect(Collectors.groupingBy(
				pd -> {
					PetDefinition def = PetRegistry.get(pd.id);
					return def != null ? def.rarity() : PetRarity.COMMON;
				},
				Collectors.counting()
			));
	}

	public List<String> getActiveSynergyNames(Player player) {
		Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
		List<String> equippedIds = pets.values().stream()
			.filter(pd -> pd.equipped)
			.map(pd -> pd.id)
			.collect(Collectors.toList());

		List<String> synergies = new ArrayList<>();
		
		if (equippedIds.size() == 3) {
			if (equippedIds.containsAll(SYNERGIES.get("commerce"))) {
				synergies.add("Commerce");
			}
			if (equippedIds.containsAll(SYNERGIES.get("savoirs"))) {
				synergies.add("Savoirs");
			}
			if (equippedIds.containsAll(SYNERGIES.get("machineries"))) {
				synergies.add("Machineries");
			}
			if (equippedIds.containsAll(SYNERGIES.get("richesses"))) {
				synergies.add("Richesses");
			}
			if (equippedIds.containsAll(SYNERGIES.get("opportunites"))) {
				synergies.add("Opportunités");
			}
		}
		
		return synergies;
	}
}