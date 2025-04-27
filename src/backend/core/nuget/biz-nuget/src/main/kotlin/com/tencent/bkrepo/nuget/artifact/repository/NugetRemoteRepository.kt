/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2020 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tencent.bkrepo.nuget.artifact.repository

import com.tencent.bkrepo.common.api.constant.HttpStatus
import com.tencent.bkrepo.common.api.constant.MediaTypes.APPLICATION_JSON_WITHOUT_CHARSET
import com.tencent.bkrepo.common.api.constant.StringPool.UTF_8
import com.tencent.bkrepo.common.api.exception.MethodNotAllowedException
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactUploadContext
import com.tencent.bkrepo.common.artifact.repository.remote.RemoteRepository
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.common.NugetRemoteAndVirtualCommon
import com.tencent.bkrepo.nuget.constant.CACHE_CONTEXT
import com.tencent.bkrepo.nuget.constant.MANIFEST
import com.tencent.bkrepo.nuget.constant.NugetQueryType
import com.tencent.bkrepo.nuget.constant.PACKAGE_BASE_ADDRESS
import com.tencent.bkrepo.nuget.constant.PACKAGE_NAME
import com.tencent.bkrepo.nuget.constant.QUERY_TYPE
import com.tencent.bkrepo.nuget.constant.REGISTRATION_PATH
import com.tencent.bkrepo.nuget.constant.REMOTE_URL
import com.tencent.bkrepo.nuget.exception.NugetFeedNotFoundException
import com.tencent.bkrepo.nuget.pojo.artifact.NugetDownloadArtifactInfo
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.response.VersionListResponse
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Resource
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.NugetUtils.getServiceIndexFullPath
import com.tencent.bkrepo.nuget.util.RemoteRegistrationUtils
import okhttp3.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.net.URLEncoder

