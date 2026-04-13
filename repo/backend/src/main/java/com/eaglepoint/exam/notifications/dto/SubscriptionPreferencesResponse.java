package com.eaglepoint.exam.notifications.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Student-facing subscription + DND view aligned with the frontend contract.
 */
public class SubscriptionPreferencesResponse {

    private Map<String, Boolean> subscriptions = new LinkedHashMap<>();

    private LocalTime dndStart;

    private LocalTime dndEnd;

    public SubscriptionPreferencesResponse() {
    }

    public SubscriptionPreferencesResponse(Map<String, Boolean> subscriptions,
                                          LocalTime dndStart,
                                          LocalTime dndEnd) {
        this.subscriptions = subscriptions != null ? subscriptions : new LinkedHashMap<>();
        this.dndStart = dndStart;
        this.dndEnd = dndEnd;
    }

    public Map<String, Boolean> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Map<String, Boolean> subscriptions) {
        this.subscriptions = subscriptions != null ? subscriptions : new LinkedHashMap<>();
    }

    @JsonProperty("dndStartTime")
    public LocalTime getDndStartTime() {
        return dndStart;
    }

    public void setDndStart(LocalTime dndStart) {
        this.dndStart = dndStart;
    }

    @JsonProperty("dndEndTime")
    public LocalTime getDndEndTime() {
        return dndEnd;
    }

    public void setDndEnd(LocalTime dndEnd) {
        this.dndEnd = dndEnd;
    }
}
