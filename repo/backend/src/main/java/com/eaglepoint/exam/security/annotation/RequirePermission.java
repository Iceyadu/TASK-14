package com.eaglepoint.exam.security.annotation;

import com.eaglepoint.exam.shared.enums.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate controller methods to restrict access to users whose role
 * includes at least one of the listed permissions.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * One or more permissions. The user must hold at least one of these.
     */
    Permission[] value();
}
