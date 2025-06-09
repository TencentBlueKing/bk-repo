package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.task.other.CheckUserStatusJob
import java.util.Collections

/**
 * 用户状态检查任务上下文
 */
class CheckUserJobContext : JobContext() {
    // 非活跃用户列表（线程安全）
    val inactiveUsers = Collections.synchronizedList(mutableListOf<CheckUserStatusJob.User>())

    // 检查失败用户列表（线程安全）
    val failedUsers = Collections.synchronizedList(mutableListOf<CheckUserStatusJob.User>())

    /**
     * 重置上下文状态
     */
    fun reset() {
        inactiveUsers.clear()
        failedUsers.clear()
    }

    /**
     * 获取上下文状态摘要
     */
    override fun toString(): String {
        return "${super.toString()}, inactiveUsers[${inactiveUsers.size}], failedUsers[${failedUsers.size}]"
    }
}
