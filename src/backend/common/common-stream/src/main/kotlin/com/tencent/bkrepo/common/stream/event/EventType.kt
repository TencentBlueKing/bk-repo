package com.tencent.bkrepo.common.stream.event

enum class EventType(type: String) {
    PROJECT_CREATED(Constants.PROJECT_CREATED),
    PROJECT_UPDATED(Constants.PROJECT_UPDATED),
    PROJECT_DELETED(Constants.PROJECT_DELETED),

    REPO_CREATED(Constants.REPO_CREATED),
    REPO_UPDATED(Constants.REPO_UPDATED),
    REPO_DELETED(Constants.REPO_DELETED),

    NODE_CREATED(Constants.NODE_CREATED),
    NODE_UPDATED(Constants.NODE_UPDATED),
    NODE_DELETED(Constants.NODE_DELETED),
    NODE_RENAMED(Constants.NODE_RENAMED),
    NODE_COPIED(Constants.NODE_COPIED),
    NODE_MOVED(Constants.NODE_MOVED),

    METADATA_CREATED(Constants.METADATA_CREATED),
    METADATA_UPDATED(Constants.METADATA_UPDATED);

    object Constants {
        const val PROJECT_CREATED = "project-created"
        const val PROJECT_UPDATED = "project-updated"
        const val PROJECT_DELETED = "project-deleted"

        const val REPO_CREATED = "repo-created"
        const val REPO_UPDATED = "repo-updated"
        const val REPO_DELETED = "repo-deleted"

        const val NODE_CREATED = "node-created"
        const val NODE_UPDATED = "node-updated"
        const val NODE_DELETED = "node-deleted"
        const val NODE_RENAMED = "node-renamed"
        const val NODE_COPIED = "node-copied"
        const val NODE_MOVED = "node-moved"

        const val METADATA_CREATED = "metadata-created"
        const val METADATA_UPDATED = "metadata-updated"
    }
}
