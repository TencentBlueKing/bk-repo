package com.tencent.bkrepo.nuget.util

import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.util.readJsonString
import com.tencent.bkrepo.common.artifact.api.ArtifactInfo
import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import com.tencent.bkrepo.common.service.util.SpringContextUtils
import com.tencent.bkrepo.nuget.constant.NugetProperties
import com.tencent.bkrepo.nuget.constant.PACKAGE
import com.tencent.bkrepo.nuget.pojo.nuspec.NuspecMetadata
import com.tencent.bkrepo.repository.pojo.packages.PackageVersion
import org.apache.commons.io.IOUtils
import java.net.URI
import java.util.StringJoiner

object NugetUtils {
    private const val NUGET_FULL_PATH = "/%s/%s.%s.nupkg"
    private const val NUGET_PACKAGE_NAME = "%s.%s.nupkg"
    private const val INDEX_FULL_PATH = "/.index/%s"
    private const val PACKAGE_DOWNLOAD_URI = "/%s/%s/%s.%s.nupkg"
    private val nugetProperties = SpringContextUtils.getBean(NugetProperties::class.java)

    fun getNupkgFullPath(id: String, version: String): String {
        return String.format(NUGET_FULL_PATH, id, id, version).toLowerCase()
    }

    fun getServiceIndexFullPath(remoteUrl: String): String {
        return String.format(INDEX_FULL_PATH, remoteUrl)
    }

    fun getPackageDownloadUri(name: String, version: String): String {
        return String.format(PACKAGE_DOWNLOAD_URI, name, version, name, version)
    }

    private fun getNupkgFileName(id: String, version: String): String {
        return String.format(NUGET_PACKAGE_NAME, id, version).toLowerCase()
    }

    fun getServiceDocumentResource(): String {
        val inputStream = this.javaClass.classLoader.getResourceAsStream("service_document.xml")
        return inputStream.use { IOUtils.toString(it, "UTF-8") }
    }

    fun getFeedResource(): String {
        val inputStream = this.javaClass.classLoader.getResourceAsStream("v3/nugetRootFeedIndex.json")
        return inputStream.use { IOUtils.toString(it, "UTF-8") }
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
            .add(packageId.toLowerCase()).add("index.json")
        return URI.create(packageContentUrl.toString())
    }

    /**
     * 从[versionPackage]中解析[NuspecMetadata]
     */
    fun resolveVersionMetadata(versionPackage: PackageVersion): NuspecMetadata {
        return versionPackage.extension[PACKAGE].toString().readJsonString()
    }
}
