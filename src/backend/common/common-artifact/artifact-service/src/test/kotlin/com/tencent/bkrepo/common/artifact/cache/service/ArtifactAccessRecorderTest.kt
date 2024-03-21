/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.common.artifact.cache.service

import com.tencent.bkrepo.common.artifact.cache.UT_PROJECT_ID
import com.tencent.bkrepo.common.artifact.cache.UT_REPO_NAME
import com.tencent.bkrepo.common.artifact.cache.UT_USER
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
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
    ArtifactPreloadProperties::class,
    ArtifactAccessRecorder::class,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties"])
class ArtifactAccessRecorderTest @Autowired constructor(
    private val recorder: ArtifactAccessRecorder,
    private val accessRecordDao: ArtifactAccessRecordDao,
    private val preloadProperties: ArtifactPreloadProperties,
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
