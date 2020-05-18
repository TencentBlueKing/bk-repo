package com.tencent.bkrepo.helm.service

import com.google.gson.JsonParser
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.config.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.exception.HelmIndexFreshFailException
import com.tencent.bkrepo.helm.pojo.HelmSuccessResponse
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.JsonUtil.gson
import com.tencent.bkrepo.helm.utils.PackDecompressorUtils
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import org.apache.commons.fileupload.util.Streams
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ChartManipulationService {

    @Value("\${helm.registry.domain: ''}")
    private lateinit var domain: String

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun uploadProv(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes = getContextAttrMap(artifactFileMap)
        if (!artifactFileMap.keys.contains("prov")) throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
        repository.upload(context)
        return HelmSuccessResponse.pushSuccess()
    }

    fun getContextAttrMap(artifactFileMap: ArtifactFileMap): MutableMap<String, Any> {
        val attributesMap = mutableMapOf<String, Any>()
        artifactFileMap.entries.forEach { (name, file) ->
            if (name != "chart" && name != "prov") {
                throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
            }
            attributesMap[name + "_full_path"] = getFileFullPath(file)
        }
        return attributesMap
    }

    private fun getFileFullPath(artifactFile: ArtifactFile): String {
        val multipartArtifactFile = artifactFile as MultipartArtifactFile
        val fileName = multipartArtifactFile.getOriginalFilename()
        return FILE_SEPARATOR + fileName.substringAfterLast('/')
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes = getContextAttrMap(artifactFileMap)
        repository.upload(context)
        if (artifactFileMap.keys.contains("chart")) {
            try {
                freshIndexYamlForPush(artifactInfo, artifactFileMap)
            } catch (exception: HelmIndexFreshFailException) {
                targetFileRollback(artifactFileMap)
                throw HelmIndexFreshFailException(exception.message)
            } catch (exception: Exception) {
                targetFileRollback(artifactFileMap)
                throw HelmIndexFreshFailException("fresh $INDEX_CACHE_YAML file failed, file push failed")
            }
        }
        return HelmSuccessResponse.pushSuccess()
    }

    private fun targetFileRollback(artifactFileMap: ArtifactFileMap) {
        val context = ArtifactRemoveContext()
        context.contextAttributes[FULL_PATH] = getFileFullPath(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.remove(context)
        logger.error("fresh file $INDEX_CACHE_YAML failed")
    }

    private fun freshIndexYamlForPush(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap) {
        val inputStream = (artifactFileMap["chart"] as MultipartArtifactFile).getInputStream()
        val tempDir = System.getProperty("java.io.tmpdir")
        PackDecompressorUtils.unTarGZ(inputStream, tempDir)
        logger.info("file " + getFileName(artifactFileMap) + " unTar success!")
        val file = getUnTgzFile(artifactFileMap, tempDir)
        val chartMap = YamlUtils.getObject<MutableMap<String, Any>>(file)
        val indexEntity = getIndexYamlFile(artifactInfo, chartMap, artifactFileMap)
        uploadIndexYaml(indexEntity)
        logger.info("fresh index.yaml for push [${chartMap["name"]}-${chartMap["version"]}.tgz] success!")
    }

    private fun uploadIndexYaml(indexEntity: IndexEntity) {
        val artifactFile = ArtifactFileFactory.build()
        Streams.copy(YamlUtils.transEntity2File(indexEntity).byteInputStream(), artifactFile.getOutputStream(), true)
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.contextAttributes[OCTET_STREAM + FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val uploadRepository = RepositoryHolder.getRepository(uploadContext.repositoryInfo.category)
        uploadRepository.upload(uploadContext)
    }

    private fun getIndexYamlFile(
        artifactInfo: HelmArtifactInfo,
        chartMap: MutableMap<String, Any>,
        artifactFileMap: ArtifactFileMap
    ): IndexEntity {
        val indexEntity = getOriginalIndexYaml()
        updateChartMap(artifactInfo, chartMap, artifactFileMap)
        val chartName = chartMap["name"] as String
        val isFirstChart = indexEntity.entries.containsKey(chartName)
        indexEntity.entries.let {
            if (!isFirstChart) {
                it[chartName] = mutableListOf(chartMap)
            } else {
                it[chartName]?.add(chartMap)
            }
        }
        return indexEntity
    }

    private fun getOriginalIndexYaml(): IndexEntity {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val indexFile = repository.search(context) as File
        logger.info("search $INDEX_CACHE_YAML success!")
        val indexMap = YamlUtils.getObject<Map<String, Any>>(indexFile)
        return gson.fromJson(JsonParser().parse(gson.toJson(indexMap)).asJsonObject, IndexEntity::class.java)
    }

    private fun updateChartMap(
        artifactInfo: HelmArtifactInfo,
        chartMap: MutableMap<String, Any>,
        artifactFileMap: ArtifactFileMap
    ) {
        chartMap["urls"] = listOf(
            domain.trimEnd('/') + formatFullPath(
                "${artifactInfo.projectId}/${artifactInfo.repoName}/charts/${getFileName(artifactFileMap)}"
            )
        )
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+08:00")
        chartMap["created"] = LocalDateTime.now().format(format)
        chartMap["digest"] = artifactFileMap["chart"]?.getInputStream()?.let { FileDigestUtils.fileSha256(it) }!!
    }

    private fun getUnTgzFile(artifactFileMap: ArtifactFileMap, tempDir: String): File {
        try {
            val name = getFileName(artifactFileMap).substringBeforeLast('-')
            val filePath = "$tempDir/$name${FILE_SEPARATOR}Chart.yaml"
            logger.info("unTgz Chart.yaml file path : $filePath")
            return File(filePath)
        } catch (e: Exception) {
            logger.error("get unTgz file error : " + e.message)
            throw HelmIndexFreshFailException("file Chart.yaml not found")
        }
    }

    private fun getFileFullPath(artifactFileMap: ArtifactFileMap): String {
        val multipartArtifactFile = artifactFileMap["chart"] as? MultipartArtifactFile
            ?: throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
        val fileName = multipartArtifactFile.getOriginalFilename()
        return FILE_SEPARATOR + fileName.substringAfterLast('/')
    }

    private fun getFileName(artifactFileMap: ArtifactFileMap): String {
        return getFileFullPath(artifactFileMap).trimStart('/')
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun delete(artifactInfo: HelmArtifactInfo): HelmSuccessResponse {
        val chartInfo = getChartInfo(artifactInfo)
        val context = ArtifactRemoveContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        val fullPath = "/${chartInfo.first}-${chartInfo.second}.tgz"
        context.contextAttributes[FULL_PATH] = fullPath
        repository.remove(context)
        logger.info("remove artifact [$fullPath] success!")
        freshIndexYamlForRemove(chartInfo)
        return HelmSuccessResponse.deleteSuccess()
    }

    private fun getChartInfo(artifactInfo: HelmArtifactInfo): Pair<String, String> {
        val artifactUri = artifactInfo.artifactUri.trimStart('/')
        val name = artifactUri.substringBeforeLast('/')
        val version = artifactUri.substringAfterLast('/')
        return Pair(name, version)
    }

    private fun freshIndexYamlForRemove(chartInfo: Pair<String, String>) {
        try {
            val indexEntity = getOriginalIndexYaml()
            indexEntity.entries.let {
                if (it[chartInfo.first]?.size == 1 && chartInfo.second == it[chartInfo.first]?.get(0)?.get("version") as String) {
                    it.remove(chartInfo.first)
                } else {
                    run stop@{
                        it[chartInfo.first]?.forEachIndexed { index, chartMap ->
                            if (chartInfo.second == chartMap["version"] as String) {
                                it[chartInfo.first]?.removeAt(index)
                                return@stop
                            }
                        }
                    }
                }
            }
            uploadIndexYaml(indexEntity)
            logger.info("fresh index.yaml for delete [${chartInfo.first}-${chartInfo.second}.tgz] success!")
        } catch (exception: Exception) {
            logger.error("fresh index.yaml for delete [${chartInfo.first}-${chartInfo.second}.tgz] failed, ${exception.message}")
            throw exception
        }
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ChartManipulationService::class.java)
    }
}
