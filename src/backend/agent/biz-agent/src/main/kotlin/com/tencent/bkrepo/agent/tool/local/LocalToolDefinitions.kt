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

/** 本地工具 schema（模型可见）。description 须与客户端 tools.ts 保持一致。 */
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
    private val LOG_TOPICS = listOf("schedule", "cleanup", "extract", "script", "download", "engine", "update", "login")
    private val LOG_LEVELS = listOf("error", "warn", "info")
    private const val MAX_SPEED_SAMPLE_TASKS = 10
    private const val TASK_IDS_DESC = "任务 ID 数组，必须从 list_download_tasks 返回中整串复制，禁止编造"

    fun allTools(): List<LocalToolDefinition> = listOf(
        // ── 观测 ──────────────────────────────────────────────
        LocalToolDefinition(
            name = "list_download_tasks",
            description =
                "返回传输列表中的任务：taskId、文件名、状态、进度、实时速度；失败任务附 failedCode 与 failedMessage。" +
                "用户问「有哪些下载」「哪些失败了」，或需要拿到 taskId 时使用。" +
                "默认只查活动列表，已完成历史须显式传 state=completed；这是获取 taskId 的唯一途径。",
            inputSchema = obj(
                "keyword" to str("文件名关键字，仅用户提到具体文件名时传"),
                "state" to enumOf(TASK_STATES, "任务状态；不传则查活动列表（不含已完成历史）"),
                "source" to enumOf(TASK_SOURCES, "下载来源；一般不必传"),
                "limit" to int("最多返回条数，不传默认 10", min = 1, max = 50),
            ),
        ),
        LocalToolDefinition(
            name = "get_download_tasks",
            description =
                "按 taskId 返回任务详情：savePath、traceId、gid、已下载字节、耗时、所属项目仓库；" +
                "失败任务附原始 failedCode 与 failedMessage。" +
                "已经拿到 taskId、需要比列表更多字段时使用。" +
                "只回读任务自身记录的字段，不查磁盘、路径、引擎或日志。",
            inputSchema = obj(
                "taskIds" to strArray(TASK_IDS_DESC, min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "sample_download_speed",
            description =
                "对任务连续采样数秒，返回窗口均速、全程均速、瞬时速度区间，以及 progressed（窗口内字节数是否增长）。" +
                "需要判断「一直慢 / 刚变慢 / 根本没动」时使用。" +
                "会阻塞约 3 秒；只对 downloading、waiting 状态有意义，已暂停或失败的任务采不到速度。",
            inputSchema = obj(
                "taskIds" to strArray(
                    "任务 ID 数组，最多 $MAX_SPEED_SAMPLE_TASKS 个（工具会采样数秒）",
                    min = 1,
                    max = MAX_SPEED_SAMPLE_TASKS,
                ),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "get_download_engine_status",
            description =
                "返回 aria2 下载引擎的进程状态、RPC 是否可达、监听端口、全局下载速度、活动/等待/已停止任务数。" +
                "判断问题是出在单个任务还是整个引擎时使用。" +
                "只反映引擎自身，不含具体任务的速度。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "get_download_settings",
            description =
                "返回下载目录、最大并发数、单任务分片数、单服务器连接数、自动清理开关与保留天数。" +
                "需要知道当前配置，或判断并发是否已占满时使用。" +
                "并发数与分片数只读，没有对应的写工具；客户端也没有限速设置项。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "get_disk_space",
            description =
                "返回指定路径所在磁盘的总容量、剩余空间（含按系统习惯换算好的文本）、盘型与型号。" +
                "需要确认空间是否够用，或想知道保存目录是不是机械盘时使用。" +
                "不传 path 时查当前下载目录所在盘；查的是整块磁盘，不是某个目录的占用。",
            inputSchema = obj("path" to str("绝对路径；不传则检查当前下载目录")),
        ),
        LocalToolDefinition(
            name = "check_path_access",
            description =
                "返回路径是否存在、是否被占用、当前用户能否在其中创建目录，不可写时带系统原始报错。" +
                "怀疑保存目录不存在或没有写权限时使用。" +
                "可写性靠实际创建临时目录试出来，比查权限位可靠；路径不存在时检查的是它的父目录。",
            inputSchema = obj("path" to str("要检查的绝对路径"), required = listOf("path")),
        ),
        LocalToolDefinition(
            name = "search_client_logs",
            description =
                "检索客户端自身日志（main.log），返回命中条数、错误/告警数、首末时间与折叠后的日志条目。" +
                "查预约触发、清理、解压、脚本、登录这些界面上没有记录的事件时使用。" +
                "topic 与 keyword 至少传一个；同类日志会折叠计数，查引擎日志请用 search_engine_logs。",
            inputSchema = obj(
                "topic" to enumOf(LOG_TOPICS, "关注点，与 keyword 至少传一个"),
                "level" to enumOf(LOG_LEVELS, "最低级别，warn 表示同时包含 error；不传则不过滤"),
                "keyword" to str("关键字，可传文件名或 traceId；与 topic 至少传一个"),
                "sinceMinutes" to int("只看最近 N 分钟；不传则不限", min = 1, max = 10080),
                "limit" to int("返回条目上限，不传默认 8", min = 1, max = 20),
            ),
        ),
        LocalToolDefinition(
            name = "search_engine_logs",
            description =
                "检索 aria2 下载引擎日志（aria2.log），返回命中条数、错误/告警数、首末时间与折叠后的日志条目。" +
                "需要连接层面的报错细节，例如反复重连、超时、证书或代理问题时使用。" +
                "这里没有关注点分类，靠 keyword 与 level 收窄；客户端自身的事件在 search_client_logs。",
            inputSchema = obj(
                "level" to enumOf(LOG_LEVELS, "最低级别，warn 表示同时包含 error；不传则不过滤"),
                "keyword" to str("关键字，可传域名、GID 或报错片段"),
                "sinceMinutes" to int("只看最近 N 分钟；不传则不限", min = 1, max = 10080),
                "limit" to int("返回条目上限，不传默认 8", min = 1, max = 20),
            ),
        ),
        LocalToolDefinition(
            name = "get_login_status",
            description =
                "返回当前是否已登录、登录模式、用户标识，以及登录态是否已被标记失效。" +
                "任务报鉴权类错误，或用户怀疑「是不是掉登录了」时使用。" +
                "只反映客户端本地的会话状态，不代表某个下载请求一定会通过鉴权。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "check_service_reachability",
            description =
                "依次探测登录服务、蓝盾站点、制品库三个地址，返回各自的可达性、HTTP 状态码与耗时。" +
                "怀疑连不上服务端、或本地各项都正常但下载依然失败/很慢时使用。" +
                "这是 HTTP 探测不是 ping，任一目标失败即停止后续探测；能证明端点是否可达，" +
                "但证不了服务端某个文件是否存在，也测不出服务端带宽。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "get_network_info",
            description =
                "返回本机出口 IPv4 与网卡协商链路速率。" +
                "需要判断用户是否走了 VPN/异常网段，或链路速率本身就受限时使用。" +
                "只是网卡的协商速率，不代表实际可用带宽，也不做任何连通性探测。",
            inputSchema = NO_ARGS,
        ),
        // ── 动作 ──────────────────────────────────────────────
        LocalToolDefinition(
            name = "open_task_location",
            description =
                "在系统文件管理器中打开任务文件所在目录，返回实际定位到的路径。" +
                "用户问「文件在哪」「帮我打开目录」时使用。" +
                "会在用户桌面弹出文件管理器窗口；一次只能定位一个任务。",
            inputSchema = obj(
                "taskId" to str("任务 ID，必须从 list_download_tasks 返回中整串复制"),
                required = listOf("taskId"),
            ),
        ),
        LocalToolDefinition(
            name = "pause_download_tasks",
            description =
                "暂停下载中或等待中的任务，返回 affected/skipped/verified/unverified 四组结果。" +
                "用户明确要求暂停，或需要腾出带宽给其他任务时使用。" +
                "只有出现在 verified 里的任务才算确实暂停了；已失败或已完成的任务会进 skipped。",
            inputSchema = obj(
                "taskIds" to strArray(TASK_IDS_DESC, min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "resume_download_tasks",
            description =
                "恢复已暂停的任务，或让失败任务重试，返回 affected/skipped/verified/unverified。" +
                "用户说「继续下载」「再试一次」时使用。" +
                "沿用任务创建时就固化的保存路径：如果刚用 set_download_path 改过下载目录，" +
                "失败任务必须改用 requeue_download_tasks，否则仍会写向旧路径并以同样原因再失败。",
            inputSchema = obj(
                "taskIds" to strArray(TASK_IDS_DESC, min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "requeue_download_tasks",
            description =
                "按当前的全局下载目录重算保存路径后重新入队，返回 affected/skipped/verified/unverified。" +
                "改过下载目录之后要重下失败任务，或需要整个从头下载时使用。" +
                "只对 failed 状态有效；暂停中的任务不在处理范围内。未改过目录时用 resume_download_tasks 即可。",
            inputSchema = obj(
                "taskIds" to strArray(TASK_IDS_DESC, min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "delete_download_tasks",
            description =
                "删除下载任务，返回 affected/skipped/verified/unverified。" +
                "仅在用户明确要求删除时使用。" +
                "未完成的任务会连同已下载的分片一起删除且不可恢复，已完成的只删列表记录、保留磁盘文件。",
            inputSchema = obj(
                "taskIds" to strArray(TASK_IDS_DESC, min = 1, max = 50),
                required = listOf("taskIds"),
            ),
        ),
        LocalToolDefinition(
            name = "set_download_path",
            description =
                "修改全局下载目录，返回修改后的完整下载配置。" +
                "用户要求换盘或换目录，或当前目录不可写、空间不足需要改到别处时使用。" +
                "必须是绝对路径；只影响此后新建或重新入队的任务，已存在的任务仍用原路径，" +
                "要让失败任务落到新目录得再调 requeue_download_tasks。",
            inputSchema = obj(
                "path" to str("新的下载目录，必须是绝对路径"),
                required = listOf("path"),
            ),
        ),
        LocalToolDefinition(
            name = "set_cleanup_policy",
            description =
                "修改自动清理策略的开关与保留天数，返回修改后的完整下载配置。" +
                "用户要求开启/关闭自动清理，或调整保留多少天时使用。" +
                "两个字段至少传一个，只传用户要求改的那个；改策略本身不会立刻删文件，" +
                "要立即清理用 run_disk_cleanup。",
            inputSchema = obj(
                "enabled" to bool("是否开启自动清理"),
                "daysToKeep" to int("保留天数", min = 1, max = 30),
            ),
        ),
        LocalToolDefinition(
            name = "run_disk_cleanup",
            description =
                "按当前清理策略立即删除磁盘上过期的已完成下载文件，返回删除数量、释放字节与跳过数量。" +
                "磁盘空间不足、用户要求立刻释放空间时使用。" +
                "删的是磁盘上的真实文件且不可恢复，只按现有策略决定删哪些，不能指定文件；" +
                "只想清空列表记录用 clear_completed_records。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "restart_download_engine",
            description =
                "重启 aria2 下载引擎，返回重启后的进程状态与 RPC 可达性。" +
                "仅在 get_download_engine_status 显示进程异常或 RPC 不可达时使用。" +
                "会中断所有正在进行的传输，重启后需要用户或后续操作重新恢复任务；" +
                "单个任务慢或卡住不是重启引擎的理由。",
            inputSchema = NO_ARGS,
        ),
        LocalToolDefinition(
            name = "clear_completed_records",
            description =
                "清空传输列表中的全部已完成记录，返回被清掉的记录条数。" +
                "用户嫌历史记录太多、要清列表时使用。" +
                "只删列表记录，磁盘上的文件一个都不动；要释放磁盘空间用 run_disk_cleanup。" +
                "作用于全部已完成记录，不能只清其中几条。",
            inputSchema = NO_ARGS,
        ),
    )

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
