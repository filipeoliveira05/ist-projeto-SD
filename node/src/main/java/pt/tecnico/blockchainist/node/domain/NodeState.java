package pt.tecnico.blockchainist.node.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.tecnico.blockchainist.contract.Transaction;

public class NodeState {
    
    // - The set of wallets, indexed by their identifiers, and their owner user identifiers (including the 'bc' wallet)
    private final Map<String, String> walletOwners = new HashMap<>();
    // - The balance of each wallet
    private final Map<String, Long> balances = new HashMap<>();
    
    // - The transaction ledger (up to A.2, a chain of individual transactions; after B.1, a chain of blocks)
    private final List<Transaction> blockchain = new ArrayList<>();

    public NodeState() {
        // Initialize the state with the central bank wallet
        String bcUser = "BC";
        String bcWallet = "bc";
        walletOwners.put(bcWallet, bcUser);
        balances.put(bcWallet, 1000L);
    }

    public synchronized void createWallet(String userId, String walletId) {
        if (walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet already exists: " + walletId);
        }
        walletOwners.put(walletId, userId);
        balances.put(walletId, 0L);
    }

    public synchronized void deleteWallet(String userId, String walletId) {
        if (!walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet does not exist: " + walletId);
        }
        if (!walletOwners.get(walletId).equals(userId)) {
            throw new IllegalArgumentException("User " + userId + " does not own wallet " + walletId);
        }
        if (balances.get(walletId) != 0) {
            throw new IllegalArgumentException("Wallet " + walletId + " has non-zero balance");
        }
        walletOwners.remove(walletId);
        balances.remove(walletId);
    }

    public synchronized void transfer(String srcUserId, String srcWalletId, String dstWalletId, Long amount) {
        if (!walletOwners.containsKey(srcWalletId)) {
            throw new IllegalArgumentException("Source wallet does not exist: " + srcWalletId);
        }
        if (!walletOwners.containsKey(dstWalletId)) {
            throw new IllegalArgumentException("Destination wallet does not exist: " + dstWalletId);
        }
        if (!walletOwners.get(srcWalletId).equals(srcUserId)) {
            throw new IllegalArgumentException("User " + srcUserId + " does not own source wallet " + srcWalletId);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        long currentBalance = balances.get(srcWalletId);
        if (currentBalance < amount) {
            throw new IllegalArgumentException("Insufficient balance");
        }
        
        balances.put(srcWalletId, currentBalance - amount);
        balances.put(dstWalletId, balances.get(dstWalletId) + amount);
    }

    public synchronized long readBalance(String walletId) {
        if (!walletOwners.containsKey(walletId)) {
            throw new IllegalArgumentException("Wallet does not exist: " + walletId);
        }
        return balances.get(walletId);
    }

    public synchronized void addTransaction(Transaction transaction) {
        blockchain.add(transaction);
    }

    public synchronized List<Transaction> getBlockchain() {
        return new ArrayList<>(blockchain);
    }

}
