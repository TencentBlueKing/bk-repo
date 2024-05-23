package com.tencent.bkrepo.job.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("job.storage-reconcile")
class StorageReconcileJobProperties(
    override var cron: String = "0 0 0 1 */1 ?",
) : BatchJobProperties()
