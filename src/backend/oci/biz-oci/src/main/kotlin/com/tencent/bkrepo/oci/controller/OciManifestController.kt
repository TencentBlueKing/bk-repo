package com.tencent.bkrepo.oci.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.exception.PackageNotFoundException
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.service.OciManifestService
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * oci manifest controller
 */
@RestController
@RequestMapping(OCI_API_PREFIX)
@Suppress("MVCPathVariableInspection")
class OciManifestController(
	private val ociManifestService: OciManifestService
) {
	/**
	 * 检查manifest文件是否存在
	 */
	@RequestMapping("/{projectId}/{repoName}/**/manifests/{reference}", method = [RequestMethod.HEAD])
	fun checkManifestsExists(artifactInfo: OciManifestArtifactInfo) {
		throw PackageNotFoundException(artifactInfo.packageName)
	}

	/**
	 * 上传manifest文件
	 */
	@Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
	@PutMapping("/{projectId}/{repoName}/**/manifests/{tag}")
	fun uploadManifests(
		artifactInfo: OciManifestArtifactInfo,
		artifactFile: ArtifactFile
	) {
		ociManifestService.uploadManifest(artifactInfo, artifactFile)
	}
}
