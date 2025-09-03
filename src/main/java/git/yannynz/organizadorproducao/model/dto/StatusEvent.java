package git.yannynz.organizadorproducao.model.dto;

public record StatusEvent(
        String kind,        // "filewatcher"
        boolean online,
        Long latencyMs,
        String lastChecked, // ISO-8601
        String lastSeenTs,  // ISO-8601 do pong
        String instanceId,
        String version,
        String source       // "rpc"
) {}

