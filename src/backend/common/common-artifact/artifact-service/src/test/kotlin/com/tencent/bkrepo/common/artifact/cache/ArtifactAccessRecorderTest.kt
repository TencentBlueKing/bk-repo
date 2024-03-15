package com.tencent.bkrepo.common.artifact.cache

import com.tencent.bkrepo.common.artifact.cache.dao.ArtifactAccessRecordDao
import com.tencent.bkrepo.fs.server.constant.FAKE_SHA256
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.util.unit.DataSize
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("制品访问记录器")
@DataMongoTest
@Import(
    ArtifactAccessRecordDao::class,
    PreloadProperties::class,
    ArtifactAccessRecorder::class,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ArtifactAccessRecorderTest @Autowired constructor(
    private val recorder: ArtifactAccessRecorder,
    private val accessRecordDao: ArtifactAccessRecordDao,
    private val preloadProperties: PreloadProperties,
) {

    @BeforeAll
    fun before() {
        preloadProperties.enabled = true
    }

    @Test
    fun testOnArtifactAccess() {
        val node = buildNodeDetail()
        with(node) {
            recorder.onArtifactAccess(node, true)
            var record = accessRecordDao.find(projectId, repoName, fullPath, sha256!!)
            Assertions.assertNotNull(record)
            Assertions.assertEquals(1, record!!.cacheMissCount)
            Assertions.assertEquals(1, record.accessTimeSequence.size)

            // test access interval
            Thread.sleep(1000)
            preloadProperties.minAccessInterval = Duration.ofMinutes(0L)
            recorder.onArtifactAccess(node, true)
            preloadProperties.minAccessInterval = Duration.ofMinutes(5L)
            recorder.onArtifactAccess(node, true)
            // test only record cache miss
            recorder.onArtifactAccess(node, false)
            // test invalid node
            recorder.onArtifactAccess(node.copy(size = 1L), true)
            record = accessRecordDao.find(projectId, repoName, fullPath, sha256!!)

            Assertions.assertNotNull(record)
            Assertions.assertEquals(2, record!!.cacheMissCount)
            Assertions.assertEquals(2, record.accessTimeSequence.size)
        }
    }

    private fun buildNodeDetail(): NodeDetail {
        val path = "/a/b/c"
        val name = "/d.txt"
        val nodeInfo = NodeInfo(
            createdBy = UT_USER,
            createdDate = LocalDateTime.now().toString(),
            lastModifiedBy = UT_USER,
            lastModifiedDate = LocalDateTime.now().toString(),
            lastAccessDate = LocalDateTime.now().toString(),
            folder = false,
            path = path,
            name = name,
            fullPath = "$path/$name",
            size = DataSize.ofGigabytes(2L).toBytes(),
            projectId = UT_PROJECT_ID,
            repoName = UT_REPO_NAME,
            sha256 = FAKE_SHA256,
            metadata = emptyMap()
        )
        return NodeDetail(nodeInfo)
    }
}
