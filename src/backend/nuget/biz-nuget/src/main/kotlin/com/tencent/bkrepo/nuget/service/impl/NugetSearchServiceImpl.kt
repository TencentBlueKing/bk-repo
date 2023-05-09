package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.common.api.util.Preconditions
import com.tencent.bkrepo.nuget.artifact.NugetArtifactInfo
import com.tencent.bkrepo.nuget.pojo.request.NugetSearchRequest
import com.tencent.bkrepo.nuget.pojo.response.search.NugetSearchResponse
import com.tencent.bkrepo.nuget.pojo.response.search.SearchResponseData
import com.tencent.bkrepo.nuget.service.NugetSearchService
import com.tencent.bkrepo.nuget.util.NugetUtils
import com.tencent.bkrepo.nuget.util.NugetV3RegistrationUtils
import com.tencent.bkrepo.nuget.util.NugetVersionUtils
import com.tencent.bkrepo.repository.api.PackageClient
import com.tencent.bkrepo.repository.pojo.packages.PackageListOption
import com.tencent.bkrepo.repository.pojo.packages.PackageSummary
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.streams.toList

@Service
class NugetSearchServiceImpl(
    private val packageClient: PackageClient
) : NugetSearchService {
    override fun search(artifactInfo: NugetArtifactInfo, searchRequest: NugetSearchRequest): NugetSearchResponse {
        logger.info("handling search request in repo [${artifactInfo.getRepoIdentify()}], parameter: $searchRequest")
        with(searchRequest) {
            // limits the skip parameter to 3,000 and the take parameter to 1,000
            Preconditions.checkArgument(skip in 0..3000, "skip")
            Preconditions.checkArgument(take in 0..1000, "take")

            val v3RegistrationUrl = NugetUtils.getV3Url(artifactInfo) + "/registration-semver2"
            val searchResponseDataList = mutableListOf<SearchResponseData>()
            val packageListOption = PackageListOption(pageSize = 1000, packageName = q)

            while (true) {
                val page = packageClient.listPackagePage(
                    artifactInfo.projectId, artifactInfo.repoName, packageListOption
                ).data?.records.takeUnless { it.isNullOrEmpty() } ?: break
                packageListOption.pageNumber++
                val pageResult = page.map {
                    buildSearchResponseData(it, this, v3RegistrationUrl)
                }.filter {
                    packageType.isNullOrBlank() ||
                            it.packageTypes.map { type -> type.name.toLowerCase() }.contains(packageType.toLowerCase())
                }
                searchResponseDataList.addAll(pageResult)
            }
            return NugetSearchResponse(searchResponseDataList.size, searchResponseDataList.drop(skip).take(take))
        }
    }

    private fun buildSearchResponseData(
        packageSummary: PackageSummary,
        searchRequest: NugetSearchRequest,
        v3RegistrationUrl: String
    ): SearchResponseData {
        with(packageSummary) {
            val packageVersionList = packageClient.listAllVersion(projectId, repoName, key).data!!
            // preRelease需要处理
            val sortedPackageVersionList =
                packageVersionList.stream()
                    .sorted { o1, o2 -> NugetVersionUtils.compareSemVer(o1.name, o2.name) }.toList()
            return NugetV3RegistrationUtils.versionListToSearchResponse(
                sortedPackageVersionList, this, searchRequest, v3RegistrationUrl
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NugetSearchServiceImpl::class.java)
    }
}
