package com.tencent.bkrepo.migrate.artifact

import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.local.LocalRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.service.util.HttpContextHolder
import com.tencent.bkrepo.migrate.BKREPO
import com.tencent.bkrepo.migrate.SYNCREPO
import com.tencent.bkrepo.migrate.REQUESTJSON
import com.tencent.bkrepo.migrate.PENDING
import com.tencent.bkrepo.migrate.MIGRATE_OPERATOR
import com.tencent.bkrepo.migrate.FINISH
import com.tencent.bkrepo.migrate.KAFKA_RESULT
import com.tencent.bkrepo.migrate.conf.KafkaConf
import com.tencent.bkrepo.migrate.dao.suyan.SuyanMavenArtifactDao
import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact
import com.tencent.bkrepo.migrate.pojo.BkProduct
import com.tencent.bkrepo.migrate.pojo.MavenSyncInfo
import com.tencent.bkrepo.migrate.pojo.SyncRequest
import com.tencent.bkrepo.migrate.pojo.SyncResult
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanArtifactInfo
import com.tencent.bkrepo.migrate.pojo.suyan.SuyanSyncRequest
import com.tencent.bkrepo.migrate.util.ShortUUIDUtils
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import com.tencent.bkrepo.repository.pojo.node.service.NodeDeleteRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.search.NodeQueryBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class MigrateLocalRepository : LocalRepository() {

    @Autowired
    lateinit var suyanMavenArtifactDao: SuyanMavenArtifactDao

    @Autowired
    lateinit var kafkaConf: KafkaConf

    private fun storeRequest(syncRequest: Any, kafkaResult: Boolean?): String {
        val request = HttpContextHolder.getRequest()
        val requestJson = syncRequest.toJsonString()
        val artifactFile = ArtifactFileFactory.build(requestJson.byteInputStream())
        val jsonName = "${System.currentTimeMillis()}-${ShortUUIDUtils.shortUUID()}.json"
        val metadata = mutableMapOf<String, Any>()
        if (request.getHeader("ci-cd-origin") != null) {
            metadata["ci-cd-origin"] = request.getHeader("ci-cd-origin")
        }
        kafkaResult?.let { metadata[KAFKA_RESULT] = kafkaResult }
        // 将请求以json格式保存到节点 /ci-cd-json
        val nodeCreateRequest = NodeCreateRequest(
            projectId = BKREPO,
            repoName = SYNCREPO,
            fullPath = "$REQUESTJSON$PENDING/$jsonName",
            folder = false,
            expires = 0L,
            overwrite = false,
            size = artifactFile.getSize(),
            sha256 = artifactFile.getFileSha256(),
            md5 = artifactFile.getFileMd5(),
            metadata = metadata,
            operator = "migrate server",
            createdBy = "migrate server",
            createdDate = LocalDateTime.now(),
            lastModifiedDate = LocalDateTime.now(),
            lastModifiedBy = "migrate server"
        )
        store(nodeCreateRequest, artifactFile)
        return jsonName
    }

    fun sync(context: ArtifactSearchContext, sync: SuyanSyncRequest): SyncResult {
        val syncRequest = transfer(sync)
        logger.info("accepted: ${syncRequest.info()}")
        try {
            checkTargetRepo(syncRequest)
        } catch (e: Exception) {
            return SyncResult(
                message = "checkTargetRepo Failed: ${e.message}",
                data = false
            )
        }
        // 推送消息 到 kafka
        val kafkaResult = null
        val jsonName = storeRequest(sync, kafkaResult)
        return SyncResult(
            message = "Sync request success store, id: $jsonName",
            data = true
        )
    }

    fun clean(artifactRemoveContext: ArtifactRemoveContext, metaField: String, value: String): Boolean {
        var pages = searchFinishNode(metaField, value)
        while (pages != null && pages.records.isNotEmpty()) {
            val maps = pages.records
            maps.forEach {
                val fullPath = it["fullPath"] as String
                nodeClient.deleteNode(
                    NodeDeleteRequest(
                        projectId = BKREPO,
                        repoName = SYNCREPO,
                        fullPath = fullPath,
                        operator = MIGRATE_OPERATOR
                    )
                )
            }
            pages = searchFinishNode(metaField, value)
        }
        return true
    }

    override fun query(context: ArtifactQueryContext): SuyanArtifactInfo? {
        val request = HttpContextHolder.getRequest()
        val repoName = request.getParameter("repoName")
        val groupId = request.getParameter("groupId")
        val artifactId = request.getParameter("artifactId")
        val type = request.getParameter("type")
        val version = request.getParameter("version")
        val tSuyanArtifact = suyanMavenArtifactDao.findFirstByRepositoryNameAndGroupIdAndArtifactIdAndVersionAndType(
            repositoryName = repoName,
            groupId = groupId,
            artifactId = artifactId,
            type = type,
            version = version
        ) ?: return null
        return transferTo(tSuyanArtifact)
    }

    fun transferTo(tSuyanMavenArtifact: TSuyanMavenArtifact): SuyanArtifactInfo {
        val productList = mutableSetOf<BkProduct>()
        tSuyanMavenArtifact.productList?.let { products ->
            for (str in products) {
                productList.add(str.readJsonString())
            }
        }
        return SuyanArtifactInfo(
            id = tSuyanMavenArtifact.id,
            createdBy = tSuyanMavenArtifact.createdBy,
            createdDate = tSuyanMavenArtifact.createdDate,
            lastModifiedBy = tSuyanMavenArtifact.lastModifiedBy,
            lastModifiedDate = tSuyanMavenArtifact.lastModifiedDate,
            repositoryName = tSuyanMavenArtifact.repositoryName,
            groupId = tSuyanMavenArtifact.groupId,
            artifactId = tSuyanMavenArtifact.artifactId,
            version = tSuyanMavenArtifact.version,
            type = tSuyanMavenArtifact.type,
            productList = productList
        )
    }

    fun searchFinishNode(metaField: String, value: String): Page<Map<String, Any?>>? {
        val query = NodeQueryBuilder()
            .select("fullPath")
            .projectId(BKREPO)
            .repoName(SYNCREPO)
            .sortByAsc("createdDate")
            .page(1, 1000)
            .path("$REQUESTJSON$FINISH/")
            .metadata(metaField, value)
            .excludeFolder()
            .build()
        return nodeClient.search(query).data
    }

    fun checkTargetRepo(syncRequest: SyncRequest) {
        syncRequest.maven?.let {
            repositoryClient.getRepoInfo(BKREPO, it.repositoryName).data
                ?: repositoryClient.createRepo(
                    RepoCreateRequest(
                        projectId = BKREPO,
                        name = it.repositoryName,
                        type = RepositoryType.MAVEN,
                        category = RepositoryCategory.COMPOSITE,
                        public = false,
                        description = "create by migrate server",
                        configuration = null,
                        storageCredentialsKey = null,
                        operator = MIGRATE_OPERATOR
                    )
                )
        }
        syncRequest.docker?.let {
            val repoList = mutableListOf<String>()
            for (image in it) {
                repoList.add(image.project)
            }
            for (repoName in repoList) {
                repositoryClient.getRepoInfo(BKREPO, repoName).data
                    ?: repositoryClient.createRepo(
                        RepoCreateRequest(
                            projectId = BKREPO,
                            name = repoName,
                            type = RepositoryType.DOCKER,
                            category = RepositoryCategory.COMPOSITE,
                            public = false,
                            description = "create by migrate server",
                            configuration = null,
                            storageCredentialsKey = null,
                            operator = MIGRATE_OPERATOR
                        )
                    )
            }
        }
    }

    private fun transfer(suyanSyncRequest: SuyanSyncRequest): SyncRequest {
        val mavenSyncInfo = MavenSyncInfo(
            repositoryName = suyanSyncRequest.repositoryName,
            groupId = suyanSyncRequest.groupId,
            artifactId = suyanSyncRequest.artifactId,
            version = suyanSyncRequest.version,
            packaging = suyanSyncRequest.packaging,
            name = suyanSyncRequest.name,
            artifactList = suyanSyncRequest.artifactList
        )
        return SyncRequest(
            maven = mavenSyncInfo,
            docker = suyanSyncRequest.docker?.map { it.transfer() },
            productList = suyanSyncRequest.productList
        )
    }

    private fun store(node: NodeCreateRequest, artifactFile: ArtifactFile) {
        storageManager.storeArtifactFile(node, artifactFile, null)
        artifactFile.delete()
        with(node) { logger.info("Success to store $projectId/$repoName$fullPath") }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(MigrateLocalRepository::class.java)
    }
}
