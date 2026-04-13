package com.eaglepoint.exam.security.masking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a DTO field for value masking when the requesting user lacks
 * the {@code VIEW_SENSITIVE_DATA} permission.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskedField {

    /**
     * The masking strategy to apply.
     */
    MaskType maskType();

    /**
     * Supported masking types.
     */
    enum MaskType {
        /** Show only last 4 characters */
        STUDENT_ID,
        /** Replace entire value with asterisks */
        CONTACT,
        /** Replace entire value with asterisks */
        NOTES
    }
}
