package com.tencent.bkrepo.nuget.util

import com.github.zafarkhaja.semver.Version
import com.tencent.bkrepo.nuget.constant.*
import com.tencent.bkrepo.nuget.pojo.nuspec.NuspecMetadata
import com.tencent.bkrepo.nuget.pojo.request.NugetSearchRequest
import com.tencent.bkrepo.nuget.pojo.response.search.SearchResponseData
import com.tencent.bkrepo.nuget.pojo.response.search.SearchResponseDataTypes
import com.tencent.bkrepo.nuget.pojo.response.search.SearchResponseDataVersion
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.*
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.stream.IntStream
import kotlin.streams.toList

object NugetV3RegistrationUtils {

    private val logger: Logger = LoggerFactory.getLogger(NugetV3RegistrationUtils::class.java)

    fun metadataToRegistrationLeaf(
        packageId: String,
        version: String,
        listed: Boolean,
        v3RegistrationUrl: String
    ): RegistrationLeaf {
        return RegistrationLeaf(
            id = NugetUtils.buildRegistrationLeafUrl(v3RegistrationUrl, packageId, version),
            listed = listed,
            packageContent = NugetUtils.buildPackageContentUrl(v3RegistrationUrl, packageId, version),
            registration = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, packageId)
        )
    }

    fun metadataToRegistrationPage(
        sortedPackageVersionList: List<PackageVersion>,
        packageId: String,
        lowerVersion: String,
        upperVersion: String,
        v3RegistrationUrl: String
    ): RegistrationPage {
        val registrationPageItemList = sortedPackageVersionList.stream().filter {
            betweenVersions(lowerVersion, upperVersion, it.name)
        }.map {
            metadataToRegistrationPageItem(it, v3RegistrationUrl)
        }.toList()
        // 涉及到分页的问题需要处理
        return registrationPageItemToRegistrationPage(
            registrationPageItemList, packageId, lowerVersion, upperVersion, v3RegistrationUrl
        )
    }

    private fun betweenVersions(lowerVersion: String, upperVersion: String, version: String): Boolean {
        return NugetVersionUtils.compareSemVer(lowerVersion, version) <= 0 &&
            NugetVersionUtils.compareSemVer(upperVersion, version) >= 0
    }

    fun registrationPageItemToRegistrationPage(
        registrationPageItemList: List<RegistrationPageItem>,
        packageId: String,
        lowerVersion: String,
        upperVersion: String,
        v3RegistrationUrl: String
    ): RegistrationPage {
        if (registrationPageItemList.isEmpty()) {
            throw IllegalArgumentException("Cannot build registration with no package version")
        } else {
            val pageURI =
                NugetUtils.buildRegistrationPageUrl(v3RegistrationUrl, packageId, lowerVersion, upperVersion)
            val count = registrationPageItemList.size
            val registrationUrl: URI = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, packageId)
            return RegistrationPage(
                id = pageURI,
                count = count,
                items = registrationPageItemList,
                lower = lowerVersion,
                parent = registrationUrl,
                upper = upperVersion
            )
        }
    }

    fun metadataToRegistrationIndex(
        sortedPackageVersionList: List<PackageVersion>,
        v3RegistrationUrl: String
    ): RegistrationIndex {
        val registrationLeafList = sortedPackageVersionList.stream().map {
            metadataToRegistrationPageItem(it, v3RegistrationUrl)
        }.toList()
        // 涉及到分页的问题需要处理
        return registrationPageItemToRegistrationIndex(registrationLeafList, v3RegistrationUrl)
    }

    fun metadataToRegistrationPageItem(
        packageVersion: PackageVersion,
        v3RegistrationUrl: String
    ): RegistrationPageItem {
//        val writeValueAsString = JsonUtils.objectMapper.writeValueAsString(metadataMap)
//        val nuspecMetadata = JsonUtils.objectMapper.readValue(writeValueAsString, NuspecMetadata::class.java)
        val nuspecMetadata = NugetUtils.resolveVersionMetadata(packageVersion)
        val registrationPageItemId =
            NugetUtils.buildRegistrationLeafUrl(v3RegistrationUrl, nuspecMetadata.id, nuspecMetadata.version)
        val packageContent =
            NugetUtils.buildPackageContentUrl(v3RegistrationUrl, nuspecMetadata.id, nuspecMetadata.version)
        val dependencyGroups = metadataToDependencyGroups(nuspecMetadata.dependencies, v3RegistrationUrl)
        val catalogEntry = metadataToRegistrationCatalogEntry(nuspecMetadata, v3RegistrationUrl, dependencyGroups)
        return RegistrationPageItem(
            id = registrationPageItemId,
            catalogEntry = catalogEntry,
            packageContent = packageContent
        )
    }

    private fun metadataToDependencyGroups(
        dependencies: List<Any>?,
        v3RegistrationUrl: String
    ): List<DependencyGroups> {
        // nuspec文件的dependencies可能是"简单依赖列表"或"依赖项组"，都需要转换到DependencyGroups
        val dependencyMaps = dependencies?.map { it as Map<*, *> }
        val first = dependencyMaps?.firstOrNull() ?: return emptyList()
        return if (first.containsKey(ID) && first.containsKey(VERSION)) {
            // 简单依赖列表的targetFramework为空
            listOf(resolveSingleFlatList(dependencyMaps, v3RegistrationUrl))
        } else {
            resolveDependencyGroups(dependencyMaps, v3RegistrationUrl)
        }
    }

    /**
     * reference: https://learn.microsoft.com/en-us/nuget/reference/nuspec#dependencies-element
     *
     * 简单依赖列表: 包含1个或多个Map，每个Map一定会包含ID和Version的键，对应一个依赖项
     */
    private fun resolveSingleFlatList(
        source: List<Map<*, *>>,
        v3RegistrationUrl: String,
        targetFramework: String? = null
    ): DependencyGroups {
        val singleFlatList = mutableListOf<Dependency>()
        source.forEach {
            singleFlatList.add(Dependency(packageId = it[ID].toString()))
        }
        return DependencyGroups(dependencies = singleFlatList, targetFramework = targetFramework)
    }

    /**
     * reference: https://learn.microsoft.com/en-us/nuget/reference/nuspec#dependencies-element
     *
     * 依赖项组: 包含1个或多个Map，每个Map可能有如下情况
     * 1.dependency和targetFramework都不为空，表示targetFramework下有dependency这些依赖
     * 2.dependency为空，targetFramework不为空，表示targetFramework下没有依赖
     * 3.dependency不为空，targetFramework为空，这样的Map最多只有1个，表示匹配不到框架时的默认或回落依赖
     * 4.dependency和targetFramework都为空，表示默认或回落情况下没有依赖
     */
    private fun resolveDependencyGroups(source: List<Map<*, *>>, v3RegistrationUrl: String): List<DependencyGroups> {
        val dependencyGroupList = mutableListOf<DependencyGroups>()
        source.forEach {
            val dependencyObject = it[DEPENDENCY]
            val targetFramework = it[TARGET_FRAMEWORKS]?.toString()
            // 解析单个依赖项组
            val dependencyGroup = when (dependencyObject) {
                // 当依赖项组中的依赖项只有1个时，dependency是包含键为ID和Version的Map
                is Map<*, *> -> {
                    val singleFlatList = listOf(Dependency(packageId = dependencyObject[ID].toString()))
                    DependencyGroups(dependencies = singleFlatList, targetFramework = targetFramework)
                }
                // 当依赖项组中的依赖项有多个时，dependency是一个列表，包含多个Map，每个Map对应一个依赖项，包含ID和Version的键
                is List<*> -> {
                    val dependencyMaps = dependencyObject.map { dependency -> dependency as Map<*, *> }
                    resolveSingleFlatList(dependencyMaps, v3RegistrationUrl, targetFramework)
                }
                else -> {
                    DependencyGroups(dependencies = null, targetFramework = targetFramework)
                }
            }
            dependencyGroupList.add(dependencyGroup)
        }
        return dependencyGroupList
    }

    private fun metadataToRegistrationCatalogEntry(
        nupkgMetadata: NuspecMetadata,
        v3RegistrationUrl: String,
        dependencyGroups: List<DependencyGroups>
    ): RegistrationCatalogEntry {
        with(nupkgMetadata) {
            return RegistrationCatalogEntry(
                id = URI.create(v3RegistrationUrl),
                authors = authors,
                dependencyGroups = dependencyGroups,
                deprecation = null,
                description = description,
                iconUrl = iconUrl?.let { URI.create(it) },
                packageId = id,
                licenseUrl = licenseUrl?.let { URI.create(it) },
                licenseExpression = null,
                listed = false,
                minClientVersion = minClientVersion,
                projectUrl = projectUrl?.let { URI.create(it) },
                published = null,
                requireLicenseAcceptance = requireLicenseAcceptance,
                summary = summary,
                tags = emptyList(),
                title = title,
                version = version
            )
        }
    }

    fun registrationPageItemToRegistrationIndex(
        registrationLeafList: List<RegistrationPageItem>,
        v3RegistrationUrl: String
    ): RegistrationIndex {
        if (registrationLeafList.isEmpty()) {
            throw IllegalArgumentException("Cannot build registration with no package version")
        } else {
            val versionCount = registrationLeafList.size
            val pagesCount = versionCount / 64 + if (versionCount % 64 != 0) 1 else 0
            val packageId = registrationLeafList[0].catalogEntry.packageId
            val registrationPageList =
                buildRegistrationItems(registrationLeafList, v3RegistrationUrl, versionCount, pagesCount, packageId)
            return RegistrationIndex(
                id = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, packageId),
                count = pagesCount,
                items = registrationPageList
            )
        }
    }

    private fun buildRegistrationItems(
        registrationLeafList: List<RegistrationPageItem>,
        v3RegistrationUrl: String,
        versionCount: Int,
        pagesCount: Int,
        packageId: String
    ): List<RegistrationItem> {
        return IntStream.range(0, pagesCount).mapToObj { i ->
            // 计算每一页中的最小版本与最大版本
            val lowerVersion = registrationLeafList[64 * i].catalogEntry.version
            val isLastPage = i == pagesCount - 1
            val lastPackageIndexInPage = if (isLastPage) versionCount - 1 else 64 * i + 63
            val upperVersion = registrationLeafList[lastPackageIndexInPage].catalogEntry.version
            val packagesInPageCount = computedPageCount(isLastPage, versionCount)
            RegistrationItem(
                id = NugetUtils.buildRegistrationPageUrl(v3RegistrationUrl, packageId, lowerVersion, upperVersion),
                count = packagesInPageCount,
                items = registrationLeafList,
                lower = lowerVersion,
                parent = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, packageId),
                upper = upperVersion
            )
        }.toList()
    }

    private fun computedPageCount(lastPage: Boolean, versionCount: Int): Int {
        if (!lastPage && versionCount < 64) {
            val message = "Number of packages in a page must be 64 unless it's the last page"
            logger.error(message)
            throw IllegalArgumentException(message)
        }
        val versionCountPrePage = 64
        if (versionCount % versionCountPrePage != 0 && lastPage) {
            return versionCount % versionCountPrePage
        }
        return versionCountPrePage
    }

    private fun isPreRelease(version: String): Boolean {
        return try {
            val v = Version.valueOf(version)
            v.preReleaseVersion.isNotEmpty()
        } catch (ex: Exception) {
            logger.trace("could not parse version: [$version] as semver2.")
            true
        }
    }

    fun versionListToSearchResponse(
        sortedPackageVersionList: List<PackageVersion>,
        packageSummary: PackageSummary,
        searchRequest: NugetSearchRequest,
        v3RegistrationUrl: String
    ): SearchResponseData {
        val latestVersionPackage = sortedPackageVersionList.last()
        val searchResponseDataVersionList =
            sortedPackageVersionList.filter {
                searchRequest.prerelease ?: false || !isPreRelease(it.name)
            }.map { buildSearchResponseDataVersion(it, packageSummary.name, v3RegistrationUrl) }
        val nuspecMetadata = NugetUtils.resolveVersionMetadata(latestVersionPackage)
        return buildSearchResponseData(v3RegistrationUrl, searchResponseDataVersionList, nuspecMetadata, packageSummary)
    }

    private fun buildSearchResponseData(
        v3RegistrationUrl: String,
        searchResponseDataVersionList: List<SearchResponseDataVersion>,
        nuspecMetadata: NuspecMetadata,
        packageSummary: PackageSummary
    ): SearchResponseData {
        with(nuspecMetadata) {
            return SearchResponseData(
                registration = NugetUtils.buildRegistrationIndexUrl(v3RegistrationUrl, id),
                id = id,
                version = version,
                description = description,
                versions = searchResponseDataVersionList,
                authors = authors.split(','),
                iconUrl = iconUrl?.let { URI.create(it) },
                licenseUrl = licenseUrl?.let { URI.create(it) },
                owners = owners?.split(','),
                projectUrl = projectUrl?.let { URI.create(it) },
                summary = summary,
                tags = tags?.split(','),
                title = title,
                totalDownloads = packageSummary.downloads.toInt(),
                verified = false,
                packageTypes = packageTypes?.map { SearchResponseDataTypes(it.name) }
                    ?: listOf(SearchResponseDataTypes())
            )
        }
    }

    private fun buildSearchResponseDataVersion(
        packageVersion: PackageVersion,
        packageId: String,
        v3RegistrationUrl: String
    ): SearchResponseDataVersion {
        with(packageVersion) {
            return SearchResponseDataVersion(
                NugetUtils.buildRegistrationLeafUrl(v3RegistrationUrl, packageId, name),
                name,
                downloads.toInt()
            )
        }
    }
}
