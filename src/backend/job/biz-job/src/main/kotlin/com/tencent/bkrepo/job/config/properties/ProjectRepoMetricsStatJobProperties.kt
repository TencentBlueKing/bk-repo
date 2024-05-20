package com.tencent.bkrepo.job.config.properties

open class ProjectRepoMetricsStatJobProperties : MongodbJobProperties() {
    val ignoreArchiveProjects: MutableList<String> = ArrayList()
}
