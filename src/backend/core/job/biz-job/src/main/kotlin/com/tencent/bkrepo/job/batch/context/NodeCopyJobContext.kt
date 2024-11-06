package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.job.batch.task.other.NodeCopyJob

class NodeCopyJobContext(
    val alreadyCopySet: ConcurrentHashSet<NodeCopyJob.TargetCopy> = ConcurrentHashSet()
) : FileJobContext()
