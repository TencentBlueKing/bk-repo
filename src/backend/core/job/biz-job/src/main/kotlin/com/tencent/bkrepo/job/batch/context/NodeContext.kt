package com.tencent.bkrepo.job.batch.context

import com.tencent.bkrepo.common.api.util.HumanReadable
import com.tencent.bkrepo.job.batch.base.JobContext
import java.util.concurrent.atomic.AtomicLong

class NodeContext(
    var count: AtomicLong = AtomicLong(),
    var size: AtomicLong = AtomicLong(),
) : JobContext() {
    override fun toString(): String {
        return super.toString() + ",count[$count],size[${HumanReadable.size(size.get())}]"
    }
}
