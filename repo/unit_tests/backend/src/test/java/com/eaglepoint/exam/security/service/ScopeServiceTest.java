package com.eaglepoint.exam.security.service;

import com.eaglepoint.exam.notifications.repository.NotificationTargetRepository;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.UserScopeAssignmentRepository;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScopeServiceTest {

    @Mock private UserScopeAssignmentRepository scopeRepository;
    @Mock private NotificationTargetRepository notificationTargetRepository;

    private ScopeService newService() {
        return new ScopeService(scopeRepository, notificationTargetRepository);
    }

    // ---- hasExamSessionScope ----

    @Test
    void testHasExamSessionScopeWithClassMatch() {
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.CAMPUS, 1L)).thenReturn(false);
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.TERM, 2L)).thenReturn(false);
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.COURSE, 3L)).thenReturn(false);
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.CLASS, 99L)).thenReturn(true);

        assertTrue(newService().hasExamSessionScope(11L, Role.HOMEROOM_TEACHER, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testHasExamSessionScopeDeniedWithoutAnyScope() {
        assertFalse(newService().hasExamSessionScope(11L, Role.HOMEROOM_TEACHER, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testHasExamSessionScopeAdminAlwaysTrue() {
        assertTrue(newService().hasExamSessionScope(1L, Role.ADMIN, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testHasExamSessionScopeStudentAlwaysFalse() {
        assertFalse(newService().hasExamSessionScope(5L, Role.STUDENT, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testHasExamSessionScopeWithCampusMatch() {
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.CAMPUS, 1L)).thenReturn(true);

        assertTrue(newService().hasExamSessionScope(11L, Role.ACADEMIC_COORDINATOR, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testHasExamSessionScopeWithTermMatch() {
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.CAMPUS, 1L)).thenReturn(false);
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.TERM, 2L)).thenReturn(true);

        assertTrue(newService().hasExamSessionScope(11L, Role.ACADEMIC_COORDINATOR, 1L, 2L, 3L, List.of(99L)));
    }

    // ---- enforceExamSessionScope ----

    @Test
    void testEnforceExamSessionScopeThrowsOnNoMatch() {
        assertThrows(AccessDeniedException.class, () -> newService().enforceExamSessionScope(
                11L, Role.ACADEMIC_COORDINATOR, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testEnforceExamSessionScopeAdminBypassesCheck() {
        assertDoesNotThrow(() -> newService().enforceExamSessionScope(
                1L, Role.ADMIN, 1L, 2L, 3L, List.of(99L)));
    }

    @Test
    void testEnforceExamSessionScopeStudentAlwaysDenied() {
        assertThrows(AccessDeniedException.class, () -> newService().enforceExamSessionScope(
                5L, Role.STUDENT, 1L, 2L, 3L, List.of(99L)));
    }

    // ---- enforceScope ----

    @Test
    void testEnforceScopeAdminBypassesAllChecks() {
        assertDoesNotThrow(() -> newService().enforceScope(1L, Role.ADMIN, "CAMPUS", 10L));
    }

    @Test
    void testEnforceScopeGrantedWhenScopeAssignmentExists() {
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.CAMPUS, 10L)).thenReturn(true);

        assertDoesNotThrow(() -> newService().enforceScope(11L, Role.ACADEMIC_COORDINATOR, "CAMPUS", 10L));
    }

    @Test
    void testEnforceScopeDeniedWhenNoAssignment() {
        when(scopeRepository.existsByUserIdAndScopeTypeAndScopeId(11L, ScopeType.CAMPUS, 10L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> newService().enforceScope(11L, Role.ACADEMIC_COORDINATOR, "CAMPUS", 10L));
    }

    // ---- filterByUserScope ----

    @Test
    void testFilterByUserScopeAdminReturnsEmptyList() {
        List<UserScopeAssignment> result = newService().filterByUserScope(1L, Role.ADMIN, "CAMPUS");
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterByUserScopeTeacherReturnsScopeAssignments() {
        UserScopeAssignment assignment = new UserScopeAssignment(11L, ScopeType.CLASS, 50L);
        when(scopeRepository.findByUserIdAndScopeType(11L, ScopeType.CLASS))
                .thenReturn(List.of(assignment));

        List<UserScopeAssignment> result = newService().filterByUserScope(11L, Role.HOMEROOM_TEACHER, "CLASS");
        assertEquals(1, result.size());
        assertEquals(50L, result.get(0).getScopeId());
    }

    // ---- listScopeIds ----

    @Test
    void testListScopeIdsAdminReturnsEmpty() {
        List<Long> ids = newService().listScopeIds(1L, Role.ADMIN, ScopeType.CAMPUS);
        assertTrue(ids.isEmpty());
    }

    @Test
    void testListScopeIdsStudentReturnsEmpty() {
        List<Long> ids = newService().listScopeIds(5L, Role.STUDENT, ScopeType.CLASS);
        assertTrue(ids.isEmpty());
    }

    @Test
    void testListScopeIdsNonAdminReturnsDistinctIds() {
        when(scopeRepository.findByUserIdAndScopeType(11L, ScopeType.CAMPUS))
                .thenReturn(List.of(
                        new UserScopeAssignment(11L, ScopeType.CAMPUS, 1L),
                        new UserScopeAssignment(11L, ScopeType.CAMPUS, 1L),
                        new UserScopeAssignment(11L, ScopeType.CAMPUS, 2L)));

        List<Long> ids = newService().listScopeIds(11L, Role.ACADEMIC_COORDINATOR, ScopeType.CAMPUS);
        assertEquals(2, ids.size());
    }
}
