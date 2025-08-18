package fr.prisontycoon.pets;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.GlobalBonusManager;
import fr.prisontycoon.utils.HeadEnum;
import fr.prisontycoon.utils.HeadUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Service cœur du système de pets :
 * - stockage/sérialisation dans PlayerData (colonne custom via PlayerDataManager)
 * - gestion croissance/XP et duplicatas
 * - calcul des bonus (en %), puis application via GlobalBonusManager (pour les catégories mappées)
 * - utilitaires GUI (icônes de pets via HeadUtils)
 */
public class PetService {
	private final PrisonTycoon plugin;
	private static final long XP_PER_GROWTH = 100L; // 100 niveaux = +1 croissance
	private final NamespacedKey petBoxTierKey;
	private final Map<UUID, java.util.List<ArmorStand>> petStands = new java.util.concurrent.ConcurrentHashMap<>();
	private BukkitTask visualTask;

	public PetService(PrisonTycoon plugin) {
		this.plugin = plugin;
		this.petBoxTierKey = new NamespacedKey(plugin, "pet_box_tier");
		startVisualTask();
	}

	// ====== Données sérialisables ======
	public static class PetData {
		public String id;       // id dans PetRegistry
		public int growth;      // 0..50
		public long xp;         // xp courante vers prochaine croissance
		public boolean equipped;
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
			default -> {
				// fallback par rareté pour sécurité
				head = switch (pet.rarity()) {
					case COMMON -> HeadEnum.GLOBE;
					case RARE -> HeadEnum.CHEST_GUI;
					case EPIC -> HeadEnum.STAR;
					case MYTHIC -> HeadEnum.ENDER_DRAGON;
				};
			}
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
		for (PetData pd : pets.values()) {
			if (!pd.equipped) continue;
			var def = PetRegistry.get(pd.id);
			if (def == null) continue;
			if (def.effectType() == effectType) {
				total += def.basePerGrowthPercent() * pd.growth;
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
			if (pd.growth < 50) pd.growth += 1; // doublon => croissance +1
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
		java.util.List<PetDefinition> list = new java.util.ArrayList<>();
		for (var def : PetRegistry.all()) if (def.rarity() == target) list.add(def);
		if (list.isEmpty()) return null;
		PetDefinition won = list.get(new java.util.Random().nextInt(list.size()));
		if (grantPet(player, won.id())) {
			player.sendMessage("§6🐾 Vous obtenez le pet §e" + won.displayName() + "§6 !");
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
				case 1 -> "§a📦 Boîte de Pet — §lTier 1";
				case 2 -> "§9📦 Boîte de Pet — §lTier 2";
				default -> "§6📦 Boîte de Pet — §lTier 3";
			};
			plugin.getGUIManager().applyName(meta, name);
			plugin.getGUIManager().applyLore(meta, java.util.List.of(
				"§7Ouvre pour obtenir un compagnon (tier " + tier + ")",
				"§8Clic-droit pour ouvrir"
			));
			PersistentDataContainer pdc = meta.getPersistentDataContainer();
			pdc.set(petBoxTierKey, PersistentDataType.INTEGER, tier);
			box.setItemMeta(meta);
		}
		return box;
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
		boolean changed = false;
		for (PetData pd : pets.values()) {
			if (!pd.equipped) continue;
			long before = pd.xp;
			pd.xp = Math.max(0, pd.xp) + xpAmount;
			while (pd.xp >= XP_PER_GROWTH && pd.growth < 50) {
				pd.xp -= XP_PER_GROWTH;
				pd.growth += 1;
			}
			if (pd.growth >= 50) pd.xp = 0; // croissance max: XP inutile
			if (pd.xp != before) changed = true;
		}
		if (changed) savePlayerPets(pid, pets);
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
		return true;
	}

	public boolean isOwned(Player player, String petId) {
		return getPlayerPets(player.getUniqueId()).containsKey(petId);
	}

