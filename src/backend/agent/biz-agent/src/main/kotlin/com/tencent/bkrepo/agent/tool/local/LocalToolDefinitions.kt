/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2026 Tencent.  All rights reserved.
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

package com.tencent.bkrepo.agent.tool.local

/**
 * 本地工具 schema 定义，与客户端 tools.ts 对齐。
 */
data class LocalToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>,
)

object LocalToolDefinitions {

    private val NO_ARGS = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "additionalProperties" to false,
    )

    private val TASK_STATES = listOf("downloading", "paused", "failed", "completed", "waiting")
    private val TASK_SOURCES = listOf("manual", "link", "schedule", "experience")
    private val LOG_SOURCES = listOf("main", "aria2")
    private val LOG_TOPICS = listOf("schedule", "cleanup", "extract", "script", "download", "engine", "update", "login")
    private val LOG_LEVELS = listOf("error", "warn", "info")

    fun readOnlyTools(): List<LocalToolDefinition> = listOf(
        LocalToolDefinition(
            name = "list_download_tasks",
            description = "列出当前的下载任务，即传输列表里的那些：下载中、已暂停、等待中、已失败，默认最近 10 条。",
            inputSchema = obj(
                "keyword" to str("按文件名模糊匹配，仅在用户提到具体文件名时传"),
                "state" to enumOf(TASK_STATES, "按状态过滤；用户没限定状态时不要传"),
                "source" to enumOf(TASK_SOURCES, "按下载来源过滤；用户没提来源时不要传"),
                "limit" to int("返回条数上限，默认 10", min = 1, max = 50),
            ),
        ),
        LocalToolDefinition(
            name = "get_download_task",
            description = "查询一个或多个下载任务的完整详情，含失败码、保存路径、GID、已下载字节数。",
            inputSchema = obj(
                "taskIds" to strArray("任务 ID 列表，来自 list_download_tasks 的返回；不要编造", min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "get_engine_status",
            description = "查询下载引擎（aria2）的运行状态、RPC 是否可达、全局下载速度、活动与等待任务数。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "get_disk_space",
            description = "查询指定路径所在磁盘的总容量、剩余空间和磁盘类型。",
            inputSchema = obj("path" to str("要检查的路径；不传则检查当前下载目录")),
        ),
        LocalToolDefinition(
            name = "get_download_settings",
            description = "读取当前下载配置：下载目录、最大并发数、单任务分片数、自动清理策略。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "check_path",
            description = "检查某个路径是否存在、是否被占用、当前用户能不能在其中创建目录。",
            inputSchema = obj("path" to str("要检查的绝对路径"), required = listOf("path")),
        ),
        LocalToolDefinition(
            name = "read_local_logs",
            description = "按条件检索客户端本地日志。查 main 日志时必须同时给出 topic 或 keyword。",
            inputSchema = obj(
                "source" to enumOf(LOG_SOURCES, "日志来源，默认 main"),
                "topic" to enumOf(LOG_TOPICS, "关注点；查 main 日志时必传 topic 或 keyword"),
                "level" to enumOf(LOG_LEVELS, "最低日志级别"),
                "keyword" to str("关键字模糊匹配"),
                "sinceMinutes" to int("只看最近多少分钟", min = 1, max = 10080),
                "limit" to int("返回条数上限，默认 8，最多 20", min = 1, max = 20),
            ),
        ),
        LocalToolDefinition(
            name = "open_task_location",
            description = "在系统文件管理器中打开某个任务的文件所在目录，并选中该文件。",
            inputSchema = obj(
                "taskId" to str("任务 ID，来自 list_download_tasks 的返回；不要编造"),
                required = listOf("taskId"),
            ),
        ),
    )

    fun writeTools(): List<LocalToolDefinition> = listOf(
        LocalToolDefinition(
            name = "pause_download_tasks",
            description = "暂停指定的下载任务。仅在用户明确要求暂停时使用。",
            inputSchema = obj(
                "taskIds" to strArray("任务 ID 列表，来自 list_download_tasks 的返回；不要编造", min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "resume_download_tasks",
            description = "恢复（继续）指定的下载任务，也用于重试失败的任务。用户说重新下载、继续下载时使用。",
            inputSchema = obj(
                "taskIds" to strArray("任务 ID 列表，来自 list_download_tasks 的返回；不要编造", min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "requeue_download_tasks",
            description = "把失败的任务按当前配置的下载目录重新加入下载队列，只对失败任务有效。换过下载目录后应优先用本工具。",
            inputSchema = obj(
                "taskIds" to strArray("任务 ID 列表，来自 list_download_tasks 的返回；不要编造", min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "delete_download_tasks",
            description = "删除指定的下载任务。未完成会取消并删已下载部分；已完成只删记录。仅在用户明确要求删除时使用。",
            inputSchema = obj(
                "taskIds" to strArray("任务 ID 列表，来自 list_download_tasks 的返回；不要编造", min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "update_download_settings",
            description = "修改下载配置。只能改下载目录、是否开启自动清理、自动清理保留天数。",
            inputSchema = obj(
                "downloadPath" to str("下载保存目录，必须是绝对路径"),
                "cleanupEnabled" to bool("是否开启自动清理"),
                "cleanupDaysToKeep" to int("自动清理保留天数", min = 1, max = 30),
            ),
        ),
        LocalToolDefinition(
            name = "run_cleanup",
            description = "立即执行一次文件清理，按当前清理策略删除磁盘上过期的已完成下载文件。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "restart_download_engine",
            description = "重启下载引擎（aria2）。进行中的传输会中断，之后需要恢复任务才会继续。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "clear_completed_tasks",
            description = "清空传输列表里的全部已完成记录。只删记录，磁盘文件不受影响。",
            inputSchema = NO_ARGS,
        ),
    )

    fun allTools(): List<LocalToolDefinition> = readOnlyTools() + writeTools()

    private fun obj(
        vararg properties: Pair<String, Map<String, Any>>,
        required: List<String> = emptyList(),
    ): Map<String, Any> {
        val props = linkedMapOf<String, Any>()
        properties.forEach { (key, value) -> props[key] = value }
        return linkedMapOf(
            "type" to "object",
            "properties" to props,
            "required" to required,
            "additionalProperties" to false,
        )
    }

    private fun str(description: String): Map<String, Any> =
        mapOf("type" to "string", "description" to description)

    private fun bool(description: String): Map<String, Any> =
        mapOf("type" to "boolean", "description" to description)

    private fun int(description: String, min: Int? = null, max: Int? = null): Map<String, Any> {
        val schema = linkedMapOf<String, Any>("type" to "integer", "description" to description)
        min?.let { schema["minimum"] = it }
        max?.let { schema["maximum"] = it }
        return schema
    }

    private fun enumOf(values: List<String>, description: String): Map<String, Any> =
        mapOf("type" to "string", "enum" to values, "description" to description)

    private fun strArray(description: String, min: Int? = null, max: Int? = null): Map<String, Any> {
        val items = mapOf("type" to "string")
        val schema = linkedMapOf<String, Any>(
            "type" to "array",
            "description" to description,
            "items" to items,
        )
        min?.let { schema["minItems"] = it }
        max?.let { schema["maxItems"] = it }
        return schema
    }
}
