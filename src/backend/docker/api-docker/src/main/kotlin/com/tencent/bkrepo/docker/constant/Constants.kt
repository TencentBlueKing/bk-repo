package com.tencent.bkrepo.docker.constant

const val REPO_TYPE = "DOCKER"

const val BLOB_PATTERN = "/blobs"
const val DOCKER_PRE_SUFFIX = "/"

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

const val DOCKER_USER_MANIFEST_SUFFIX = "/manifest/{projectId}/{repoName}/**/{tag}"
const val DOCKER_USER_LAYER_SUFFIX = "/layer/{projectId}/{repoName}/**/{id}"
const val DOCKER_USER_REPO_SUFFIX = "/repo/{projectId}/{repoName}"
const val DOCKER_USER_TAG_SUFFIX = "/repo/tag/{projectId}/{repoName}/**"

const val DOCKER_TAGS_SUFFIX = "/{projectId}/{repoName}/{name}/tags/list"
const val DOCKER_CATALOG_SUFFIX = "_catalog"

const val HTTP_FORWARDED_PROTO = "X-Forwarded-Proto"
const val HTTP_PROTOCOL_HTTP = "http"
const val HTTP_PROTOCOL_HTTPS = "https"

const val AUTH_ENABLE = "enable"
const val AUTH_DISABLE = "disable"

const val REGISTRY_SERVICE = "bkrepo"

const val ERROR_MESSAGE = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":{%s}}]}"
const val ERROR_MESSAGE_EMPTY = "{\"errors\":[{\"code\":\"%s\",\"message\":\"%s\",\"detail\":null}]}"
const val AUTH_CHALLENGE = "Bearer realm=\"%s\",service=\"%s\""
const val AUTH_CHALLENGE_SERVICE_SCOPE = "Bearer realm=\"%s\",service=\"%s\",scope=\"%s\""
const val AUTH_CHALLENGE_SCOPE = ",scope=\"%s:%s:%s\""
const val AUTH_CHALLENGE_TOKEN = "{\"token\": \"%s\", \"access_token\": \"%s\",\"issued_at\": \"%s\"}"
const val DOCKER_UNAUTHED_BODY =
    "{\"errors\":[{\"code\":\"UNAUTHORIZED\",\"message\":\"access to the requested resource is not authorized\",\"detail\":[{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"pull\"},{\"Type\":\"repository\",\"Name\":\"samalba/my-app\",\"Action\":\"push\"}]}]}"

const val DOCKER_HEADER_API_VERSION = "Docker-Distribution-Api-Version"
const val DOCKER_API_VERSION = "registry/2.0"
const val DOCKER_CONTENT_DIGEST = "Docker-Content-Digest"
const val DOCKER_UPLOAD_UUID = "Docker-Upload-Uuid"

const val DOCKER_MANIFEST = "manifest.json"
const val DOCKER_MANIFEST_LIST = "list.manifest.json"
const val DOCKER_SEARCH_INDEX = 0
const val DOCKER_SEARCH_LIMIT = 9999999
const val DOCKER_SEARCH_LIMIT_SMALL = 10

const val DOCKER_PROJECT_ID = "projectId"
const val DOCKER_REPO_NAME = "repoName"
const val DOCKER_NODE_PATH = "path"
const val DOCKER_NODE_NAME = "name"
const val DOCKER_NODE_SIZE = "size"
const val DOCKER_NODE_FULL_PATH = "fullPath"
const val DOCKER_CREATE_BY = "createdBy"
const val DOCKER_DIGEST = "digest"
const val DOCKER_REFERENCE = "reference"
const val DOCKER_UUID = "uuid"
const val DOCKER_TAG = "tag"

const val DOCKER_MANIFEST_DIGEST = "docker.manifest.digest"
const val DOCKER_MANIFEST_NAME = "docker.manifest"
const val DOCKER_NAME_REPO = "docker.repoName"
const val DOCKER_MANIFEST_TYPE = "docker.manifest.type"
