package pt.tecnico.blockchainist.node;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * Server interceptor that extracts the "delay-seconds" metadata header
 * from incoming gRPC requests and stores it in the gRPC Context.
 * The delay is later applied by NodeServiceImpl.applyDelay() before
 * processing the request (B.1 requirement).
 */
public class DelayInterceptor implements ServerInterceptor {

    // Metadata key matching the one sent by the client.
    public static final Metadata.Key<String> DELAY_KEY =
            Metadata.Key.of("delay-seconds", Metadata.ASCII_STRING_MARSHALLER);

    // Context key used to pass the parsed delay value to the RPC handler.
    public static final Context.Key<Integer> DELAY_CTX_KEY = Context.key("delay-seconds");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String delayString = headers.get(DELAY_KEY);
        int delaySeconds = 0;
        
        if (delayString != null) {
            try {
                delaySeconds = Integer.parseInt(delayString);
            } catch (NumberFormatException e) {
                // Ignore invalid values
            }
        }

        Context ctx = Context.current().withValue(DELAY_CTX_KEY, delaySeconds);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
