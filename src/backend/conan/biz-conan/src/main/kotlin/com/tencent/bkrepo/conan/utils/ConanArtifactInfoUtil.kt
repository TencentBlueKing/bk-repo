package com.tencent.bkrepo.conan.utils

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.conan.pojo.ConanFileReference
import com.tencent.bkrepo.conan.pojo.PackageReference
import com.tencent.bkrepo.conan.pojo.artifact.ConanArtifactInfo

object ConanArtifactInfoUtil {
    fun convertToConanFileReference(conanArtifactInfo: ConanArtifactInfo, revision: String? = null): ConanFileReference {
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
            var revision: String? = null
            if (packageId!!.contains(StringPool.HASH_TAG)) {
                val list = packageId!!.split(StringPool.HASH_TAG)
                packageId = list.first()
                revision = list.lastOrNull()
            }
            val conanFileReference = ConanFileReference(
                name = name,
                version = version,
                channel = channel,
                userName = userName,
                revision = revision
            )
            return PackageReference(
                conRef = conanFileReference,
                packageId = packageId!!
            )
        }
    }
}