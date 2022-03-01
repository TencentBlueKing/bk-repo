package com.tencent.bkrepo.job.executor

/**
 * 带身份标识的任务
 * */
class IdentityTask(val id: String, private val runnable: Runnable) : Runnable {
    override fun run() {
        runnable.run()
    }
}
