package com.eaglepoint.exam.roster.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RosterServiceTest {

    @Mock private RosterEntryRepository rosterEntryRepository;
    @Mock private ScopeService scopeService;
    @Mock private AuditService auditService;
    @Mock private UserRepository userRepository;
    @Mock private ClassRepository classRepository;
    @Mock private TermRepository termRepository;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private RosterService newService() {
        return new RosterService(rosterEntryRepository, scopeService, auditService,
                userRepository, classRepository, termRepository);
    }

    private RosterEntry sampleEntry(Long id, Long studentUserId, Long classId, Long termId) {
        RosterEntry entry = new RosterEntry();
        entry.setId(id);
        entry.setStudentUserId(studentUserId);
        entry.setClassId(classId);
        entry.setTermId(termId);
        entry.setStudentIdNumberEnc("SID-001");
        entry.setGuardianContactEnc("guardian@example.com");
        entry.setAccommodationNotesEnc("none");
        entry.setIsDeleted(false);
        return entry;
    }

    // ---- exportRosterCsv with empty result ----

    @Test
    void testExportRosterCsvContainsVisibleRowsAndHeader() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Page<RosterEntry> empty = new PageImpl<>(List.of());
        when(rosterEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(empty);

        byte[] csv = newService().exportRosterCsv(null, null);
        String text = new String(csv);
        assertTrue(text.contains("student_username,class_name,term_name,student_id_number,guardian_contact,accommodation_notes"));
    }

    // ---- exportRosterCsv with data ----

    @Test
    void testExportRosterCsvIncludesStudentUsernameAndClassAndTermNames() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        RosterEntry entry = sampleEntry(1L, 10L, 20L, 30L);

        Page<RosterEntry> page = new PageImpl<>(List.of(entry));
        when(rosterEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        User student = new User();
        student.setUsername("student_abc");
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));

        SchoolClass cls = new SchoolClass();
        cls.setName("Class A");
        when(classRepository.findById(20L)).thenReturn(Optional.of(cls));

        Term term = new Term();
        term.setName("Spring 2026");
        when(termRepository.findById(30L)).thenReturn(Optional.of(term));

        byte[] csv = newService().exportRosterCsv(null, null);
        String text = new String(csv);
        assertTrue(text.contains("student_abc"));
        assertTrue(text.contains("Class A"));
        assertTrue(text.contains("Spring 2026"));
    }

    // ---- deleteRosterEntry ----

    @Test
    void testDeleteRosterEntryByNonAdminThrowsAccessDenied() {
        RequestContext.set(5L, "teacher", Role.HOMEROOM_TEACHER, "s", "127.0.0.1", "t");

        assertThrows(AccessDeniedException.class, () -> newService().deleteRosterEntry(1L));
    }

    @Test
    void testDeleteRosterEntryByAdminSoftDeletesEntry() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        RosterEntry entry = sampleEntry(1L, 10L, 20L, 30L);
        when(rosterEntryRepository.findById(1L)).thenReturn(Optional.of(entry));
        when(rosterEntryRepository.save(any(RosterEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        newService().deleteRosterEntry(1L);

        assertTrue(entry.getIsDeleted());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDeleteRosterEntryThrowsForMissingEntry() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        when(rosterEntryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> newService().deleteRosterEntry(999L));
    }

    // ---- getRosterEntry ----

    @Test
    void testGetRosterEntryForStudentOwnerSucceeds() {
        RequestContext.set(10L, "student", Role.STUDENT, "s", "127.0.0.1", "t");
        RosterEntry entry = sampleEntry(1L, 10L, 20L, 30L);
        when(rosterEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        newService().getRosterEntry(1L);
        // no exception means success
    }

    @Test
    void testGetRosterEntryForStudentNonOwnerThrowsAccessDenied() {
        RequestContext.set(99L, "other_student", Role.STUDENT, "s", "127.0.0.1", "t");
        RosterEntry entry = sampleEntry(1L, 10L, 20L, 30L);
        when(rosterEntryRepository.findById(1L)).thenReturn(Optional.of(entry));

        assertThrows(AccessDeniedException.class, () -> newService().getRosterEntry(1L));
    }
}
