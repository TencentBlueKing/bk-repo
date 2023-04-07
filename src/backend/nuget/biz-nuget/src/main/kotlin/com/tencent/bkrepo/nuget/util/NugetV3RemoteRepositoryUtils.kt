package com.tencent.bkrepo.nuget.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.nuget.common.NugetRemoteAndVirtualCommon
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Resource
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationCatalogEntry
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationItem
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationPageItem
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import java.net.URI
import java.net.URLEncoder
import java.util.Objects

object NugetV3RemoteRepositoryUtils {
    fun convertOriginalToBkrepoResource(
        original: Resource,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): Resource? {
        return if (!NugetRemoteAndVirtualCommon().originalToBkrepoConverters.containsKey(original.type)) {
            null
        } else {
            val urlConvert = NugetRemoteAndVirtualCommon().originalToBkrepoConverters[original.type]!!
            val convertedUrl: String = urlConvert.convert(v2BaseUrl, v3BaseUrl)
            Resource(convertedUrl, original.type, original.comment, original.clientVersion)
        }
    }

    @SuppressWarnings("LongParameterList")
    fun rewriteRegistrationIndexUrls(
        originalRegistrationIndex: RegistrationIndex,
        artifactInfo: NugetRegistrationArtifactInfo,
        v2BaseUrl: String,
        v3BaseUrl: String,
        registrationPath: String,
        proxyChannelName: String? = null
    ): RegistrationIndex {
        val v3RegistrationUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val itemList = originalRegistrationIndex.items.map { item ->
            registrationResultItemRewriter(
                item, artifactInfo.packageName, v2BaseUrl, v3RegistrationUrl, proxyChannelName
            )
        }
        return RegistrationIndex(
            id = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, artifactInfo.packageName),
            count = originalRegistrationIndex.count,
            items = itemList
        )
    }

    fun combineRegistrationIndex(
        localRegistrationIndex: RegistrationIndex,
        remoteRegistrationIndex: RegistrationIndex,
        artifactInfo: NugetRegistrationArtifactInfo,
        v3RegistrationUrl: String
    ): RegistrationIndex {
        // 提取远程查询结果不折叠的分页
        val remoteLeafList =
            remoteRegistrationIndex.items.mapNotNull { it.items }.flatten().onEach { it.sourceType = "PROXY" }
        // 远程Registration Index查询结果为折叠的分页结果时，本地结果也折叠不显示具体版本元数据
        return if (remoteRegistrationIndex.items.isNotEmpty() && remoteLeafList.isEmpty()) {
            localRegistrationIndex.items.forEach { it.items = null }
            RegistrationIndex(
                id = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, artifactInfo.packageName),
                count = localRegistrationIndex.count + remoteRegistrationIndex.count,
                items = localRegistrationIndex.items + remoteRegistrationIndex.items
            )
        // 远程Registration Index查询结果不折叠时，与本地版本聚合并重新分页
        } else {
            // 提取本地包每个版本的元数据和版本列表
            val localLeafList = localRegistrationIndex.items.mapNotNull { it.items }.flatten().toMutableList()
            val localVersions = localLeafList.map { it.catalogEntry.version }
            remoteLeafList.forEach {
                if (!localVersions.contains(it.catalogEntry.version)) { localLeafList.add(it) }
            }
            val sortedLeafList = localLeafList.sortedWith { o1, o2 ->
                NugetVersionUtils.compareSemVer(o1.catalogEntry.version, o2.catalogEntry.version)
            }
            NugetV3RegistrationUtils.registrationPageItemToRegistrationIndex(sortedLeafList, v3RegistrationUrl)
        }
    }

    private fun registrationResultItemRewriter(
        originalItem: RegistrationItem,
        packageName: String,
        v2BaseUrl: String,
        v3RegistrationUrl: String,
        proxyChannelName: String? = null
    ): RegistrationItem {
        val isPaged = Objects.isNull(originalItem.items)
        val registrationIndexUrl = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, packageName)
        val registrationPageUrl = buildRegistrationPageProxyUrl(
            originalItem.id, packageName, v3RegistrationUrl, proxyChannelName
        )
        val pageItemList = originalItem.items?.map { item ->
            registrationResultPageItemRewriter(item, packageName, v2BaseUrl, v3RegistrationUrl)
        }
        return RegistrationItem(
            id = if (isPaged) registrationPageUrl else registrationIndexUrl,
            count = originalItem.count,
            items = pageItemList,
            lower = originalItem.lower,
            upper = originalItem.upper,
            parent = if (isPaged) null else registrationIndexUrl
        )
    }

    private fun buildRegistrationPageProxyUrl(
        remoteRegistrationPageUrl: URI,
        packageName: String,
        v3RegistrationUrl: String,
        proxyChannelName: String?
    ): URI {
        val encodedRemoteUrl = URLEncoder.encode(remoteRegistrationPageUrl.toString(), StringPool.UTF_8)
        val encodedProxyChannelName = URLEncoder.encode(proxyChannelName, StringPool.UTF_8)
        val queryString = "proxyChannelName=$encodedProxyChannelName&url=$encodedRemoteUrl"
        val proxyPageUrl = UrlFormatter.format(v3RegistrationUrl, "proxy/page/$packageName", queryString)
        return URI.create(proxyPageUrl)
    }

    fun registrationResultPageItemRewriter(
        originalPageItem: RegistrationPageItem,
        packageName: String,
        v2BaseUrl: String,
        v3RegistrationUrl: String
    ): RegistrationPageItem {
        val version = originalPageItem.catalogEntry.version
        val packageContentUrl = NugetUtils.buildPackageContentUrl(v3RegistrationUrl, packageName, version)
        val id = NugetUtils.buildRegistrationLeafUrl(v3RegistrationUrl, packageName, version)
        val rewriteCatalogEntry =
            registrationCatalogEntryRewriter(originalPageItem.catalogEntry, packageContentUrl, v3RegistrationUrl)
        return RegistrationPageItem(
            id = id,
            catalogEntry = rewriteCatalogEntry,
            packageContent = packageContentUrl
        )
    }

    /**
     * 这里如果有dependency依赖，需要重写dependency中的registration
     */
    private fun registrationCatalogEntryRewriter(
        originalCatalogEntry: RegistrationCatalogEntry,
        rewrittenPackageContentUrl: URI,
        v3RegistrationUrl: String
    ): RegistrationCatalogEntry {
        return with(originalCatalogEntry) {
            RegistrationCatalogEntry(
                id = URI.create(v3RegistrationUrl),
                authors = authors,
                dependencyGroups = dependencyGroups,
                deprecation = null,
                description = description,
                iconUrl = iconUrl,
                packageId = packageId,
                licenseUrl = licenseUrl,
                licenseExpression = licenseExpression,
                listed = listed,
                minClientVersion = minClientVersion,
                projectUrl = projectUrl,
                published = published,
                requireLicenseAcceptance = requireLicenseAcceptance,
                summary = summary,
                tags = tags,
                title = title,
                version = version
            )
        }
    }

    fun rewriteRegistrationPageUrls(
        originalRegistrationPage: RegistrationPage,
        artifactInfo: NugetRegistrationArtifactInfo,
        v2BaseUrl: String,
        v3BaseUrl: String,
        registrationPath: String
    ): RegistrationPage {
        val v3RegistrationUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val rewrittenPageUrl = NugetUtils.buildRegistrationPageUrl(
            v3RegistrationUrl, artifactInfo.packageName, originalRegistrationPage.lower, originalRegistrationPage.upper
        )
        val rewrittenParentUrl = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, artifactInfo.packageName)
        val itemList = originalRegistrationPage.items.map { item ->
            registrationResultPageItemRewriter(item, artifactInfo.packageName, v2BaseUrl, v3RegistrationUrl)
        }
        return RegistrationPage(
            id = rewrittenPageUrl,
            count = originalRegistrationPage.count,
            items = itemList,
            lower = originalRegistrationPage.lower,
            upper = originalRegistrationPage.upper,
            parent = rewrittenParentUrl
        )
    }

    fun rewriteRegistrationLeafUrls(
        originalRegistrationLeaf: RegistrationLeaf,
        artifactInfo: NugetRegistrationArtifactInfo,
        v2BaseUrl: String,
        v3BaseUrl: String,
        registrationPath: String
    ): RegistrationLeaf {
        val v3RegistrationUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val rewrittenIndexUrl = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, artifactInfo.packageName)
        val rewrittenLeafUrl = NugetUtils.buildRegistrationLeafUrl(
            v3RegistrationUrl, artifactInfo.packageName, artifactInfo.version
        )
        val rewritePackageContentUrl =
            NugetUtils.buildPackageContentUrl(v3RegistrationUrl, artifactInfo.packageName, artifactInfo.version)
        return RegistrationLeaf(
            id = rewrittenLeafUrl,
            // 这个接口展示没提供，
            catalogEntry = originalRegistrationLeaf.catalogEntry,
            listed = originalRegistrationLeaf.listed,
            packageContent = rewritePackageContentUrl,
            published = originalRegistrationLeaf.published,
            registration = rewrittenIndexUrl
        )
    }
}
