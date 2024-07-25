package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.storage-reconcile")
class StorageReconcileJobProperties(
    override var cron: String = "0 0 0 1 */1 ?",
    var safeMode: Boolean = true, // 安全模式下，只要数据库存在引用，即保留存储。
) : BatchJobProperties()
