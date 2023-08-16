package com.tencent.bkrepo.nuget.common

import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.nuget.constant.PACKAGE_BASE_ADDRESS
import com.tencent.bkrepo.nuget.constant.PACKAGE_DISPLAY_METADATA_URI_TEMPLATE
import com.tencent.bkrepo.nuget.constant.PACKAGE_PUBLISH
import com.tencent.bkrepo.nuget.constant.PACKAGE_VERSION_DISPLAY_METADATA_URI_TEMPLATE
import com.tencent.bkrepo.nuget.constant.REGISTRATIONS_BASE_GZ_SEMVER2_URL
import com.tencent.bkrepo.nuget.constant.REGISTRATIONS_BASE_GZ_URL
import com.tencent.bkrepo.nuget.constant.REGISTRATIONS_BASE_URL
import com.tencent.bkrepo.nuget.constant.REGISTRATIONS_BASE_URL_BETA
import com.tencent.bkrepo.nuget.constant.REGISTRATIONS_BASE_URL_RC
import com.tencent.bkrepo.nuget.constant.REGISTRATIONS_BASE_URL_VERSIONED
import com.tencent.bkrepo.nuget.constant.REMOTE_URL
import com.tencent.bkrepo.nuget.constant.SEARCH_QUERY_SERVICE
import com.tencent.bkrepo.nuget.constant.SEARCH_QUERY_SERVICE_BETA
import com.tencent.bkrepo.nuget.constant.SEARCH_QUERY_SERVICE_RC
import com.tencent.bkrepo.nuget.exception.NugetFeedNotFoundException
import com.tencent.bkrepo.nuget.pojo.artifact.NugetRegistrationArtifactInfo
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.nuget.pojo.v3.metadata.index.RegistrationIndex
import com.tencent.bkrepo.nuget.pojo.v3.metadata.leaf.RegistrationLeaf
import com.tencent.bkrepo.nuget.pojo.v3.metadata.page.RegistrationPage
import com.tencent.bkrepo.nuget.util.NugetUtils
import org.springframework.stereotype.Component
import java.io.InputStream

@Component
class NugetRemoteAndVirtualCommon {

    final val urlConvertersMap = mutableMapOf<String, UrlConvert>()

    fun downloadRemoteFeed(): Feed {
        val context = ArtifactQueryContext()
        val configuration = context.getRemoteConfiguration()
        val requestUrl = UrlFormatter.format(configuration.url, "/v3/index.json")
        context.putAttribute(REMOTE_URL, requestUrl)
        val repository = ArtifactContextHolder.getRepository()
        return repository.query(context)?.let { JsonUtils.objectMapper.readValue(it as InputStream, Feed::class.java) }
            ?: throw NugetFeedNotFoundException(
                "query remote feed index.json for [${context.getRemoteConfiguration().url}] failed!"
            )
    }

