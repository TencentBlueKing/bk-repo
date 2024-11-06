/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.oci.util

import com.tencent.bkrepo.common.api.constant.StringPool
import com.tencent.bkrepo.oci.constant.OCI_MANIFEST
import com.tencent.bkrepo.oci.pojo.artifact.OciArtifactInfo
import com.tencent.bkrepo.oci.pojo.digest.OciDigest

object OciLocationUtils {

    fun buildManifestPath(packageName: String, tag: String): String {
        return buildManifestVersionFolderPath(packageName, tag) + OCI_MANIFEST
    }

    fun buildManifestVersionFolderPath(packageName: String, tag: String): String {
        return buildManifestFolderPath(packageName) +"$tag/"
    }

    fun buildManifestFolderPath(packageName: String): String {
        return "/$packageName/manifest/"
    }

    fun buildDigestManifestPathWithReference(packageName: String, reference: String): String {
        return buildDigestManifestPath(packageName, OciDigest(reference))
    }

    private fun buildDigestManifestPath(packageName: String, ref: OciDigest): String {
        return buildPath(packageName, ref, "manifest")
    }

    fun buildDigestBlobsPath(packageName: String, ref: OciDigest): String {
        return buildPath(packageName, ref, "blobs")
    }

    fun buildBlobsFolderPath(packageName: String): String {
        return buildPath(packageName, null, "blobs")
    }

    fun buildDigestBlobsUploadPath(packageName: String, ref: OciDigest): String {
        return buildPath(packageName, ref, "_uploads")
    }

    private fun buildPath(packageName: String, ref: OciDigest? = null, type: String): String {
        return "/$packageName/$type/"+ (ref?.fileName() ?: StringPool.EMPTY)
    }

    fun manifestLocation(digest: OciDigest, ociArtifactInfo: OciArtifactInfo): String {
        return returnLocation(digest, ociArtifactInfo, "manifest")
    }

    fun blobPathLocation(digest: OciDigest, ociArtifactInfo: OciArtifactInfo): String {
        return returnPathLocation(digest, ociArtifactInfo, "blobs")
    }

    fun blobLocation(digest: OciDigest, ociArtifactInfo: OciArtifactInfo): String {
        return returnLocation(digest, ociArtifactInfo, "blobs")
    }

    fun manifestPathLocation(reference: String, ociArtifactInfo: OciArtifactInfo): String {
        with(ociArtifactInfo) {
            return "/$packageName/manifests/$reference"
        }
    }

    fun blobVersionPathLocation(reference: String, packageName: String, fileName: String): String {
            return blobVersionFolderLocation(reference, packageName)+ fileName
    }

    fun blobVersionFolderLocation(reference: String, packageName: String): String {
        return "/$packageName/blobs/$reference/"
    }

    private fun returnPathLocation(digest: OciDigest, ociArtifactInfo: OciArtifactInfo, type: String): String {
        with(ociArtifactInfo) {
            return "/$packageName/$type/$digest"
        }
    }

    private fun returnLocation(digest: OciDigest, ociArtifactInfo: OciArtifactInfo, type: String): String {
        with(ociArtifactInfo) {
            return "/$projectId/$repoName/$packageName/$type/$digest"
        }
    }

    fun blobUUIDLocation(uuid: String, ociArtifactInfo: OciArtifactInfo): String {
        with(ociArtifactInfo) {
            return "/$projectId/$repoName/$packageName/blobs/uploads/$uuid"
        }
    }
}
