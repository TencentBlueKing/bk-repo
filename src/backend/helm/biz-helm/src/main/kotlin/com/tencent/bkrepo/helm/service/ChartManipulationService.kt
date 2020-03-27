package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.UPLOAD_SUCCESS_MAP
import com.tencent.bkrepo.helm.pojo.ChartEntity
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.PackDecompressorUtils
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.apache.commons.fileupload.util.Streams
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File

@Service
class ChartManipulationService {

	@Permission(ResourceType.REPO, PermissionAction.WRITE)
	@Transactional(rollbackFor = [Throwable::class])
	fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): Map<String, Boolean> {
		val context = ArtifactUploadContext(artifactFileMap)
		val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
		context.contextAttributes[FULL_PATH] = getFileFullPath(artifactFileMap)
		repository.upload(context)
		freshIndexYaml(artifactInfo, artifactFileMap)
		return UPLOAD_SUCCESS_MAP
	}

	private fun freshIndexYaml(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap) {
		val inputStream = (artifactFileMap["chart"] as MultipartArtifactFile).getInputStream()
		val tempDir = System.getProperty("java.io.tmpdir")
		PackDecompressorUtils.unTarGZ(inputStream, tempDir)
		val file = getUnTgzFile(artifactFileMap, tempDir)
		val chartEntity = YamlUtils.getObject<ChartEntity>(file)
		val indexEntity = getIndexYamlFile(chartEntity)
		uploadIndexYaml(indexEntity)
	}

	private fun uploadIndexYaml(indexEntity: IndexEntity) {
		val artifactFile = ArtifactFileFactory.build()
		Streams.copy(YamlUtils.transEntity2File(indexEntity).byteInputStream(), artifactFile.getOutputStream(), true)
		val uploadContext = ArtifactUploadContext(artifactFile)
		uploadContext.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
		val uploadRepository = RepositoryHolder.getRepository(uploadContext.repositoryInfo.category)
		uploadRepository.upload(uploadContext)
	}

	private fun getIndexYamlFile(chartEntity: ChartEntity): IndexEntity {
		val context = ArtifactSearchContext()
		val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
		context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
		val indexFile = repository.search(context) as File
		val indexEntity = YamlUtils.getObject<IndexEntity>(indexFile)
		indexEntity.entries = mapOf(chartEntity.name!! to chartEntity)
		return indexEntity
	}

	private fun getUnTgzFile(artifactFileMap: ArtifactFileMap, tempDir: String): File {
		val name = getFileFullPath(artifactFileMap).trimStart('/').split('-')[0]
		return File("$tempDir$name${FILE_SEPARATOR}Chart.yaml")
	}

	private fun getFileFullPath(artifactFileMap: ArtifactFileMap): String {
		val fileName = (artifactFileMap["chart"] as MultipartArtifactFile).getOriginalFilename()
		return FILE_SEPARATOR + fileName.substringAfterLast('/')
	}
}
