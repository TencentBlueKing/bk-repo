package com.tencent.bkrepo.conan.utils

import com.tencent.bkrepo.conan.constant.DEFAULT_REVISION_V1
import com.tencent.bkrepo.conan.pojo.ConanFileReference
import com.tencent.bkrepo.conan.pojo.PackageReference
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo

object ConanArtifactInfoUtil {
    fun convertToConanFileReference(
        conanArtifactInfo: ConanArtifactInfo,
        revision: String? = null
    ): ConanFileReference {
        with(conanArtifactInfo) {
            return ConanFileReference(
                name = name,
                version = version,
                channel = channel,
                userName = userName,
                revision = revision
            )
        }
    }

    fun convertToPackageReference(conanArtifactInfo: ConanArtifactInfo): PackageReference {
        with(conanArtifactInfo) {
            val conanFileReference = ConanFileReference(
                name = name,
                version = version,
                channel = channel,
                userName = userName,
                revision = if (revision.isNullOrEmpty()) DEFAULT_REVISION_V1 else revision
            )
            return PackageReference(
                conRef = conanFileReference,
                packageId = packageId!!,
                revision = pRevision
            )
        }
    }
}
