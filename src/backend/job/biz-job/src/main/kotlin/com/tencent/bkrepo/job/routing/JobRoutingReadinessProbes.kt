package com.tencent.bkrepo.job.routing

import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessProbe
import org.springframework.stereotype.Component

/** job 服务本地 P0 就绪探测（A5/A6/B1-B10/C1-C12/D2）。类路径不可达时返回 false。 */
@Component
class JobRoutingReadinessProbe : RoutingReadinessProbe {

    override fun probeId() = "job"

    override fun checkAll(): List<ReadinessCheckItem> = listOf(
        item("A5", "job NodeIterator routing", checkA5()),
        item("A6", "job NodeCommonUtils routing", checkA6()),
        item("B1", "InactiveProjectNodeFolderStatJob", checkB1()),
        item("B2", "InactiveProjectEmptyFolderCleanupJob", checkB2()),
        item("B3", "ExpiredNodeMarkupJob", checkB3()),
        item("B4", "ProjectRepoMetricsStatJob", checkB4()),
        item("B5", "StatBaseJob", checkB5()),
        item("B6", "NodeStatCompositeMongoDbBatchJob", checkB6()),
        item("B7", "ArchiveNodeStatJob", checkB7()),
        item("B8", "NodeReport2BkbaseJob", checkB8()),
        item("B9", "BasedRepositoryNodeRetainResolver", checkB9()),
        item("B10", "SeparationStatBaseJob", checkB10()),
        item("C1", "DeletedRepositoryCleanupJob", checkC1()),
        item("C2", "DeletedNodeCleanupJob", checkC2()),
        item("C3", "PipelineArtifactCleanupJob", checkC3()),
        item("C4", "NodeCopyJob", checkC4()),
        item("C5", "IdleNodeArchiveJob", checkC5()),
        item("C6", "SystemGcJob", checkC6()),
        item("C7", "FileReferenceCleanupJob", checkC7()),
        item("C8", "Separation AbstractHandler", checkC8()),
        item("C9", "MavenRepoSpecialDataSeparatorHandler", checkC9()),
        item("C10", "FixFailedDataSeparationJob", checkC10()),
        item("C11", "BackupNodeDataHandler", checkC11()),
        item("C12", "MigrateExecutor", checkC12()),
        item("D2", "NodeCommonUtils routing", checkD2()),
    )

    // ──── check methods ────

    private fun checkA5() =
        hasField("com.tencent.bkrepo.job.migrate.utils.NodeIterator", "routingRegistry")

    private fun checkA6() =
        classExists("com.tencent.bkrepo.job.batch.utils.NodeCommonUtils")

    private fun checkB1() = isMongoDbBatchJob(batchJobClasses["B1"]!!)
    private fun checkB2() = isMongoDbBatchJob(batchJobClasses["B2"]!!)
    private fun checkB3() = isMongoDbBatchJob(batchJobClasses["B3"]!!)
    private fun checkB4() = isMongoDbBatchJob(batchJobClasses["B4"]!!)

    private fun checkB5() =
        hasField("com.tencent.bkrepo.job.batch.task.stat.StatBaseJob", "nodeShardReadSupport")

    private fun checkB6() = isMongoDbBatchJob(batchJobClasses["B6"]!!)
    private fun checkB7() = isMongoDbBatchJob(batchJobClasses["B7"]!!)
    private fun checkB8() = isMongoDbBatchJob(batchJobClasses["B8"]!!)

    private fun checkB9() =
        hasField(
            "com.tencent.bkrepo.job.batch.file.BasedRepositoryNodeRetainResolver",
            "nodeShardReadSupport",
        )

    private fun checkB10() =
        hasField(
            "com.tencent.bkrepo.job.batch.task.stat.EmptyFolderCleanup",
            "nodeMongoOperations",
        ) || hasField(
            "com.tencent.bkrepo.job.batch.task.stat.NodeFolderStat",
            "nodeMongoOperations",
        )

    private fun checkC1() = isMongoDbBatchJob(batchJobClasses["C1"]!!)
    private fun checkC2() = isMongoDbBatchJob(batchJobClasses["C2"]!!)
    private fun checkC3() = isMongoDbBatchJob(batchJobClasses["C3"]!!)
    private fun checkC4() = isMongoDbBatchJob(batchJobClasses["C4"]!!)
    private fun checkC5() = isMongoDbBatchJob(batchJobClasses["C5"]!!)
    private fun checkC6() = checkRoutingField("C6")
    private fun checkC7() = isMongoDbBatchJob(batchJobClasses["C7"]!!)
    private fun checkC8() = checkRoutingField("C8")
    private fun checkC9() = checkRoutingField("C9")
    private fun checkC10() = checkRoutingField("C10")
    private fun checkC11() = checkRoutingField("C11")
    private fun checkC12() = checkRoutingField("C12")

    private fun checkD2() =
        hasField("com.tencent.bkrepo.job.batch.utils.NodeCommonUtils", "routingRegistry")

    private fun checkRoutingField(key: String): Boolean {
        val (field, className) = routingFieldClasses.getValue(key)
        return hasField(className, field)
    }

    // ──── helpers ────

    private fun classExists(name: String): Boolean =
        runCatching { Class.forName(name) }.isSuccess

    private fun hasField(className: String, fieldName: String): Boolean =
        runCatching {
            Class.forName(className).declaredFields.any { it.name == fieldName }
        }.getOrDefault(false)

    private fun isMongoDbBatchJob(className: String): Boolean =
        runCatching {
            val clazz = Class.forName(className)
            val batchJob = Class.forName("com.tencent.bkrepo.job.batch.base.MongoDbBatchJob")
            batchJob.isAssignableFrom(clazz)
        }.getOrDefault(false)

    companion object {
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
            "C10" to Pair(
                "routingRegistry",
                "com.tencent.bkrepo.job.batch.task.separation.FixFailedDataSeparationJob",
            ),
            "C11" to Pair(
                "nodeMongoOperations",
                "com.tencent.bkrepo.job.backup.service.impl.base.BackupNodeDataHandler",
            ),
            "C12" to Pair(
                "routingRegistry",
                "com.tencent.bkrepo.job.migrate.executor.MigrateExecutor",
            ),
        )

        private fun item(id: String, desc: String, passed: Boolean) =
            ReadinessCheckItem(id = id, description = desc, passed = passed)
    }
}