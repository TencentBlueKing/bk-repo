package com.tencent.bkrepo.oci.controller

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.oci.constant.OCI_API_PREFIX
import com.tencent.bkrepo.oci.pojo.artifact.OciBlobArtifactInfo
import com.tencent.bkrepo.oci.service.OciBlobService
import org.springframework.http.ResponseEntity
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
	@Permission(type = ResourceType.REPO, action = PermissionAction.READ)
	@RequestMapping("/{projectId}/{repoName}/**/blobs/{digest}", method = [RequestMethod.HEAD])
	fun checkBlobExists(
		artifactInfo: OciBlobArtifactInfo
	):ResponseEntity<Any> {
		return ociBlobService.checkBlobExists(artifactInfo)
	}

	/**
	 * 上传blob文件或者是完成上传，通过请求头来判断
	 */
	@PutMapping("/{projectId}/{repoName}/**/blobs/uploads/{uuid}")
	fun uploadBlob() {
		throw MethodNotAllowedException()
	}

	/**
	 * 开始上传blob文件
	 */
	@PostMapping("/{projectId}/{repoName}/**/blobs/uploads/")
	fun startBlobUpload() {
		throw MethodNotAllowedException()
	}

	/**
	 * 追加上传
	 */
	@RequestMapping("/{projectId}/{repoName}/**/blobs/uploads/{uuid}", method = [RequestMethod.PATCH])
	fun appendBlobUpload() {
		throw MethodNotAllowedException()
	}
}
