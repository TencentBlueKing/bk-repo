package com.tencent.bkrepo.rpm.servcie.impl

import com.tencent.bkrepo.repository.api.PackageClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RpmWebServiceImpl {
    @Autowired
    lateinit var packageClient: PackageClient
}
