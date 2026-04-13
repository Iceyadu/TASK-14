package com.eaglepoint.exam.security.masking;

import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Role;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MaskedFieldSerializer} covering STUDENT_ID masking,
 * CONTACT masking, unmasked output with permission, and null handling.
 */
@ExtendWith(MockitoExtension.class)
class MaskingTest {

    @Mock
    private JsonGenerator jsonGenerator;

    @Mock
    private SerializerProvider serializerProvider;

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testMaskedStudentId() throws IOException {
        // User without VIEW_HEALTH_DATA permission (e.g., HOMEROOM_TEACHER)
        RequestContext.set(1L, "teacher1", Role.HOMEROOM_TEACHER, "s-1", "127.0.0.1", "t-1");

        MaskedFieldSerializer serializer = new MaskedFieldSerializer(MaskedField.MaskType.STUDENT_ID);

        serializer.serialize("STU12345678", jsonGenerator, serializerProvider);

        // "STU12345678" has 11 chars, last 4 = "5678", masked = "*******5678"
        verify(jsonGenerator).writeString("*******5678");
    }

    @Test
    void testMaskedContact() throws IOException {
        // User without VIEW_HEALTH_DATA permission
        RequestContext.set(1L, "teacher1", Role.HOMEROOM_TEACHER, "s-1", "127.0.0.1", "t-1");

        MaskedFieldSerializer serializer = new MaskedFieldSerializer(MaskedField.MaskType.CONTACT);

        serializer.serialize("parent@school.local", jsonGenerator, serializerProvider);

        verify(jsonGenerator).writeString("****");
    }

    @Test
    void testUnmaskedWithPermission() throws IOException {
        // ADMIN has VIEW_HEALTH_DATA permission
        RequestContext.set(99L, "admin1", Role.ADMIN, "s-99", "127.0.0.1", "t-99");

        MaskedFieldSerializer serializer = new MaskedFieldSerializer(MaskedField.MaskType.STUDENT_ID);

        serializer.serialize("STU12345678", jsonGenerator, serializerProvider);

        // ADMIN can view sensitive data -> full value serialized
        verify(jsonGenerator).writeString("STU12345678");
    }

    @Test
    void testNullFieldMasked() throws IOException {
        RequestContext.set(1L, "teacher1", Role.HOMEROOM_TEACHER, "s-1", "127.0.0.1", "t-1");

        MaskedFieldSerializer serializer = new MaskedFieldSerializer(MaskedField.MaskType.STUDENT_ID);

        serializer.serialize(null, jsonGenerator, serializerProvider);

        verify(jsonGenerator).writeNull();
    }
}
