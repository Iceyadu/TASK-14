package com.eaglepoint.exam.notifications.controller;

import com.eaglepoint.exam.notifications.dto.NotificationResponse;
import com.eaglepoint.exam.notifications.model.NotificationStatus;
import com.eaglepoint.exam.notifications.service.NotificationService;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerUnitTest {

    @Mock
    private NotificationService notificationService;

    @Test
    void testCancelNotificationDelegatesAndReturnsPayload() {
        NotificationController controller = new NotificationController(notificationService);
        NotificationResponse response = new NotificationResponse();
        response.setId(77L);
        response.setStatus(NotificationStatus.CANCELED);
        when(notificationService.cancelNotification(77L)).thenReturn(response);

        ResponseEntity<ApiResponse<NotificationResponse>> entity = controller.cancelNotification(77L);
        assertNotNull(entity.getBody());
        assertEquals(77L, entity.getBody().getData().getId());
        assertEquals(NotificationStatus.CANCELED, entity.getBody().getData().getStatus());
        verify(notificationService).cancelNotification(77L);
    }
}
