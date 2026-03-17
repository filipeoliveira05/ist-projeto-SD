package pt.tecnico.blockchainist.node;

/**
 * Immutable result of a processed transaction, used to cache outcomes
 * for idempotent retry support (B.2). Stores either success or the
 * error that occurred during execution.
 */
public final class RequestResult {

    private static final RequestResult SUCCESS = new RequestResult(null);

    private final Throwable error;

    private RequestResult(Throwable error) {
        this.error = error;
    }

    public static RequestResult success() {
        return SUCCESS;
    }

    public static RequestResult failure(Throwable error) {
        return new RequestResult(error);
    }

    public boolean isSuccess() {
        return error == null;
    }

    public Throwable getError() {
        return error;
    }
}
