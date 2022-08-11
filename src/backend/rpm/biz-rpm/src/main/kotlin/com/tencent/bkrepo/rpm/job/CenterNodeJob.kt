package com.tencent.bkrepo.rpm.job

import com.tencent.bkrepo.common.service.cluster.ClusterProperties
import com.tencent.bkrepo.common.service.cluster.RoleType
import org.springframework.beans.factory.annotation.Autowired

abstract class CenterNodeJob {

    @Autowired
    private lateinit var clusterProperties: ClusterProperties

    private fun shouldExecute(): Boolean {
        return clusterProperties.role == RoleType.CENTER
    }

    open fun start() {
        if (!shouldExecute()) {
            return
        }
        run()
    }

    abstract fun run()
}
