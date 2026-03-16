package pt.tecnico.blockchainist.node;

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
