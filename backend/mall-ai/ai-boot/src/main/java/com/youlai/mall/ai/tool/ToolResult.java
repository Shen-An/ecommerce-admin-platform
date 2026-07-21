package com.youlai.mall.ai.tool;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tool 统一执行结果。
 */
@Data
@Builder
public class ToolResult {

    private String reply;
    private String intent;
    private List<Map<String, Object>> cards;
    private String toolName;
    private String toolError;

    public static ToolResult of(String intent, String toolName, String reply, List<Map<String, Object>> cards) {
        return ToolResult.builder()
                .intent(intent)
                .toolName(toolName)
                .reply(reply)
                .cards(cards != null ? cards : new ArrayList<>())
                .build();
    }

    public static ToolResult error(String intent, String toolName, String message) {
        return ToolResult.builder()
                .intent(intent)
                .toolName(toolName)
                .reply(message)
                .cards(new ArrayList<>())
                .toolError(message)
                .build();
    }
}
