package com.eaglepoint.exam.rooms.controller;

import com.eaglepoint.exam.rooms.model.Course;
import com.eaglepoint.exam.rooms.model.Grade;
import com.eaglepoint.exam.rooms.model.SchoolClass;
import com.eaglepoint.exam.rooms.repository.ClassRepository;
import com.eaglepoint.exam.rooms.repository.CourseRepository;
import com.eaglepoint.exam.rooms.repository.GradeRepository;
import com.eaglepoint.exam.scheduling.model.Term;
import com.eaglepoint.exam.scheduling.repository.TermRepository;
import com.eaglepoint.exam.security.annotation.RequirePermission;
import com.eaglepoint.exam.security.model.UserScopeAssignment;
import com.eaglepoint.exam.security.service.ScopeService;
import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.dto.ApiResponse;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for reference data lookups (terms, grades, classes, courses).
 */
@RestController
@RequestMapping("/api")
@RequirePermission(Permission.SESSION_VIEW)
public class ReferenceDataController {

    private final TermRepository termRepository;
    private final GradeRepository gradeRepository;
    private final ClassRepository classRepository;
    private final CourseRepository courseRepository;
    private final ScopeService scopeService;

    public ReferenceDataController(TermRepository termRepository,
                                   GradeRepository gradeRepository,
                                   ClassRepository classRepository,
                                   CourseRepository courseRepository,
                                   ScopeService scopeService) {
        this.termRepository = termRepository;
        this.gradeRepository = gradeRepository;
        this.classRepository = classRepository;
        this.courseRepository = courseRepository;
        this.scopeService = scopeService;
    }

    /**
     * Lists all terms.
     */
    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<List<Term>>> listTerms() {
        List<Term> terms = termRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(terms));
    }

    /**
     * Lists all grades.
     */
    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<Grade>>> listGrades() {
        List<Grade> grades = gradeRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(grades));
    }

    /**
     * Lists classes, filtered by scope for non-admin users.
     */
    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<SchoolClass>>> listClasses() {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        if (role == Role.ADMIN) {
            return ResponseEntity.ok(ApiResponse.success(classRepository.findAll()));
        }

        List<UserScopeAssignment> scopes = scopeService.filterByUserScope(userId, role, "CLASS");
        if (scopes.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<Long> scopedIds = scopes.stream()
                .map(UserScopeAssignment::getScopeId)
                .collect(Collectors.toList());
        List<SchoolClass> classes = classRepository.findAllById(scopedIds);
        return ResponseEntity.ok(ApiResponse.success(classes));
    }

    /**
     * Lists courses, filtered by scope for non-admin users.
     */
    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<Course>>> listCourses() {
        Long userId = RequestContext.getUserId();
        Role role = RequestContext.getRole();

        if (role == Role.ADMIN) {
            return ResponseEntity.ok(ApiResponse.success(courseRepository.findAll()));
        }

        List<UserScopeAssignment> scopes = scopeService.filterByUserScope(userId, role, "COURSE");
        if (scopes.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of()));
        }

        List<Long> scopedIds = scopes.stream()
                .map(UserScopeAssignment::getScopeId)
                .collect(Collectors.toList());
        List<Course> courses = courseRepository.findAllById(scopedIds);
        return ResponseEntity.ok(ApiResponse.success(courses));
    }
}
