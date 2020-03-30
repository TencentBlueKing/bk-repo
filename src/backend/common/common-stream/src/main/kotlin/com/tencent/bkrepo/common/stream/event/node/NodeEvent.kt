package com.tencent.bkrepo.common.stream.event.node

import com.tencent.bkrepo.common.stream.event.IEvent

abstract class NodeEvent(
    open val projectId: String,
    open val repoName: String,
    open val fullPath: String
) : IEvent