    fun downloadRemoteRegistrationIndex(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): RegistrationIndex? {
        val registrationBaseUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val originalRegistrationBaseUrl =
            convertToRemoteUrl(registrationBaseUrl, v2BaseUrl, v3BaseUrl)
        val originalRegistrationIndexUrl = NugetUtils.buildRegistrationIndexUrl(
            originalRegistrationBaseUrl, artifactInfo.packageName
        ).toString()
        val context = ArtifactQueryContext()
        context.putAttribute(REMOTE_URL, originalRegistrationIndexUrl)
        val repository = ArtifactContextHolder.getRepository()
        return repository.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, RegistrationIndex::class.java)
        }
    }

    fun downloadRemoteRegistrationPage(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): RegistrationPage {
        val registrationBaseUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val originalRegistrationBaseUrl =
            convertToRemoteUrl(registrationBaseUrl, v2BaseUrl, v3BaseUrl)
        val originalRegistrationPageUrl = NugetUtils.buildRegistrationPageUrl(
            originalRegistrationBaseUrl, artifactInfo.packageName, artifactInfo.lowerVersion, artifactInfo.upperVersion
        )
        val context = ArtifactQueryContext()
        context.putAttribute(REMOTE_URL, originalRegistrationPageUrl)
        val repository = ArtifactContextHolder.getRepository()
        return repository.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, RegistrationPage::class.java)
        }
        // 这里不应该抛这个异常
            ?: throw NugetFeedNotFoundException(
                "query remote registrationIndex for [$originalRegistrationPageUrl] failed!"
            )
    }

    fun downloadRemoteRegistrationLeaf(
        artifactInfo: NugetRegistrationArtifactInfo,
        registrationPath: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): RegistrationLeaf {
        val registrationBaseUrl = "$v3BaseUrl/$registrationPath".trimEnd('/')
        val originalRegistrationBaseUrl =
            convertToRemoteUrl(registrationBaseUrl, v2BaseUrl, v3BaseUrl)
        val originalRegistrationLeafUrl = NugetUtils.buildRegistrationLeafUrl(
            originalRegistrationBaseUrl, artifactInfo.packageName, artifactInfo.version
        )
        val context = ArtifactQueryContext()
        context.putAttribute(REMOTE_URL, originalRegistrationLeafUrl)
        val repository = ArtifactContextHolder.getRepository()
        return repository.query(context)?.let {
            JsonUtils.objectMapper.readValue(it as InputStream, RegistrationLeaf::class.java)
        }
        // 这里不应该抛这个异常
            ?: throw NugetFeedNotFoundException(
                "query remote registrationIndex for [$originalRegistrationLeafUrl] failed!"
            )
    }

    private fun convertToRemoteUrl(
        resourceId: String,
        v2BaseUrl: String,
        v3BaseUrl: String
    ): String {
        val feed = downloadRemoteFeed()
        val matchTypes = urlConvertersMap.filterValues {
            it.convert(v2BaseUrl, v3BaseUrl).trimEnd('/') == resourceId
        }.keys.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("Failed to resolve type by url [$resourceId]")
        return feed.resources.firstOrNull { matchTypes.contains(it.type) }?.id
            ?: throw IllegalStateException("Failed to match url for types: [$matchTypes]")
    }

    init {
        urlConvertersMap[PACKAGE_BASE_ADDRESS] = packageBaseAddress
        urlConvertersMap[REGISTRATIONS_BASE_URL] = registrationsBaseUrl
        urlConvertersMap[SEARCH_QUERY_SERVICE] = searchQueryService
//        urlConvertersMap["LegacyGallery"] = legacyGallery
//        urlConvertersMap["LegacyGallery/2.0.0"] = legacyGallery
        urlConvertersMap[PACKAGE_PUBLISH] = packagePublish
        urlConvertersMap[SEARCH_QUERY_SERVICE_RC] = searchQueryService
        urlConvertersMap[REGISTRATIONS_BASE_URL_RC] = registrationsBaseUrl
        urlConvertersMap[PACKAGE_DISPLAY_METADATA_URI_TEMPLATE] = packageDisplayMetadataUriTemplate
        urlConvertersMap[PACKAGE_VERSION_DISPLAY_METADATA_URI_TEMPLATE] = packageVersionDisplayMetadataUriTemplate
        urlConvertersMap[SEARCH_QUERY_SERVICE_BETA] = searchQueryService
        urlConvertersMap[REGISTRATIONS_BASE_URL_BETA] = registrationsBaseUrl
        urlConvertersMap[REGISTRATIONS_BASE_GZ_URL] = registrationsBaseUrl
        urlConvertersMap[REGISTRATIONS_BASE_GZ_SEMVER2_URL] = registrationsBaseSemver2Url
        urlConvertersMap[REGISTRATIONS_BASE_URL_VERSIONED] = registrationsBaseSemver2Url
    }

    companion object {
        private val packageBaseAddress = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return packageBaseAddress(v3BaseUrl)
            }
        }

        private val registrationsBaseUrl = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return registrationsBaseUrlId(v3BaseUrl)
            }
        }

        private val registrationsBaseSemver2Url = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return registrationsBaseUrWithSemVer2lId(v3BaseUrl)
            }
        }

        private val searchQueryService = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return searchQueryServiceId(v3BaseUrl)
            }
        }

        private val legacyGallery = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return UrlFormatter.formatUrl(v2BaseUrl)
            }
        }

        private val packagePublish = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return packagePublish(v2BaseUrl)
            }
        }

        private val packageDisplayMetadataUriTemplate = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return packageDisplayMetadataUriTemplate(v3BaseUrl)
            }
        }

        private val packageVersionDisplayMetadataUriTemplate = object : UrlConvert {
            override fun convert(v2BaseUrl: String, v3BaseUrl: String): String {
                return packageVersionDisplayMetadataUriTemplate(v3BaseUrl)
            }
        }

        private fun packageBaseAddress(v3BaseUrl: String): String {
            return UrlFormatter.format(v3BaseUrl, "/flatcontainer")
        }

        private fun registrationsBaseUrlId(v3BaseUrl: String): String {
            return UrlFormatter.formatUrl(v3BaseUrl) + "/registration/"
        }

        private fun packagePublish(v2BaseUrl: String): String {
            return UrlFormatter.formatUrl(v2BaseUrl) + "/v2/package"
        }

        private fun registrationsBaseUrWithSemVer2lId(v3BaseUrl: String): String {
            return UrlFormatter.formatUrl(v3BaseUrl) + "/registration-semver2/"
        }

        private fun searchQueryServiceId(v3BaseUrl: String): String {
            return UrlFormatter.formatUrl(v3BaseUrl) + "/query"
        }

        private fun packageDisplayMetadataUriTemplate(v3BaseUrl: String): String {
            return UrlFormatter.formatUrl(v3BaseUrl) + "/registration/{id-lower}/index.json"
        }

        private fun packageVersionDisplayMetadataUriTemplate(v3BaseUrl: String): String {
            return UrlFormatter.formatUrl(v3BaseUrl) + "/registration/{id-lower}/{version-lower}.json"
        }
    }
}
