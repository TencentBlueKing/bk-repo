package com.tencent.bkrepo.common.metadata.routing

/** §3.19.2 P0 清单自动探测（G-34）；类路径不可达时返回 false。 */
object P0RoutingReadinessProbes {

    private val batchJobClasses = mapOf(
        "B1" to "com.tencent.bkrepo.job.batch.task.stat.InactiveProjectNodeFolderStatJob",
        "B2" to "com.tencent.bkrepo.job.batch.task.stat.InactiveProjectEmptyFolderCleanupJob",
        "B3" to "com.tencent.bkrepo.job.batch.task.other.ExpiredNodeMarkupJob",
        "B4" to "com.tencent.bkrepo.job.batch.task.stat.ProjectRepoMetricsStatJob",
        "B6" to "com.tencent.bkrepo.job.batch.base.NodeStatCompositeMongoDbBatchJob",
        "B7" to "com.tencent.bkrepo.job.batch.task.archive.ArchiveNodeStatJob",
        "B8" to "com.tencent.bkrepo.job.batch.task.bkbase.NodeReport2BkbaseJob",
        "C1" to "com.tencent.bkrepo.job.batch.task.clean.DeletedRepositoryCleanupJob",
        "C2" to "com.tencent.bkrepo.job.batch.task.clean.DeletedNodeCleanupJob",
        "C3" to "com.tencent.bkrepo.job.batch.task.clean.PipelineArtifactCleanupJob",
        "C4" to "com.tencent.bkrepo.job.batch.task.other.NodeCopyJob",
        "C5" to "com.tencent.bkrepo.job.batch.task.archive.IdleNodeArchiveJob",
        "C7" to "com.tencent.bkrepo.job.batch.task.clean.FileReferenceCleanupJob",
    )

    private val routingFieldClasses = mapOf(
        "C6" to Pair(
            "routingRegistry",
            "com.tencent.bkrepo.job.batch.task.archive.SystemGcJob",
        ),
        "C8" to Pair(
            "nodeMongoOperations",
            "com.tencent.bkrepo.job.separation.service.impl.AbstractHandler",
        ),
        "C9" to Pair(
            "routingRegistry",
            "com.tencent.bkrepo.job.separation.service.impl.repo.MavenRepoSpecialDataSeparatorHandler",
        ),
        "C11" to Pair(
            "nodeMongoOperations",
            "com.tencent.bkrepo.job.backup.service.impl.base.BackupNodeDataHandler",
        ),
        "C12" to Pair(
            "routingRegistry",
            "com.tencent.bkrepo.job.migrate.executor.MigrateExecutor",
        ),
        "C10" to Pair(
            "routingRegistry",
            "com.tencent.bkrepo.job.batch.task.separation.FixFailedDataSeparationJob",
        ),
    )

    /** 目标类不在当前 classpath 时跳过（多模块 E2E 分片验收）。 */
    fun isApplicable(id: String): Boolean = when (id) {
        "A1" -> classExists("com.tencent.bkrepo.auth.service.bkiamv3.callback.BkiamNodeResourceService")
        "A2" -> classExists("com.tencent.bkrepo.replication.manager.LocalDataManager")
        "A3" -> classExists("com.tencent.bkrepo.opdata.model.GcInfoModel")
        "A4", "A6", "D1", "D3" -> classExists(probeClassName(id))
        "A5" -> classExists("com.tencent.bkrepo.job.migrate.utils.NodeIterator")
        "B10" -> classExists("com.tencent.bkrepo.job.batch.task.stat.SeparationStatBaseJob")
        "B5" -> classExists("com.tencent.bkrepo.job.batch.task.stat.StatBaseJob")
        "B9" -> classExists("com.tencent.bkrepo.job.batch.file.BasedRepositoryNodeRetainResolver")
        "D2" -> classExists("com.tencent.bkrepo.job.batch.utils.NodeCommonUtils")
        in batchJobClasses -> classExists(batchJobClasses.getValue(id))
        in routingFieldClasses -> classExists(routingFieldClasses.getValue(id).second)
        else -> false
    }

