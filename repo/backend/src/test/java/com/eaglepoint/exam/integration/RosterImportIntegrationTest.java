package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.jobs.repository.JobRunRepository;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Grade;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.rooms.repository.GradeRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.IdempotencyKeyRepository;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.versioning.repository.EntityVersionRepository;
import com.eaglepoint.exam.audit.repository.AuditLogRepository;
import com.eaglepoint.exam.imports.repository.ImportJobRepository;
import com.eaglepoint.exam.imports.repository.ImportJobRowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the roster import workflow covering upload, preview,
 * commit, idempotency, and error handling.
 */
@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class RosterImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private RosterEntryRepository rosterEntryRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private ImportJobRowRepository importJobRowRepository;

    @Autowired
    private EntityVersionRepository entityVersionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private CampusRepository campusRepository;

    @Autowired
    private GradeRepository gradeRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private static final String COORDINATOR_USERNAME = "coordinator_import_test";
    private static final String COORDINATOR_PASSWORD = "Coord@12345678";
    private static final String DEVICE_FINGERPRINT = "test-device-import-001";

    private static final String TERM_NAME = "Import Term Spring";
    private static final String CLASS_NAME = "Import Class 7-A";

    private static final List<String> IMPORT_STUDENT_USERNAMES = List.of(
            "import_stu_1", "import_stu_2", "import_stu_3");

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        auditLogRepository.deleteAll();
        entityVersionRepository.deleteAll();
        importJobRowRepository.deleteAll();
        importJobRepository.deleteAll();
        jobRunRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        rosterEntryRepository.deleteAll();
        classRepository.deleteAll();
        termRepository.deleteAll();
        gradeRepository.deleteAll();
        campusRepository.deleteAll();

        for (String u : IMPORT_STUDENT_USERNAMES) {
            userRepository.findByUsername(u).ifPresent(userRepository::delete);
        }

        if (userRepository.findByUsername(COORDINATOR_USERNAME).isEmpty()) {
            User coordinator = new User();
            coordinator.setUsername(COORDINATOR_USERNAME);
            coordinator.setPasswordHash(new BCryptPasswordEncoder(4).encode(COORDINATOR_PASSWORD));
            coordinator.setFullName("Test Coordinator");
            coordinator.setRole(Role.ACADEMIC_COORDINATOR);
            coordinator.setAllowConcurrentSessions(false);
            coordinator.setFailedLoginAttempts(0);
            coordinator.setCreatedAt(LocalDateTime.now());
            coordinator.setUpdatedAt(LocalDateTime.now());
            userRepository.save(coordinator);
        }

        seedReferenceData();
        seedImportStudents();
    }

    private void seedReferenceData() {
        Campus campus = new Campus();
        campus.setName("Import Campus");
        campus.setAddress("1 Test Street");
        campus = campusRepository.save(campus);

        Grade grade = new Grade();
        grade.setName("Grade 7");
        grade.setLevel(7);
        grade = gradeRepository.save(grade);

        Term term = new Term();
        term.setName(TERM_NAME);
        term.setStartDate(LocalDate.of(2026, 2, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        term.setIsActive(true);
        termRepository.save(term);

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setName(CLASS_NAME);
        schoolClass.setGradeId(grade.getId());
        schoolClass.setCampusId(campus.getId());
        classRepository.save(schoolClass);
    }

    private void seedImportStudents() {
        for (String username : IMPORT_STUDENT_USERNAMES) {
            User student = new User();
            student.setUsername(username);
            student.setPasswordHash(new BCryptPasswordEncoder(4).encode("Student@12345678"));
            student.setFullName("Student " + username);
            student.setRole(Role.STUDENT);
            student.setAllowConcurrentSessions(false);
            student.setFailedLoginAttempts(0);
            student.setCreatedAt(LocalDateTime.now());
            student.setUpdatedAt(LocalDateTime.now());
            userRepository.save(student);
        }
    }

    private String login(String username, String password) throws Exception {
        LoginRequest loginRequest = new LoginRequest(username, password, DEVICE_FINGERPRINT);
        String body = objectMapper.writeValueAsString(loginRequest);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionToken").exists())
                .andReturn();

        JsonNode responseJson = objectMapper.readTree(result.getResponse().getContentAsString());
        return responseJson.get("data").get("sessionToken").asText();
    }

    /**
     * Matches {@link com.eaglepoint.exam.imports.service.ImportService} required headers and seeded reference data.
     */
    private String buildValidCsvContent() {
        return """
                student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes
                import_stu_1,%s,%s,ID001,parent1@example.com,none
                import_stu_2,%s,%s,ID002,parent2@example.com,none
                import_stu_3,%s,%s,ID003,parent3@example.com,none
                """.formatted(CLASS_NAME, TERM_NAME, CLASS_NAME, TERM_NAME, CLASS_NAME, TERM_NAME);
    }

    private String buildInvalidCsvContent() {
        return """
                student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes
                ,%s,%s,ID100,parent@example.com,notes
                missing_user_999,%s,%s,ID101,parent@example.com,notes
                import_stu_1,Unknown Class Name,%s,ID102,parent@example.com,notes
                import_stu_1,%s,%s,ID103,parent@example.com,notes
                import_stu_1,%s,%s,ID104,parent@example.com,notes
                """.formatted(CLASS_NAME, TERM_NAME, CLASS_NAME, TERM_NAME, TERM_NAME, CLASS_NAME, TERM_NAME, CLASS_NAME, TERM_NAME);
    }

    @Test
    void testFullImportWorkflow() throws Exception {
        String token = login(COORDINATOR_USERNAME, COORDINATOR_PASSWORD);

        String csvContent = buildValidCsvContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "roster_import.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/rosters/import/upload")
                        .file(file)
                        .param("entityType", "RosterEntry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.validRows").exists())
                .andExpect(jsonPath("$.data.invalidRows").exists())
                .andReturn();

        JsonNode previewJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        Long jobId = previewJson.get("data").get("jobId").asLong();
        int validRows = previewJson.get("data").get("validRows").size();

        assertThat(jobId).isPositive();
        assertThat(validRows).isGreaterThan(0);

        long rosterCountBefore = rosterEntryRepository.count();

        mockMvc.perform(post("/api/rosters/import/" + jobId + "/commit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long rosterCountAfter = rosterEntryRepository.count();
        assertThat(rosterCountAfter).isGreaterThan(rosterCountBefore);

        long versionCount = entityVersionRepository.count();
        assertThat(versionCount).isGreaterThan(0);

        long auditCount = auditLogRepository.count();
        assertThat(auditCount).isGreaterThan(0);
    }

    @Test
    void testImportPreviewShowsErrors() throws Exception {
        String token = login(COORDINATOR_USERNAME, COORDINATOR_PASSWORD);

        String csvContent = buildInvalidCsvContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "roster_invalid.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/rosters/import/upload")
                        .file(file)
                        .param("entityType", "RosterEntry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").exists())
                .andExpect(jsonPath("$.data.invalidRows").exists())
                .andReturn();

        JsonNode previewJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        int invalidRows = previewJson.get("data").get("invalidRows").size();

        assertThat(invalidRows).isGreaterThan(0);

        JsonNode rows = previewJson.get("data").get("rows");
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                if (row.has("errors") && row.get("errors").size() > 0) {
                    JsonNode firstError = row.get("errors").get(0);
                    assertThat(row.has("rowNumber") || firstError.has("rowNumber")).isTrue();
                    assertThat(firstError.has("field") || firstError.has("errorReason")).isTrue();
                }
            }
        }
    }

    @Test
    void testImportCommitIdempotent() throws Exception {
        String token = login(COORDINATOR_USERNAME, COORDINATOR_PASSWORD);

        String csvContent = buildValidCsvContent();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "roster_idempotent.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/rosters/import/upload")
                        .file(file)
                        .param("entityType", "RosterEntry")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode previewJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        Long jobId = previewJson.get("data").get("jobId").asLong();

        String idempotencyKey = "import-idem-key-" + System.currentTimeMillis();

        mockMvc.perform(post("/api/rosters/import/" + jobId + "/commit")
                        .param("idempotencyKey", idempotencyKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long countAfterFirstCommit = rosterEntryRepository.count();

        mockMvc.perform(post("/api/rosters/import/" + jobId + "/commit")
                        .param("idempotencyKey", idempotencyKey)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        long countAfterSecondCommit = rosterEntryRepository.count();

        assertThat(countAfterSecondCommit).isEqualTo(countAfterFirstCommit);
    }
}
