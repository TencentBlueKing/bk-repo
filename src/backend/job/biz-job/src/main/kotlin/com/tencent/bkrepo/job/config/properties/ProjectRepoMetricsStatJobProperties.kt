package com.tencent.bkrepo.job.config.properties

open class ProjectRepoMetricsStatJobProperties : MongodbJobProperties() {
    var ignoreArchiveProjects: MutableList<String> = ArrayList()
}
