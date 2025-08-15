package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.ChatColor;
import org.bukkit.conversations.*;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {

    private final CustomSkyblock plugin;
    private final Map<UUID, Double> playerBalances = new ConcurrentHashMap<>();
    private ConversationFactory conversationFactory;

    public EconomyManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        setupConversations();
        loadBalances();
    }

    private void setupConversations() {
        conversationFactory = new ConversationFactory(plugin)
                .withModality(true)
                .withTimeout(30)
                .withFirstPrompt(new AmountPrompt())
                .withEscapeSequence("cancel")
                .addConversationAbandonedListener(event -> {
                    if (event.getContext().getForWhom() instanceof Player player) {
                        if (!event.gracefulExit()) {
                            player.sendMessage(ChatColor.RED + "Transaction annulée.");
                        }
                    }
                });
    }

    // === GESTION DES BALANCES JOUEURS ===

    public double getBalance(UUID playerUuid) {
        return plugin.getPrisonTycoonHook().getCoins(playerUuid);
    }

    public void setBalance(UUID playerUuid, double amount) {
        playerBalances.put(playerUuid, Math.max(0, amount));
        saveBalance(playerUuid);
    }

    public void addBalance(UUID playerUuid, double amount) {
        plugin.getPrisonTycoonHook().addCoins(playerUuid, Math.round(amount));
    }

    public boolean removeBalance(UUID playerUuid, double amount) {
        return plugin.getPrisonTycoonHook().hasCoins(playerUuid, Math.round(amount));
    }

    public boolean hasBalance(UUID playerUuid, double amount) {
        return plugin.getPrisonTycoonHook().hasCoins(playerUuid, Math.round(amount));
    }

    // === GESTION DES TRANSACTIONS BANCAIRES ===

    public void startBankDeposit(Player player, Island island) {
        if (!island.isMember(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas membre de cette île !");
            return;
        }

        double playerBalance = getBalance(player.getUniqueId());
        if (playerBalance <= 0) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas d'argent à déposer !");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Combien voulez-vous déposer dans la banque de l'île ?");
        player.sendMessage(ChatColor.GRAY + "Votre solde: " + ChatColor.WHITE + String.format("%.2f $", playerBalance));
        player.sendMessage(ChatColor.GRAY + "Tapez un montant ou 'cancel' pour annuler.");

        Conversation conversation = conversationFactory.buildConversation(player);
        conversation.getContext().setSessionData("action", "deposit");
        conversation.getContext().setSessionData("island", island);
        conversation.getContext().setSessionData("player", player);
        conversation.begin();
    }

    public void startBankWithdrawal(Player player, Island island) {
        if (!island.isMember(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas membre de cette île !");
            return;
        }

        double islandBank = island.getBank();
        if (islandBank <= 0) {
            player.sendMessage(ChatColor.RED + "La banque de l'île est vide !");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Combien voulez-vous retirer de la banque de l'île ?");
        player.sendMessage(ChatColor.GRAY + "Banque de l'île: " + ChatColor.WHITE + String.format("%.2f $", islandBank));
        player.sendMessage(ChatColor.GRAY + "Tapez un montant ou 'cancel' pour annuler.");

        Conversation conversation = conversationFactory.buildConversation(player);
        conversation.getContext().setSessionData("action", "withdraw");
        conversation.getContext().setSessionData("island", island);
        conversation.getContext().setSessionData("player", player);
        conversation.begin();
    }

    // === SYSTÈME DE RÉCOMPENSES ===

    public void rewardPlayer(UUID playerUuid, double amount, String reason) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null) {
            plugin.getPrisonTycoonHook().addCoins(player.getUniqueId(), Math.round(amount));
        } else {
            plugin.getLogger().info("Joueur " + playerUuid + " aurait reçu " + amount + "$ pour: " + reason);
        }
    }

    public void rewardIsland(Island island, double amount, String reason) {
        island.addToBank(amount);
        plugin.getDatabaseManager().saveIsland(island);

        // Notifier tous les membres en ligne
        notifyIslandMembers(island, ChatColor.GREEN + "L'île a reçu " + ChatColor.GOLD +
                String.format("%.2f $", amount) + ChatColor.GREEN + " ! Raison: " + ChatColor.WHITE + reason);

        plugin.getLogger().info("Île " + island.getId() + " a reçu " + amount + "$ pour: " + reason);
    }

    // === SYSTÈME D'ACHAT/VENTE ===

    public boolean purchaseIslandUpgrade(Player player, Island island, String upgradeType, double cost) {
        if (!island.isMember(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas membre de cette île !");
            return false;
        }

        if (!island.removeFromBank(cost)) {
            player.sendMessage(ChatColor.RED + "Fonds insuffisants dans la banque de l'île !");
            player.sendMessage(ChatColor.GRAY + "Coût: " + ChatColor.WHITE + String.format("%.2f $", cost));
            player.sendMessage(ChatColor.GRAY + "Banque: " + ChatColor.WHITE + String.format("%.2f $", island.getBank()));
            return false;
        }

        plugin.getDatabaseManager().saveIsland(island);

        // Log de la transaction
        plugin.getLogger().info("Achat d'amélioration '" + upgradeType + "' pour l'île " + island.getId() +
                " par " + player.getName() + " coût: " + cost + "$");

        notifyIslandMembers(island, ChatColor.GREEN + player.getName() + " a acheté: " + ChatColor.YELLOW +
                upgradeType + ChatColor.GREEN + " pour " + ChatColor.GOLD + String.format("%.2f $", cost));

        return true;
    }

    public boolean transferMoney(UUID fromPlayer, UUID toPlayer, double amount) {
        return plugin.getPrisonTycoonHook().transferCoins(fromPlayer, toPlayer, Math.round(amount));
    }

    // === SYSTÈME DE SALAIRE PASSIF ===

    public void processIslandIncome() {
        for (Island island : plugin.getDatabaseManager().getAllIslands()) {
            double income = calculateIslandIncome(island);
            if (income > 0) {
                island.addToBank(income);
                plugin.getDatabaseManager().saveIsland(island);

                // Notifier le propriétaire s'il est en ligne
                Player owner = plugin.getServer().getPlayer(island.getOwner());
                if (owner != null && owner.isOnline()) {
                    owner.sendMessage(ChatColor.GREEN + "Votre île a généré " + ChatColor.GOLD +
                            String.format("%.2f $", income) + ChatColor.GREEN + " de revenus passifs !");
                }
            }
        }
    }

    private double calculateIslandIncome(Island island) {
        // Revenus basés sur le niveau de l'île
        double baseIncome = island.getLevel() * 0.5;

        // Bonus basé sur la taille
        double sizeMultiplier = 1.0 + (island.getSize() - 50) * 0.001;

        // Bonus d'activité récente
        long daysSinceActivity = (System.currentTimeMillis() - island.getLastActivity()) / (24 * 60 * 60 * 1000);
        double activityMultiplier = daysSinceActivity <= 1 ? 1.5 : (daysSinceActivity <= 7 ? 1.0 : 0.5);

        return baseIncome * sizeMultiplier * activityMultiplier;
    }

    // === SAUVEGARDE ET CHARGEMENT ===

    private void loadBalances() {
        // Charger les balances depuis la base de données YAML
        for (SkyblockPlayer skyblockPlayer : plugin.getDatabaseManager().getAllPlayers()) {
            if (skyblockPlayer.hasData("balance")) {
                double balance = skyblockPlayer.getData("balance", Double.class);
                playerBalances.put(skyblockPlayer.getUuid(), balance);
            }
        }
        plugin.getLogger().info("Balances de " + playerBalances.size() + " joueurs chargées.");
    }

    private void saveBalance(UUID playerUuid) {
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(playerUuid);
        if (skyblockPlayer != null) {
            skyblockPlayer.setData("balance", getBalance(playerUuid));
            plugin.getDatabaseManager().savePlayer(skyblockPlayer);
        }
    }

    public void saveAllBalances() {
        for (Map.Entry<UUID, Double> entry : playerBalances.entrySet()) {
            saveBalance(entry.getKey());
        }
        plugin.getLogger().info("Toutes les balances sauvegardées.");
    }

    // === MÉTHODES UTILITAIRES ===

    private void notifyIslandMembers(Island island, String message) {
        // Notifier le propriétaire
        Player owner = plugin.getServer().getPlayer(island.getOwner());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(message);
        }

        // Notifier tous les membres
        for (UUID memberUuid : island.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberUuid);
            if (member != null && member.isOnline()) {
                member.sendMessage(message);
            }
        }
    }

    public String formatMoney(double amount) {
        if (amount >= 1000000) {
            return String.format("%.1fM $", amount / 1000000);
        } else if (amount >= 1000) {
            return String.format("%.1fK $", amount / 1000);
        } else {
            return String.format("%.2f $", amount);
        }
    }

    // === CLASSE INTERNE POUR LES CONVERSATIONS ===

    private class AmountPrompt extends NumericPrompt {
        @Override
        public String getPromptText(ConversationContext context) {
            return ChatColor.YELLOW + "Entrez le montant:";
        }

        @Override
        protected Prompt acceptValidatedInput(ConversationContext context, Number input) {
            double amount = input.doubleValue();
            String action = (String) context.getSessionData("action");
            Island island = (Island) context.getSessionData("island");
            Player player = (Player) context.getSessionData("player");

            if (amount <= 0) {
                player.sendMessage(ChatColor.RED + "Le montant doit être positif !");
                return END_OF_CONVERSATION;
            }

            if ("deposit".equals(action)) {
                return handleDeposit(player, island, amount);
            } else if ("withdraw".equals(action)) {
                return handleWithdrawal(player, island, amount);
            }

            return END_OF_CONVERSATION;
        }

        private Prompt handleDeposit(Player player, Island island, double amount) {
            if (!hasBalance(player.getUniqueId(), amount)) {
                player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent !");
                player.sendMessage(ChatColor.GRAY + "Votre solde: " + ChatColor.WHITE +
                        formatMoney(getBalance(player.getUniqueId())));
                return END_OF_CONVERSATION;
            }

            removeBalance(player.getUniqueId(), amount);
            island.addToBank(amount);
            plugin.getDatabaseManager().saveIsland(island);

            player.sendMessage(ChatColor.GREEN + "Vous avez déposé " + ChatColor.GOLD +
                    formatMoney(amount) + ChatColor.GREEN + " dans la banque de l'île !");
            player.sendMessage(ChatColor.GRAY + "Nouveau solde banque: " + ChatColor.WHITE +
                    formatMoney(island.getBank()));

            // Notifier les autres membres
            notifyIslandMembers(island, ChatColor.AQUA + player.getName() + ChatColor.GREEN +
                    " a déposé " + ChatColor.GOLD + formatMoney(amount) +
                    ChatColor.GREEN + " dans la banque de l'île !");

            return END_OF_CONVERSATION;
        }

        private Prompt handleWithdrawal(Player player, Island island, double amount) {
            if (!island.removeFromBank(amount)) {
                player.sendMessage(ChatColor.RED + "Fonds insuffisants dans la banque de l'île !");
                player.sendMessage(ChatColor.GRAY + "Banque de l'île: " + ChatColor.WHITE +
                        formatMoney(island.getBank()));
                return END_OF_CONVERSATION;
            }

            addBalance(player.getUniqueId(), amount);
            plugin.getDatabaseManager().saveIsland(island);

            player.sendMessage(ChatColor.GREEN + "Vous avez retiré " + ChatColor.GOLD +
                    formatMoney(amount) + ChatColor.GREEN + " de la banque de l'île !");
            player.sendMessage(ChatColor.GRAY + "Nouveau solde banque: " + ChatColor.WHITE +
                    formatMoney(island.getBank()));

            // Notifier les autres membres
            notifyIslandMembers(island, ChatColor.AQUA + player.getName() + ChatColor.YELLOW +
                    " a retiré " + ChatColor.GOLD + formatMoney(amount) +
                    ChatColor.YELLOW + " de la banque de l'île !");

            return END_OF_CONVERSATION;
        }

        @Override
        protected String getFailedValidationText(ConversationContext context, String invalidInput) {
            return ChatColor.RED + "'" + invalidInput + "' n'est pas un nombre valide ! Réessayez:";
        }
    }

    // === COMMANDES D'ÉCONOMIE ===

    public void handleBalanceCommand(Player player) {
        plugin.getPrisonTycoonHook().showPlayerEconomy(player);
    }

    public void handlePayCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /pay <joueur> <montant>");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous envoyer de l'argent à vous-même !");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Montant invalide !");
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "Le montant doit être positif !");
            return;
        }

        if (transferMoney(player.getUniqueId(), target.getUniqueId(), amount)) {
            player.sendMessage(ChatColor.GREEN + "Transaction réussie !");
        } else {
            player.sendMessage(ChatColor.RED + "Fonds insuffisants !");
        }
    }
}