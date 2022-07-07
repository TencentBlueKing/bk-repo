package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.common.api.collection.concurrent.ConcurrentHashSet
import com.tencent.bkrepo.job.batch.NodeCopyJob

class NodeCopyJobContext(
    val alreadyCopySet: ConcurrentHashSet<NodeCopyJob.TargetCopy> = ConcurrentHashSet()
) : FileJobContext()