    fun check(id: String): Boolean = when (id) {
        "A1" -> hasRoutingField(
            "com.tencent.bkrepo.auth.service.bkiamv3.callback.BkiamNodeResourceService",
            "nodeShardReadSupport",
        )
        "A2" -> hasRoutingField(
            "com.tencent.bkrepo.replication.manager.LocalDataManager",
            "nodeShardReadSupport",
        )
        "A3" -> hasRoutingField(
            "com.tencent.bkrepo.opdata.model.GcInfoModel",
            "nodeShardReadSupport",
        )
        "A4" -> classExists("com.tencent.bkrepo.common.metadata.dao.node.RNodeDao")
        "A5" -> hasRoutingField(
            "com.tencent.bkrepo.job.migrate.utils.NodeIterator",
            "routingRegistry",
        )
        "A6" -> classExists("com.tencent.bkrepo.job.batch.utils.NodeCommonUtils")
        in batchJobClasses -> isMongoDbBatchJob(batchJobClasses.getValue(id))
        in routingFieldClasses.keys -> {
            val (field, className) = routingFieldClasses.getValue(id)
            hasRoutingField(className, field)
        }
        "B10" -> hasRoutingField(
            "com.tencent.bkrepo.job.batch.task.stat.EmptyFolderCleanup",
            "nodeMongoOperations",
        ) || hasRoutingField(
            "com.tencent.bkrepo.job.batch.task.stat.NodeFolderStat",
            "nodeMongoOperations",
        )
        "B5" -> hasRoutingField(
            "com.tencent.bkrepo.job.batch.task.stat.StatBaseJob",
            "nodeShardReadSupport",
        )
        "B9" -> hasRoutingField(
            "com.tencent.bkrepo.job.batch.file.BasedRepositoryNodeRetainResolver",
            "nodeShardReadSupport",
        )
        "D1" -> classUsesNodeRoutingContext(
            "com.tencent.bkrepo.common.metadata.listener.NodeModifyEventListener",
        )
        "D2" -> hasRoutingField(
            "com.tencent.bkrepo.job.batch.utils.NodeCommonUtils",
            "routingRegistry",
        )
        "D3" -> classExists("com.tencent.bkrepo.common.metadata.routing.NodeMongoOperations")
        else -> false
    }

    private fun probeClassName(id: String): String = when (id) {
        "A4" -> "com.tencent.bkrepo.common.metadata.dao.node.RNodeDao"
        "A6" -> "com.tencent.bkrepo.job.batch.utils.NodeCommonUtils"
        "D1" -> "com.tencent.bkrepo.common.metadata.listener.NodeModifyEventListener"
        "D3" -> "com.tencent.bkrepo.common.metadata.routing.NodeMongoOperations"
        else -> ""
    }

    private fun classExists(name: String): Boolean =
        runCatching { Class.forName(name) }.isSuccess

    private fun hasRoutingField(className: String, fieldName: String): Boolean =
        runCatching {
            Class.forName(className).declaredFields.any { it.name == fieldName }
        }.getOrDefault(false)

    private fun isMongoDbBatchJob(className: String): Boolean =
        runCatching {
            val clazz = Class.forName(className)
            val batchJob = Class.forName("com.tencent.bkrepo.job.batch.base.MongoDbBatchJob")
            batchJob.isAssignableFrom(clazz)
        }.getOrDefault(false)

    private fun classUsesNodeRoutingContext(className: String): Boolean =
        runCatching {
            val bytes = Class.forName(className).classLoader
                ?.getResourceAsStream(className.replace('.', '/') + ".class")
                ?.readBytes()
            bytes != null && "NodeRoutingContext" in String(bytes, Charsets.ISO_8859_1)
        }.getOrDefault(false)

    /** §3.19.2 全量 P0 清单（G-34 E2E 验收） */
    val P0_MANIFEST: Map<String, String> = buildMap {
        put("A1", "auth BkiamNodeResourceService routing")
        put("A2", "replication LocalDataManager routing")
        put("A3", "opdata GcInfoModel fan-out")
        put("A4", "metadata RNodeDao.pageBySha256 scatter")
        put("A5", "job NodeIterator routing")
        put("A6", "job NodeCommonUtils routing")
        batchJobClasses.forEach { (id, className) ->
            put(id, "job ${className.substringAfterLast('.')}")
        }
        put("B5", "job StatBaseJob routing")
        put("B9", "job BasedRepositoryNodeRetainResolver routing")
        put("B10", "job separation stat routing")
        routingFieldClasses.forEach { (id, fieldAndClass) ->
            put(id, "${fieldAndClass.second.substringAfterLast('.')} ${fieldAndClass.first}")
        }
        put("D1", "NodeModifyEventListener async routing")
        put("D2", "NodeCommonUtils routingRegistry")
        put("D3", "NodeMongoOperations write API")
    }
}
