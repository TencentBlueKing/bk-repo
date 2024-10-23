package com.tencent.bkrepo.job.schedule.controller

import com.tencent.devops.schedule.constants.SERVER_BASE_PATH
import com.tencent.devops.schedule.constants.SERVER_RPC_V1
import com.tencent.devops.schedule.manager.WorkerManager
import com.tencent.devops.schedule.web.WorkerController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("$SERVER_BASE_PATH$SERVER_RPC_V1")
class WorkerGroupRpcController(
    workerManager: WorkerManager,
) : WorkerController(workerManager)
