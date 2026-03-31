package pt.tecnico.blockchainist.node.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import pt.tecnico.blockchainist.contract.Block;
import pt.tecnico.blockchainist.contract.Transaction;

/**
 * Local blockchain state maintained by each node.
 * Holds the set of wallets, their balances, and the ordered chain of blocks.
 * Uses a ReadWriteLock so that multiple reads can proceed in parallel while
 * writes (mutations and block additions) are exclusive.
 */
public class NodeState {
    // Wallet ID -> owner user ID (includes the pre-existing 'bc' wallet)
    private final Map<String, String> walletOwners = new HashMap<>();
    
    // Wallet ID -> current balance
    private final Map<String, Long> balances = new HashMap<>();
    
    // Ordered chain of blocks (B.1)
    private final List<Block> blockchain = new ArrayList<>();

    // Allows concurrent reads while writes are exclusive.
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    public NodeState() {
        // Initialize the state with the central bank wallet
        String bcUser = "BC";
        String bcWallet = "bc";
        walletOwners.put(bcWallet, bcUser);
        balances.put(bcWallet, 1000L);
    }

    public void createWallet(String userId, String walletId) {
        rwLock.writeLock().lock();
        try {
            if (walletOwners.containsKey(walletId)) {
                throw new IllegalArgumentException("Wallet already exists: " + walletId);
            }
            walletOwners.put(walletId, userId);
            balances.put(walletId, 0L);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void deleteWallet(String userId, String walletId) {
        rwLock.writeLock().lock();
        try {
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
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public void transfer(String srcUserId, String srcWalletId, String dstWalletId, Long amount) {
        rwLock.writeLock().lock();
        try {
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
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public long readBalance(String walletId) {
        rwLock.readLock().lock();
        try {
            if (!walletOwners.containsKey(walletId)) {
                throw new IllegalArgumentException("Wallet does not exist: " + walletId);
            }
            return balances.get(walletId);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    public void addBlock(Block block) {
        rwLock.writeLock().lock();
        try {
            blockchain.add(block);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    public List<Block> getBlockchain() {
        rwLock.readLock().lock();
        try {
            return new ArrayList<>(blockchain);
        } finally {
            rwLock.readLock().unlock();
        }
    }

    /** Flatten all blocks into a single ordered list of transactions (for debug command B). */
    public List<Transaction> getAllTransactions() {
        rwLock.readLock().lock();
        try {
            List<Transaction> allTransactions = new ArrayList<>();
            for (Block block : blockchain) {
                allTransactions.addAll(block.getTransactionsList());
            }
            return allTransactions;
        } finally {
            rwLock.readLock().unlock();
        }
    }

}
