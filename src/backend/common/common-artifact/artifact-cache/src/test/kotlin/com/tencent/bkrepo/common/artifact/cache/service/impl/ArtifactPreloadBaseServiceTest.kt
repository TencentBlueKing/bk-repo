/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2024 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.artifact.cache.service.impl

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.FileSystemArtifactFile
import com.tencent.bkrepo.common.artifact.cache.NoopObservationRegistry
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadConfiguration
import com.tencent.bkrepo.common.artifact.cache.config.ArtifactPreloadProperties
import com.tencent.bkrepo.common.artifact.metrics.ArtifactMetricsConfiguration
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import com.tencent.bkrepo.common.metadata.service.repo.StorageCredentialService
import com.tencent.bkrepo.common.storage.StorageAutoConfiguration
import com.tencent.bkrepo.common.storage.config.StorageProperties
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.core.cache.CacheStorageService
import com.tencent.bkrepo.common.storage.core.locator.FileLocator
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
@DataMongoTest
@ImportAutoConfiguration(
    ArtifactPreloadConfiguration::class,
    StorageAutoConfiguration::class,
    TaskExecutionAutoConfiguration::class,
    ArtifactMetricsConfiguration::class
)
@TestPropertySource(locations = ["classpath:bootstrap-ut.properties", "classpath:application-test.properties"])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(NoopObservationRegistry::class)
open class ArtifactPreloadBaseServiceTest(
    protected val properties: ArtifactPreloadProperties,
    protected val storageService: StorageService,
    protected val fileLocator: FileLocator,
    protected val storageProperties: StorageProperties,
) {

    @MockitoBean
    protected lateinit var nodeService: NodeService

    @MockitoBean
    protected lateinit var repositoryService: RepositoryService

    @MockitoBean
    protected lateinit var storageCredentialService: StorageCredentialService

    @MockitoBean
    protected lateinit var meterRegistry: MeterRegistry

    protected fun createTempArtifactFile(size: Long): ArtifactFile {
        val tempFile = File.createTempFile("preload-", ".tmp")
        val content = StringPool.randomString(size.toInt())
        content.byteInputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return FileSystemArtifactFile(tempFile)
    }

    protected fun deleteCache(credentials: StorageCredentials, sha256: String): Path {
        require(storageService is CacheStorageService)
        val path = fileLocator.locate(sha256)
        storageService.deleteCacheFile(path, sha256, credentials)
        val cachePath = Paths.get(credentials.cache.path, path, sha256)
        Assertions.assertFalse(cachePath.toFile().exists())
        Assertions.assertTrue(storageService.exist(sha256, credentials))
        return cachePath
    }
}
