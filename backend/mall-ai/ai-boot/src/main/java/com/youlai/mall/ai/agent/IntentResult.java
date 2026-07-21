package com.youlai.mall.ai.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IntentResult {

    /** complaint / refund / logistics / consult / other */
    private String intent;
    private String intentLabel;
    /** 0.0 ~ 1.0 */
    private double confidence;
    private String orderSn;
    private String priority;
}
