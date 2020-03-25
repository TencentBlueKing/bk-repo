package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.UPLOAD_SUCCESS_MAP
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChartManipulationService {

	@Permission(ResourceType.REPO, PermissionAction.WRITE)
	@Transactional(rollbackFor = [Throwable::class])
	fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap) : Map<String,Boolean> {
		val context = ArtifactUploadContext(artifactFileMap)
		val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
		context.contextAttributes[FULL_PATH] = getFileFullPath(artifactFileMap)
		repository.upload(context)
		return UPLOAD_SUCCESS_MAP
	}

	private fun getFileFullPath(artifactFileMap: ArtifactFileMap): String {
		val fileName = (artifactFileMap["chart"] as MultipartArtifactFile).getOriginalFilename()
		return FILE_SEPARATOR + fileName.substringAfterLast('/')
	}
}
