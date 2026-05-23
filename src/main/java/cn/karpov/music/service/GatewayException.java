package cn.karpov.music.service;

import cn.karpov.music.model.MusicModels.GatewayAttempt;
import java.util.List;
import java.util.Map;

public class GatewayException extends RuntimeException {
    private final String operation;
    private final Map<String, String> params;
    private final List<GatewayAttempt> attempts;

    public GatewayException(String operation, Map<String, String> params, List<GatewayAttempt> attempts) {
        super("No gateway candidate succeeded for operation: " + operation);
        this.operation = operation;
        this.params = params;
        this.attempts = attempts;
    }

    public String getOperation() {
        return operation;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public List<GatewayAttempt> getAttempts() {
        return attempts;
    }
}
