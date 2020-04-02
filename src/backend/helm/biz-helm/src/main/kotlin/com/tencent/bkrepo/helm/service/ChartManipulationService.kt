package com.tencent.bkrepo.helm.service

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.file.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.DELETE_SUCCESS_MAP
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.UPLOAD_SUCCESS_MAP
import com.tencent.bkrepo.helm.exception.HelmIndexFreshFailException
import com.tencent.bkrepo.helm.pojo.ChartEntity
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.PackDecompressorUtils
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import org.apache.commons.fileupload.util.Streams
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ChartManipulationService {

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): Map<String, Any> {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = getFileFullPath(artifactFileMap)
        repository.upload(context)
        try {
            freshIndexYaml(artifactFileMap)
        } catch (exception: Exception) {
            val removeContext = ArtifactRemoveContext()
            removeContext.contextAttributes[FULL_PATH] = getFileFullPath(artifactFileMap)
            repository.remove(removeContext)
            logger.error("fresh $INDEX_CACHE_YAML file failed")
            throw HelmIndexFreshFailException(exception.message!!)
        }
        return UPLOAD_SUCCESS_MAP
    }

    private fun freshIndexYaml(artifactFileMap: ArtifactFileMap) {
        val inputStream = (artifactFileMap["chart"] as MultipartArtifactFile).getInputStream()
        val tempDir = System.getProperty("java.io.tmpdir")
        PackDecompressorUtils.unTarGZ(inputStream, tempDir)
        logger.info("file " + getFileFullPath(artifactFileMap) + " unTar success!")
        val file = getUnTgzFile(artifactFileMap, tempDir)
        val chartEntity = YamlUtils.getObject<ChartEntity>(file)
        val indexEntity = getIndexYamlFile(chartEntity, artifactFileMap)
        uploadIndexYaml(indexEntity)
    }

    private fun uploadIndexYaml(indexEntity: IndexEntity) {
        val artifactFile = ArtifactFileFactory.build()
        Streams.copy(YamlUtils.transEntity2File(indexEntity).byteInputStream(), artifactFile.getOutputStream(), true)
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val uploadRepository = RepositoryHolder.getRepository(uploadContext.repositoryInfo.category)
        uploadRepository.upload(uploadContext)
        logger.info("fresh $INDEX_CACHE_YAML success!")
    }

    private fun getIndexYamlFile(chartEntity: ChartEntity, artifactFileMap: ArtifactFileMap): IndexEntity {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val indexFile = repository.search(context) as File
        logger.info("search $INDEX_CACHE_YAML success!")
        val indexEntity = YamlUtils.getObject<IndexEntity>(indexFile)
        updateChartEntity(chartEntity, artifactFileMap)
        val chartName = chartEntity.name!!
        val isFirstChart = indexEntity.entries!!.containsKey(chartName)
        indexEntity.entries?.let {
            if (!isFirstChart) {
                it[chartName] = mutableListOf(chartEntity)
            } else {
                it[chartName]!!.add(chartEntity)
            }
        }
        return indexEntity
    }

    private fun updateChartEntity(
        chartEntity: ChartEntity,
        artifactFileMap: ArtifactFileMap
    ) {
        chartEntity.urls = listOf("charts${getFileFullPath(artifactFileMap)}")
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS+08:00")
        chartEntity.created = LocalDateTime.now().format(format)
        chartEntity.digest = artifactFileMap["chart"]?.getInputStream()?.let { FileDigestUtils.fileSha256(it) }
    }

    private fun getUnTgzFile(artifactFileMap: ArtifactFileMap, tempDir: String): File {
        try {
            val name = getFileFullPath(artifactFileMap).trimStart('/').substringBeforeLast('-')
            val filePath = "$tempDir/$name${FILE_SEPARATOR}Chart.yaml"
            logger.info("unTgz Chart.yaml file path : $filePath")
            return File(filePath)
        } catch (e: Exception) {
            logger.error("get unTgz file error : " + e.message)
            throw HelmIndexFreshFailException("file Chart.yaml not found")
        }
    }

    private fun getFileFullPath(artifactFileMap: ArtifactFileMap): String {
        val fileName = (artifactFileMap["chart"] as MultipartArtifactFile).getOriginalFilename()
        return FILE_SEPARATOR + fileName.substringAfterLast('/')
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun delete(artifactInfo: HelmArtifactInfo): Map<String, Any> {
        val chartInfo = getChartInfo(artifactInfo)
        val context = ArtifactRemoveContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "/${chartInfo.first}-${chartInfo.second}.tgz"
        repository.remove(context)
        freshIndexYaml(chartInfo)
        return DELETE_SUCCESS_MAP
    }

    private fun getChartInfo(artifactInfo: HelmArtifactInfo): Pair<String, String> {
        val artifactUri = artifactInfo.artifactUri.trimStart('/')
        val name = artifactUri.substringBeforeLast('/')
        val version = artifactUri.substringAfterLast('/')
        return Pair(name, version)
    }

    private fun freshIndexYaml(chartInfo: Pair<String, String>) {
        val context = ArtifactSearchContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        context.contextAttributes[FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val indexFile = repository.search(context) as File
        logger.info("helm search $INDEX_CACHE_YAML for delete chart success!")
        val indexMap = YamlUtils.getObject<Map<String,Any>>(indexFile)
        val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val asJsonObject = JsonParser().parse(gson.toJson(indexMap)).asJsonObject
        val indexEntity = gson.fromJson(asJsonObject, IndexEntity::class.java)
        indexEntity.entries?.let {
            if (it[chartInfo.first]?.size == 1 && chartInfo.second == it[chartInfo.first]?.get(0)?.version) {
                it.remove(chartInfo.first)
            } else {
                run stop@{
                    it[chartInfo.first]?.forEachIndexed { index, chartEntity ->
                        if (chartInfo.second == chartEntity.version) {
                            it[chartInfo.first]?.removeAt(index)
                            return@stop
                        }
                    }
                }
            }
        }
        uploadIndexYaml(indexEntity)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ChartManipulationService::class.java)
    }
}
