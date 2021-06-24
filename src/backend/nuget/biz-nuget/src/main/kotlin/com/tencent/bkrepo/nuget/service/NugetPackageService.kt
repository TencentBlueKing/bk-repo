package com.tencent.bkrepo.nuget.service

import com.tencent.bkrepo.nuget.pojo.request.PackageDeleteRequest
import com.tencent.bkrepo.nuget.pojo.request.PackageVersionDeleteRequest

interface NugetPackageService {
    /**
     * 删除包
     */
    fun deletePackage(deleteRequest: PackageDeleteRequest)

    /**
     * 删除版本
     */
    fun deleteVersion(deleteRequest: PackageVersionDeleteRequest)
}
