package com.tencent.bkrepo.nuget.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationCatalogEntry
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationItem
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationPageItem
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import java.net.URI
import java.net.URLEncoder
import java.util.Objects

object RemoteRegistrationUtils {

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

    @Suppress("ComplexCondition")
    fun combineRegistrationIndex(
        originIndex: RegistrationIndex,
        newIndex: RegistrationIndex,
        artifactInfo: NugetRegistrationArtifactInfo,
        v3RegistrationUrl: String
    ): RegistrationIndex {
        val originLeafList = originIndex.items.mapNotNull { it.items }.flatten().toMutableList()
        val newLeafList = newIndex.items.mapNotNull { it.items }.flatten()
        // 远程Registration Index查询结果为折叠的分页结果时，本地结果也折叠不显示具体版本元数据
        return if (
            originIndex.items.isNotEmpty() && originLeafList.isEmpty() ||
            newIndex.items.isNotEmpty() && newLeafList.isEmpty()
        ) {
            originIndex.items.forEach { it.items = null }
            newIndex.items.forEach { it.items = null }
            RegistrationIndex(
                id = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, artifactInfo.packageName),
                count = originIndex.count + newIndex.count,
                items = originIndex.items + newIndex.items
            )
        // RegistrationIndex不折叠时，聚合并重新分页
        } else {
            // 提取原Registration每个版本的元数据和版本列表
            val originVersions = originLeafList.map { it.catalogEntry.version }
            newLeafList.forEach {
                if (!originVersions.contains(it.catalogEntry.version)) { originLeafList.add(it) }
            }
            val sortedLeafList = originLeafList.sortedWith { o1, o2 ->
                NugetVersionUtils.compareSemVer(o1.catalogEntry.version, o2.catalogEntry.version)
            }
            NugetV3RegistrationUtils.registrationPageItemToRegistrationIndex(sortedLeafList, v3RegistrationUrl)
        }
    }

    fun combineRegistrationPage(
        originPage: RegistrationPage,
        newPage: RegistrationPage,
        artifactInfo: NugetRegistrationArtifactInfo,
        v3RegistrationUrl: String
    ): RegistrationPage {
        val leafList = originPage.items.toMutableList()
        val originVersions = leafList.map { it.catalogEntry.version }
        newPage.items.forEach {
            if (!originVersions.contains(it.catalogEntry.version)) { leafList.add(it) }
        }
        val sortedLeafList = leafList.sortedWith { o1, o2 ->
            NugetVersionUtils.compareSemVer(o1.catalogEntry.version, o2.catalogEntry.version)
        }
        return RegistrationPage(
            id = NugetUtils.buildRegistrationPageUrl(
                v3RegistrationUrl, artifactInfo.packageName, artifactInfo.lowerVersion, artifactInfo.upperVersion
            ),
            count = sortedLeafList.size,
            items = sortedLeafList,
            lower = artifactInfo.lowerVersion,
            parent = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, artifactInfo.packageName),
            upper = artifactInfo.upperVersion
        )
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
        val registrationPageUrl = proxyChannelName?.let {
            buildRegistrationPageProxyUrl(originalItem.id, packageName, v3RegistrationUrl, proxyChannelName)
        } ?: NugetUtils.buildRegistrationPageUrl(v3RegistrationUrl, packageName, originalItem.lower, originalItem.upper)
        val pageItemList = originalItem.items?.map { item ->
            registrationResultPageItemRewriter(item, packageName, v2BaseUrl, v3RegistrationUrl)
        }
        return RegistrationItem(
            id = if (isPaged) registrationPageUrl else registrationIndexUrl,
            sourceType = ArtifactChannel.PROXY,
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
            sourceType = ArtifactChannel.PROXY,
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
