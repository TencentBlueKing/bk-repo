package com.tencent.bkrepo.npm.constants

const val BKREPO_PREFIX = "X-BKREPO-"

const val APPLICATION_OCTET_STEAM = "mime_type"

/**
 * 文件分隔符
 */
const val FILE_SEPARATOR = "/"
const val FILE_DASH = "-"
const val FILE_SUFFIX = ".tgz"

const val ATTRIBUTE_OCTET_STREAM_SHA1 = "artifact.sha1.octet-stream"

const val NPM_METADATA = "npm_metadata"

// fileName
const val NPM_PACKAGE_TGZ_FILE = "npm_package_tgz_file"
const val NPM_PACKAGE_VERSION_JSON_FILE = "npm_package_version_json_file"
const val NPM_PACKAGE_JSON_FILE = "npm_package_json_file"

// full path
const val NPM_PKG_TGZ_FILE_FULL_PATH = NPM_PACKAGE_TGZ_FILE + "_full_path"
const val NPM_PKG_VERSION_JSON_FILE_FULL_PATH = NPM_PACKAGE_VERSION_JSON_FILE + "_full_path"
const val NPM_PKG_JSON_FILE_FULL_PATH = NPM_PACKAGE_JSON_FILE + "_full_path"
// full path value
const val NPM_PKG_TGZ_FULL_PATH = "/%s/-/%s-%s.tgz"
const val NPM_PKG_VERSION_FULL_PATH = "/.npm/%s/%s-%s.json"
const val NPM_PKG_FULL_PATH = "/.npm/%s/package.json"

const val NPM_FILE_FULL_PATH = "npm_file_full_path"

const val SEARCH_REQUEST = "search_request"

// constants map
val ERROR_MAP = mapOf("error" to "not_found", "reason" to "document not found")
val SUCCESS_MAP = mapOf(Pair("ok", "created new tag"))
val SEARCH_MAP = mapOf(Pair("objects", emptyList<Void>()))
