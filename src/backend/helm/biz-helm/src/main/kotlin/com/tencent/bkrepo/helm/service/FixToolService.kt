package com.tencent.bkrepo.helm.service

import com.tencent.bkrepo.helm.pojo.fixtool.PackageManagerResponse

interface FixToolService {

    /**
     * helm的历史数据增加包管理功能
     */
    fun fixPackageVersion(): List<PackageManagerResponse>
}