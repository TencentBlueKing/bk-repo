package com.tencent.bkrepo.nuget.util

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.constant.StringPool.UTF_8
import com.tencent.bkrepo.common.api.util.JsonUtils
import com.tencent.bkrepo.common.api.util.UrlFormatter
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.nuget.constant.INDEX
import com.tencent.bkrepo.nuget.constant.NugetProperties
import com.tencent.bkrepo.nuget.constant.PACKAGE
import com.tencent.bkrepo.nuget.pojo.nuspec.NuspecMetadata
import com.tencent.bkrepo.nuget.pojo.v3.metadata.feed.Feed
import com.tencent.bkrepo.common.metadata.pojo.packages.PackageVersion
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.StringJoiner

@Suppress("TooManyFunctions")
object NugetUtils {
    private const val NUGET_FULL_PATH = "/%s/%s.%s.nupkg"
    private const val NUGET_MANIFEST_FULL_PATH = ".nuspec/%s/%s.%s.nuspec"
    private const val NUGET_PACKAGE_NAME = "%s.%s.nupkg"
    private const val INDEX_FULL_PATH = "/.index/%s"
    private const val PACKAGE_CONTENT_URI = "/%s/%s/%s.%s.nupkg"
    private const val PACKAGE_MANIFEST_URI = "%s/%s/%s.nuspec"
    private val nugetProperties = SpringContextUtils.getBean(NugetProperties::class.java)
    private val logger = LoggerFactory.getLogger(NugetUtils::class.java)

    fun getNupkgFullPath(id: String, version: String): String {
        return String.format(NUGET_FULL_PATH, id, id, version).toLowerCase()
    }

    fun getNuspecFullPath(id: String, version: String): String {
        return String.format(NUGET_MANIFEST_FULL_PATH, id, id, version).toLowerCase()
    }

    fun getServiceIndexFullPath(remoteUrl: String): String {
        return String.format(INDEX_FULL_PATH, remoteUrl)
    }

    fun getPackageContentUri(id: String, version: String): String {
        return String.format(PACKAGE_CONTENT_URI, id, version, id, version)
    }

    fun getPackageManifestUri(id: String, version: String): String {
        return String.format(PACKAGE_MANIFEST_URI, id, version, id)
    }

    private fun getNupkgFileName(id: String, version: String): String {
        return String.format(NUGET_PACKAGE_NAME, id, version).toLowerCase()
    }

    fun getServiceDocumentResource(): String {
        val inputStream = this.javaClass.classLoader.getResourceAsStream("service_document.xml")
        return inputStream.use { IOUtils.toString(it, UTF_8) }
    }

    fun getFeedResource(): String {
        val inputStream = this.javaClass.classLoader.getResourceAsStream("v3/nugetRootFeedIndex.json")
        return inputStream.use { IOUtils.toString(it, UTF_8) }
    }

    fun getV2Url(artifactInfo: ArtifactInfo): String {
        val domain = UrlFormatter.formatHost(nugetProperties.domain)
        return domain + artifactInfo.getRepoIdentify()
    }

    fun getV3Url(artifactInfo: ArtifactInfo): String {
        val domain = UrlFormatter.formatHost(nugetProperties.domain)
        return domain + artifactInfo.getRepoIdentify() + CharPool.SLASH + "v3"
    }

    fun buildPackageContentUrl(v3RegistrationUrl: String, packageId: String, version: String): URI {
        val packageContentUrl = StringJoiner("/")
            .add(
                UrlFormatter.formatUrl(
                    v3RegistrationUrl.removeSuffix("registration-semver2").plus("flatcontainer")
                )
            )
            .add(packageId.toLowerCase()).add(version).add(getNupkgFileName(packageId, version))
        return URI.create(packageContentUrl.toString())
    }

    fun buildPackageVersionsUrl(packageBaseAddress: String, packageId: String): URI {
        val url = UrlFormatter.format(packageBaseAddress, "$packageId/$INDEX")
        return URI.create(url)
    }

    fun buildRegistrationLeafUrl(v3RegistrationUrl: String, packageId: String, version: String): URI {
        val packageContentUrl = StringJoiner("/").add(UrlFormatter.format(v3RegistrationUrl))
            .add(packageId.toLowerCase()).add("$version.json")
        return URI.create(packageContentUrl.toString())
    }

    fun buildRegistrationPageUrl(v3RegistrationUrl: String, packageId: String, lower: String, upper: String): URI {
        val packageContentUrl = StringJoiner("/").add(UrlFormatter.format(v3RegistrationUrl))
            .add(packageId.toLowerCase()).add("page").add(lower).add("$upper.json")
        return URI.create(packageContentUrl.toString())
    }

    fun buildRegistrationIndexUrl(v3RegistrationUrl: String, packageId: String): URI {
        val packageContentUrl = StringJoiner("/").add(UrlFormatter.format(v3RegistrationUrl))
            .add(packageId.toLowerCase()).add(INDEX)
        return URI.create(packageContentUrl.toString())
    }

    /**
     * 从[versionPackage]中解析[NuspecMetadata]
     */
    fun resolveVersionMetadata(versionPackage: PackageVersion): NuspecMetadata {
        return versionPackage.extension[PACKAGE].toString().readJsonString()
    }

    fun renderServiceIndex(artifactInfo: ArtifactInfo): Feed {
        return try {
            val feedResource = getFeedResource().replace(
                "@NugetV2Url", getV2Url(artifactInfo)
            ).replace(
                "@NugetV3Url", getV3Url(artifactInfo)
            )
            JsonUtils.objectMapper.readValue(feedResource, Feed::class.java)
        } catch (exception: IOException) {
            logger.error("unable to read resource: $exception")
            throw exception
        }
    }
}
