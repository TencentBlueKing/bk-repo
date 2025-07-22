/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.preview.service

import jakarta.annotation.PreDestroy
import org.apache.commons.lang3.StringUtils
import org.jodconverter.core.office.InstalledOfficeManagerHolder
import org.jodconverter.core.office.OfficeUtils
import org.jodconverter.core.util.OSUtils
import org.jodconverter.local.office.LocalOfficeManager
import org.jodconverter.local.office.LocalOfficeUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.convert.DurationStyle
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * 创建office文件转换器
 */
@Component
class OfficePluginManager {
    private var officeManager: LocalOfficeManager? = null

    @Value("\${preview.office.plugin.server.ports:2001,2002}")
    private val serverPorts = "2001,2002"

    @Value("\${preview.office.plugin.task.timeout:5m}")
    private val timeOut = "5m"

    @Value("\${preview.office.plugin.task.taskExecutionTimeout:5m}")
    private val taskExecutionTimeout = "5m"

    @Value("\${preview.office.plugin.task.maxTasksPerProcess:5}")
    private val maxTasksPerProcess = 5

    @Value("\${preview.office.home:/opt/libreoffice7.6}")
    private val officeHome: String = "/opt/libreoffice7.6"

    fun startOfficeManagerIfNeeded() {
        if (officeManager == null) {
            startOfficeManager()
        }
    }

    /**
     * 启动Office组件进程
     */
    private fun startOfficeManager() {
        val officeHome: File = getOfficeHome(officeHome)
        logger.info("Office component path：${officeHome.path}")
        val killOffice = killProcess()
        if (killOffice) {
            logger.warn("A running Office process has been detected and the process has been automatically terminated")
        }
        try {
            val portsString = serverPorts!!.split(",".toRegex()).toTypedArray()
            val ports = portsString.map { it.toInt() }.toIntArray()
            val timeout = DurationStyle.detectAndParse(timeOut).toMillis()
            val taskexecutiontimeout = DurationStyle.detectAndParse(taskExecutionTimeout).toMillis()
            officeManager = LocalOfficeManager.builder()
                .officeHome(officeHome)
                .portNumbers(*ports)
                .processTimeout(timeout)
                .maxTasksPerProcess(maxTasksPerProcess)
                .taskExecutionTimeout(taskexecutiontimeout)
                .build()
            officeManager?.start()
            InstalledOfficeManagerHolder.setInstance(officeManager)
        } catch (e: Exception) {
            logger.error("The office component fails to start, check whether the office component is available", e)
        }
    }

    private fun getOfficeHome(homePath: String?): File {
        if (homePath.isNullOrEmpty()) {
            return LocalOfficeUtils.getDefaultOfficeHome()
        }
        val officeHome = File(homePath)
        return if (officeHome.exists()) officeHome else LocalOfficeUtils.getDefaultOfficeHome()
    }

    private fun killProcess(): Boolean {
        var flag = false
        try {
            if (OSUtils.IS_OS_WINDOWS) {
                val p = Runtime.getRuntime().exec("cmd /c tasklist ")
                val baos = ByteArrayOutputStream()
                val os = p.inputStream
                val b = ByteArray(256)
                while (os.read(b) > 0) {
                    baos.write(b)
                }
                val s = baos.toString()
                if (s.contains("soffice.bin")) {
                    Runtime.getRuntime().exec("taskkill /im " + "soffice.bin" + " /f")
                    flag = true
                }
            } else if (OSUtils.IS_OS_MAC || OSUtils.IS_OS_MAC_OSX) {
                val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ps -ef | grep " + "soffice.bin"))
                val baos = ByteArrayOutputStream()
                val os = p.inputStream
                val b = ByteArray(256)
                while (os.read(b) > 0) {
                    baos.write(b)
                }
                val s = baos.toString()
                if (StringUtils.ordinalIndexOf(s, "soffice.bin", 3) > 0) {
                    val cmd = arrayOf("sh", "-c", "kill -15 `ps -ef|grep " + "soffice.bin" + "|awk 'NR==1{print $2}'`")
                    Runtime.getRuntime().exec(cmd)
                    flag = true
                }
            } else {
                val p = Runtime.getRuntime()
                    .exec(arrayOf("sh", "-c", "ps -ef | grep " + "soffice.bin" + " |grep -v grep | wc -l"))
                val baos = ByteArrayOutputStream()
                val os = p.inputStream
                val b = ByteArray(256)
                while (os.read(b) > 0) {
                    baos.write(b)
                }
                val s = baos.toString()
                if (!s.startsWith("0")) {
                    val cmd = arrayOf(
                        "sh",
                        "-c",
                        "ps -ef | grep soffice.bin | grep -v grep | awk '{print \"kill -9 \"$2}' | sh"
                    )
                    Runtime.getRuntime().exec(cmd)
                    flag = true
                }
            }
        } catch (e: IOException) {
            logger.error("Detect office process exceptions", e)
        }
        return flag
    }

    @PreDestroy
    fun destroyOfficeManager() {
        if (null != officeManager && officeManager!!.isRunning()) {
            logger.info("Shutting down office process")
            OfficeUtils.stopQuietly(officeManager)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OfficePluginManager::class.java)
    }
}
