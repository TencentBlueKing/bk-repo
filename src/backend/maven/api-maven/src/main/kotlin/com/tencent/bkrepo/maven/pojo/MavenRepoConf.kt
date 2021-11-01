package com.tencent.bkrepo.maven.pojo

data class MavenRepoConf(
    val checksumPolicy: Int?,
    val mavenSnapshotVersionBehavior: Int?,
    /**
     * The maximum number of unique snapshots of a single artifact to store.
     Once the number of snapshots exceeds this setting, older versions are removed.
     A value of 0 (default) indicates there is no limit, and unique snapshots are not cleaned up.
     */
    val maxUniqueSnapshots: Int?
)
