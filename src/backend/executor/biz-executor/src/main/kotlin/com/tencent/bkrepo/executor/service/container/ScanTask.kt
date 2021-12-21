package com.tencent.bkrepo.executor.service.container

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.executor.dao.ScanRunResult
import com.tencent.bkrepo.executor.exception.LoadFileFailedException
import com.tencent.bkrepo.executor.model.DockerRunTime
import com.tencent.bkrepo.executor.model.HostRunTime
import com.tencent.bkrepo.executor.pojo.ReportScanRecord
import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.pojo.context.RepoScanContext
import com.tencent.bkrepo.executor.pojo.context.ScanReportContext
import com.tencent.bkrepo.executor.pojo.context.ScanTaskContext
import com.tencent.bkrepo.executor.pojo.enums.TaskRunStatus
import com.tencent.bkrepo.executor.pojo.response.TaskRunResponse
import com.tencent.bkrepo.executor.service.Task
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 任务执行入口
 * 执行文件扫描任务、仓库扫描任务
 */
@Service
class ScanTask @Autowired constructor(
    private val dockerRunTime: DockerRunTime,
    private val nodeClient: NodeClient,
    private val hostRunTime: HostRunTime
) : Task {

    @Autowired
    lateinit var scanResult: ScanRunResult

    override fun runFile(context: FileScanContext): String {
        val task = {
            with(context) {
                val workDir = "${config.rootDir}/$taskId"
                val outputDir = "$workDir${config.outputDir}"
                // 生成当前task存储表
                buildReportStore(taskId)
                // 执行扫描
                scanFile(context, workDir, outputDir, taskId)
                logger.info("finish  run file [$context] ")
            }
        }
        executor.submit(task)
        return context.taskId
    }

    override fun runRepo(context: RepoScanContext): String {
        val totalPage = getTotalPage(context)
        var pageNumber = DEFAULT_PAGE_NUMBER

        logger.info("run repo scan task total page [$totalPage]")

        // 需要存储数据
        if (totalPage != 0L) {
            buildReportStore(context.taskId)
        }
        var index = 0
        while (pageNumber.toLong() <= totalPage) {
            logger.info("start scan task  page [$pageNumber]")
            val queryModel = buildQueryModel(context, pageNumber, DEFAULT_PAGE_SIZE)
            val result = nodeClient.search(queryModel.build())
            if (result.isNotOk() || result.data == null || result.data!!.totalPages == 0L) {
                continue
            }
            result.data!!.records.forEach {
                val tempIndex = index
                val task = {
                    val fileContext = FileScanContext(
                        taskId = context.taskId,
                        config = context.config,
                        projectId = context.projectId,
                        repoName = context.repoName,
                        fullPath = it["fullPath"] as String
                    )
                    val runTaskId = "${context.taskId}$tempIndex"
                    val workDir = "${context.config.rootDir}/$runTaskId"
                    val outputDir = "$workDir${context.config.outputDir}"
                    scanFile(fileContext, workDir, outputDir, runTaskId)
                }
                index++
                executor.submit(task)
            }
            logger.info("finish to submit task  page [$pageNumber]")
            pageNumber++
        }
        return context.taskId
    }

    override fun getTaskStatus(taskId: String, pageNum: Int?, pageSize: Int?): TaskRunResponse {
        val totalNum = scanResult.getTotalTaskCount(taskId)
        val records = scanResult.getTotalTaskRecord(taskId, pageNum, pageSize)
        return TaskRunResponse(totalNum, records)
    }

    override fun getTaskReport(context: ScanReportContext): MutableList<*>? {
        with(context) {
            val result = scanResult.geTaskRecord(taskId, report, projectId, repoName, fullPath) ?: run {
                return null
            }
            return JsonUtils.objectMapper.readValue(result, MutableList::class.java)
        }
    }

    /**
     * 文件扫描入口
     */
    private fun scanFile(context: FileScanContext, workDir: String, outputDir: String, runTaskId: String) {
        with(context) {

            logger.info("start to run file [$context]")
            val scanContext = ScanTaskContext(taskId, projectId, repoName, fullPath, TaskRunStatus.START)
            scanResult.setTaskStatus(scanContext)

            try {
                // 生成运行时环境
                hostRunTime.buildWorkSpace(workDir)

                // 生成扫描文件
                val sha256 = hostRunTime.loadFileToRunTime(context, workDir) ?: run {
                    logger.warn("load file fail [$context]")
                    throw LoadFileFailedException("load file failed")
                }

                // 生成配置文件
                hostRunTime.loadConfigFile(runTaskId, workDir, config, sha256)

                // 执行任务
                dockerRunTime.runContainerOnce(workDir)

                // 输出报告
                reportOutput(context, outputDir)
                scanContext.status = TaskRunStatus.FINISH
                scanResult.setTaskStatus(scanContext)
            } catch (e: RuntimeException) {
                logger.warn("scan file exception [$context, $e]")
                scanContext.status = TaskRunStatus.FAILED
                scanResult.setTaskStatus(scanContext)
            } finally {
                // 清理工作目录
                if (context.config.clean) {
                    hostRunTime.cleanWorkSpace(workDir)
                }
            }
        }
    }

    private fun buildQueryModel(context: RepoScanContext, pageNumber: Int, pageSize: Int): NodeQueryBuilder {
        val queryModel = NodeQueryBuilder().select("fullPath").projectId(context.projectId).repoName(context.repoName)
        context.name?.let {
            if (context.rule == null) {
                queryModel.name(context.name, OperationType.MATCH)
            } else {
                queryModel.name(context.name, context.rule)
            }
        }
        return queryModel.page(pageNumber, pageSize)
    }

    private fun getTotalPage(context: RepoScanContext): Long {
        val queryModel = buildQueryModel(context, DEFAULT_PAGE_NUMBER, DEFAULT_PAGE_SIZE)
        val result = nodeClient.search(queryModel.build())
        if (result.isNotOk() || result.data == null || result.data!!.totalPages == 0L) {
            return 0L
        }
        return result.data!!.totalPages
    }

    private fun buildReportStore(taskId: String) {
        scanResult.buildTaskCollection(taskId)
        fileMap.forEach {
            scanResult.buildReportCollection(taskId, it.key)
        }
    }

    private fun reportOutput(context: FileScanContext, outputDir: String): Boolean {
        fileMap.forEach {
            val file = File("$outputDir${it.value}")
            if (!file.exists()) return@forEach
            with(context) {
                val content = file.readText()
                val reportContext = ReportScanRecord(
                    taskId = taskId,
                    projectId = projectId,
                    repoName = repoName,
                    fullPath = fullPath,
                    content = content.toJsonString()
                )
                scanResult.setReport(reportContext, it.key)
            }
        }
        return true
    }

    companion object {

        private val logger = LoggerFactory.getLogger(ScanTask::class.java)

        // 任务执行线程池
        private val executor: ThreadPoolExecutor = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            200, 60, TimeUnit.SECONDS, LinkedBlockingQueue(10000)
        )

        private val fileMap = mapOf(
            "cvesec" to "cvesec_items.json",
            "checksec" to "checksec_items.json",
            "license" to "license_items.json",
            "sensitive" to "sensitive_info_items.json"
        )
    }
}
