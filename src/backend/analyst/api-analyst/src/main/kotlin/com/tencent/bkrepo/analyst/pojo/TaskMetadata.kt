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

package com.tencent.bkrepo.analyst.pojo

data class TaskMetadata(
    val key: String,
    val value: String
) {
    companion object {
        /**
         * 所属构建id metadata key
         */
        const val TASK_METADATA_KEY_BID = "BUILD_ID"

        /**
         * 所属流水线的构建号
         */
        const val TASK_METADATA_BUILD_NUMBER = "BUILD_NUMBER"

        /**
         * 所属流水线id metadata key
         */
        const val TASK_METADATA_KEY_PID = "PIPELINE_ID"

        /**
         * 所属流水线名
         */
        const val TASK_METADATA_PIPELINE_NAME = "PIPELINE_NAME"

        /**
         * 流水线中使用的插件名
         */
        const val TASK_METADATA_PLUGIN_NAME = "PLUGIN_NAME"

        /**
         * 只扫描单个制品时，可以通过该元数据指定扫描器加载的文件名
         */
        const val TASK_METADATA_FILE_NAME = "FILE_NAME"

        /**
         * 指定任务使用哪个分发器分发子任务
         */
        const val TASK_METADATA_DISPATCHER = "DISPATCHER"

        /**
         * 标记任务是否为全局扫描任务
         */
        const val TASK_METADATA_GLOBAL = "GLOBAL"
    }
}
