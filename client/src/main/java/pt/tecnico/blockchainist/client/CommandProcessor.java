package pt.tecnico.blockchainist.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.blockchainist.client.grpc.ClientNodeService;
import pt.tecnico.blockchainist.contract.CreateWalletResponse;
import pt.tecnico.blockchainist.contract.DeleteWalletResponse;
import pt.tecnico.blockchainist.contract.ReadBalanceResponse;
import pt.tecnico.blockchainist.contract.TransferResponse;

/**
 * Reads user commands from stdin and dispatches them to blockchain nodes.
 * Supports both blocking (C/E/S/T) and async (c/e/t/s) command variants.
 * Implements round-robin retry across known nodes on suspected failures (B.2).
 */
public class CommandProcessor {

    private static final String SPACE = " ";

    // Blocking command identifiers
    private static final String CREATE_BLOCKING = "C";
    private static final String CREATE_ASYNC = "c";
    private static final String DELETE_BLOCKING = "E";
    private static final String DELETE_ASYNC = "e";
    private static final String BALANCE_BLOCKING = "S";
    private static final String BALANCE_ASYNC = "s";
    private static final String TRANSFER_BLOCKING = "T";
    private static final String TRANSFER_ASYNC = "t";
    private static final String DEBUG_BLOCKCHAIN_STATE = "B";
    private static final String PAUSE = "P";
    private static final String EXIT = "X";

    // IDs must be ASCII alphanumeric only
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    // Maximum number of causal dependencies to track (older ones are pruned).
    private static final int MAX_CAUSAL_CONTEXT_SIZE = 50;

    // Sequential command counter shared across blocking and async commands
    private final AtomicLong commandCounter = new AtomicLong(0);
    private final ArrayList<ClientNodeService> nodes;

    // C.1: Tracks requestIds of completed transactions for causal dependency tracking.
    // Transfers include this set as causal dependencies so that the node can enforce
    // causal ordering before speculative execution.
    private final Set<String> causalContext = new LinkedHashSet<>();

    public CommandProcessor(ArrayList<ClientNodeService> nodes) {
        this.nodes = nodes;
    }

    /** Generate a unique request ID for idempotent transaction retries (B.2). */
    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    /** C.1: Add a requestId to the causal context, pruning oldest entries if needed. */
    private void addToCausalContext(String requestId) {
        causalContext.add(requestId);
        while (causalContext.size() > MAX_CAUSAL_CONTEXT_SIZE) {
            causalContext.iterator().next();
            causalContext.remove(causalContext.iterator().next());
        }
    }

    /** C.1: Snapshot the current causal context for inclusion in a transfer request. */
    private List<String> snapshotCausalContext() {
        return new ArrayList<>(causalContext);
    }

    /** Functional interface for a blocking RPC call that can be retried on another node. */
    @FunctionalInterface
    private interface RetryableNodeCall<T> {
        T execute(ClientNodeService node) throws StatusRuntimeException;
    }

    /** Functional interface for an async RPC call that can be retried on another node. */
    @FunctionalInterface
    private interface RetryableAsyncNodeCall<T> {
        void execute(ClientNodeService node, StreamObserver<T> observer);
    }

    /** UNAVAILABLE or DEADLINE_EXCEEDED indicate the node may have crashed (B.2). */
    private boolean isSuspectedNodeFailure(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
    }

