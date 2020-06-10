package com.tencent.bkrepo.docker.constant

const val REPO_TYPE = "DOCKER"

const val BLOB_PATTERN = "/blobs"

const val MANIFEST_PATTERN = "/manifests"

const val EMPTYSTR = ""

const val USER_API_PREFIX = "/api"
const val DOCKER_API_PREFIX = "/v2"
const val DOCKER_API_SUFFIX = "/auth"

const val DOCKER_BLOB_SUFFIX = "{projectId}/{repoName}/**/blobs/uploads"
const val DOCKER_BLOB_UUID_SUFFIX = "{projectId}/{repoName}/**/blobs/uploads/{uuid}"
const val DOCKER_BLOB_DIGEST_SUFFIX = "{projectId}/{repoName}/**/blobs/{digest}"

const val DOCKER_MANIFEST_TAG_SUFFIX = "/{projectId}/{repoName}/**/manifests/{tag}"
const val DOCKER_MANIFEST_REFERENCE_SUFFIX = "/{projectId}/{repoName}/**/manifests/{reference}"

const val DOCKER_TAGS_SUFFIX = "/{projectId}/{repoName}/{name}/tags/list"
const val DOCKER_CATALOG_SUFFIX = "_catalog"

const val AUTH_ENABLE = "enable"

const val AUTH_DISABLE = "disable"
