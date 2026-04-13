package com.eaglepoint.exam.notifications.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Request payload for updating a student's notification subscription settings.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateSubscriptionRequest {

    private List<SubscriptionEntry> settings;
    @JsonAlias({"dndStartTime"})
    private LocalTime dndStart;
    @JsonAlias({"dndEndTime"})
    private LocalTime dndEnd;

    public UpdateSubscriptionRequest() {
    }

    // ---- Getters / Setters ----

    public List<SubscriptionEntry> getSettings() {
        return settings;
    }

    public void setSettings(List<SubscriptionEntry> settings) {
        this.settings = settings;
    }

    public LocalTime getDndStart() {
        return dndStart;
    }

    public void setDndStart(LocalTime dndStart) {
        this.dndStart = dndStart;
    }

    public LocalTime getDndEnd() {
        return dndEnd;
    }

    public void setDndEnd(LocalTime dndEnd) {
        this.dndEnd = dndEnd;
    }

    /**
     * Accepts a map of event type → enabled from the student UI ({@code subscriptions}).
     */
    @JsonProperty("subscriptions")
    public void setSubscriptionsFromClient(Map<String, Boolean> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return;
        }
        this.settings = subscriptions.entrySet().stream()
                .map(e -> new SubscriptionEntry(e.getKey(), Boolean.TRUE.equals(e.getValue())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * A single subscription preference entry.
     */
    public static class SubscriptionEntry {

        private String eventType;
        private boolean enabled;

        public SubscriptionEntry() {
        }

        public SubscriptionEntry(String eventType, boolean enabled) {
            this.eventType = eventType;
            this.enabled = enabled;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
