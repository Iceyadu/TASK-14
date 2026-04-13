package com.eaglepoint.exam.notifications.controller;

import com.eaglepoint.exam.notifications.dto.CreateNotificationRequest;
import com.eaglepoint.exam.notifications.dto.NotificationResponse;
import com.eaglepoint.exam.notifications.dto.SubscriptionPreferencesResponse;
import com.eaglepoint.exam.notifications.dto.UpdateSubscriptionRequest;
import com.eaglepoint.exam.notifications.model.DeliveryStatusEntry;
import com.eaglepoint.exam.notifications.model.InboxMessage;
import com.eaglepoint.exam.notifications.model.NotificationStatus;
import com.eaglepoint.exam.notifications.service.NotificationService;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.dto.PaginationInfo;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for notification management, inbox, delivery status,
 * and subscription settings.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Lists notifications, paginated, with optional status filter.
     */
    @GetMapping
    @RequirePermission(Permission.NOTIFICATION_CREATE)
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> listNotifications(
            @RequestParam(required = false) NotificationStatus status,
            Pageable pageable) {

        Page<NotificationResponse> page = notificationService.listNotifications(pageable, status);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Creates a new notification in DRAFT status.
     */
    @PostMapping
    @RequirePermission(Permission.NOTIFICATION_CREATE)
    public ResponseEntity<ApiResponse<NotificationResponse>> createNotification(
            @Valid @RequestBody CreateNotificationRequest request) {

        NotificationResponse response = notificationService.createNotification(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Submits a draft notification for compliance review.
     */
    @PostMapping("/{id}/submit-review")
    @RequirePermission(Permission.NOTIFICATION_CREATE)
    public ResponseEntity<ApiResponse<NotificationResponse>> submitForReview(@PathVariable Long id) {
        NotificationResponse response = notificationService.submitForReview(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Publishes a compliance-approved notification for delivery.
     */
    @PostMapping("/{id}/publish")
    @RequirePermission(Permission.NOTIFICATION_PUBLISH)
    public ResponseEntity<ApiResponse<NotificationResponse>> publishNotification(
            @PathVariable Long id,
            @RequestParam(required = false) String idempotencyKey) {

        NotificationResponse response = notificationService.publishNotification(id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Cancels a DRAFT or QUEUED notification.
     */
    @PostMapping("/{id}/cancel")
    @RequirePermission(Permission.NOTIFICATION_CREATE)
    public ResponseEntity<ApiResponse<NotificationResponse>> cancelNotification(@PathVariable Long id) {
        NotificationResponse response = notificationService.cancelNotification(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Returns the student's inbox messages (student only).
     */
    @GetMapping("/inbox")
    @RequirePermission(Permission.INBOX_VIEW)
    public ResponseEntity<ApiResponse<List<InboxMessage>>> getInbox(
            @RequestParam(required = false) Boolean read,
            Pageable pageable) {

        Long studentUserId = RequestContext.getUserId();

        Page<InboxMessage> page = notificationService.getStudentInbox(studentUserId, pageable, read);

        PaginationInfo pagination = new PaginationInfo(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(page.getContent(), pagination));
    }

    /**
     * Marks an inbox message as read (student only).
     */
    @PostMapping("/inbox/{id}/read")
    @RequirePermission(Permission.INBOX_VIEW)
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        Long studentUserId = RequestContext.getUserId();
        notificationService.markRead(id, studentUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Returns delivery status entries for a notification.
     */
    @GetMapping("/delivery-status")
    @RequirePermission(Permission.NOTIFICATION_CREATE)
    public ResponseEntity<ApiResponse<List<DeliveryStatusEntry>>> getDeliveryStatus(
            @RequestParam Long notificationId) {

        List<DeliveryStatusEntry> entries = notificationService.getDeliveryStatus(notificationId);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    /**
     * Returns the student's subscription settings (student only).
     */
    @GetMapping("/subscriptions")
    @RequirePermission(Permission.SUBSCRIPTION_MANAGE)
    public ResponseEntity<ApiResponse<SubscriptionPreferencesResponse>> getSubscriptions() {
        Long studentUserId = RequestContext.getUserId();
        SubscriptionPreferencesResponse prefs = notificationService.getSubscriptionPreferences(studentUserId);
        return ResponseEntity.ok(ApiResponse.success(prefs));
    }

    /**
     * Updates the student's subscription settings and DND configuration (student only).
     */
    @PutMapping("/subscriptions")
    @RequirePermission(Permission.SUBSCRIPTION_MANAGE)
    public ResponseEntity<ApiResponse<Void>> updateSubscriptions(
            @RequestBody UpdateSubscriptionRequest request) {

        Long studentUserId = RequestContext.getUserId();
        notificationService.updateSubscriptionSettings(studentUserId, request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ---- Private helpers ----

    private void enforceStudentRole() {
        Role role = RequestContext.getRole();
        if (role != Role.STUDENT) {
            throw new AccessDeniedException("This endpoint is available to students only");
        }
    }
}
