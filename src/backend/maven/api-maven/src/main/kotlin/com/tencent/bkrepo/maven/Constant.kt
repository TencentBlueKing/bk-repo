package com.tencent.bkrepo.maven

const val CHECKSUM_POLICY = "CHECKSUM_POLICY"
const val SNAPSHOT_BEHAVIOR = "SNAPSHOT_BEHAVIOR"
const val MAX_UNIQUE_SNAPSHOTS = "MAX_UNIQUE_SNAPSHOTS"
const val SNAPSHOT_SUFFIX = "-SNAPSHOT"

const val PACKAGE_SUFFIX_REGEX =
    "(.+)\\.(jar|war|tar|ear|ejb|rar|msi|rpm|tar\\.bz2|tar\\.gz|tar\\.xz|tbz|zip|pom)\$"

const val ARTIFACT_FORMAT = "^%s-%s-?(SNAPSHOT|[0-9]{8}\\.[0-9]{6}-[0-9]+)?-?(.+)?.%s\$"

const val TIMESTAMP_FORMAT = "([0-9]{8}\\.[0-9]{6})-([0-9]+)"
