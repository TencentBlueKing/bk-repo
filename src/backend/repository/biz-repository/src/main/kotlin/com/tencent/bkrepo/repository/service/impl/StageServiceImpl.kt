package com.tencent.bkrepo.repository.service.impl

import com.tencent.bkrepo.common.api.exception.ErrorCodeException
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.message.ArtifactMessageCode
import com.tencent.bkrepo.repository.constant.SystemMetadata
import com.tencent.bkrepo.repository.dao.NodeDao
import com.tencent.bkrepo.repository.model.TMetadata
import com.tencent.bkrepo.repository.model.TNode
import com.tencent.bkrepo.repository.pojo.stage.ArtifactStageEnum
import com.tencent.bkrepo.repository.service.StageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 制品晋级服务接口实现类
 */
@Service
class StageServiceImpl(
    private val nodeDao: NodeDao
) : StageService {

    override fun query(artifactInfo: ArtifactInfo): ArtifactStageEnum {
        val node = findAndCheckNode(artifactInfo)
        return getNodeStage(node)
    }

    override fun upgrade(artifactInfo: ArtifactInfo, tag: String?) {
        val node = findAndCheckNode(artifactInfo)
        val stageMetadata = findStageMetadata(node)
        try {
            val oldStage = ArtifactStageEnum.ofTagOrDefault(stageMetadata.value)
            var newStage = ArtifactStageEnum.ofTagOrNull(tag)
            newStage = oldStage.upgrade(newStage)
            stageMetadata.value = if (stageMetadata.value.isEmpty()) {
                newStage.tag
            } else {
                stageMetadata.value + "," + newStage.tag
            }
            nodeDao.save(node)
            logger.info("Upgrade stage[$artifactInfo] to $newStage success")
        } catch (exception: IllegalArgumentException) {
            throw ErrorCodeException(ArtifactMessageCode.STAGE_UPGRADE_ERROR,  exception.message.orEmpty())
        }
    }

    private fun findAndCheckNode(artifactInfo: ArtifactInfo): TNode {
        with(artifactInfo) {
            return nodeDao.findNode(projectId, repoName, getArtifactFullPath())
                ?: throw ErrorCodeException(ArtifactMessageCode.NODE_NOT_FOUND, getArtifactName())
        }
    }

    private fun getNodeStage(node: TNode): ArtifactStageEnum {
        val tag = node.metadata?.firstOrNull { it.key == SystemMetadata.STAGE.key }?.value
        return ArtifactStageEnum.ofTagOrDefault(tag)
    }

    private fun findStageMetadata(node: TNode): TMetadata {
        val metadataList = node.metadata ?: run {
            val emptyList = mutableListOf<TMetadata>()
            node.metadata = emptyList
            emptyList
        }
        return metadataList.firstOrNull { it.key == SystemMetadata.STAGE.key } ?: run {
            val stageMetadata = TMetadata(SystemMetadata.STAGE.key, ArtifactStageEnum.NONE.tag)
            metadataList.add(stageMetadata)
            return stageMetadata
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StageServiceImpl::class.java)
    }
}