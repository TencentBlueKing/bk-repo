package com.tencent.bkrepo.executor.util

import org.slf4j.LoggerFactory

object BashUtil {

    fun runCmd(cmd: String): Boolean {
        try {
            val process = Runtime.getRuntime().exec(cmd)
            val exitVal = process.waitFor()
            if (exitVal == 0) {
                return true
            }
            logger.warn("task run fail [$cmd , $exitVal]")
        } catch (e: Exception) {
            logger.warn("exec task exception[$cmd, $e]")
        }
        return false
    }

    private val logger = LoggerFactory.getLogger(BashUtil::class.java)
}
