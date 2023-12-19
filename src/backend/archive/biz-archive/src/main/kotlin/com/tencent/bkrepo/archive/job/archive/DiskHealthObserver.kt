package com.tencent.bkrepo.archive.job.archive

/**
 * 磁盘健康观察者
 * */
interface DiskHealthObserver {
    fun healthy()
    fun unHealthy()
}
