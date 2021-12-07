package com.tencent.bkrepo.oci.service.impl

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.core.ArtifactService
import com.tencent.bkrepo.oci.pojo.artifact.OciManifestArtifactInfo
import com.tencent.bkrepo.oci.service.OciManifestService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OciManifestServiceImpl: OciManifestService, ArtifactService() {

	override fun checkManifestsExists(artifactInfo: OciManifestArtifactInfo) {
		with(artifactInfo) {
			logger.info("handing request check manifest exists for package [$packageName] " +
				"with reference [$reference] in repo [${getRepoIdentify()}]"
			)
			val context = ArtifactDownloadContext()
			repository.download(context)
		}
	}

	override fun uploadManifest(artifactInfo: OciManifestArtifactInfo, artifactFile: ArtifactFile) {
		with(artifactInfo) {
			logger.info("handing request upload manifest for package [$packageName] " +
				"with reference [$reference] in repo [${getRepoIdentify()}]"
			)
			val context = ArtifactUploadContext(artifactFile)
			repository.upload(context)
		}
	}

	override fun downloadManifests(artifactInfo: OciManifestArtifactInfo) {
		logger.info("handing request download manifest for package [${artifactInfo.packageName}] " +
			"with reference [${artifactInfo.reference}] in repo [${artifactInfo.getRepoIdentify()}]"
		)
		val context = ArtifactDownloadContext()
		repository.download(context)
	}

	companion object {
		private val logger = LoggerFactory.getLogger(OciManifestServiceImpl::class.java)
	}
}
