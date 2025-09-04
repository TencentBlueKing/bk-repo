/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.common.analysis.pojo.scanner

/**
 * 子扫描任务状态
 */
enum class SubScanTaskStatus {
    /**
     * 从未扫描过
     */
    NEVER_SCANNED,

    /**
     * 因项目配额不足任务阻塞
     */
    BLOCKED,

    /**
     * 子任务已创建
     */
    CREATED,

    /**
     * 已被拉取
     */
    PULLED,

    /**
     * 扫描执行中
     */
    EXECUTING,

    /**
     * 扫描停止
     */
    STOPPED,

    /**
     * 阻塞超时
     */
    BLOCK_TIMEOUT,

    /**
     * 扫描超时
     */
    TIMEOUT,

    /**
     * 扫描失败
     */
    FAILED,

    /**
     * 扫描成功
     */
    SUCCESS;

    companion object {
        val RUNNING_STATUS = listOf(CREATED.name, PULLED.name, EXECUTING.name)
        val UNFINISH_STATUS = listOf(BLOCKED.name, CREATED.name, PULLED.name)

        /**
         * 判断[status]是否是已结束的状态
         */
        private fun finishedStatus(status: SubScanTaskStatus): Boolean {
            return status == BLOCK_TIMEOUT
                || status == TIMEOUT
                || status == FAILED
                || status == STOPPED
                || status == SUCCESS
        }

        /**
         * 判断[status]是否是已结束的状态
         */
        fun finishedStatus(status: String): Boolean {
            return finishedStatus(valueOf(status))
        }
    }
}
