package com.tencent.bkrepo.nuget.util

import com.tencent.bkrepo.common.artifact.util.http.UrlFormatter
import org.apache.commons.io.IOUtils
import java.net.URI
import java.util.StringJoiner

object NugetUtils {
    private const val NUGET_FULL_PATH = "/%s/%s.%s.nupkg"
    private const val NUGET_PACKAGE_NAME = "%s.%s.nupkg"

    fun getNupkgFullPath(id: String, version: String): String {
        return String.format(NUGET_FULL_PATH, id, id, version).toLowerCase()
    }

    fun getNupkgFileName(id: String, version: String): String {
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

    fun buildPackageContentUrl(v3RegistrationUrl: String, packageId: String, version: String): URI {
        val packageContentUrl = StringJoiner("/")
            .add(
                UrlFormatter.formatUrl(
                    v3RegistrationUrl.removeSuffix("registration-semver2").plus("flatcontainer")
                )
            )
            .add(packageId).add(version).add(getNupkgFileName(packageId, version))
        return URI.create(packageContentUrl.toString())
    }

    fun buildRegistrationLeafUrl(v3RegistrationUrl: String, packageId: String, version: String): URI {
        val packageContentUrl = StringJoiner("/").add(UrlFormatter.formatUrl(v3RegistrationUrl))
            .add(packageId.toLowerCase()).add("$version.json")
        return URI.create(packageContentUrl.toString())
    }

    fun buildRegistrationPageUrl(v3RegistrationUrl: String, packageId: String, lower: String, upper: String): URI {
        val packageContentUrl = StringJoiner("/").add(UrlFormatter.formatUrl(v3RegistrationUrl))
            .add(packageId.toLowerCase()).add("page").add(lower).add("$upper.json")
        return URI.create(packageContentUrl.toString())
    }

    fun buildRegistrationIndexUrl(v3RegistrationUrl: String, packageId: String): URI {
        val packageContentUrl = StringJoiner("/").add(UrlFormatter.formatUrl(v3RegistrationUrl))
            .add(packageId.toLowerCase()).add("index.json")
        return URI.create(packageContentUrl.toString())
    }
}
