package com.eaglepoint.exam.rooms.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.rooms.model.Campus;
import com.eaglepoint.exam.rooms.model.Room;
import com.eaglepoint.exam.rooms.repository.CampusRepository;
import com.eaglepoint.exam.rooms.repository.RoomRepository;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing campuses and rooms with scope enforcement.
 */
@Service
public class RoomService {

    private final CampusRepository campusRepository;
    private final RoomRepository roomRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;

    public RoomService(CampusRepository campusRepository,
                       RoomRepository roomRepository,
                       ScopeService scopeService,
                       AuditService auditService) {
        this.campusRepository = campusRepository;
        this.roomRepository = roomRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
    }

    // ---- Campus operations ----

    /**
     * Lists all campuses, filtered by scope for non-admin users.
     */
    @Transactional(readOnly = true)
    public List<Campus> listCampuses() {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        if (role == Role.ADMIN) {
            return campusRepository.findAll();
        }

        List<UserScopeAssignment> scopes = scopeService.filterByUserScope(userId, role, "CAMPUS");
        if (scopes.isEmpty()) {
            return List.of();
        }

        List<Long> scopedIds = scopes.stream()
                .map(UserScopeAssignment::getScopeId)
                .collect(Collectors.toList());
        return campusRepository.findAllById(scopedIds);
    }

    /**
     * Gets a campus by ID.
     */
    @Transactional(readOnly = true)
    public Campus getCampus(Long id) {
        Campus campus = campusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Campus", id));
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "CAMPUS", id);
        return campus;
    }

    /**
     * Creates a new campus.
     */
    @Transactional
    public Campus createCampus(Campus campus) {
        Campus saved = campusRepository.save(campus);
        auditService.logAction("CREATE_CAMPUS", "Campus", saved.getId(),
                null, saved.getName(), "Created campus: " + saved.getName());
        return saved;
    }

    /**
     * Updates an existing campus.
     */
    @Transactional
    public Campus updateCampus(Long id, Campus update) {
        Campus campus = campusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Campus", id));
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "CAMPUS", id);

        String oldName = campus.getName();
        campus.setName(update.getName());
        campus.setAddress(update.getAddress());
        Campus saved = campusRepository.save(campus);

        auditService.logAction("UPDATE_CAMPUS", "Campus", saved.getId(),
                oldName, saved.getName(), "Updated campus");
        return saved;
    }

    /**
     * Deletes a campus.
     */
    @Transactional
    public void deleteCampus(Long id) {
        Campus campus = campusRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Campus", id));
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "CAMPUS", id);
        campusRepository.delete(campus);
        auditService.logAction("DELETE_CAMPUS", "Campus", id,
                campus.getName(), null, "Deleted campus");
    }

    // ---- Room operations ----

    /**
     * Lists rooms for a campus, filtered by scope.
     */
    @Transactional(readOnly = true)
    public List<Room> listRooms(Long campusId) {
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "ROOM", campusId);
        return roomRepository.findByCampusId(campusId);
    }

    /**
     * Gets a room by ID.
     */
    @Transactional(readOnly = true)
    public Room getRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Room", id));
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "ROOM", room.getCampusId());
        return room;
    }

    /**
     * Creates a new room.
     */
    @Transactional
    public Room createRoom(Room room) {
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "ROOM", room.getCampusId());
        Room saved = roomRepository.save(room);
        auditService.logAction("CREATE_ROOM", "Room", saved.getId(),
                null, saved.getName(), "Created room: " + saved.getName() + " at campus " + saved.getCampusId());
        return saved;
    }

    /**
     * Updates an existing room.
     */
    @Transactional
    public Room updateRoom(Long id, Room update) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Room", id));
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "ROOM", room.getCampusId());

        String oldName = room.getName();
        room.setName(update.getName());
        room.setBuilding(update.getBuilding());
        room.setCapacity(update.getCapacity());
        room.setFacilities(update.getFacilities());
        room.setCampusId(update.getCampusId());
        Room saved = roomRepository.save(room);

        auditService.logAction("UPDATE_ROOM", "Room", saved.getId(),
                oldName, saved.getName(), "Updated room");
        return saved;
    }

    /**
     * Deletes a room.
     */
    @Transactional
    public void deleteRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Room", id));
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(), "ROOM", room.getCampusId());
        roomRepository.delete(room);
        auditService.logAction("DELETE_ROOM", "Room", id,
                room.getName(), null, "Deleted room");
    }
}
