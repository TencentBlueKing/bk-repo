package com.tencent.bkrepo.common.metadata.routing

import com.tencent.bkrepo.common.mongo.api.routing.MongoRoutingRegistry
import com.tencent.bkrepo.common.mongo.api.routing.ReadinessCheckItem
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessChecker
import com.tencent.bkrepo.common.mongo.api.routing.RoutingReadinessResult
import com.tencent.bkrepo.common.mongo.routing.MongoMultiInstanceProperties
import com.tencent.bkrepo.common.mongo.routing.MongoRoutingPodRegistry
import com.tencent.bkrepo.common.mongo.routing.NodeDirectMongoAuditor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DefaultRoutingReadinessChecker(
    @Autowired(required = false)
    private val registry: MongoRoutingRegistry? = null,
    @Autowired(required = false)
    private val scatterQueryService: NodeScatterQueryService? = null,
    @Autowired(required = false)
    private val nodeBatchQueryHelper: NodeBatchQueryHelper? = null,
    @Autowired(required = false)
    private val podRegistry: MongoRoutingPodRegistry? = null,
    private val properties: MongoMultiInstanceProperties,
) : RoutingReadinessChecker {

    override fun check(): RoutingReadinessResult {
        val checks = mutableListOf<ReadinessCheckItem>()
        checks += item("INFRA-01", "MongoRoutingRegistry bean", registry != null)
        checks += item(
            "INFRA-02",
            "node routing-enabled",
            registry?.isRoutingEnabled(NODE_RULE) == true,
        )
        checks += item("M5-01", "NodeScatterQueryService", scatterQueryService != null)
        checks += item("M5-02", "NodeBatchQueryHelper", nodeBatchQueryHelper != null)
        checks += item(
            "M5-03",
            "local config-version up to date",
            registry?.isConfigUpToDate() != false,
        )
        val minVersion = registry?.getMinConfigVersion() ?: 0L
        val clusterPods = podRegistry?.verifyClusterUpToDate(minVersion)
        checks += item(
            "M5-06",
            "cluster pods config-version",
            clusterPods?.passed != false,
            clusterPods?.reason,
        )
        checks += item("M5-04", "G-37 sha256 projectId cache", classExists(Sha256ProjectIdCache::class.java.name))
        checks += item(
            "M5-05",
            "G-43 scatter dedicated pool",
            classExists("com.tencent.bkrepo.common.mongo.routing.ScatterMongoTemplateProvider"),
        )
        val auditViolations = NodeDirectMongoAuditor.audit()
        checks += item(
            "G-34-CI",
            "no direct node_ mongo access",
            auditViolations.isEmpty(),
            auditViolations.take(5).joinToString().ifEmpty { null },
        )
        val routingActive = registry?.isRoutingEnabled(NODE_RULE) == true
        P0_MANIFEST.forEach { (id, desc) ->
            val passed = !routingActive ||
                id in properties.scatterQuery.completedReadinessItems ||
                P0RoutingReadinessProbes.check(id)
            checks += item(id, desc, passed)
        }
        return RoutingReadinessResult(
            ready = checks.all { it.passed },
            checks = checks,
        )
    }

    private fun item(
        id: String,
        description: String,
        passed: Boolean,
        detail: String? = null,
    ): ReadinessCheckItem =
        ReadinessCheckItem(id = id, description = description, passed = passed, detail = detail)

    private fun classExists(name: String): Boolean =
        runCatching { Class.forName(name) }.isSuccess

    companion object {
        private const val NODE_RULE = "node"

        /** G-34 E2E 验收用：§3.19.2 P0 清单 */
        val P0_MANIFEST: Map<String, String> = linkedMapOf(
            "A1" to "auth BkiamNodeResourceService routing",
            "A2" to "replication LocalDataManager routing",
            "A3" to "opdata GcInfoModel fan-out",
            "A4" to "metadata RNodeDao.pageBySha256 scatter",
            "A5" to "job NodeIterator routing",
            "A6" to "job NodeCommonUtils routing",
            "B1" to "InactiveProjectNodeFolderStatJob",
            "B2" to "InactiveProjectEmptyFolderCleanupJob",
            "B3" to "ExpiredNodeMarkupJob",
            "B4" to "ProjectRepoMetricsStatJob",
            "B5" to "StatBaseJob",
            "B6" to "NodeStatCompositeMongoDbBatchJob",
            "B7" to "ArchiveNodeStatJob",
            "B8" to "NodeReport2BkbaseJob",
            "B9" to "BasedRepositoryNodeRetainResolver",
            "B10" to "SeparationStatBaseJob",
            "C1" to "DeletedRepositoryCleanupJob",
            "C2" to "DeletedNodeCleanupJob",
            "C3" to "PipelineArtifactCleanupJob",
            "C4" to "NodeCopyJob",
            "C5" to "IdleNodeArchiveJob",
            "C6" to "SystemGcJob",
            "C7" to "FileReferenceCleanupJob",
            "C8" to "Separation AbstractHandler",
            "C9" to "MavenRepoSpecialDataSeparatorHandler",
            "C10" to "FixFailedDataSeparationJob",
            "C11" to "BackupNodeDataHandler",
            "C12" to "MigrateExecutor",
            "D1" to "NodeModifyEventListener async routing",
            "D2" to "NodeCommonUtils routing",
            "D3" to "async write paths explicit projectId",
        )
    }
}
