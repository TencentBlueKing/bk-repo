package com.tencent.bkrepo.npm.service

import com.tencent.bkrepo.npm.artifact.NpmArtifactInfo
import com.tencent.bkrepo.npm.pojo.user.request.PackageDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.request.PackageVersionDeleteRequest
import com.tencent.bkrepo.npm.pojo.user.PackageVersionInfo

interface NpmWebService {

    /**
     * 查询版本信息
     */
    fun detailVersion(artifactInfo: NpmArtifactInfo, name: String, version: String): PackageVersionInfo

    /**
     * 删除包
     */
    fun deletePackage(deleteRequest: PackageDeleteRequest)

    /**
     * 删除包版本
     */
    fun deleteVersion(deleteRequest: PackageVersionDeleteRequest)
}
