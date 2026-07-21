package com.youlai.mall.ai.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EscalationDecision {

    private boolean escalate;
    private List<String> reasons;
}
