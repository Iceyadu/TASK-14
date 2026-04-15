package com.eaglepoint.exam.integration;

import com.eaglepoint.exam.ExamSchedulingApplication;
import com.eaglepoint.exam.auth.dto.LoginRequest;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Grade;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.rooms.repository.GradeRepository;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.SessionRepository;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.repository.UserScopeAssignmentRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = ExamSchedulingApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "integration"})
class RosterExportAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CampusRepository campusRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private ClassRepository classRepository;
    @Autowired private TermRepository termRepository;
    @Autowired private RosterEntryRepository rosterEntryRepository;
    @Autowired private UserScopeAssignmentRepository scopeAssignmentRepository;

    private static final String PW = "Teacher@123456";
    private Long classId;
    private Long termId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        rosterEntryRepository.deleteAll();
        scopeAssignmentRepository.deleteAll();

        Long campusId = campusRepository.save(newCampus("Export Campus")).getId();
        Long gradeId = gradeRepository.save(newGrade()).getId();
        classId = classRepository.save(newClass(campusId, gradeId)).getId();
        termId = termRepository.save(newTerm()).getId();

        Long studentId = seedUser("export_student_user", Role.STUDENT).getId();
        User scopedTeacher = seedUser("export_teacher_scoped", Role.HOMEROOM_TEACHER);
        seedUser("export_teacher_unscoped", Role.HOMEROOM_TEACHER);
        seedUser("export_student_no_export", Role.STUDENT);
        scopeAssignmentRepository.save(new UserScopeAssignment(scopedTeacher.getId(), ScopeType.CLASS, classId));

        RosterEntry entry = new RosterEntry();
        entry.setStudentUserId(studentId);
        entry.setClassId(classId);
        entry.setTermId(termId);
        entry.setStudentIdNumberEnc("SID-EXP-1");
        entry.setGuardianContactEnc("parent-export@example.local");
        entry.setAccommodationNotesEnc("none");
        entry.setIsDeleted(false);
        rosterEntryRepository.save(entry);
    }

    private Campus newCampus(String name) {
        Campus c = new Campus();
        c.setName(name);
        return c;
    }

    private Grade newGrade() {
        Grade g = new Grade();
        g.setName("Grade 7");
        g.setLevel(7);
        return g;
    }

    private SchoolClass newClass(Long campusId, Long gradeId) {
        SchoolClass c = new SchoolClass();
        c.setName("Class Export");
        c.setCampusId(campusId);
        c.setGradeId(gradeId);
        return c;
    }

    private Term newTerm() {
        Term t = new Term();
        t.setName("Export Term");
        t.setStartDate(LocalDate.now().minusDays(1));
        t.setEndDate(LocalDate.now().plusDays(30));
        t.setIsActive(true);
        return t;
    }

    private User seedUser(String username, Role role) {
        return userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPasswordHash(new BCryptPasswordEncoder(4).encode(PW));
            user.setFullName(username);
            user.setRole(role);
            user.setAllowConcurrentSessions(true);
            user.setFailedLoginAttempts(0);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            return userRepository.save(user);
        });
    }

    private String login(String username) throws Exception {
        LoginRequest req = new LoginRequest(username, PW, "fp-" + username);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("sessionToken").asText();
    }

    @Test
    void testScopedTeacherCanExportButUnscopedTeacherGetsHeaderOnlyAndStudentForbidden() throws Exception {
        String scopedTeacherToken = login("export_teacher_scoped");
        MvcResult scopedRes = mockMvc.perform(get("/api/rosters/export")
                        .param("classId", classId.toString())
                        .param("termId", termId.toString())
                        .header("Authorization", "Bearer " + scopedTeacherToken))
                .andExpect(status().isOk())
                .andReturn();
        String scopedCsv = scopedRes.getResponse().getContentAsString();
        assertThat(scopedCsv).contains("student_username,class_name,term_name");
        assertThat(scopedCsv).contains("export_student_user");

        String unscopedTeacherToken = login("export_teacher_unscoped");
        MvcResult unscopedRes = mockMvc.perform(get("/api/rosters/export")
                        .param("classId", classId.toString())
                        .param("termId", termId.toString())
                        .header("Authorization", "Bearer " + unscopedTeacherToken))
                .andExpect(status().isOk())
                .andReturn();
        String unscopedCsv = unscopedRes.getResponse().getContentAsString();
        assertThat(unscopedCsv).contains("student_username,class_name,term_name");
        assertThat(unscopedCsv).doesNotContain("export_student_user");

        String studentToken = login("export_student_no_export");
        mockMvc.perform(get("/api/rosters/export")
                        .param("classId", classId.toString())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
