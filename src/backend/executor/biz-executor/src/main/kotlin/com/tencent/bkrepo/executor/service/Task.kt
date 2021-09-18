package com.tencent.bkrepo.executor.service

import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 任务执行入口
 */
class Task {

    /**
     * 制品扫描任务触发接口
     */
    fun run(taskId: String): Boolean {

        //生成运行时环境
        val cmd = buildRunTime(taskId)
        //执行任务
        if (cmd != null) {
            val ll = exec(cmd)
            //采集输出
            loadOutput()
            return true
        }

        return false
    }

    private fun exec(cmd: String): Boolean {
        try {
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val exitVal = process.waitFor()
            if (exitVal == 0) {
                return true
            }
        } catch (e: Exception) {
            logger.warn("exec task exception[$cmd, $e]")

        }
        return false
    }

    fun buildRunTime(taskId: String): String? {

        //生成命令行
        val workDir = "/data"
        val cmd = "docker run -it --rm -v /data/${taskId}:/data arrowhead /data/standalone.toml"

        //生成workspace
        val workSpace = "mkdir -p /data/${taskId}"
        if (exec(workSpace)) {
            return cmd
        }
        return null
    }

    private fun loadOutput() {

    }

    companion object {
        private val logger = LoggerFactory.getLogger(Task::class.java)
    }
}
