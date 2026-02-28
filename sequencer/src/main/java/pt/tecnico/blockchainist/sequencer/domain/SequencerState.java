package pt.tecnico.blockchainist.sequencer.domain;

import java.util.ArrayList;
import java.util.List;

import pt.tecnico.blockchainist.contract.Transaction;

public class SequencerState {

    private final List<Transaction> transactions = new ArrayList<>();

    public SequencerState() {
    }

    public synchronized int addTransaction(Transaction transaction) {
        int seqNumber = transactions.size();
        transactions.add(transaction);
        return seqNumber;
    }

    public synchronized Transaction getNextTransaction(int lastSeenSeqNumber) {
        // If the node has seen 'lastSeenSeqNumber' transactions, 
        // the next one is at index 'lastSeenSeqNumber' (since IDs are 1-based and indices 0-based).
        if (lastSeenSeqNumber >= 0 && lastSeenSeqNumber < transactions.size()) {
            return transactions.get(lastSeenSeqNumber);
        }
        return null;
    }
}