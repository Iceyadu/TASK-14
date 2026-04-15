package com.eaglepoint.exam.security.service;

import com.eaglepoint.exam.security.model.IdempotencyKey;
import com.eaglepoint.exam.security.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private IdempotencyKeyRepository repository;
    @Mock private ObjectMapper objectMapper;

    @Test
    void testCheckAndStoreReturnsNullWhenMissingOrExpired() {
        IdempotencyService service = new IdempotencyService(repository, objectMapper);
        when(repository.findByIdempotencyKeyAndUserIdAndOperationType("k1", 1L, "OP"))
                .thenReturn(Optional.empty());
        assertNull(service.checkAndStore("k1", 1L, "OP"));

        IdempotencyKey expired = new IdempotencyKey("k2", 1L, "OP", "{\"a\":1}",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().minusMinutes(1));
        when(repository.findByIdempotencyKeyAndUserIdAndOperationType("k2", 1L, "OP"))
                .thenReturn(Optional.of(expired));
        assertNull(service.checkAndStore("k2", 1L, "OP"));
    }

    @Test
    void testCheckAndStoreReturnsDeserializedObject() throws Exception {
        IdempotencyService service = new IdempotencyService(repository, objectMapper);
        IdempotencyKey active = new IdempotencyKey("k3", 2L, "OP", "{\"ok\":true}",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        when(repository.findByIdempotencyKeyAndUserIdAndOperationType("k3", 2L, "OP"))
                .thenReturn(Optional.of(active));
        when(objectMapper.readValue("{\"ok\":true}", Object.class)).thenReturn(Map.of("ok", true));

        Object result = service.checkAndStore("k3", 2L, "OP");
        assertNotNull(result);
    }

    @Test
    void testStoreResponsePersistsSerializedPayload() throws Exception {
        IdempotencyService service = new IdempotencyService(repository, objectMapper);
        when(objectMapper.writeValueAsString(Map.of("id", 1))).thenReturn("{\"id\":1}");

        service.storeResponse("k4", 3L, "CREATE", Map.of("id", 1));

        ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
        verify(repository).save(captor.capture());
        assertEquals("k4", captor.getValue().getIdempotencyKey());
        assertEquals(3L, captor.getValue().getUserId());
        assertEquals("CREATE", captor.getValue().getOperationType());
    }

    @Test
    void testStoreResponseSkipsWhenSerializationFails() throws Exception {
        IdempotencyService service = new IdempotencyService(repository, objectMapper);
        when(objectMapper.writeValueAsString(any())).thenThrow(JsonProcessingException.class);
        service.storeResponse("k5", 9L, "OP", Map.of("x", "y"));
        // no save invocation expected
        verify(repository, org.mockito.Mockito.never()).save(any());
    }
}
