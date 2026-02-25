package pt.tecnico.blockchainist.node.domain;


public class NodeState {
    
    // TODO Declare state maintained by each node
    // - The set of wallets, indexed by their identifiers, and their owner user identifiers (including the 'bc' wallet)
    // - The balance of each wallet
    // - The transaction ledger (up to A.2, a chain of individual transactions; after B.1, a chain of blocks)

    public NodeState() {
        // TODO
    }

    private void createWallet(String userId, String walletId) {
        // TODO
    }

    private void deleteWallet(String userId, String walletId) {
        // TODO
    }

    private void transfer(String srcUserId, String srcWalletId, String dstWalletId, Long amount) {
        // TODO
    }

    public long readBalance(String walletId) {
        // TODO
        return -1L;
    }

    // TODO Add other operations (e.g., getBlockchainState)

}
