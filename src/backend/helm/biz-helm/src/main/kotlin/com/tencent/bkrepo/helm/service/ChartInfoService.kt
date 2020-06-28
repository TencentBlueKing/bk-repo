package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.util.JsonUtils.objectMapper
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.artifact.repository.HelmLocalRepository
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.utils.JsonUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ChartInfoService {
    @Autowired
    private lateinit var chartRepositoryService: ChartRepositoryService

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun allChartsList(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime?): String {
        if(startTime != null){
            val nodeList = chartRepositoryService.queryNodeList(artifactInfo, lastModifyTime = startTime)
            val indexEntity = chartRepositoryService.initIndexEntity()
            if (nodeList.isNotEmpty()) {
                chartRepositoryService.generateIndexFile(nodeList, indexEntity, artifactInfo)
            }
            return objectMapper.writeValueAsString(indexEntity.entries)
        }
        chartRepositoryService.freshIndexFile(artifactInfo)
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = INDEX_CACHE_YAML
        val inputStream = repository.search(context) as ArtifactInputStream
        return JsonUtil.searchJson(inputStream, artifactInfo.artifactUri)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    fun isExists(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        (repository as HelmLocalRepository).isExists(context)
    }
}
