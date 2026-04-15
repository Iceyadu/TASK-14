package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.compliance.dto.ReviewDecisionRequest;
import com.eaglepoint.exam.notifications.dto.CreateNotificationRequest;
import com.eaglepoint.exam.notifications.model.NotificationEventType;
import com.eaglepoint.exam.notifications.model.NotificationTargetType;
import com.eaglepoint.exam.compliance.repository.ComplianceReviewRepository;
import com.eaglepoint.exam.jobs.service.JobService;
import com.eaglepoint.exam.notifications.repository.DeliveryStatusRepository;
import com.eaglepoint.exam.notifications.repository.DndSettingRepository;
import com.eaglepoint.exam.notifications.repository.InboxMessageRepository;
import com.eaglepoint.exam.notifications.repository.NotificationRepository;
import com.eaglepoint.exam.notifications.repository.SubscriptionSettingRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the notification workflow covering creation, compliance
 * review, delivery with fallback, inbox isolation, and authorization gating.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
                "app.wechat.mode=disabled",
                "app.wechat.enabled=false"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private InboxMessageRepository inboxMessageRepository;

    @Autowired
    private DeliveryStatusRepository deliveryStatusRepository;

    @Autowired
    private ComplianceReviewRepository complianceReviewRepository;

    @Autowired
    private DndSettingRepository dndSettingRepository;

    @Autowired
    private SubscriptionSettingRepository subscriptionSettingRepository;

    @Autowired
    private JobService jobService;

    private static final String COORDINATOR_USERNAME = "coordinator_notif_test";
    private static final String ADMIN_USERNAME = "admin_notif_test";
    private static final String STUDENT_A_USERNAME = "student_a_notif_test";
    private static final String STUDENT_B_USERNAME = "student_b_notif_test";
    private static final String COMMON_PASSWORD = "Test@12345678";
    private static final String DEVICE_FP_COORD = "device-notif-coord";
    private static final String DEVICE_FP_ADMIN = "device-notif-admin";
    private static final String DEVICE_FP_STU_A = "device-notif-stu-a";
    private static final String DEVICE_FP_STU_B = "device-notif-stu-b";

    private Long studentAId;
    private Long studentBId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        complianceReviewRepository.deleteAll();
        inboxMessageRepository.deleteAll();
        deliveryStatusRepository.deleteAll();
        notificationRepository.deleteAll();
        dndSettingRepository.deleteAll();
        subscriptionSettingRepository.deleteAll();

        seedUserIfAbsent(COORDINATOR_USERNAME, "Notification Coordinator", Role.ACADEMIC_COORDINATOR);
        seedUserIfAbsent(ADMIN_USERNAME, "Notification Admin", Role.ADMIN);
        studentAId = seedUserIfAbsent(STUDENT_A_USERNAME, "Student Alpha", Role.STUDENT);
        studentBId = seedUserIfAbsent(STUDENT_B_USERNAME, "Student Beta", Role.STUDENT);
    }

    private Long seedUserIfAbsent(String username, String fullName, Role role) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setPasswordHash(new BCryptPasswordEncoder(4).encode(COMMON_PASSWORD));
                    user.setFullName(fullName);
                    user.setRole(role);
                    user.setAllowConcurrentSessions(false);
                    user.setFailedLoginAttempts(0);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());
                    return userRepository.save(user).getId();
                });
    }

    private String login(String username, String deviceFingerprint) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, COMMON_PASSWORD, deviceFingerprint);
        String body = objectMapper.writeValueAsString(loginRequest);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("sessionToken").asText();
    }

    private CreateNotificationRequest buildNotificationRequest(Long... targetStudentIds) {
        CreateNotificationRequest request = new CreateNotificationRequest();
        request.setTitle("Schedule Change Notice");
        request.setContent("The math exam has been rescheduled to next Monday.");
        request.setEventType(NotificationEventType.SCHEDULE_CHANGE);
        request.setTargetType(NotificationTargetType.INDIVIDUAL);
        request.setTargetIds(List.of(targetStudentIds));
        return request;
    }

    private Long findReviewId(String adminToken) throws Exception {
        MvcResult reviewsResult = mockMvc.perform(get("/api/compliance/reviews")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reviewsJson = objectMapper.readTree(reviewsResult.getResponse().getContentAsString());
        JsonNode reviewsList = reviewsJson.get("data");
        assertThat(reviewsList.size()).isGreaterThan(0);
        return reviewsList.get(0).get("id").asLong();
    }

    private void approveReview(Long reviewId, String adminToken, String comment) throws Exception {
        ReviewDecisionRequest approveRequest = new ReviewDecisionRequest();
        approveRequest.setComment(comment);
        String approveBody = objectMapper.writeValueAsString(approveRequest);
        mockMvc.perform(post("/api/compliance/reviews/" + reviewId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void testNotificationToInboxFallback() throws Exception {
        // Individual-target notifications require an administrator to create
        String adminCreateToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        CreateNotificationRequest createRequest = buildNotificationRequest(studentAId);
        String createBody = objectMapper.writeValueAsString(createRequest);

        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + adminCreateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long notificationId = createJson.get("data").get("id").asLong();

        // Step 2: Submit for compliance review
        mockMvc.perform(post("/api/notifications/" + notificationId + "/submit-review")
                        .header("Authorization", "Bearer " + adminCreateToken))
                .andExpect(status().isOk());

        // Step 3: Login as Admin and approve
        sessionRepository.deleteAll();
        String adminToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        Long reviewId = findReviewId(adminToken);
        approveReview(reviewId, adminToken, "Approved for delivery");

        // Step 4: Publish the notification
        mockMvc.perform(post("/api/notifications/" + notificationId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Delivery is asynchronous via job queue; process the queued NOTIFICATION_SEND job.
        jobService.processNextJob();

        // Step 5: Verify delivery status shows fallback to in-app (WeChat disabled in test)
        MvcResult deliveryResult = mockMvc.perform(get("/api/notifications/delivery-status")
                        .param("notificationId", notificationId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode deliveryJson = objectMapper.readTree(deliveryResult.getResponse().getContentAsString());
        JsonNode deliveryEntries = deliveryJson.get("data");
        assertThat(deliveryEntries).isNotNull();
        assertThat(deliveryEntries.size()).isEqualTo(1);
        JsonNode entry = deliveryEntries.get(0);
        assertThat(entry.get("channel").asText()).isEqualTo("IN_APP");
        assertThat(entry.get("status").asText()).isEqualTo("fallback_delivered");

        // Step 6: Verify inbox message created for target student
        sessionRepository.deleteAll();
        String studentAToken = login(STUDENT_A_USERNAME, DEVICE_FP_STU_A);

        MvcResult inboxResult = mockMvc.perform(get("/api/notifications/inbox")
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode inboxJson = objectMapper.readTree(inboxResult.getResponse().getContentAsString());
        JsonNode inboxMessages = inboxJson.get("data");
        assertThat(inboxMessages.size()).isEqualTo(1);
        assertThat(inboxMessages.get(0).get("title").asText()).isEqualTo("Schedule Change Notice");
    }

    @Test
    void testNotificationBlockedWithoutApproval() throws Exception {
        String adminToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        CreateNotificationRequest createRequest = buildNotificationRequest(studentAId);
        String createBody = objectMapper.writeValueAsString(createRequest);

        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long notificationId = createJson.get("data").get("id").asLong();

        // Attempt to publish without review -> should be blocked
        mockMvc.perform(post("/api/notifications/" + notificationId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    void testStudentInboxIsolation() throws Exception {
        String adminToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        CreateNotificationRequest createRequest = buildNotificationRequest(studentAId);
        createRequest.setTitle("Private Notice for Student A");
        String createBody = objectMapper.writeValueAsString(createRequest);

        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long notificationId = createJson.get("data").get("id").asLong();

        // Submit for review
        mockMvc.perform(post("/api/notifications/" + notificationId + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Admin approves and publishes
        sessionRepository.deleteAll();
        String adminToken2 = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);

        Long reviewId = findReviewId(adminToken2);
        approveReview(reviewId, adminToken2, "Approved");

        mockMvc.perform(post("/api/notifications/" + notificationId + "/publish")
                        .header("Authorization", "Bearer " + adminToken2))
                .andExpect(status().isOk());

        jobService.processNextJob();

        // Login as student B and check inbox -> should be empty (only own messages)
        sessionRepository.deleteAll();
        String studentBToken = login(STUDENT_B_USERNAME, DEVICE_FP_STU_B);

        MvcResult inboxResult = mockMvc.perform(get("/api/notifications/inbox")
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode inboxJson = objectMapper.readTree(inboxResult.getResponse().getContentAsString());
        JsonNode inboxMessages = inboxJson.get("data");
        assertThat(inboxMessages.size()).isEqualTo(0);
    }

    @Test
    void testNotificationDndHeldDeliveryStatus() throws Exception {
        // Student configures DND to always cover current time
        String studentToken = login(STUDENT_A_USERNAME, DEVICE_FP_STU_A);
        String dndBody = """
                {
                  "subscriptions": {"SCHEDULE_CHANGE": true},
                  "dndStartTime": "00:00:00",
                  "dndEndTime": "23:59:00"
                }
                """;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/notifications/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dndBody)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        // Admin creates + submits + approves + publishes
        sessionRepository.deleteAll();
        String adminToken = login(ADMIN_USERNAME, DEVICE_FP_ADMIN);
        MvcResult createResult = mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildNotificationRequest(studentAId)))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Long notificationId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(post("/api/notifications/" + notificationId + "/submit-review")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        approveReview(findReviewId(adminToken), adminToken, "DND case approved");
        mockMvc.perform(post("/api/notifications/" + notificationId + "/publish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        jobService.processNextJob();

        MvcResult deliveryResult = mockMvc.perform(get("/api/notifications/delivery-status")
                        .param("notificationId", notificationId.toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode deliveryEntries = objectMapper.readTree(deliveryResult.getResponse().getContentAsString()).get("data");
        assertThat(deliveryEntries.size()).isEqualTo(1);
        assertThat(deliveryEntries.get(0).get("channel").asText()).isEqualTo("IN_APP");
        assertThat(deliveryEntries.get(0).get("status").asText()).isEqualTo("delivered_dnd_held");
    }
}
