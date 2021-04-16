package com.tencent.bkrepo.migrate.job

import com.tencent.bkrepo.migrate.BKREPO
import com.tencent.bkrepo.migrate.MIGRATE_OPERATOR
import com.tencent.bkrepo.migrate.conf.HarborConf
import com.tencent.bkrepo.migrate.dao.suyan.FailedDockerImageDao
import com.tencent.bkrepo.migrate.model.suyan.TFailedDockerImage
import com.tencent.bkrepo.migrate.pojo.DockerSyncInfo
import com.tencent.bkrepo.migrate.util.DockerUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import java.time.LocalDateTime

@Component
class DockerJobService(
    private val harborConf: HarborConf,
    private val mongoTemplate: MongoTemplate,
    private val failedDockerImageDao: FailedDockerImageDao
) {

    fun syncDockerImage(dockerSyncInfo: DockerSyncInfo) {
        if (dockerSyncInfo.imageList.isNullOrEmpty()) return
        val sw = StopWatch("Docker sync job")
        sw.start()
        DockerUtils.dockerLogin(harborConf.username, harborConf.password, harborConf.host)
        DockerUtils.dockerLogin(harborConf.bkrepoAdmin, harborConf.bkrepoPassword, harborConf.syncUrl)
        val targetImageHost = harborConf.host.removeSuffix("/").removePrefix("http://").removePrefix("http://")
        val tagHost = harborConf.syncUrl.removeSuffix("/").removePrefix("http://").removePrefix("http://")

        for (dockerImage in dockerSyncInfo.imageList) {
            val project = dockerImage.project
            val targetImage = "$targetImageHost/$project/${dockerImage.name}:${dockerImage.tag}"
            val tag = "$tagHost/$BKREPO/$project/${dockerImage.name}:${dockerImage.tag}"
            if (atomShell(targetImage, tag)) {
                logger.info("Docker push success: $project ${dockerImage.name} ${dockerImage.tag}")
            } else {
                logger.error("Docker push failed: $project ${dockerImage.name} ${dockerImage.tag}")
                // 写到数据库
                val existImage = failedDockerImageDao.findFirstByProjectAndNameAndTag(
                    project = project,
                    name = dockerImage.name,
                    tag = dockerImage.tag
                )
                if (existImage == null) {
                    mongoTemplate.insert(
                        TFailedDockerImage(
                            createdBy = MIGRATE_OPERATOR,
                            createdDate = LocalDateTime.now(),
                            lastModifiedBy = MIGRATE_OPERATOR,
                            lastModifiedDate = LocalDateTime.now(),
                            project = project,
                            name = dockerImage.name,
                            tag = dockerImage.tag
                        )
                    )
                }
            }
            /*
            非必成功操作
            1,上一步打tag失败
            2,docker rmi 失败
            两种情况都不会对最终的结果判断产生影响
             */
            DockerUtils.dockerRmi(targetImage)
            DockerUtils.dockerRmi(tag)
        }
        sw.stop()
        logger.info("$sw")
    }

    fun atomShell(image: String, tag: String): Boolean {
        if (!DockerUtils.dockerPull(image)) return false
        if (!DockerUtils.dockerTag(image, tag)) return false
        if (!DockerUtils.dockerPush(tag)) return false
        return true
    }

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(DockerJobService::class.java)
    }
}