@Suppress("TooManyFunctions")
@Component
class NugetRemoteRepository(
    private val commonUtils: NugetRemoteAndVirtualCommon
) : RemoteRepository() {

    override fun query(context: ArtifactQueryContext): Any? {
        return when(context.getAttribute<NugetQueryType>(QUERY_TYPE)!!) {
            NugetQueryType.PACKAGE_VERSIONS -> enumerateVersions(context, context.getStringAttribute(PACKAGE_NAME)!!)
            NugetQueryType.SERVICE_INDEX -> feed(context.artifactInfo as NugetArtifactInfo)
            NugetQueryType.REGISTRATION_INDEX -> registrationIndex(context)
            NugetQueryType.REGISTRATION_PAGE -> registrationPage(context)
            NugetQueryType.REGISTRATION_LEAF -> registrationLeaf(context)
        }
    }

    private fun enumerateVersions(context: ArtifactQueryContext, packageId: String): List<String>? {
        val packageBaseAddress = getResourceId(PACKAGE_BASE_ADDRESS, context)
        val requestUrl = NugetUtils.buildPackageVersionsUrl(packageBaseAddress, packageId)
        context.putAttribute(REMOTE_URL, requestUrl)
        return super.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, VersionListResponse::class.java).versions
                .takeIf { versionList -> versionList.isNotEmpty() }
        }
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        val nugetArtifactInfo = context.artifactInfo as NugetDownloadArtifactInfo
        val feedContext = ArtifactQueryContext(context.repositoryDetail, nugetArtifactInfo)
        val packageBaseAddress = getResourceId(PACKAGE_BASE_ADDRESS, feedContext)
        val packageName = nugetArtifactInfo.getArtifactName()
        val version = nugetArtifactInfo.getArtifactVersion()
        val uri = if (nugetArtifactInfo.type == MANIFEST) {
            NugetUtils.getPackageManifestUri(packageName, version)
        } else {
            NugetUtils.getPackageContentUri(packageName, version)
        }
        val downloadUrl = UrlFormatter.format(packageBaseAddress, uri)
        context.putAttribute(REMOTE_URL, downloadUrl)
        return super.onDownload(context)
    }

    private fun feed(artifactInfo: NugetArtifactInfo): Feed {
        // 1、请求远程索引文件
        // 2、将resource里面的内容进行更改
        // 先使用type进行匹配筛选，然后在进行id的替换
        val feed = downloadServiceIndex(ArtifactQueryContext())
        val v2BaseUrl = NugetUtils.getV2Url(artifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(artifactInfo)
        val rewriteResource = feed.resources.mapNotNull { rewriteResource(it, v2BaseUrl, v3BaseUrl) }
        return Feed(feed.version, rewriteResource)
    }

    override fun checkQueryResponse(response: Response): Boolean {
        return super.checkQueryResponse(response) && checkJsonFormat(response)
    }

    override fun onQueryResponse(context: ArtifactQueryContext, response: Response): InputStream? {
        val artifactFile = createTempFile(response.body!!)
        context.getAndRemoveAttribute<ArtifactContext>(CACHE_CONTEXT)?.let {
            cacheArtifactFile(it, artifactFile)
        }
        return artifactFile.getInputStream()
    }

    override fun createRemoteDownloadUrl(context: ArtifactContext): String {
        return context.getStringAttribute(REMOTE_URL) ?: context.getRemoteConfiguration().url
    }

    private fun registrationIndex(context: ArtifactQueryContext): RegistrationIndex? {
        // 1、先根据请求URL匹配对应的远程URL地址的type，在根据type去找到对应的key
        // 2、根据匹配到的URL去添加请求packageId之后去请求远程索引文件
        // 3、缓存索引文件，然后将文件中的URL改成对应的仓库URL进行返回
        val nugetArtifactInfo = context.artifactInfo as NugetRegistrationArtifactInfo
        val registrationPath = context.getStringAttribute(REGISTRATION_PATH)!!
        val v2BaseUrl = NugetUtils.getV2Url(nugetArtifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(nugetArtifactInfo)
        val registrationIndex = downloadRemoteRegistrationIndex(
            context, nugetArtifactInfo, registrationPath, v2BaseUrl, v3BaseUrl
        ) ?: return null
        return RemoteRegistrationUtils.rewriteRegistrationIndexUrls(
            registrationIndex, nugetArtifactInfo, v2BaseUrl, v3BaseUrl, registrationPath
        )
    }

    private fun registrationPage(context: ArtifactQueryContext): RegistrationPage? {
        val nugetArtifactInfo = context.artifactInfo as NugetRegistrationArtifactInfo
        val registrationPath = context.getStringAttribute(REGISTRATION_PATH)!!
        val v2BaseUrl = NugetUtils.getV2Url(nugetArtifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(nugetArtifactInfo)
        val registrationPage = downloadRemoteRegistrationPage(
            context, nugetArtifactInfo, registrationPath, v2BaseUrl, v3BaseUrl
        ) ?: return null
        return RemoteRegistrationUtils.rewriteRegistrationPageUrls(
            registrationPage, nugetArtifactInfo, v2BaseUrl, v3BaseUrl, registrationPath
        )
    }

    private fun registrationLeaf(context: ArtifactQueryContext): RegistrationLeaf? {
        val nugetArtifactInfo = context.artifactInfo as NugetRegistrationArtifactInfo
        val registrationPath = context.getStringAttribute(REGISTRATION_PATH)!!
        val v2BaseUrl = NugetUtils.getV2Url(nugetArtifactInfo)
        val v3BaseUrl = NugetUtils.getV3Url(nugetArtifactInfo)
        val registrationLeaf = downloadRemoteRegistrationLeaf(
            context, nugetArtifactInfo, registrationPath, v2BaseUrl, v3BaseUrl
        ) ?: return null
        return RemoteRegistrationUtils.rewriteRegistrationLeafUrls(
            registrationLeaf, nugetArtifactInfo, v2BaseUrl, v3BaseUrl, registrationPath
        )
    }

    override fun upload(context: ArtifactUploadContext) {
        with(context) {
            val message = "Unable to upload nuget package into a remote repository [$projectId/$repoName]"
            logger.warn(message)
            // return 400 bad request
            response.status = HttpStatus.BAD_REQUEST.value
        }
    }

    // 获取服务地址
    private fun getResourceId(resourceType: String, context: ArtifactQueryContext): String {
        val url = context.getRemoteConfiguration().url
        val feed = downloadServiceIndex(context)
        return feed.resources.find { it.type == resourceType }?.id
            ?: throw MethodNotAllowedException(
                "Resource Type[$resourceType] Not Found in Service Index[$url]"
            )
    }

    // 向代理源请求服务索引文件，优先查询缓存
    private fun downloadServiceIndex(context: ArtifactQueryContext): Feed {
        val downloadContext = buildServiceIndexDownloadContext(context)
        context.putAttribute(CACHE_CONTEXT, downloadContext)
        return getCacheArtifactResource(downloadContext)?.let {
            context.getAndRemoveAttribute<ArtifactContext>(CACHE_CONTEXT)
            it.getSingleStream().use { inputStream -> JsonUtils.objectMapper.readValue(inputStream, Feed::class.java) }
        } ?: run {
            val requestUrl = context.getRemoteConfiguration().url
            logger.info("Query Remote Service Index from [$requestUrl]")
            super.query(context)?.let { JsonUtils.objectMapper.readValue(it as InputStream, Feed::class.java) }
                ?: throw NugetFeedNotFoundException("query remote feed index.json for [$requestUrl] failed!")
        }
    }

    private fun downloadRemoteRegistrationIndex(
        context: ArtifactQueryContext,
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): RegistrationIndex? {
        val registrationBaseUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val remoteRegistrationBaseUrl = convertToRemoteUrl(context, registrationBaseUrl, v2BaseUrl, v3BaseUrl)
        val remoteIndexUrl = NugetUtils.buildRegistrationIndexUrl(
            remoteRegistrationBaseUrl, artifactInfo.packageName
        ).toString()
        context.putAttribute(REMOTE_URL, remoteIndexUrl)
        logger.info("Query Remote Registration Index from [$remoteIndexUrl]")
        return super.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, RegistrationIndex::class.java)
        }
    }

    private fun downloadRemoteRegistrationPage(
        context: ArtifactQueryContext,
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): RegistrationPage? {
        val registrationBaseUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val remoteRegistrationBaseUrl = convertToRemoteUrl(context, registrationBaseUrl, v2BaseUrl, v3BaseUrl)
        val remotePageUrl = NugetUtils.buildRegistrationPageUrl(
            remoteRegistrationBaseUrl, artifactInfo.packageName, artifactInfo.lowerVersion, artifactInfo.upperVersion
        )
        context.putAttribute(REMOTE_URL, remotePageUrl)
        logger.info("Query Remote Registration Page from [$remotePageUrl]")
        return super.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, RegistrationPage::class.java)
        }
    }

    fun downloadRemoteRegistrationLeaf(
        context: ArtifactQueryContext,
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): RegistrationLeaf? {
        val registrationBaseUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val remoteRegistrationBaseUrl = convertToRemoteUrl(context, registrationBaseUrl, v2BaseUrl, v3BaseUrl)
        val remoteLeafUrl = NugetUtils.buildRegistrationLeafUrl(
            remoteRegistrationBaseUrl, artifactInfo.packageName, artifactInfo.version
        )
        context.putAttribute(REMOTE_URL, remoteLeafUrl)
        logger.info("Query Remote Registration Leaf from [$remoteLeafUrl]")
        return super.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, RegistrationLeaf::class.java)
        }
    }

    private fun rewriteResource(
        originalResource: Resource,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): Resource? {
        val urlConverter = commonUtils.urlConvertersMap[originalResource.type] ?: return null
        val convertedUrl = urlConverter.convert(v2BaseUrl, v3BaseUrl)
        return Resource(convertedUrl, originalResource.type, originalResource.comment, originalResource.clientVersion)
    }

    private fun buildServiceIndexDownloadContext(context: ArtifactContext): ArtifactDownloadContext {
        with(context) {
            val configuration = getRemoteConfiguration()
            val fullPath = getServiceIndexFullPath(URLEncoder.encode(configuration.url, UTF_8))
            val artifactInfo = ArtifactInfo(projectId, repoName, fullPath)
            return ArtifactDownloadContext(
                artifact = artifactInfo,
                repo = repositoryDetail
            )
        }
    }

    private fun convertToRemoteUrl(
        context: ArtifactQueryContext,
        resourceId: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): String {
        val feed = downloadServiceIndex(context)
        val matchTypes = commonUtils.urlConvertersMap.filterValues {
            it.convert(v2BaseUrl, v3BaseUrl).trimEnd('/') == resourceId
        }.keys.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("Failed to extract type by url [$resourceId]")
        return feed.resources.firstOrNull { matchTypes.contains(it.type) }?.id
            ?: throw IllegalStateException("Failed to match url for types: [$matchTypes]")
    }

    private fun checkJsonFormat(response: Response): Boolean {
        val contentType = response.body!!.contentType()
        if (!contentType.toString().contains(APPLICATION_JSON_WITHOUT_CHARSET)) {
            logger.warn(
                "Query Failed: Response from [${response.request.url}] is not JSON format. " +
                    "Content-Type: $contentType"
            )
            return false
        }
        return true
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(NugetRemoteRepository::class.java)
    }
}
