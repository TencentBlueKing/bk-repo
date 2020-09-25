package com.tencent.bkrepo.common.artifact.util.version

import kotlin.math.pow

data class SemVer(
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val preRelease: String? = null,
    val buildMetadata: String? = null
) {

    init {
        require(major >= 0) { "Major version must be a positive number" }
        require(minor >= 0) { "Minor version must be a positive number" }
        require(patch >= 0) { "Patch version must be a positive number" }
        preRelease?.let { require(it.matches(Regex("""[\dA-z\-]+(?:\.[\dA-z\-]+)*"""))) { "Pre-release version is not valid" } }
        buildMetadata?.let { require(it.matches(Regex("""[\dA-z\-]+(?:\.[\dA-z\-]+)*"""))) { "Pre-release version is not valid" } }
    }


    /**
     * Build the version name string.
     * @return version name string in Semantic Versioning 2.0.0 specification.
     */
    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (preRelease != null) {
            append('-')
            append(preRelease)
        }
        if (buildMetadata != null) {
            append('+')
            append(buildMetadata)
        }
    }

    /**
     * Calculate the version ordinal.
     */
    fun ordinal(maxDigitsPerComponent: Int = 4): Long {
        require(maxDigitsPerComponent > 0)
        val max = 10.0.pow(maxDigitsPerComponent) - 1
        require(patch < 10.0.pow(maxDigitsPerComponent))
        require(minor < 10.0.pow(maxDigitsPerComponent))
        var ordinal = 0.0
        ordinal += if (major > max) max else major * 10.0.pow(maxDigitsPerComponent * 3)
        ordinal += if (minor > max) max else major * 10.0.pow(maxDigitsPerComponent * 3)
        ordinal += if (patch > max) max else patch * 10.0.pow(maxDigitsPerComponent * 2)
        ordinal += if (preRelease == null) 10.0.pow(maxDigitsPerComponent * 1) - 1 else 0.0
        return ordinal.toLong()

    }

    companion object {

        private val pattern = Regex("""(0|[1-9]\d*)?(?:\.)?(0|[1-9]\d*)?(?:\.)?(0|[1-9]\d*)?(?:-([\dA-z\-]+(?:\.[\dA-z\-]+)*))?(?:\+([\dA-z\-]+(?:\.[\dA-z\-]+)*))?""")

        /**
         * Parse the version string to [SemVer] data object.
         * @param version version string.
         * @throws IllegalArgumentException if the version is not valid.
         */
        fun parse(version: String): SemVer {
            val result = pattern.matchEntire(version) ?: throw IllegalArgumentException("Invalid version string [$version]")
            return SemVer(
                major = if (result.groupValues[1].isEmpty()) 0 else result.groupValues[1].toInt(),
                minor = if (result.groupValues[2].isEmpty()) 0 else result.groupValues[2].toInt(),
                patch = if (result.groupValues[3].isEmpty()) 0 else result.groupValues[3].toInt(),
                preRelease = if (result.groupValues[4].isEmpty()) null else result.groupValues[4],
                buildMetadata = if (result.groupValues[5].isEmpty()) null else result.groupValues[5]
            )
        }
    }
}