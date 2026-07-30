package com.paytrust.fraud.dto;

import jakarta.validation.constraints.NotBlank;

public class FeedbackRequest {
    @NotBlank(message = "idempotencyKey is required")
    public String idempotencyKey;

    @NotBlank(message = "label is required")
    public String label; // e.g. FRAUD, LEGIT
}
