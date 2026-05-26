package com.rivers.im.record;

import tools.jackson.databind.JsonNode;

public record WsEnvelope(
        String topic,
        String msgId,
        JsonNode payload) {
}
