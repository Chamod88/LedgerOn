package com.paytrust.fraud.dto;

public class FraudScoreResponse {
    public String decision;
    public String reason;
    public double score;

    public FraudScoreResponse() {}

    public FraudScoreResponse(String decision, String reason, double score) {
        this.decision = decision;
        this.reason = reason;
        this.score = score;
    }
}
