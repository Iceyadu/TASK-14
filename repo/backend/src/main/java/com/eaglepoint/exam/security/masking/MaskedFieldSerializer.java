package com.eaglepoint.exam.security.masking;

import com.eaglepoint.exam.shared.context.RequestContext;
import com.eaglepoint.exam.shared.enums.Permission;
import com.eaglepoint.exam.shared.enums.Role;
import com.eaglepoint.exam.shared.enums.RolePermissions;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Jackson serializer that masks field values based on the {@link MaskedField}
 * annotation and the current user's permissions.
 * <p>
 * If the user holds {@link Permission#VIEW_HEALTH_DATA} (used as the sensitive
 * data permission), the full value is serialized. Otherwise the value is masked
 * according to the configured {@link MaskedField.MaskType}.
 */
public class MaskedFieldSerializer extends StdSerializer<String> implements ContextualSerializer {

    private MaskedField.MaskType maskType;

    public MaskedFieldSerializer() {
        super(String.class);
    }

    public MaskedFieldSerializer(MaskedField.MaskType maskType) {
        super(String.class);
        this.maskType = maskType;
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property != null) {
            MaskedField annotation = property.getAnnotation(MaskedField.class);
            if (annotation == null) {
                annotation = property.getContextAnnotation(MaskedField.class);
            }
            if (annotation != null) {
                return new MaskedFieldSerializer(annotation.maskType());
            }
        }
        return this;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        if (canViewSensitiveData()) {
            gen.writeString(value);
            return;
        }

        gen.writeString(mask(value));
    }

    private boolean canViewSensitiveData() {
        Role role = RequestContext.getRole();
        if (role == null) {
            return false;
        }
        return RolePermissions.hasPermission(role, Permission.VIEW_HEALTH_DATA);
    }

    private String mask(String value) {
        if (maskType == null) {
            return "****";
        }
        return switch (maskType) {
            case STUDENT_ID -> {
                if (value.length() <= 4) {
                    yield "****";
                }
                yield "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
            }
            case CONTACT, NOTES -> "****";
        };
    }
}
