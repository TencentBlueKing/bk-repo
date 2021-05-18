package com.tencent.bkrepo.nuget.service.impl

import com.tencent.bkrepo.nuget.service.NugetPackageService
import com.tencent.bkrepo.repository.api.PackageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NugetPackageServiceImpl(
    private val packageClient: PackageClient
) : NugetPackageService {
    companion object {
        private val logger = LoggerFactory.getLogger(NugetPackageServiceImpl::class.java)
    }
}
