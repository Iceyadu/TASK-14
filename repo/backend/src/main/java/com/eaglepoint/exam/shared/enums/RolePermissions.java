package com.eaglepoint.exam.shared.enums;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Static mapping from {@link Role} to the {@link Permission}s granted to that role.
 */
public final class RolePermissions {

    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS;

    static {
        Map<Role, Set<Permission>> map = new EnumMap<>(Role.class);

        // ADMIN - all permissions
        map.put(Role.ADMIN, Collections.unmodifiableSet(EnumSet.allOf(Permission.class)));

        // ACADEMIC_COORDINATOR - scoped roster/session/room/compliance_submit/notification/version/job_monitor/anticheat_review
        map.put(Role.ACADEMIC_COORDINATOR, Collections.unmodifiableSet(EnumSet.of(
                Permission.ROSTER_VIEW,
                Permission.ROSTER_CREATE,
                Permission.ROSTER_IMPORT,
                Permission.ROSTER_EXPORT,
                Permission.SESSION_VIEW,
                Permission.SESSION_CREATE,
                Permission.SESSION_EDIT,
                Permission.SESSION_SUBMIT_REVIEW,
                Permission.SESSION_PUBLISH,
                Permission.ROOM_MANAGE,
                Permission.PROCTOR_ASSIGN,
                Permission.COMPLIANCE_SUBMIT,
                Permission.NOTIFICATION_CREATE,
                Permission.NOTIFICATION_PUBLISH,
                Permission.VERSION_VIEW,
                Permission.VERSION_RESTORE,
                Permission.JOB_MONITOR,
                Permission.ANTICHEAT_REVIEW
        )));

        // HOMEROOM_TEACHER
        map.put(Role.HOMEROOM_TEACHER, Collections.unmodifiableSet(EnumSet.of(
                Permission.ROSTER_VIEW,
                Permission.ROSTER_EXPORT,
                Permission.SESSION_VIEW,
                Permission.NOTIFICATION_CREATE,
                Permission.VERSION_VIEW
        )));

        // SUBJECT_TEACHER
        map.put(Role.SUBJECT_TEACHER, Collections.unmodifiableSet(EnumSet.of(
                Permission.ROSTER_VIEW,
                Permission.ROSTER_EXPORT,
                Permission.SESSION_VIEW,
                Permission.NOTIFICATION_CREATE,
                Permission.VERSION_VIEW
        )));

        // STUDENT
        map.put(Role.STUDENT, Collections.unmodifiableSet(EnumSet.of(
                Permission.SESSION_VIEW,
                Permission.SUBSCRIPTION_MANAGE,
                Permission.INBOX_VIEW
        )));

        ROLE_PERMISSIONS = Collections.unmodifiableMap(map);
    }

    private RolePermissions() {
        // utility class
    }

    /**
     * Returns the immutable set of permissions for the given role.
     */
    public static Set<Permission> getPermissions(Role role) {
        return ROLE_PERMISSIONS.getOrDefault(role, Collections.emptySet());
    }

    /**
     * Checks whether the given role has the specified permission.
     */
    public static boolean hasPermission(Role role, Permission permission) {
        return getPermissions(role).contains(permission);
    }
}