    private StatusRuntimeException toStatusRuntimeException(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            return (StatusRuntimeException) throwable;
        }
        return Status.fromThrowable(throwable).asRuntimeException();
    }

    private String describeStatus(StatusRuntimeException e) {
        String description = e.getStatus().getDescription();
        return (description == null || description.isBlank())
                ? e.getStatus().getCode().name()
                : description;
    }

    private void printCommandError(long commandNumber, StatusRuntimeException e) {
        synchronized (System.out) {
            System.out.println();
            System.err.println(commandNumber + " " + describeStatus(e));
        }
    }

    /**
     * Blocking round-robin retry: try the initial node, then cycle through
     * remaining nodes on suspected failures. Application-level errors (e.g.
     * ALREADY_EXISTS) are thrown immediately without retry.
     */
    private <T> T invokeWithRoundRobinRetry(int initialNodeIndex, RetryableNodeCall<T> call) throws StatusRuntimeException {
        int totalNodes = nodes.size();
        for (int attempt = 0; attempt < totalNodes; attempt++) {
            int currentNodeIndex = (initialNodeIndex + attempt) % totalNodes;
            try {
                return call.execute(nodes.get(currentNodeIndex));
            } catch (StatusRuntimeException e) {
                boolean lastAttempt = attempt == totalNodes - 1;
                if (!isSuspectedNodeFailure(e) || lastAttempt) {
                    throw e;
                }
            }
        }
        throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription("All nodes unavailable"));
    }

    /**
     * Async round-robin retry: on suspected node failure, the StreamObserver's
     * onError callback re-issues the call to the next node in the list.
     */
    private <T> void invokeAsyncWithRoundRobinRetry(
            int initialNodeIndex,
            long commandNumber,
            RetryableAsyncNodeCall<T> call,
            Consumer<T> onSuccess) {
        invokeAsyncWithRoundRobinRetry(initialNodeIndex, 0, commandNumber, call, onSuccess);
    }

    /** Recursive helper for async retry; each attempt increments the node index. */
    private <T> void invokeAsyncWithRoundRobinRetry(
            int initialNodeIndex,
            int attempt,
            long commandNumber,
            RetryableAsyncNodeCall<T> call,
            Consumer<T> onSuccess) {
        int totalNodes = nodes.size();
        int currentNodeIndex = (initialNodeIndex + attempt) % totalNodes;

        call.execute(nodes.get(currentNodeIndex), new StreamObserver<T>() {
            @Override
            public void onNext(T value) {
                onSuccess.accept(value);
            }

            @Override
            public void onError(Throwable throwable) {
                StatusRuntimeException e = toStatusRuntimeException(throwable);
                boolean lastAttempt = attempt == totalNodes - 1;
                if (!lastAttempt && isSuspectedNodeFailure(e)) {
                    invokeAsyncWithRoundRobinRetry(initialNodeIndex, attempt + 1, commandNumber, call, onSuccess);
                } else {
                    printCommandError(commandNumber, e);
                }
            }

            @Override
            public void onCompleted() {
            }
        });
    }

    private void shutdownNodes() {
        for (ClientNodeService node : nodes) {
            try {
                node.shutdown();
            } catch (Exception ignored) {
                // Best-effort shutdown to avoid lingering gRPC threads on client exit.
            }
        }
    }

    void userInputLoop() {
        boolean exit = false;

        System.out.println();

        try (Scanner scanner = new Scanner(System.in)) {
            while (!exit) {
                System.out.print("> ");
                String line = scanner.nextLine().trim();

                if (line.isEmpty()) {
                    System.out.println();
                    continue;
                }

                String[] split = line.split(SPACE);
                try {
                    switch (split[0]) {
                        case CREATE_BLOCKING:
                            this.create(split, true);
                            break;

                        case CREATE_ASYNC:
                            this.create(split, false);
                            break;

                        case DELETE_BLOCKING:
                            this.delete(split, true);
                            break;

                        case DELETE_ASYNC:
                            this.delete(split, false);
                            break;

                        case BALANCE_BLOCKING:
                            this.balance(split, true);
                            break;

                        case BALANCE_ASYNC:
                            this.balance(split, false);
                            break;

                        case TRANSFER_BLOCKING:
                            this.transfer(split, true);
                            break;

                        case TRANSFER_ASYNC:
                            this.transfer(split, false);
                            break;

                        case DEBUG_BLOCKCHAIN_STATE:
                            this.debugBlockchainState(split);
                            break;

                        case PAUSE:
                            this.pause(split);
                            break;

                        case EXIT:
                            exit = true;
                            break;

                        default:
                            printUsage();
                            break;
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: " + e.getMessage());
                    printUsage();
                }
            }
        } finally {
            shutdownNodes();
        }
    }

    private void create(String[] split, boolean isBlocking) {
        this.checkCreateCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);
        String requestId = newRequestId();

        if (isBlocking) {
            try {
                var response = invokeWithRoundRobinRetry(
                        nodeIndex,
                        node -> node.createWallet(userId, walletId, requestId, nodeDelay));
                // C.1: Track successful create in causal context.
                addToCausalContext(requestId);
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response);
                }
            } catch (StatusRuntimeException e) {
                printCommandError(commandNumber, e);
            }
        } else {
            // C.1: Add to causal context before sending (async ordering guarantee).
            addToCausalContext(requestId);
            this.<CreateWalletResponse>invokeAsyncWithRoundRobinRetry(
                    nodeIndex,
                    commandNumber,
                    (node, observer) -> node.createWalletAsync(userId, walletId, requestId, nodeDelay, observer),
                    (CreateWalletResponse response) -> {
                        synchronized (System.out) {
                            System.out.println("OK " + commandNumber);
                            System.out.println(response);
                        }
                    });
        }
    }

    private void delete(String[] split, boolean isBlocking) {
        this.checkDeleteCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String userId = split[1];
        String walletId = split[2];
        Integer nodeIndex = Integer.parseInt(split[3]);
        Integer nodeDelay = Integer.parseInt(split[4]);
        String requestId = newRequestId();

        if (isBlocking) {
            try {
                var response = invokeWithRoundRobinRetry(
                        nodeIndex,
                        node -> node.deleteWallet(userId, walletId, requestId, nodeDelay));
                // C.1: Track successful delete in causal context.
                addToCausalContext(requestId);
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response);
                }
            } catch (StatusRuntimeException e) {
                printCommandError(commandNumber, e);
            }
        } else {
            // C.1: Add to causal context before sending (async ordering guarantee).
            addToCausalContext(requestId);
            this.<DeleteWalletResponse>invokeAsyncWithRoundRobinRetry(
                    nodeIndex,
                    commandNumber,
                    (node, observer) -> node.deleteWalletAsync(userId, walletId, requestId, nodeDelay, observer),
                    (DeleteWalletResponse response) -> {
                        synchronized (System.out) {
                            System.out.println("OK " + commandNumber);
                            System.out.println(response);
                        }
                    });
        }
    }

    private void balance(String[] split, boolean isBlocking) {
        this.checkBalanceCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String walletId = split[1];
        Integer nodeIndex = Integer.parseInt(split[2]);
        Integer nodeDelay = Integer.parseInt(split[3]);

        if (isBlocking) {
            try {
                var response = invokeWithRoundRobinRetry(
                        nodeIndex,
                        node -> node.readBalance(walletId, nodeDelay));
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response.getBalance());
                    System.out.println();
                }
            } catch (StatusRuntimeException e) {
                printCommandError(commandNumber, e);
            }
        } else {
            this.<ReadBalanceResponse>invokeAsyncWithRoundRobinRetry(
                    nodeIndex,
                    commandNumber,
                    (node, observer) -> node.readBalanceAsync(walletId, nodeDelay, observer),
                    (ReadBalanceResponse response) -> {
                        synchronized (System.out) {
                            System.out.println("OK " + commandNumber);
                            System.out.println(response.getBalance());
                            System.out.println();
                        }
                    });
        }
    }

    private void transfer(String[] split, boolean isBlocking) {
        this.checkTransferCommandArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        String sourceUserId = split[1];
        String sourceWalletId = split[2];
        String destinationWalletId = split[3];
        Long amount = Long.parseLong(split[4]);
        Integer nodeIndex = Integer.parseInt(split[5]);
        Integer nodeDelay = Integer.parseInt(split[6]);
        String requestId = newRequestId();

        // C.1: Capture causal dependencies before sending the transfer.
        List<String> deps = snapshotCausalContext();

        if (isBlocking) {
            try {
                var response = invokeWithRoundRobinRetry(
                        nodeIndex,
                        node -> node.transfer(sourceUserId, sourceWalletId, destinationWalletId, amount, requestId, deps, nodeDelay));
                // C.1: Track successful transfer in causal context.
                addToCausalContext(requestId);
                synchronized (System.out) {
                    System.out.println("OK " + commandNumber);
                    System.out.println(response);
                }
            } catch (StatusRuntimeException e) {
                printCommandError(commandNumber, e);
            }
        } else {
            // C.1: Add to causal context before sending (async ordering guarantee).
            addToCausalContext(requestId);
            this.<TransferResponse>invokeAsyncWithRoundRobinRetry(
                    nodeIndex,
                    commandNumber,
                    (node, observer) -> node.transferAsync(sourceUserId, sourceWalletId, destinationWalletId, amount, requestId, deps, nodeDelay, observer),
                    (TransferResponse response) -> {
                        synchronized (System.out) {
                            System.out.println("OK " + commandNumber);
                            System.out.println(response);
                        }
                    });
        }
    }

    private void debugBlockchainState(String[] split) {
        this.checkDebugBlockchainStateArgs(split);

        Long commandNumber = this.commandCounter.incrementAndGet();

        Integer nodeIndex = Integer.parseInt(split[1]);

        try {
            var response = invokeWithRoundRobinRetry(
                    nodeIndex,
                    ClientNodeService::getBlockchainState);
            System.out.println("OK " + commandNumber);
            System.out.println(response);
        } catch (StatusRuntimeException e) {
            printCommandError(commandNumber, e);
        }
    }

    private void pause(String[] split) {
        this.checkPauseArgs(split);

        Integer time;

        time = Integer.parseInt(split[1]);

        try {
            Thread.sleep(time * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void checkCreateCommandArgs(String[] split) {
        // C|c <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != 5) {
            throw new IllegalArgumentException("Expected 5 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected initial balance, node number, and node delay to be integers");
        }
    }

    private void checkDeleteCommandArgs(String[] split) {
        // E|e <user_id> <wallet_id> <node_index> <node_delay>
        if (split.length != 5) {
            throw new IllegalArgumentException("Expected 5 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[3]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[4]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node number and node delay to be integers");
        }
    }

    private void checkBalanceCommandArgs(String[] split) {
        // S|s <wallet_id> <node_index> <node_delay>
        if (split.length != 4) {
            throw new IllegalArgumentException("Expected 4 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        try {
            int nodeIndex = Integer.parseInt(split[2]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[3]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node number and node delay to be integers");
        }
    }

    private void checkTransferCommandArgs(String[] split) {
        // T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>
        if (split.length != 7) {
            throw new IllegalArgumentException("Expected 7 arguments, got " + split.length);
        }

        if (!ID_PATTERN.matcher(split[1]).matches()) {
            throw new IllegalArgumentException("Expected Source User ID to be composed of ASCII alphanumeric characters, got \"" + split[1] + "\"");
        }

        if (!ID_PATTERN.matcher(split[2]).matches()) {
            throw new IllegalArgumentException("Expected Source Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[2] + "\"");
        }

        if (!ID_PATTERN.matcher(split[3]).matches()) {
            throw new IllegalArgumentException("Expected Destination Wallet ID to be composed of ASCII alphanumeric characters, got \"" + split[3] + "\"");
        }

        try {
            if (Long.parseLong(split[4]) < 0) {
                throw new IllegalArgumentException("Amount cannot be negative");
            }
            int nodeIndex = Integer.parseInt(split[5]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
            if (Integer.parseInt(split[6]) < 0) {
                throw new IllegalArgumentException("Node delay cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected amount, node number, and node delay to be integers");
        }
    }

    private void checkDebugBlockchainStateArgs(String[] split) {
        // B <node_index>
        if (split.length != 2) {
            throw new IllegalArgumentException("Expected 2 arguments, got " + split.length);
        }

        try {
            int nodeIndex = Integer.parseInt(split[1]);
            if (nodeIndex < 0 || nodeIndex >= this.nodes.size()) {
                throw new IllegalArgumentException("Node index must be between 0 and " + (this.nodes.size() - 1));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected node index to be an integer");
        }
    }

    private void checkPauseArgs(String[] split) {
        // P <integer>
        if (split.length != 2) {
            throw new IllegalArgumentException("Expected 2 arguments, got " + split.length);
        }

        try {
            if (Integer.parseInt(split[1]) < 0) {
                throw new IllegalArgumentException("Pause time cannot be negative");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected pause time to be an integer");
        }
    }

    private static void printUsage() {
        System.err.println("Usage:\n" +
                "- C|c <user_id> <wallet_id> <node_index> <node_delay>\n" +
                "- E|e <user_id> <wallet_id> <node_index> <node_delay>\n" +
                "- S|s <wallet_id> <node_index> <node_delay>\n" +
                "- T|t <source_user_id> <source_wallet_id> <destination_wallet_id> <amount> <node_index> <node_delay>\n" +
                "- B <node_index>\n" +
                "- P <integer>\n" +
                "- X\n");
    }
}
