package com.tencent.bkrepo.oci.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.service.OciBlobService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

/**
 * oci blob controller
 */
@RestController
@RequestMapping(OCI_API_PREFIX)
@Suppress("MVCPathVariableInspection")
class OciBlobController(
	val ociBlobService: OciBlobService
) {
	/**
	 * 检查blob文件是否存在
	 * helm chart push 时会调用该请求去判断blob文件在服务器上是否存在，如果存在则不上传
	 */
	@Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
	@RequestMapping("/{projectId}/{repoName}/**/blobs/{digest}", method = [RequestMethod.HEAD])
	fun checkBlobExists(
		artifactInfo: OciBlobArtifactInfo
	) {
		ociBlobService.checkBlobExists(artifactInfo)
	}

	/**
	 * 上传blob文件或者是完成上传，通过请求头[User-Agent]来判断
	 * 如果正则匹配成功，则进行上传，执行完成则完成；否则使用的是追加上传的方式，完成最后一块的上传进行合并。
	 */
	@Permission(type = ResourceType.REPO, action = PermissionAction.WRITE)
	@PutMapping("/{projectId}/{repoName}/**/blobs/uploads/{uuid}")
	fun uploadBlob(
		artifactInfo: OciBlobArtifactInfo,
		artifactFile: ArtifactFile
	) {
		ociBlobService.uploadBlob(artifactInfo, artifactFile)
	}

	/**
	 * 开始上传blob文件
	 */
	@PostMapping("/{projectId}/{repoName}/**/blobs/uploads/")
	fun startBlobUpload(
		artifactInfo: OciArtifactInfo
	) {
		return ociBlobService.startUploadBlob(artifactInfo)
	}

	/**
	 * 追加上传
	 */
	@RequestMapping("/{projectId}/{repoName}/**/blobs/uploads/{uuid}", method = [RequestMethod.PATCH])
	fun appendBlobUpload() {
		throw MethodNotAllowedException()
	}
}
