package com.tencent.bkrepo.common.metadata.service.separation.impl

import com.tencent.bkrepo.common.metadata.dao.separation.SeparationFailedRecordDao
import com.tencent.bkrepo.common.metadata.dao.separation.SeparationTaskDao
import com.tencent.bkrepo.common.metadata.model.TSeparationTask
import com.tencent.bkrepo.common.metadata.pojo.separation.SeparationContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime

@DataMongoTest
@Import(
    SeparationTaskServiceImpl::class,
    SeparationTaskDao::class,
    SeparationFailedRecordDao::class,
    SeparationTaskServiceImplMongoTestConfig::class,
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("SeparationTaskServiceImpl findDistinctSeparationDate 嵌入式 Mongo")
class SeparationTaskServiceImplMongoIntegrationTest @Autowired constructor(
    private val service: SeparationTaskServiceImpl,
    private val mongoTemplate: MongoTemplate,
) {

    private val stamp = LocalDateTime.of(2024, 3, 1, 10, 0)

    @BeforeEach
    fun clean() {
        mongoTemplate.dropCollection("separation_task")
    }

    @Test
    @DisplayName("指定 taskType 时只收集该类型的 separationDate")
    fun findDistinctSeparationDate_respectsTaskType() {
        val dArchived = LocalDateTime.of(2023, 1, 1, 0, 0)
        val dSeparate = LocalDateTime.of(2023, 6, 1, 0, 0)
        mongoTemplate.save(
            task(dArchived, SeparationTaskServiceImpl.SEPARATE_ARCHIVED),
        )
        mongoTemplate.save(
            task(dSeparate, SeparationTaskServiceImpl.SEPARATE),
        )
        val dates = service.findDistinctSeparationDate("p", "r", SeparationTaskServiceImpl.SEPARATE_ARCHIVED)
        assertEquals(setOf(dArchived), dates)
    }

    @Test
    @DisplayName("未指定 taskType 时仅包含 SEPARATE / SEPARATE_ARCHIVED")
    fun findDistinctSeparationDate_defaultExcludesRestore() {
        val dSep = LocalDateTime.of(2022, 1, 1, 0, 0)
        val dRestore = LocalDateTime.of(2022, 2, 1, 0, 0)
        mongoTemplate.save(task(dSep, SeparationTaskServiceImpl.SEPARATE))
        mongoTemplate.save(task(dRestore, SeparationTaskServiceImpl.RESTORE))
        val dates = service.findDistinctSeparationDate("p", "r", null)
        assertEquals(setOf(dSep), dates)
    }

    private fun task(separationDate: LocalDateTime, type: String) = TSeparationTask(
        projectId = "p",
        repoName = "r",
        createdBy = "u",
        createdDate = stamp,
        lastModifiedBy = "u",
        lastModifiedDate = stamp,
        separationDate = separationDate,
        content = SeparationContent(),
        type = type,
    )
}
