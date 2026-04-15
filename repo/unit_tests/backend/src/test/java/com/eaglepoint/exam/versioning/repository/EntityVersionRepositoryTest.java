package com.eaglepoint.exam.versioning.repository;

import com.eaglepoint.exam.versioning.model.EntityVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "AES_ENCRYPTION_KEY=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE="
)
@ActiveProfiles({"test", "integration"})
class EntityVersionRepositoryTest {

    @Autowired
    private EntityVersionRepository entityVersionRepository;

    @Test
    void testFindByEntityAndVersionAndCount() {
        EntityVersion v1 = new EntityVersion();
        v1.setEntityType("ExamSession");
        v1.setEntityId(900L);
        v1.setVersionNumber(1);
        v1.setSnapshotJson("{\"name\":\"v1\"}");
        v1.setCreatedBy(1L);
        entityVersionRepository.save(v1);

        EntityVersion v2 = new EntityVersion();
        v2.setEntityType("ExamSession");
        v2.setEntityId(900L);
        v2.setVersionNumber(2);
        v2.setSnapshotJson("{\"name\":\"v2\"}");
        v2.setCreatedBy(1L);
        entityVersionRepository.save(v2);

        assertThat(entityVersionRepository.countByEntityTypeAndEntityId("ExamSession", 900L)).isEqualTo(2);
        assertThat(entityVersionRepository.findByEntityTypeAndEntityIdOrderByVersionNumberDesc("ExamSession", 900L))
                .extracting(EntityVersion::getVersionNumber)
                .containsExactly(2, 1);
        assertThat(entityVersionRepository.findByEntityTypeAndEntityIdAndVersionNumber("ExamSession", 900L, 1))
                .isPresent();
    }
}
