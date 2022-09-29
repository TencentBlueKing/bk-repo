/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.analysis.image

import com.tencent.bkrepo.common.analysis.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.analysis.pojo.scanner.arrowhead.ArrowheadScanner
import com.tencent.bkrepo.analysis.executor.arrowhead.AbsArrowheadScanExecutor
import com.tencent.bkrepo.analysis.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.analysis.executor.util.CommonUtils.buildLogMsg
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ArrowheadCmdScanExecutor(
    private val workDir: File = File(System.getProperty("java.io.tmpdir"))
) : AbsArrowheadScanExecutor() {
    private val configTemplate by lazy {
        javaClass.classLoader.getResourceAsStream("standalone.toml")!!.use { it.reader().readText() }
    }
    private val taskIdProcessMap = ConcurrentHashMap<String, Process>()

    override fun doScan(
        taskWorkDir: File,
        scannerInputFile: File,
        configFile: File,
        task: ScanExecutorTask
    ): SubScanTaskStatus {
        val scanner = task.scanner
        require(scanner is ArrowheadScanner)

        val process = Runtime.getRuntime().exec(arrayOf("/bin/bash", "-c", scanCommand(configFile)))
        try {
            taskIdProcessMap[task.taskId] = process
            logger.info(buildLogMsg(task, "running arrowhead [$taskWorkDir]"))
            val maxScanDuration = scanner.maxScanDuration(scannerInputFile.length())
            val result = process.waitFor(maxScanDuration, TimeUnit.MILLISECONDS)
            logger.info(buildLogMsg(task, "arrowhead run result[$result], [$taskWorkDir]"))
            if (!result) {
                return scanStatus(task, taskWorkDir, SubScanTaskStatus.TIMEOUT)
            }
            return scanStatus(task, taskWorkDir)
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                logger.info("destroy arrowhead process, process is alive[${process.isAlive}]")
            }
            taskIdProcessMap.remove(task.taskId)
        }
    }

    private fun scanCommand(configFile: File) =
        "/opt/sysauditor/bin/sys/arrowhead -cfg /opt/sysauditor/config/backend.toml -in ${configFile.absolutePath}"

    override fun stop(taskId: String): Boolean {
        return taskIdProcessMap[taskId]?.destroyForcibly()?.isAlive == false
    }

    override fun workDir() = workDir

    override fun configTemplate() = configTemplate

    companion object {
        private val logger = LoggerFactory.getLogger(ArrowheadCmdScanExecutor::class.java)
    }
}
