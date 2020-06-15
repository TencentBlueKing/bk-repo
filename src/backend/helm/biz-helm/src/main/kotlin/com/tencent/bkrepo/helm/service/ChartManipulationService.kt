package com.tencent.bkrepo.helm.service

import com.google.gson.JsonParser
import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.artifact.api.ArtifactFileMap
import com.tencent.bkrepo.common.artifact.config.OCTET_STREAM
import com.tencent.bkrepo.common.artifact.permission.Permission
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactRemoveContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.context.RepositoryHolder
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.file.multipart.MultipartArtifactFile
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.storage.util.FileDigestUtils
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.CHART
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.NAME
import com.tencent.bkrepo.helm.constants.PROV
import com.tencent.bkrepo.helm.constants.PROVENANCE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.VERSION
import com.tencent.bkrepo.helm.exception.HelmErrorInvalidProvenanceFileException
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.exception.HelmIndexFreshFailException
import com.tencent.bkrepo.helm.pojo.HelmSuccessResponse
import com.tencent.bkrepo.helm.pojo.IndexEntity
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.JsonUtil.gson
import com.tencent.bkrepo.helm.utils.YamlUtils
import com.tencent.bkrepo.repository.util.NodeUtils.FILE_SEPARATOR
import com.tencent.bkrepo.repository.util.NodeUtils.formatFullPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
        context.contextAttributes = getContextAttrMap(artifactFileMap = artifactFileMap)
        if (!artifactFileMap.keys.contains(PROV)) throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
        repository.upload(context)
        return HelmSuccessResponse.pushSuccess()
    }

    fun getContextAttrMap(
        artifactFileMap: ArtifactFileMap,
        chartFileInfo: Map<String, Any>? = null
    ): MutableMap<String, Any> {
        val attributesMap = mutableMapOf<String, Any>()
        artifactFileMap.entries.forEach { (name, _) ->
            if (CHART != name && PROV != name) {
                throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
            }
            if (CHART == name) {
                attributesMap[name + "_full_path"] = getChartFileFullPath(chartFileInfo)
            }
            if (PROV == name) {
                attributesMap[name + "_full_path"] = getProvFileFullPath(artifactFileMap)
            }
        }
        return attributesMap
    }

    private fun getChartFileFullPath(chartFile: Map<String, Any>?): String {
        val chartName = chartFile?.get(NAME) as String
        val chartVersion = chartFile[VERSION] as String
        return String.format("$FILE_SEPARATOR%s-%s.%s", chartName, chartVersion, CHART_PACKAGE_FILE_EXTENSION)
    }

    private fun getProvFileFullPath(artifactFileMap: ArtifactFileMap): String {
        val inputStream = (artifactFileMap[PROV] as MultipartArtifactFile).getInputStream()
        val contentStr = String(inputStream.readBytes())
        val hasPGPBegin = contentStr.startsWith("-----BEGIN PGP SIGNED MESSAGE-----")
        val nameMatch = Regex("\nname:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        val versionMatch = Regex("\nversion:[ *](.+)").findAll(contentStr).toList().flatMap(MatchResult::groupValues)
        if (!hasPGPBegin || nameMatch.size != 2 || versionMatch.size != 2) {
            throw HelmErrorInvalidProvenanceFileException("invalid provenance file")
        }
        return provenanceFilenameFromNameVersion(nameMatch[1], versionMatch[1])
    }

    private fun provenanceFilenameFromNameVersion(name: String, version: String): String {
        return String.format("$FILE_SEPARATOR%s-%s.%s", name, version, PROVENANCE_FILE_EXTENSION)
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun upload(artifactInfo: HelmArtifactInfo, artifactFileMap: ArtifactFileMap): HelmSuccessResponse {
        val context = ArtifactUploadContext(artifactFileMap)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        val chartFileInfo = getChartFile(artifactFileMap)
        context.contextAttributes = getContextAttrMap(artifactFileMap, chartFileInfo)
        repository.upload(context)
        if (artifactFileMap.keys.contains(CHART)) {
            try {
                freshIndexYamlForPush(artifactInfo, chartFileInfo)
            } catch (exception: HelmIndexFreshFailException) {
                targetFileRollback(chartFileInfo)
                throw HelmIndexFreshFailException(exception.message)
            } catch (exception: Exception) {
                targetFileRollback(chartFileInfo)
                throw HelmIndexFreshFailException("fresh $INDEX_CACHE_YAML file failed, file push failed")
            }
        }
        return HelmSuccessResponse.pushSuccess()
    }

    private fun targetFileRollback(chartFileInfo: Map<String, Any>) {
        val context = ArtifactRemoveContext()
        context.contextAttributes[FULL_PATH] = getChartFileFullPath(chartFileInfo)
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        repository.remove(context)
        logger.error("fresh file $INDEX_CACHE_YAML failed")
    }

    private fun freshIndexYamlForPush(
        artifactInfo: HelmArtifactInfo,
        chartFileInfo: MutableMap<String, Any>
    ) {
        val indexEntity = getIndexYamlFile(artifactInfo, chartFileInfo)
        uploadIndexYaml(indexEntity)
        logger.info("fresh index.yaml for push [${chartFileInfo[NAME]}-${chartFileInfo[VERSION]}.$CHART_PACKAGE_FILE_EXTENSION] success!")
    }

    private fun getChartFile(artifactFileMap: ArtifactFileMap): MutableMap<String, Any> {
        if (!artifactFileMap.keys.contains(CHART)) throw HelmFileNotFoundException("no package or provenance file found in form fields chart and prov")
        val inputStream = (artifactFileMap[CHART] as MultipartArtifactFile).getInputStream()
        val result = inputStream.getArchivesContent("tgz")
        val chartFileInfoMap = YamlUtils.convertStringToEntity<MutableMap<String, Any>>(result)
        chartFileInfoMap["digest"] = FileDigestUtils.fileSha256(inputStream)
        return chartFileInfoMap
    }

    private fun uploadIndexYaml(indexEntity: IndexEntity) {
        val artifactFile = ArtifactFileFactory.build(YamlUtils.transEntity2File(indexEntity).byteInputStream())
        val uploadContext = ArtifactUploadContext(artifactFile)
        uploadContext.contextAttributes[OCTET_STREAM + FULL_PATH] = "$FILE_SEPARATOR$INDEX_CACHE_YAML"
        val uploadRepository = RepositoryHolder.getRepository(uploadContext.repositoryInfo.category)
        uploadRepository.upload(uploadContext)
    }

    private fun getIndexYamlFile(
        artifactInfo: HelmArtifactInfo,
        chartMap: MutableMap<String, Any>
    ): IndexEntity {
        val indexEntity = getOriginalIndexYaml()
        updateChartMap(artifactInfo, chartMap)
        val chartName = chartMap[NAME] as String
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
        val indexMap = (repository.search(context) as? ArtifactInputStream)?.run {
            YamlUtils.convertFileToEntity<Map<String, Any>>(this)
        }
        logger.info("search original $INDEX_CACHE_YAML success!")
        return gson.fromJson(JsonParser().parse(gson.toJson(indexMap)).asJsonObject, IndexEntity::class.java)
    }

    private fun updateChartMap(
        artifactInfo: HelmArtifactInfo,
        chartMap: MutableMap<String, Any>
    ) {
        val chartName = chartMap[NAME] as String
        val chartVersion = chartMap[VERSION] as String
        chartMap["urls"] = listOf(
            domain.trimEnd('/') + formatFullPath(
                "${artifactInfo.projectId}/${artifactInfo.repoName}/charts/$chartName-$chartVersion.tgz"
            )
        )
        val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+08:00")
        chartMap["created"] = LocalDateTime.now().format(format)
        chartMap["digest"] = chartMap["digest"] as String
    }

    @Permission(ResourceType.REPO, PermissionAction.WRITE)
    @Transactional(rollbackFor = [Throwable::class])
    fun delete(artifactInfo: HelmArtifactInfo): HelmSuccessResponse {
        val chartInfo = getChartInfo(artifactInfo)
        val context = ArtifactRemoveContext()
        val repository = RepositoryHolder.getRepository(context.repositoryInfo.category)
        val fullPath = String.format("/%s-%s.%s", chartInfo.first, chartInfo.second, CHART_PACKAGE_FILE_EXTENSION)
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
                if (it[chartInfo.first]?.size == 1 && chartInfo.second == it[chartInfo.first]?.get(0)?.get(VERSION) as String) {
                    it.remove(chartInfo.first)
                } else {
                    run stop@{
                        it[chartInfo.first]?.forEachIndexed { index, chartMap ->
                            if (chartInfo.second == chartMap[VERSION] as String) {
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
