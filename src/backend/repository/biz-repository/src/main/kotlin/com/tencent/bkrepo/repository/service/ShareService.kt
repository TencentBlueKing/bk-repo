package com.tencent.bkrepo.repository.service

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.api.message.CommonMessageCode
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.repository.model.TShareRecord
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.util.NodeUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 文件分享 service
 *
 * @author: carrypan
 * @date: 2019/11/12
 */
@Service
class ShareService @Autowired constructor(
    private val repositoryService: RepositoryService,
    private val nodeService: NodeService,
    private val mongoTemplate: MongoTemplate
) {

    fun create(userId: String, artifactInfo: ArtifactInfo, request: ShareRecordCreateRequest): ShareRecordInfo {
        with(artifactInfo) {
            checkNode(projectId, repoName, artifactUri)
            val shareRecord = TShareRecord(
                projectId = projectId,
                repoName = repoName,
                fullPath = artifactUri,
                expireDate = computeExpireDate(request.expireSeconds),
                authorizedUserList = request.authorizedUserList,
                authorizedIpList = request.authorizedIpList,
                token = genericToken(),
                createdBy = userId,
                createdDate = LocalDateTime.now(),
                lastModifiedBy = userId,
                lastModifiedDate = LocalDateTime.now()
            )
            mongoTemplate.save(shareRecord)
            val shareRecordInfo = convert(shareRecord)
            logger.info("Create share record[$shareRecordInfo] success.")
            return shareRecordInfo
        }

    }

    fun download(userId: String, token: String, artifactInfo: ArtifactInfo) {
        with(artifactInfo) {
            val query = Query.query(Criteria.where(TShareRecord::projectId.name).`is`(artifactInfo.projectId)
                .and(TShareRecord::repoName.name).`is`(repoName)
                .and(TShareRecord::fullPath.name).`is`(artifactUri)
                .and(TShareRecord::token.name).`is`(token))
            val shareRecord = mongoTemplate.findOne(query, TShareRecord::class.java) ?: throw ErrorCodeException(CommonMessageCode.RESOURCE_NOT_FOUND, token)
            if(shareRecord.authorizedUserList.isNotEmpty() && userId !in shareRecord.authorizedUserList) {
                throw ErrorCodeException(CommonMessageCode.PERMISSION_DENIED, token)
            }
            if(shareRecord.expireDate?.isBefore(LocalDateTime.now()) == true) {
                throw ErrorCodeException(CommonMessageCode.RESOURCE_EXPIRED, token)
            }
            val context = ArtifactDownloadContext()
            val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
            repository.download(context)
        }
    }

    private fun checkNode(projectId: String, repoName: String, fullPath: String) {
        repositoryService.checkRepository(projectId, repoName)
        val node = nodeService.detail(projectId, repoName, fullPath)
        if(node == null || node.nodeInfo.folder) {
            throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, fullPath)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ShareService::class.java)

        private fun genericToken(): String {
            return UUID.randomUUID().toString().replace("-", "").toLowerCase()
        }

        private fun genericShareUrl(shareRecord: TShareRecord): String {
            return "/api/share/${shareRecord.projectId}/${shareRecord.repoName}${shareRecord.fullPath}?token=${shareRecord.token}"
        }

        private fun computeExpireDate(expireSeconds: Long?): LocalDateTime? {
            return if(expireSeconds == null || expireSeconds <= 0) null
            else LocalDateTime.now().plusSeconds(expireSeconds)
        }

        private fun convert(tShareRecord: TShareRecord): ShareRecordInfo {
            return tShareRecord.let {
                ShareRecordInfo(
                    fullPath = it.fullPath,
                    repoName = it.repoName,
                    projectId = it.projectId,
                    shareUrl = genericShareUrl(it),
                    authorizedUserList = it.authorizedUserList,
                    authorizedIpList = it.authorizedIpList,
                    expireDate = it.expireDate?.format(DateTimeFormatter.ISO_DATE_TIME)
                )
            }
        }


    }
}
