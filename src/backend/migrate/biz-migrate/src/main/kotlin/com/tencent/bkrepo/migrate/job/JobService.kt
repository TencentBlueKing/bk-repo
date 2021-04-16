package com.tencent.bkrepo.migrate.job

import com.tencent.bkrepo.common.api.util.toJsonString
import com.tencent.bkrepo.migrate.MIGRATE_OPERATOR
import com.tencent.bkrepo.migrate.dao.suyan.SuyanDockerImageDao
import com.tencent.bkrepo.migrate.dao.suyan.SuyanMavenArtifactDao
import com.tencent.bkrepo.migrate.model.suyan.TSuyanDockerImage
import com.tencent.bkrepo.migrate.model.suyan.TSuyanMavenArtifact
import com.tencent.bkrepo.migrate.pojo.SyncRequest
import com.tencent.bkrepo.migrate.pojo.DockerSyncInfo
import com.tencent.bkrepo.migrate.pojo.MavenArtifact
import com.tencent.bkrepo.migrate.pojo.MavenSyncInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch

@Component
class JobService(
    private val suyanMavenArtifactDao: SuyanMavenArtifactDao,
    private val suyanDockerImageDao: SuyanDockerImageDao,
    private val mongoTemplate: MongoTemplate,
    private val mvnJobService: MvnJobService,
    private val dockerJobService: DockerJobService
) {

    fun syncArtifact(syncRequest: SyncRequest) {
        if (syncRequest.isNull()) return
        syncRequest.productList?.let { addProductMetadata(syncRequest) }
        val countDownLatch = CountDownLatch(syncRequest.getThreadNum())
        syncRequest.maven?.let {
            Thread {
                mvnJobService.syncMavenArtifact(it)
                countDownLatch.countDown()
            }.start()
        }
        syncRequest.docker?.let {
            Thread {
                dockerJobService.syncDockerImage(DockerSyncInfo(syncRequest.docker))
                countDownLatch.countDown()
            }.start()
        }
        countDownLatch.await()
    }

    /**
     * 添加maven artifact 与产品元数据关系
     */
    private fun addMavenMetadata(productList: MutableSet<String>, maven: MavenSyncInfo) {
        // maven artifact 与产品元数据关系
        logger.info("Start add productList to maven artifact")
        val mavenArtifactList = maven.artifactList
        mavenArtifactList.add(
            MavenArtifact(
                groupId = maven.groupId,
                artifactId = maven.artifactId,
                type = maven.packaging,
                version = maven.version
            )
        )
        for (mavenArtifact in maven.artifactList) {
            val tSuyanArtifact =
                suyanMavenArtifactDao.findFirstByRepositoryNameAndGroupIdAndArtifactIdAndVersionAndType(
                    maven.repositoryName,
                    mavenArtifact.groupId,
                    mavenArtifact.artifactId,
                    mavenArtifact.version ?: maven.version,
                    mavenArtifact.type
                )
            if (tSuyanArtifact != null) {
                val list = (tSuyanArtifact.productList ?: mutableSetOf())
                list.addAll(productList)
                val updateQuery = Query(Criteria(TSuyanMavenArtifact::id.name).`is`(tSuyanArtifact.id))
                val update = Update().set(TSuyanMavenArtifact::productList.name, list)
                mongoTemplate.updateFirst(updateQuery, update, TSuyanMavenArtifact::class.java)
            } else {
                suyanMavenArtifactDao.insert(
                    TSuyanMavenArtifact(
                        repositoryName = maven.repositoryName,
                        createdBy = MIGRATE_OPERATOR,
                        createdDate = LocalDateTime.now(),
                        lastModifiedDate = LocalDateTime.now(),
                        lastModifiedBy = MIGRATE_OPERATOR,
                        groupId = mavenArtifact.groupId,
                        artifactId = mavenArtifact.artifactId,
                        version = mavenArtifact.version ?: maven.version,
                        type = mavenArtifact.type,
                        productList = productList
                    )
                )
            }
        }
    }

    /**
     * 添加docker image 与产品元数据关系
     */
    private fun addDockerMetadata(productList: MutableSet<String>, docker: DockerSyncInfo) {
        logger.info("Start add productList to docker image")
        for (image in docker.imageList) {
            val tImage = suyanDockerImageDao.findFirstByProjectAndNameAndTag(
                project = image.project,
                name = image.name,
                tag = image.tag
            )
            if (tImage != null) {
                val list = (tImage.productList ?: mutableSetOf())
                list.addAll(productList)
                val updateQuery = Query(Criteria(TSuyanDockerImage::id.name).`is`(tImage.id))
                val update = Update().set(TSuyanDockerImage::productList.name, list)
                mongoTemplate.updateFirst(updateQuery, update, TSuyanDockerImage::class.java)
            } else {
                suyanDockerImageDao.insert(
                    TSuyanDockerImage(
                        createdBy = MIGRATE_OPERATOR,
                        createdDate = LocalDateTime.now(),
                        lastModifiedDate = LocalDateTime.now(),
                        lastModifiedBy = MIGRATE_OPERATOR,
                        project = image.project,
                        name = image.name,
                        tag = image.tag,
                        productList = productList
                    )
                )
            }
        }
    }

    /**
     * 添加制品和产品元数据依赖关系
     */
    private fun addProductMetadata(syncRequest: SyncRequest) {
        // List<BkProduct> 转 List<String>
        val productList = mutableSetOf<String>()
        if (!syncRequest.productList.isNullOrEmpty()) {
            for (product in syncRequest.productList) {
                productList.add(product.toJsonString())
            }
        }
        syncRequest.maven?.let { addMavenMetadata(productList, it) }
        syncRequest.docker?.let { addDockerMetadata(productList, DockerSyncInfo(it)) }
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(JobService::class.java)
    }
}