	// ====== Visuel (suivi en triangle) ======
	private void startVisualTask() {
		if (visualTask != null) return;
		visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				Map<String, PetData> pets = getPlayerPets(player.getUniqueId());
				boolean hasAnyEquipped = pets.values().stream().anyMatch(p -> p.equipped);
				if (hasAnyEquipped) {
					refreshVisuals(player);
				} else {
					clearVisuals(player);
				}
			}
			// Nettoyage entrées orphelines
			java.util.Iterator<java.util.Map.Entry<UUID, java.util.List<ArmorStand>>> it = petStands.entrySet().iterator();
			while (it.hasNext()) {
				java.util.Map.Entry<UUID, java.util.List<ArmorStand>> e = it.next();
				Player p = Bukkit.getPlayer(e.getKey());
				if (p == null || !p.isOnline()) {
					java.util.List<ArmorStand> list = e.getValue();
					if (list != null) for (ArmorStand as : list) if (as != null && !as.isDead()) as.remove();
					it.remove();
				}
			}
		}, 0L, 10L);
	}

	public void refreshVisuals(Player player) {
		java.util.List<PetDefinition> equipped = getEquippedPetDefinitions(player);
		if (equipped.isEmpty()) {
			clearVisuals(player);
			return;
		}

		int count = Math.min(equipped.size(), 3);
		java.util.List<ArmorStand> stands = petStands.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

		// Ajuste le nombre de stands
		if (stands.size() > count) {
			for (int i = stands.size() - 1; i >= count; i--) {
				ArmorStand as = stands.remove(i);
				if (as != null && !as.isDead()) as.remove();
			}
		}

		// Spécification formation triangle (dx, dz)
		double[][] relOffsets = new double[][]{
			{0.0, 0.6},
			{-0.35, -0.2},
			{0.35, -0.2}
		};

		Location base = player.getLocation().clone();
		World world = base.getWorld();
		if (world == null) return;
		double yawRad = Math.toRadians(base.getYaw());
		double cos = Math.cos(yawRad);
		double sin = Math.sin(yawRad);

		for (int i = 0; i < count; i++) {
			ArmorStand stand = (i < stands.size()) ? stands.get(i) : null;
			if (stand == null || stand.isDead() || !stand.isValid()) {
				Location spawnLoc = base.clone().add(0, 1.8, 0);
				stand = world.spawn(spawnLoc, ArmorStand.class, as -> {
					as.setMarker(true);
					as.setInvisible(true);
					as.setSmall(true);
					as.setBasePlate(false);
					as.setArms(false);
					as.setGravity(false);
					try { as.setCollidable(false); } catch (Throwable ignored) {}
				});
				if (i < stands.size()) stands.set(i, stand); else stands.add(stand);
			}

			// Met à jour l'apparence (tête)
			PetDefinition def = equipped.get(i);
			ItemStack head = getHeadFor(def);
			try {
				if (stand.getEquipment() != null) {
					stand.getEquipment().setHelmet(head);
				}
			} catch (Throwable ignored) {}

			// Calcule la position ciblée en triangle, en fonction de l'orientation
			double dx = relOffsets[i][0];
			double dz = relOffsets[i][1];
			double rx = dx * cos - dz * sin;
			double rz = dx * sin + dz * cos;

			Location target = base.clone().add(rx, 1.8, rz);
			stand.teleport(target);
		}
	}

	public void clearVisuals(Player player) {
		java.util.List<ArmorStand> list = petStands.remove(player.getUniqueId());
		if (list != null) {
			for (ArmorStand as : list) {
				if (as != null && !as.isDead()) as.remove();
			}
		}
	}

	private java.util.List<PetDefinition> getEquippedPetDefinitions(Player player) {
		Map<String, PetData> map = getPlayerPets(player.getUniqueId());
		if (map.isEmpty()) return java.util.Collections.emptyList();
		java.util.List<String> ids = new ArrayList<>();
		for (Map.Entry<String, PetData> e : map.entrySet()) if (e.getValue().equipped) ids.add(e.getKey());
		if (ids.isEmpty()) return java.util.Collections.emptyList();
		ids.sort(String::compareToIgnoreCase);
		java.util.List<PetDefinition> result = new ArrayList<>();
		for (String id : ids) {
			PetDefinition def = PetRegistry.get(id);
			if (def != null) result.add(def);
		}
		return result;
	}
}


