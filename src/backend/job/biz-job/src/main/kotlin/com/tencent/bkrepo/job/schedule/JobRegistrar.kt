package com.tencent.bkrepo.job.schedule

/**
 * 任务注册
 * */
interface JobRegistrar {
    /**
     * 初始化任务注册
     * */
    fun init()

    /**
     * 注册任务
     * */
    fun register(job: Job)

    /**
     * 注销任务
     * */
    fun unregister(job: Job)

    /**
     * 更新任务
     * */
    fun update(job: Job)

    /**
     * 列出所有任务
     * */
    fun list(): List<Job>

    /**
     * 注销所有任务
     * */
    fun unload()
}
