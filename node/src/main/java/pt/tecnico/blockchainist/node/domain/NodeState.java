package pt.tecnico.blockchainist.node.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.Transaction;

/**
 * Local blockchain state maintained by each node.
 * Holds the set of wallets, their balances, and the ordered chain of blocks.
 * All mutating methods are synchronized to ensure thread safety between
 * the gRPC handler threads and the block-polling thread.
 */
public class NodeState {
    // Wallet ID -> owner user ID (includes the pre-existing 'bc' wallet)
    private final Map<String, String> walletOwners = new HashMap<>();
    
    // Wallet ID -> current balance
    private final Map<String, Long> balances = new HashMap<>();
    
    // Ordered chain of blocks (B.1)
    private final List<Block> blockchain = new ArrayList<>();

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

    public synchronized void addBlock(Block block) {
        blockchain.add(block);
    }

    public synchronized List<Block> getBlockchain() {
        return new ArrayList<>(blockchain);
    }

    /** Flatten all blocks into a single ordered list of transactions (for debug command B). */
    public synchronized List<Transaction> getAllTransactions() {
        List<Transaction> allTransactions = new ArrayList<>();
        for (Block block : blockchain) {
            allTransactions.addAll(block.getTransactionsList());
        }
        return allTransactions;
    }

}
