/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2025 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.analyst.job

import com.tencent.bkrepo.analyst.AnalystBaseTest
import com.tencent.bkrepo.analyst.UT_SCANNER
import com.tencent.bkrepo.analyst.component.manager.standard.dao.SecurityResultDao
import com.tencent.bkrepo.analyst.configuration.ScannerProperties
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.FileScanResultDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.service.ScannerService
import com.tencent.bkrepo.analyst.service.impl.ScannerServiceImpl
import com.tencent.bkrepo.analyst.utils.buildArchiveSubScanTask
import com.tencent.bkrepo.analyst.utils.buildFileResult
import com.tencent.bkrepo.analyst.utils.buildPlanSubScanTask
import com.tencent.bkrepo.analyst.utils.buildScanPlan
import com.tencent.bkrepo.analyst.utils.buildScanResult
import com.tencent.bkrepo.analyst.utils.buildScanTask
import com.tencent.bkrepo.analyst.utils.buildSecurityResult
import com.tencent.bkrepo.analyst.utils.randomSha256
import com.tencent.bkrepo.common.analysis.pojo.scanner.CveOverviewKey
import com.tencent.bkrepo.common.analysis.pojo.scanner.standard.StandardScanner
import com.tencent.bkrepo.common.job.JobAutoConfiguration
import com.tencent.bkrepo.common.mongo.constant.MIN_OBJECT_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.mongodb.core.query.Query
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("制品报告清理任务测试")
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ScannerServiceImpl::class, JobAutoConfiguration::class, ScanTaskCleanupJob::class)
@ComponentScan("com.tencent.bkrepo.analyst.dao", "com.tencent.bkrepo.analyst.component.manager")
class ScanTaskCleanupJobTest @Autowired constructor(
    private val scannerProperties: ScannerProperties,
    private val scanTaskDao: ScanTaskDao,
    private val archiveSubScanTaskDao: ArchiveSubScanTaskDao,
    private val planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao,
    private val fileScanResultDao: FileScanResultDao,
    private val scanPlanDao: ScanPlanDao,
    private val scannerService: ScannerService,
    private val scanTaskCleanupJob: ScanTaskCleanupJob,
    private val securityResultDao: SecurityResultDao,
) : AnalystBaseTest() {

    @BeforeAll
    fun beforeAll() {
        scannerService.create(
            StandardScanner(
                UT_SCANNER, "", "", "", "", "1.0.0"
            )
        )
    }

    @BeforeEach
    fun beforeEach() {
        scanTaskDao.remove(Query())
    }

    @Test
    fun `test clean`() {
        mockData(LocalDateTime.now().minusDays(7))
        scannerProperties.reportKeepDuration = Duration.ofDays(1L)
        val context = ScanTaskCleanupJob.CleanContext()
        scanTaskCleanupJob.doClean(context)
        assert(context)
    }

    @Test
    fun `test clean expired task`() {
        scanTaskDao.insert(buildScanTask(LocalDateTime.now().minusDays(7L)))
        scanTaskDao.insert(buildScanTask(LocalDateTime.now().minusDays(1L)))
        scanTaskDao.insert(buildScanTask(LocalDateTime.now().minusDays(7L)))
        assertEquals(3, scanTaskDao.count(Query()))

        scannerProperties.reportKeepDuration = Duration.ofDays(3L)
        val context = ScanTaskCleanupJob.CleanContext()
        scanTaskCleanupJob.doClean(context)
        // 清理到未过期任务后即使还存在过期任务也会停止清理，因此会剩下两个任务未清理
        assertEquals(2, scanTaskDao.count(Query()))
        assertEquals(1, context.taskCount.get())
    }

    @Test
    fun `test do not clean when keep duration is zero`() {
        assertEquals(0, scanTaskDao.count(Query()))
        scanTaskDao.insert(buildScanTask(LocalDateTime.now().minusDays(7L)))

        scannerProperties.reportKeepDuration = Duration.ofDays(0L)
        val context = ScanTaskCleanupJob.CleanContext()
        scanTaskCleanupJob.doClean(context)

        assertEquals(1, scanTaskDao.count(Query()))
        assertEquals(0, context.taskCount.get())
    }

    private fun mockData(now: LocalDateTime) {
        // plan
        val overview = mapOf(
            CveOverviewKey.CVE_LOW_COUNT.key to 90L,
            CveOverviewKey.CVE_HIGH_COUNT.key to 180L,
        )
        scanPlanDao.insert(buildScanPlan(now, overview))

        // task
        val task1 = scanTaskDao.insert(buildScanTask(now))
        val task2 = scanTaskDao.insert(buildScanTask(now))
        val task3 = scanTaskDao.insert(buildScanTask(now.plusDays(30L)))

        // task1
        for (i in 0 until 40) {
            // subtask
            val archiveSubtask = archiveSubScanTaskDao.insert(buildArchiveSubScanTask(task1.id!!, randomSha256(), now))
            // file result overview
            if (i % 10 == 0) {
                val oldId = archiveSubtask.id!!
                archiveSubtask.id = "xxxxxx"
                planArtifactLatestSubScanTaskDao.insert(buildPlanSubScanTask(archiveSubtask))
                archiveSubtask.id = oldId
                val scanResult = mapOf(
                    UT_SCANNER to buildScanResult(now, archiveSubtask.parentScanTaskId),
                    "test" to buildScanResult(now, MIN_OBJECT_ID)
                )
                val fileResult = buildFileResult(now, archiveSubtask.sha256, archiveSubtask.parentScanTaskId)
                    .copy(scanResult = scanResult)
                fileScanResultDao.insert(fileResult)

                // insert reports
                securityResultDao.insert(buildSecurityResult(archiveSubtask.sha256))
                securityResultDao.insert(buildSecurityResult(archiveSubtask.sha256, "test"))
            } else {
                planArtifactLatestSubScanTaskDao.insert(buildPlanSubScanTask(archiveSubtask))
                fileScanResultDao.insert(buildFileResult(now, archiveSubtask.sha256, archiveSubtask.parentScanTaskId))
                // insert reports
                securityResultDao.insert(buildSecurityResult(archiveSubtask.sha256))
            }
        }

        // task2
        for (i in 0 until 43) {
            val archiveSubtask = archiveSubScanTaskDao.insert(buildArchiveSubScanTask(task2.id!!, randomSha256(), now))
            planArtifactLatestSubScanTaskDao.insert(buildPlanSubScanTask(archiveSubtask))
            // file result overview
            fileScanResultDao.insert(buildFileResult(now, archiveSubtask.sha256, archiveSubtask.parentScanTaskId))
        }

        // task3
        for (i in 0 until 10) {
            val archiveSubtask = archiveSubScanTaskDao.insert(buildArchiveSubScanTask(task3.id!!, randomSha256(), now))
            planArtifactLatestSubScanTaskDao.insert(buildPlanSubScanTask(archiveSubtask))
            // file result overview
            fileScanResultDao.insert(buildFileResult(now, archiveSubtask.sha256, archiveSubtask.parentScanTaskId))
        }
    }


    private fun assert(context: ScanTaskCleanupJob.CleanContext) {
        // tasks
        assertEquals(1L, scanTaskDao.count(Query()))
        assertEquals(2, context.taskCount.get())

        // subtasks
        assertEquals(10, archiveSubScanTaskDao.count(Query()))
        assertEquals(83L, context.archivedSubtaskCount.get())
        assertEquals(14, planArtifactLatestSubScanTaskDao.count(Query()))
        assertEquals(79L, context.planArtifactTaskCount.get())

        // file result overview
        assertEquals(14L, fileScanResultDao.count(Query()))
        assertEquals(83L, context.overviewResultCount.get())
        assertEquals(10, fileScanResultDao.find(Query()).count { it.scanResult.containsKey(UT_SCANNER) })

        // reports
        assertEquals(4L, securityResultDao.count(Query()))
        assertEquals(40L, context.reportResultCount.get())

        // plan
        val plan = scanPlanDao.findOne(Query())!!
        assertEquals(11, plan.scanResultOverview[CveOverviewKey.CVE_LOW_COUNT.key])
        assertEquals(22, plan.scanResultOverview[CveOverviewKey.CVE_HIGH_COUNT.key])
    }
}
