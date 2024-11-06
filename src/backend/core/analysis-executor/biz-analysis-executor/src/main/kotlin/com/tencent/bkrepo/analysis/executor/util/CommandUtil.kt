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

package com.tencent.bkrepo.analysis.executor.util

import org.apache.commons.lang3.SystemUtils
import org.slf4j.LoggerFactory

object CommandUtil {
    private val logger = LoggerFactory.getLogger(CommandUtil::class.java)

    /**
     * 命令执行成功
     */
    const val EXEC_SUCCESS = 0

    /**
     * 命令执行失败
     */
    const val EXEC_FAILED = -1

    fun exec(command: String): Int {
        if (!SystemUtils.IS_OS_UNIX) {
            return EXEC_FAILED
        }
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/bin/bash", "-c", command))
            if (process.waitFor() != EXEC_SUCCESS) {
                val msg = process.errorStream.use { it.reader().readText() }
                logger.error("exec command[$command] failed: $msg")
            }
            return process.exitValue()
        } catch (e: Exception) {
            logger.error("exec command[$command] error", e)
        }
        return EXEC_FAILED
    }
}
