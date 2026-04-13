package com.eaglepoint.exam.roster.service;

import com.eaglepoint.exam.audit.service.AuditService;
import com.eaglepoint.exam.roster.dto.CreateRosterRequest;
import com.eaglepoint.exam.roster.dto.RosterResponse;
import com.eaglepoint.exam.roster.model.RosterEntry;
import com.eaglepoint.exam.roster.repository.RosterEntryRepository;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.model.User;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.repository.UserRepository;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.exception.AccessDeniedException;
import com.eaglepoint.exam.shared.exception.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Service managing roster entries with scope enforcement, encryption of
 * sensitive fields, and audit logging.
 */
@Service
public class RosterService {

    private final RosterEntryRepository rosterEntryRepository;
    private final ScopeService scopeService;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final TermRepository termRepository;

    public RosterService(RosterEntryRepository rosterEntryRepository,
                         ScopeService scopeService,
                         AuditService auditService,
                         UserRepository userRepository,
                         ClassRepository classRepository,
                         TermRepository termRepository) {
        this.rosterEntryRepository = rosterEntryRepository;
        this.scopeService = scopeService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.classRepository = classRepository;
        this.termRepository = termRepository;
    }

    /**
     * Lists roster entries with optional class, term, and search filters.
     * Students can only view their own entries. Other roles are scope-filtered.
     */
    @Transactional(readOnly = true)
    public Page<RosterResponse> listRosterEntries(Long classId, Long termId, String search, Pageable pageable) {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        // Students can only see their own roster entries
        if (role == Role.STUDENT) {
            Specification<RosterEntry> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.equal(root.get("studentUserId"), userId));
                predicates.add(cb.isFalse(root.get("isDeleted")));
                if (classId != null) {
                    predicates.add(cb.equal(root.get("classId"), classId));
                }
                if (termId != null) {
                    predicates.add(cb.equal(root.get("termId"), termId));
                }
                return cb.and(predicates.toArray(new Predicate[0]));
            };
            return rosterEntryRepository.findAll(spec, pageable).map(this::toResponse);
        }

        // For non-student roles, apply scope filtering
        List<UserScopeAssignment> scopes = scopeService.filterByUserScope(userId, role, "ROSTER_ENTRY");
        List<Long> scopedClassIds = scopes.stream()
                .map(UserScopeAssignment::getScopeId)
                .collect(Collectors.toList());

        if (role != Role.ADMIN && scopedClassIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<RosterEntry> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("isDeleted")));

            if (classId != null) {
                predicates.add(cb.equal(root.get("classId"), classId));
            }
            if (termId != null) {
                predicates.add(cb.equal(root.get("termId"), termId));
            }

            if (!scopedClassIds.isEmpty()) {
                predicates.add(root.get("classId").in(scopedClassIds));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return rosterEntryRepository.findAll(spec, pageable).map(this::toResponse);
    }

    /**
     * Gets a single roster entry by ID with scope check.
     * Students can only view their own entry.
     */
    @Transactional(readOnly = true)
    public RosterResponse getRosterEntry(Long id) {
        RosterEntry entry = findEntryOrThrow(id);
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        if (role == Role.STUDENT) {
            if (!entry.getStudentUserId().equals(userId)) {
                throw new AccessDeniedException("Students may only view their own roster entries");
            }
        } else {
            scopeService.enforceScope(userId, role, "ROSTER_ENTRY", entry.getClassId());
        }

        return toResponse(entry);
    }

    /**
     * Creates a new roster entry, encrypting sensitive fields.
     */
    @Transactional
    public RosterResponse createRosterEntry(CreateRosterRequest request) {
        scopeService.enforceScope(RequestContext.getUserId(), RequestContext.getRole(),
                "ROSTER_ENTRY", request.getClassId());

        RosterEntry entry = new RosterEntry();
        entry.setStudentUserId(request.getStudentUserId());
        entry.setClassId(request.getClassId());
        entry.setTermId(request.getTermId());
        entry.setStudentIdNumberEnc(request.getStudentIdNumber());
        entry.setGuardianContactEnc(request.getGuardianContact());
        entry.setAccommodationNotesEnc(request.getAccommodationNotes());
        entry.setIsDeleted(false);

        RosterEntry saved = rosterEntryRepository.save(entry);

        auditService.logAction("CREATE_ROSTER", "RosterEntry", saved.getId(),
                null, null, "Created roster entry for student " + saved.getStudentUserId());

        return toResponse(saved);
    }

    /**
     * Updates an existing roster entry with scope check.
     */
    @Transactional
    public RosterResponse updateRosterEntry(Long id, CreateRosterRequest request) {
        RosterEntry entry = findEntryOrThrow(id);
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        scopeService.enforceScope(userId, role, "ROSTER_ENTRY", entry.getClassId());
        scopeService.enforceScope(userId, role, "ROSTER_ENTRY", request.getClassId());

        entry.setStudentUserId(request.getStudentUserId());
        entry.setClassId(request.getClassId());
        entry.setTermId(request.getTermId());
        entry.setStudentIdNumberEnc(request.getStudentIdNumber());
        entry.setGuardianContactEnc(request.getGuardianContact());
        entry.setAccommodationNotesEnc(request.getAccommodationNotes());

        RosterEntry saved = rosterEntryRepository.save(entry);

        auditService.logAction("UPDATE_ROSTER", "RosterEntry", saved.getId(),
                null, null, "Updated roster entry for student " + saved.getStudentUserId());

        return toResponse(saved);
    }

    /**
     * Soft-deletes a roster entry. ADMIN only.
     */
    @Transactional
    public void deleteRosterEntry(Long id) {
        Role role = RequestContext.getRole();
        if (role != Role.ADMIN) {
            throw new AccessDeniedException("Only ADMIN users can delete roster entries");
        }

        RosterEntry entry = findEntryOrThrow(id);
        entry.setIsDeleted(true);
        rosterEntryRepository.save(entry);

        auditService.logAction("DELETE_ROSTER", "RosterEntry", entry.getId(),
                null, null, "Soft-deleted roster entry for student " + entry.getStudentUserId());
    }

    /**
     * Exports roster rows visible to the current user as CSV (UTF-8), aligned with import columns.
     */
    @Transactional(readOnly = true)
    public byte[] exportRosterCsv(Long classId, Long termId) {
        Page<RosterResponse> page = listRosterEntries(classId, termId, null,
                org.springframework.data.domain.Pageable.unpaged());
        List<RosterResponse> rows = page.getContent();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader("student_username", "class_name", "term_name",
                            "student_id_number", "guardian_contact", "accommodation_notes")
                    .build();
            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(out, StandardCharsets.UTF_8), format)) {
                for (RosterResponse r : rows) {
                    String username = userRepository.findById(r.getStudentUserId())
                            .map(User::getUsername).orElse("");
                    String className = classRepository.findById(r.getClassId())
                            .map(SchoolClass::getName).orElse("");
                    String termName = termRepository.findById(r.getTermId())
                            .map(Term::getName).orElse("");
                    printer.printRecord(username, className, termName,
                            Optional.ofNullable(r.getStudentIdNumber()).orElse(""),
                            Optional.ofNullable(r.getGuardianContact()).orElse(""),
                            Optional.ofNullable(r.getAccommodationNotes()).orElse(""));
                }
            }
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to build roster export", e);
        }
    }

    // ---- Private helpers ----

    private RosterEntry findEntryOrThrow(Long id) {
        return rosterEntryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("RosterEntry", id));
    }

    private RosterResponse toResponse(RosterEntry entry) {
        RosterResponse response = new RosterResponse();
        response.setId(entry.getId());
        response.setStudentUserId(entry.getStudentUserId());
        response.setClassId(entry.getClassId());
        response.setTermId(entry.getTermId());
        response.setStudentIdNumber(entry.getStudentIdNumberEnc());
        response.setGuardianContact(entry.getGuardianContactEnc());
        response.setAccommodationNotes(entry.getAccommodationNotesEnc());
        response.setCreatedAt(entry.getCreatedAt());
        response.setUpdatedAt(entry.getUpdatedAt());
        return response;
    }
}
