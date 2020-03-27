package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChartRepositoryService {

	@Permission(ResourceType.REPO, PermissionAction.READ)
	@Transactional(rollbackFor = [Throwable::class])
	fun getIndexYaml(artifactInfo: HelmArtifactInfo) {
		val context = ArtifactDownloadContext()
		val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
		context.contextAttributes[FULL_PATH] = "/$INDEX_CACHE_YAML"
		repository.download(context)
	}
}