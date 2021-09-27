package com.tencent.bkrepo.executor.service

import com.tencent.bkrepo.common.artifact.exception.ArtifactNotFoundException
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.executor.pojo.ArtifactScanContext
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
    fun run(context: ArtifactScanContext, taskId: String, rootDir: String): Boolean {

        //生成文件
        loadFileToRunTime(context, rootDir)

        //生成运行时环境
        val cmd = buildRunTime(taskId)
        //执行任务
        if (cmd != null) {
            val ll = exec(cmd)
            //采集输出
            reportOutput()
            return true
        }

        return false
    }

    private fun exec(cmd: String): Boolean {
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

    private fun loadFileToRunTime(context: ArtifactScanContext, taskId: String, rootDir: String): String? {
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


    private fun buildRunTime(taskId: String): String? {

        //生成命令行
        val workDir = "/data"
        val cmd = "docker run -it --rm -v $workDir/${taskId}:/data arrowhead /data/standalone.toml"

        //生成workspace
        val workSpace = "mkdir -p /data/${taskId}"
        if (exec(workSpace)) {
            return cmd
        }
        return null
    }

    private fun reportOutput() {

    }

    companion object {
        private val logger = LoggerFactory.getLogger(Task::class.java)
    }
}
