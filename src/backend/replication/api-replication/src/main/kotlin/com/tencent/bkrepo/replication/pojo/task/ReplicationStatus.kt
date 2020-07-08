package com.tencent.bkrepo.replication.pojo.task

enum class ReplicationStatus {
    WAITING,
    PAUSED,
    REPLICATING,
    SUCCESS,
    INTERRUPTED,
    FAILED;

    companion object {
        val UNDO_STATUS_SET = setOf(WAITING, REPLICATING)
    }
}
