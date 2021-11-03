package com.tencent.bkrepo.executor.service.container

import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_NUMBER
import com.tencent.bkrepo.common.api.constant.DEFAULT_PAGE_SIZE
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.executor.config.ExecutorConfig
import com.tencent.bkrepo.executor.model.ScanReport
import com.tencent.bkrepo.executor.pojo.context.FileScanContext
import com.tencent.bkrepo.executor.pojo.context.RepoScanContext
import com.tencent.bkrepo.executor.pojo.ReportScanRecord
import com.tencent.bkrepo.executor.pojo.response.FileScanResponse
import com.tencent.bkrepo.executor.pojo.response.TaskRunResponse
import com.tencent.bkrepo.executor.service.Task
import com.tencent.bkrepo.executor.util.BashUtil
import com.tencent.bkrepo.executor.util.DockerUtil
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.stereotype.Service
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * 任务执行入口
 * 执行文件扫描任务、仓库扫描任务
 */
@Service
class ScanTask @Autowired constructor(
    private val dockerUtil: DockerUtil,
    private val nodeClient: NodeClient,
    private val storageService: StorageService,
    private val repositoryClient: RepositoryClient
) : Task {

    @Autowired
    lateinit var scanReport: ScanReport


    override fun runFile(context: FileScanContext): String {
        val task = {
            logger.info("start to run file [$context]")
            val workDir = "${context.config.rootDir}/${context.taskId}"
            val outputDir = "$workDir${context.config.outputDir}"
            val result = scanFile(context, workDir)
            reportOutput(context, outputDir, result)
            logger.info("finish  run file [$context] [$result]")
        }
        executor.submit(task)
        return context.taskId
    }

    /**
     * 仓库文件制品扫描任务入口
     */
    override fun runRepo(context: RepoScanContext): String {
        val totalPage = getTotalPage(context)
        logger.info("run repo scan task total page [$totalPage]")
        var pageNumber = DEFAULT_PAGE_NUMBER
        while (pageNumber.toLong() <= totalPage) {
            logger.info("start scan task  page [$pageNumber]")
            val queryModel = buildQueryModel(context, pageNumber, DEFAULT_PAGE_SIZE)
            val result = nodeClient.search(queryModel.build())
            if (result.isNotOk() || result.data == null || result.data!!.totalPages == 0L) {
                continue
            }
            result.data!!.records.forEach {
                val fileContext = FileScanContext(
                    taskId = context.taskId,
                    config = context.config,
                    projectId = context.projectId,
                    repoName = context.repoName,
                    fullPath = it["fullPath"] as String
                )
                val task = {
                    runFile(fileContext)
                }
                executor.submit(task)
            }
            logger.info("finish to submit task  page [$pageNumber]")
            pageNumber++
        }
        return context.taskId
    }

    override fun getTaskStatus(taskId: String, pageNum: Int?, pageSize: Int?): TaskRunResponse {
        if (pageNum == null || pageSize == null) {

            return TaskRunResponse
        }
    }

    private fun scanFile(context: FileScanContext, workDir: String): Boolean {
        val taskId = context.taskId

        // 生成存储表
        buildReportStore(taskId)

        // 生成运行时环境
        val runConfig = buildRunTime(workDir)
        if (!runConfig) {
            logger.warn("build runtime fail [$context]")
            return false
        }

        // 生成扫描文件
        val sha256 = loadFileToRunTime(context, workDir) ?: run {
            logger.warn("load file fail [$context]")
            return false
        }
        // 生成配置文件
        if (!loadConfigFile(taskId, workDir, context.config, sha256)) {
            logger.warn("load config file fail [$context]")
            return false
        }
        return dockerUtil.runContainerOnce(workDir)
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

    private fun loadFileToRunTime(context: FileScanContext, workDir: String): String? {
        with(context) {
            try {
                //  load file
                val repository = repositoryClient.getRepoDetail(projectId, repoName).data
                if (repository == null) {
                    logger.warn("fail to get the repo [$context]")
                    return null
                }
                val node = nodeClient.getNodeDetail(projectId, repoName, fullPath).data
                if (node == null) {
                    logger.warn("fail to get the node [$context]")
                    return null
                }
                val path = "$workDir${context.config.inputDir}${node.sha256}"
                val file = File(path)
                val inputStream = storageService.load(node.sha256!!, Range.full(node.size), repository.storageCredentials)
                inputStream.use {
                    FileUtils.copyInputStreamToFile(inputStream, file)
                }
                return node.sha256
            } catch (e: Exception) {
                logger.warn("load file to runtime exception [$e] ")
                return null
            }
        }
    }

    private fun buildReportStore(taskId: String) {
        fileMap.forEach {
            scanReport.buildReportCollection(taskId, it.key)
        }
    }

    private fun loadConfigFile(taskId: String, workDir: String, config: ExecutorConfig, sha256: String): Boolean {
        try {
            val template = File(config.configTemplateDir).readText()
            val params = mutableMapOf<String, String>()
            params["taskId"] = taskId
            params["sha256"] = sha256
            val parser = SpelExpressionParser()
            val parserContext = TemplateParserContext()
            val content = parser.parseExpression(template, parserContext).getValue(params, String::class.java)
            content?.let {
                val fileName = "$workDir/${config.configName}"
                val file = File(fileName)
                if (!file.exists()) {
                    file.createNewFile()
                }
                file.writeText(content)
                return true
            }
        } catch (e: Exception) {
            logger.warn("load config file exception [$taskId,$e] ")
        }
        return false
    }

    private fun buildRunTime(workDir: String): Boolean {

        // 清理生成workspace
        val cleanWorkSpace = "rm -rf $workDir"
        val buildWorkSpace = "mkdir -p $workDir"
        if (BashUtil.runCmd(cleanWorkSpace) && BashUtil.runCmd(buildWorkSpace)) {
            return true
        }
        return false
    }

    private fun reportOutput(context: FileScanContext, outputDir: String, status: Boolean): Boolean {
        fileMap.forEach {
            val file = File("$outputDir${it.value}")
            if (file.exists()) {
                with(context) {
                    val content = file.readText()
                    val reportContext = ReportScanRecord(
                        taskId = taskId,
                        projectId = projectId,
                        repoName = repoName,
                        fullPath = fullPath,
                        content = content,
                        status = status,
                        createAt = LocalDateTime.now()
                    )
                    scanReport.storeReport(reportContext, it.key)
                }
            }
        }
        return true
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ScanTask::class.java)

        private val fileMap = mapOf(
            "cvesec" to "cvesec_items.json",
            "checksec" to "checksec_items.json",
            "license" to "license_items.json",
            "sensitive" to "sensitive_info_items.json"
        )

        val executor: ThreadPoolExecutor = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            200, 60, TimeUnit.SECONDS, LinkedBlockingQueue(10000)
        )
    }
}
