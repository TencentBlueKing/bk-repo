package com.tencent.bkrepo.helm.model.metadata

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.github.zafarkhaja.semver.Version

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class HelmChartMetadata(
    var apiVersion: String?,
    var appVersion: String?,
    var created: String?,
    var deprecated: Boolean?,
    var description: String?,
    var digest: String?,
    var engine: String?,
    var home: String?,
    var icon: String?,
    var keywords: List<String> = emptyList(),
    var maintainers: List<HelmMaintainerMetadata?> = emptyList(),
    var name: String,
    var sources: List<String> = emptyList(),
    var urls: List<String> = emptyList(),
    var version: String
) : Comparable<HelmChartMetadata> {

    override fun compareTo(other: HelmChartMetadata): Int {
        val result = this.name.compareTo(other.name)
        return if (result != 0) {
            result
        } else {
            try {
                Version.valueOf(other.version).compareWithBuildsTo(Version.valueOf(this.version))
            } catch (ignored: Exception){
                other.version.compareTo(this.version)
            }
        }
    }
}