package com.tencent.bkrepo.analyst.service

import com.tencent.bkrepo.analyst.AnalystBaseTest
import com.tencent.bkrepo.analyst.NODE_FULL_PATH
import com.tencent.bkrepo.analyst.NODE_NAME
import com.tencent.bkrepo.analyst.NODE_SHA256
import com.tencent.bkrepo.analyst.PROJECT_ID
import com.tencent.bkrepo.analyst.REPO
import com.tencent.bkrepo.analyst.UT_DISPATCHER
import com.tencent.bkrepo.analyst.UT_PLAN_ID
import com.tencent.bkrepo.analyst.UT_SCANNER
import com.tencent.bkrepo.analyst.UT_USER
import com.tencent.bkrepo.analyst.dao.ArchiveSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.FileScanResultDao
import com.tencent.bkrepo.analyst.dao.PlanArtifactLatestSubScanTaskDao
import com.tencent.bkrepo.analyst.dao.ScanPlanDao
import com.tencent.bkrepo.analyst.dao.ScanTaskDao
import com.tencent.bkrepo.analyst.dao.SubScanTaskDao
import com.tencent.bkrepo.analyst.model.TScanTask
import com.tencent.bkrepo.analyst.model.TSubScanTask
import com.tencent.bkrepo.analyst.pojo.TaskMetadata
import com.tencent.bkrepo.analyst.pojo.TaskMetadata.Companion.TASK_METADATA_DISPATCHER
import com.tencent.bkrepo.analyst.service.impl.ScanTaskServiceImpl
import com.tencent.bkrepo.common.analysis.pojo.scanner.Scanner
import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.metadata.service.node.NodeService
import com.tencent.bkrepo.common.metadata.service.repo.RepositoryService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.unit.DataSize
import java.time.LocalDateTime
import kotlin.math.ceil

@Import(
    ScanTaskServiceImpl::class,
)
@DataMongoTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ComponentScan("com.tencent.bkrepo.analyst.component.manager" )
class ScanTaskServiceTest @Autowired constructor(
    private val scanTaskService: ScanTaskService,
) : AnalystBaseTest() {

    @MockitoBean
    lateinit var nodeService: NodeService

    @MockitoBean
    lateinit var repositoryService: RepositoryService

    @MockitoBean
    lateinit var scannerService: ScannerService

    @MockitoBean
    lateinit var spdxLicenseService: SpdxLicenseService

    @MockitoBean
    lateinit var filterRuleService: FilterRuleService

    @MockitoBean
    lateinit var scanPlanDao: ScanPlanDao

    @MockitoBean
    lateinit var scanTaskDao: ScanTaskDao

    @MockitoBean
    lateinit var subScanTaskDao: SubScanTaskDao
    @MockitoBean
    lateinit var archiveSubScanTaskDao: ArchiveSubScanTaskDao

    @MockitoBean
    lateinit var planArtifactLatestSubScanTaskDao: PlanArtifactLatestSubScanTaskDao
    @MockitoBean
    lateinit var fileScanResultDao: FileScanResultDao

    private val scanner : Scanner = buildScanner()

    @BeforeEach
    fun setup() {
        whenever(scanTaskDao.findById(anyString())).thenReturn( buildScanTask())
        whenever(subScanTaskDao.findByParentId(anyString())).thenReturn(
            listOf(
                buildSubScanTask(LocalDateTime.now(), SubScanTaskStatus.PULLED)
            )
        )
        whenever(scannerService.get(anyString())).thenReturn(scanner)
    }

    @Test
    @DisplayName("测试无队列等待时间")
    fun testNoQueueWaitingTime() {
        val waitingTime = scanTaskService.taskWaitTime(TEST_TASK_ID)
        assert(waitingTime.order == 0)
        assert(waitingTime.waitingTime == ceil(TEST_NODE_SIZE.toDouble() / scanner.scanRate).toLong())
    }

    @Test
    @DisplayName("测试排队第1个")
    fun testZeroOrderWaitingTime() {
        val startTime = LocalDateTime.now()
        whenever(subScanTaskDao.tasksCreatedBefore(any(), anyString())).thenReturn(
            listOf(
                buildSubScanTask(startTime, SubScanTaskStatus.PULLED),
                buildSubScanTask(LocalDateTime.now(), SubScanTaskStatus.EXECUTING)
            )
        )
        val waitingTime = scanTaskService.taskWaitTime(TEST_TASK_ID)
        assert(waitingTime.order == 1)
        assert(waitingTime.waitingTime > TEST_NODE_SIZE / scanner.scanRate)
    }

    @Test
    @DisplayName("测试排队数量多于执行的数量")
    fun testNOrderWaitingTime() {
        whenever(subScanTaskDao.tasksCreatedBefore(any(), anyString())).thenReturn(
            listOf(
                buildSubScanTask(LocalDateTime.now(), SubScanTaskStatus.PULLED),
                buildSubScanTask(LocalDateTime.now(), SubScanTaskStatus.PULLED),
                buildSubScanTask(LocalDateTime.now(), SubScanTaskStatus.EXECUTING)
            )
        )
        val waitingTime = scanTaskService.taskWaitTime(TEST_TASK_ID)
        assert(waitingTime.order == 2)
    }


    private fun buildScanTask(): TScanTask {
        return TScanTask(
            id = TEST_TASK_ID,
            createdBy = UT_USER,
            createdDate = LocalDateTime.now(),
            lastModifiedBy = UT_USER,
            lastModifiedDate = LocalDateTime.now(),
            name = "test_task",
            startDateTime = LocalDateTime.now(),
            finishedDateTime = LocalDateTime.now(),
            triggerType = "test_trigger_type",
            planId = UT_PLAN_ID,
            projectId = PROJECT_ID,
            projectIds = emptySet(),
            status = "test_status",
            rule = "test_rule",
            scanning = 1,
            failed = 0,
            scanned = 0,
            passed = 0,
            scanner = UT_SCANNER,
            scannerType = "test_scanner_type",
            scannerVersion = "test_scanner_version",
            scanResultOverview = emptyMap(),
            metadata = listOf(TaskMetadata(key = TASK_METADATA_DISPATCHER, value = UT_DISPATCHER)),
            total = 1
        )
    }

    private fun buildSubScanTask(startDateTime: LocalDateTime, status: SubScanTaskStatus): TSubScanTask {
        return TSubScanTask(
            id = "",
            createdDate = LocalDateTime.now(),
            createdBy = UT_USER,
            lastModifiedDate = LocalDateTime.now(),
            lastModifiedBy = UT_USER,
            startDateTime = startDateTime,
            timeoutDateTime = LocalDateTime.now(),
            heartbeatDateTime = LocalDateTime.now(),
            parentScanTaskId = TEST_TASK_ID,
            projectId = PROJECT_ID,
            repoName = REPO,
            repoType = RepositoryType.GENERIC.name,
            fullPath = NODE_FULL_PATH,
            artifactName = NODE_NAME,
            status = status.name,
            executedTimes = 0,
            scanner = UT_SCANNER,
            scannerType = "",
            sha256 = NODE_SHA256,
            size = TEST_NODE_SIZE,
            planId = UT_PLAN_ID,
            credentialsKey = ""
        )
    }

    private fun buildScanner(): Scanner {
        return Scanner(
            name = UT_SCANNER,
            type = "",
            version = "",
        )
    }


    companion object {
        private const val TEST_TASK_ID = "test_task_id"
        private val TEST_NODE_SIZE = DataSize.ofMegabytes(1024).toBytes()
    }
}