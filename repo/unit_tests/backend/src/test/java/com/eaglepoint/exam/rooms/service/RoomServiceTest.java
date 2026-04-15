package com.eaglepoint.exam.rooms.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Room;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.RoomRepository;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.ScopeType;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private CampusRepository campusRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ScopeService scopeService;
    @Mock private AuditService auditService;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    private RoomService newService() {
        return new RoomService(campusRepository, roomRepository, scopeService, auditService);
    }

    private Campus sampleCampus(Long id, String name) {
        Campus c = new Campus();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Room sampleRoom(Long id, Long campusId, String name) {
        Room r = new Room();
        r.setId(id);
        r.setCampusId(campusId);
        r.setName(name);
        r.setCapacity(30);
        return r;
    }

    // ---- listCampuses ----

    @Test
    void testListCampusesForAdminReturnsAll() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Campus c = sampleCampus(10L, "Main");
        when(campusRepository.findAll()).thenReturn(List.of(c));

        List<Campus> result = newService().listCampuses();
        assertEquals(1, result.size());
        assertEquals("Main", result.get(0).getName());
    }

    @Test
    void testListCampusesForScopedUserUsesScopeFilter() {
        RequestContext.set(2L, "coord", Role.ACADEMIC_COORDINATOR, "s", "127.0.0.1", "t");
        Campus c = sampleCampus(20L, "Scoped Campus");
        when(scopeService.filterByUserScope(2L, Role.ACADEMIC_COORDINATOR, "CAMPUS"))
                .thenReturn(List.of(new UserScopeAssignment(2L, ScopeType.CAMPUS, 20L)));
        when(campusRepository.findAllById(List.of(20L))).thenReturn(List.of(c));

        List<Campus> result = newService().listCampuses();
        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).getId());
    }

    @Test
    void testListCampusesForNonAdminWithNoScopeReturnsEmpty() {
        RequestContext.set(3L, "teacher", Role.HOMEROOM_TEACHER, "s", "127.0.0.1", "t");
        when(scopeService.filterByUserScope(3L, Role.HOMEROOM_TEACHER, "CAMPUS"))
                .thenReturn(List.of());

        List<Campus> result = newService().listCampuses();
        assertTrue(result.isEmpty());
    }

    // ---- createCampus ----

    @Test
    void testCreateCampusSavesAndAudits() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Campus input = new Campus();
        input.setName("New Campus");

        Campus saved = sampleCampus(100L, "New Campus");
        when(campusRepository.save(any(Campus.class))).thenReturn(saved);

        Campus result = newService().createCampus(input);

        assertEquals(100L, result.getId());
        assertEquals("New Campus", result.getName());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    // ---- updateCampus ----

    @Test
    void testUpdateCampusChangesNameAndAudits() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Campus existing = sampleCampus(10L, "Old Name");
        when(campusRepository.findById(10L)).thenReturn(Optional.of(existing));
        doNothing().when(scopeService).enforceScope(any(), any(), any(), any());
        when(campusRepository.save(any(Campus.class))).thenAnswer(inv -> inv.getArgument(0));

        Campus update = new Campus();
        update.setName("New Name");

        Campus result = newService().updateCampus(10L, update);

        assertEquals("New Name", result.getName());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testUpdateCampusThrowsForMissingId() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        when(campusRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> newService().updateCampus(999L, new Campus()));
    }

    // ---- deleteCampus ----

    @Test
    void testDeleteCampusDeletesAndAudits() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Campus campus = sampleCampus(10L, "To Delete");
        when(campusRepository.findById(10L)).thenReturn(Optional.of(campus));
        doNothing().when(scopeService).enforceScope(any(), any(), any(), any());

        newService().deleteCampus(10L);

        verify(campusRepository).delete(campus);
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    // ---- createRoom ----

    @Test
    void testCreateRoomEnforceScopeAndSavesAndAudits() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Room input = sampleRoom(null, 10L, "Lab A");
        Room saved = sampleRoom(200L, 10L, "Lab A");
        doNothing().when(scopeService).enforceScope(any(), any(), any(), any());
        when(roomRepository.save(any(Room.class))).thenReturn(saved);

        Room result = newService().createRoom(input);

        assertEquals(200L, result.getId());
        verify(scopeService).enforceScope(any(), any(), any(), any());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    // ---- updateRoom ----

    @Test
    void testUpdateRoomChangesFieldsAndAudits() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Room existing = sampleRoom(50L, 10L, "Old Room");
        when(roomRepository.findById(50L)).thenReturn(Optional.of(existing));
        doNothing().when(scopeService).enforceScope(any(), any(), any(), any());
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        Room update = sampleRoom(null, 10L, "Updated Room");
        update.setCapacity(60);

        Room result = newService().updateRoom(50L, update);

        assertEquals("Updated Room", result.getName());
        assertEquals(60, result.getCapacity());
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    // ---- deleteRoom ----

    @Test
    void testDeleteRoomDeletesAndAudits() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        Room room = sampleRoom(50L, 10L, "Room X");
        when(roomRepository.findById(50L)).thenReturn(Optional.of(room));
        doNothing().when(scopeService).enforceScope(any(), any(), any(), any());

        newService().deleteRoom(50L);

        verify(roomRepository).delete(room);
        verify(auditService).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDeleteRoomThrowsForMissingId() {
        RequestContext.set(1L, "admin", Role.ADMIN, "s", "127.0.0.1", "t");
        when(roomRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> newService().deleteRoom(999L));
    }
}
