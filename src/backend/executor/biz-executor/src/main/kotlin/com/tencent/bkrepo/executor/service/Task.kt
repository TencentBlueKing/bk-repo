package com.tencent.bkrepo.executor.service

import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.executor.pojo.ArtifactScanContext
import com.tencent.bkrepo.executor.pojo.TaskRunTimeConfig
import com.tencent.bkrepo.repository.api.NodeClient
import com.tencent.bkrepo.repository.api.RepositoryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import org.apache.commons.io.FileUtils

/**
 * 任务执行入口
 * TODO:任务是否启用单独的线程去处理
 */
@Service
class Task @Autowired constructor(
    private val nodeClient: NodeClient,
    private val storageService: StorageService,
    private val repositoryClient: RepositoryClient
) {

    /**
     * 制品扫描任务触发接口
     */
    fun run(context: ArtifactScanContext, rootDir: String): Boolean {

        //生成运行时环境
        val cmd = buildRunTime(context.taskId)

        //生成文件
        loadFileToRunTime(context, rootDir)

        //执行任务
        if (cmd != null) {
            runTask(cmd.execCmd)
            //采集输出
            reportOutput(cmd.outputDir)
            return true
        }

        return false
    }

    private fun runTask(cmd: String): Boolean {
        try {
            val process = Runtime.getRuntime().exec(cmd)
            BufferedReader(InputStreamReader(process.inputStream))
            val exitVal = process.waitFor()
            if (exitVal == 0) {
                return true
            }
        } catch (e: Exception) {
            logger.warn("exec task exception[$cmd, $e]")

        }
        return false
    }

    private fun loadFileToRunTime(context: ArtifactScanContext, rootDir: String): String? {
        with(context) {
            try {
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
                val path = "$rootDir/$taskId/package/${node.sha256}"
                val file = File(path)
                val inputStream = storageService.load(node.sha256!!, Range.full(node.size), repository.storageCredentials)
                FileUtils.copyInputStreamToFile(inputStream, file)
                return node.sha256
            } catch (e: Exception) {
                logger.warn("load file to runtime exception [$e] ")
                return null
            }
        }
    }

    private fun loadConfigFile(taskId: String) {

    }


    private fun buildRunTime(taskId: String): TaskRunTimeConfig? {

        //生成命令行
        val workDir = "/data/${taskId}"
        val cmd = "docker run -it --rm -v $workDir:/data arrowhead /data/standalone.toml"
        val outputDir = "$workDir/output/"

        //生成workspace
        val workSpace = "mkdir -p /data/${taskId}"
        if (runTask(workSpace)) {
            return TaskRunTimeConfig(cmd, outputDir)
        }
        return null
    }

    private fun reportOutput(outputDir: String) {

    }

    companion object {
        private val logger = LoggerFactory.getLogger(Task::class.java)
    }
}
