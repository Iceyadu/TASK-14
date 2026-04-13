package com.eaglepoint.exam.imports.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.imports.dto.ImportPreviewResponse;
import com.eaglepoint.exam.imports.model.ImportJob;
import com.eaglepoint.exam.imports.model.ImportJobRow;
import com.eaglepoint.exam.imports.model.ImportJobStatus;
import com.eaglepoint.exam.imports.repository.ImportJobRepository;
import com.eaglepoint.exam.imports.repository.ImportJobRowRepository;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.service.IdempotencyService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.StateTransitionException;
import com.eaglepoint.exam.versioning.service.VersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ImportService} covering CSV preview, validation,
 * duplicate detection, commit atomicity, and idempotency.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportServiceTest {

    @Mock
    private ImportJobRepository importJobRepository;

    @Mock
    private ImportJobRowRepository importJobRowRepository;

    @Mock
    private RosterEntryRepository rosterEntryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private TermRepository termRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private VersionService versionService;

    @Mock
    private AuditService auditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ImportService importService;

    @BeforeEach
    void setUp() {
        RequestContext.set(1L, "coordinator1", Role.ACADEMIC_COORDINATOR, "session-1", "127.0.0.1", "trace-1");
        when(classRepository.findFirstByNameIgnoreCase(anyString())).thenAnswer(inv -> {
            SchoolClass c = new SchoolClass();
            c.setId(1L);
            c.setName(inv.getArgument(0));
            return Optional.of(c);
        });
        when(termRepository.findFirstByNameIgnoreCase(anyString())).thenAnswer(inv -> {
            Term t = new Term();
            t.setId(1L);
            t.setName(inv.getArgument(0));
            return Optional.of(t);
        });
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private MockMultipartFile createCsvFile(String content) {
        return new MockMultipartFile(
                "file", "roster.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private User createStudentUser(String username) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", (long) username.hashCode());
        user.setUsername(username);
        user.setRole(Role.STUDENT);
        return user;
    }

    @Test
    void testPreviewValidCsv() {
        String csv = "student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes\n" +
                "stu001,ClassA,Term1,ID001,parent@test.com,none\n" +
                "stu002,ClassB,Term1,ID002,parent2@test.com,extra time\n";

        MockMultipartFile file = createCsvFile(csv);

        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 1L);
            return job;
        });
        when(userRepository.findByUsername("stu001")).thenReturn(Optional.of(createStudentUser("stu001")));
        when(userRepository.findByUsername("stu002")).thenReturn(Optional.of(createStudentUser("stu002")));

        ImportPreviewResponse response = importService.uploadAndPreview(file, "RosterEntry");

        assertEquals(ImportJobStatus.PREVIEWED, response.getStatus());
        assertEquals(2, response.getTotalRows());
        assertEquals(2, response.getValidRows().size());
        assertEquals(0, response.getInvalidRows().size());
        assertTrue(response.getErrors().isEmpty());

        // Verify no roster entries were created during preview
        verify(rosterEntryRepository, never()).save(any(RosterEntry.class));
    }

    @Test
    void testPreviewWithErrors() {
        String csv = "student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes\n" +
                "stu001,ClassA,Term1,ID001,parent@test.com,none\n" +
                ",ClassB,Term1,,parent2@test.com,extra time\n"; // missing username + id

        MockMultipartFile file = createCsvFile(csv);

        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 2L);
            return job;
        });
        when(userRepository.findByUsername("stu001")).thenReturn(Optional.of(createStudentUser("stu001")));

        ImportPreviewResponse response = importService.uploadAndPreview(file, "RosterEntry");

        assertEquals(ImportJobStatus.PARTIALLY_VALID, response.getStatus());
        assertEquals(2, response.getTotalRows());
        assertEquals(1, response.getValidRows().size());
        assertEquals(1, response.getInvalidRows().size());
        assertFalse(response.getErrors().isEmpty());

        // Verify error details contain rowNumber, field, and reason
        ImportPreviewResponse.ImportRowError error = response.getErrors().get(0);
        assertEquals(2, error.getRowNumber());
        assertNotNull(error.getField());
        assertNotNull(error.getErrorReason());
    }

    @Test
    void testPreviewAllInvalid() {
        String csv = "student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes\n" +
                ",,,,, \n" +
                ",,,,, \n";

        MockMultipartFile file = createCsvFile(csv);

        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 3L);
            return job;
        });

        ImportPreviewResponse response = importService.uploadAndPreview(file, "RosterEntry");

        assertEquals(ImportJobStatus.VALIDATION_FAILED, response.getStatus());
        assertEquals(0, response.getValidRows().size());
        assertEquals(2, response.getInvalidRows().size());
    }

    @Test
    void testDuplicateDetectionCaseInsensitive() {
        String csv = "student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes\n" +
                "STU001,ClassA,Term1,ID001,parent@test.com,none\n" +
                "stu001,ClassB,Term1,ID002,parent2@test.com,extra time\n";

        MockMultipartFile file = createCsvFile(csv);

        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 4L);
            return job;
        });
        when(userRepository.findByUsername("STU001")).thenReturn(Optional.of(createStudentUser("STU001")));

        ImportPreviewResponse response = importService.uploadAndPreview(file, "RosterEntry");

        // Second row should be flagged as duplicate
        assertTrue(response.getErrors().stream()
                .anyMatch(e -> e.getErrorReason().toLowerCase().contains("duplicate")));
    }

    @Test
    void testCommitAtomicity() {
        ImportJob job = new ImportJob();
        ReflectionTestUtils.setField(job, "id", 10L);
        job.setStatus(ImportJobStatus.PREVIEWED);

        ImportJobRow validRow = new ImportJobRow();
        validRow.setImportJobId(10L);
        validRow.setRowNumber(1);
        validRow.setRowDataJson("{\"student_username\":\"stu001\",\"class_name\":\"ClassA\",\"term_name\":\"Term1\",\"student_id_number\":\"ID001\",\"guardian_contact\":\"p@t.com\",\"accommodation_notes\":\"none\"}");
        validRow.setIsValid(true);

        when(importJobRepository.findById(10L)).thenReturn(Optional.of(job));
        when(importJobRowRepository.findByImportJobIdAndIsValidTrue(10L)).thenReturn(List.of(validRow));
        when(userRepository.findByUsername("stu001")).thenReturn(Optional.of(createStudentUser("stu001")));
        when(rosterEntryRepository.save(any(RosterEntry.class))).thenAnswer(inv -> {
            RosterEntry entry = inv.getArgument(0);
            ReflectionTestUtils.setField(entry, "id", 100L);
            return entry;
        });
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> inv.getArgument(0));

        importService.commitImport(10L, null);

        verify(rosterEntryRepository).save(any(RosterEntry.class));
        verify(versionService).createVersion(eq("RosterEntry"), eq(100L), any());
        assertEquals(ImportJobStatus.COMMITTED, job.getStatus());
    }

    @Test
    void testCommitIdempotency() {
        when(idempotencyService.checkAndStore("idem-key-1", 1L, "COMMIT_IMPORT"))
                .thenReturn(new Object()); // indicates duplicate

        importService.commitImport(10L, "idem-key-1");

        verify(importJobRepository, never()).findById(anyLong());
        verify(rosterEntryRepository, never()).save(any(RosterEntry.class));
    }

    @Test
    void testCommitBlockedForValidationFailed() {
        ImportJob job = new ImportJob();
        ReflectionTestUtils.setField(job, "id", 20L);
        job.setStatus(ImportJobStatus.VALIDATION_FAILED);

        when(importJobRepository.findById(20L)).thenReturn(Optional.of(job));

        assertThrows(StateTransitionException.class, () -> importService.commitImport(20L, null));
        verify(rosterEntryRepository, never()).save(any(RosterEntry.class));
    }

    @Test
    void testNoWritesBeforeCommit() {
        String csv = "student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes\n" +
                "stu001,ClassA,Term1,ID001,parent@test.com,none\n";

        MockMultipartFile file = createCsvFile(csv);

        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 30L);
            return job;
        });
        when(userRepository.findByUsername("stu001")).thenReturn(Optional.of(createStudentUser("stu001")));

        importService.uploadAndPreview(file, "RosterEntry");

        // After preview, no roster entries should exist
        verify(rosterEntryRepository, never()).save(any(RosterEntry.class));
    }
}
