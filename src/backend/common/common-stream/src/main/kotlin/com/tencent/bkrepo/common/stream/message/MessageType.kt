package com.tencent.bkrepo.common.stream.message

object MessageType {
    const val PROJECT_CREATED = "project-created"

    const val REPO_CREATED = "repo-created"
    const val REPO_UPDATED = "repo-updated"
    const val REPO_DELETED = "repo-deleted"

    const val NODE_CREATED = "node-created"
    const val NODE_UPDATED = "node-updated"
    const val NODE_DELETED = "node-deleted"
    const val NODE_RENAMED = "node-renamed"
    const val NODE_COPIED = "node-copied"
    const val NODE_MOVED = "node-moved"

    const val METADATA_SAVED = "metadata-saved"
    const val METADATA_DELETED = "metadata-deleted"
}
