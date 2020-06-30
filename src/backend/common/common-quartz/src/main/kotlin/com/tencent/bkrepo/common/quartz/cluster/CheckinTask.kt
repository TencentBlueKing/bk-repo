package com.tencent.bkrepo.common.quartz.cluster

import com.mongodb.MongoException
import com.tencent.bkrepo.common.quartz.dao.SchedulerDao
import org.slf4j.LoggerFactory

class CheckinTask(private val schedulerDao: SchedulerDao) : Runnable {

    override fun run() {
        log.debug("Node {}:{} checks-in.", schedulerDao.schedulerName, schedulerDao.instanceId)
        try {
            schedulerDao.checkIn()
        } catch (e: MongoException) {
            log.error("Node " + schedulerDao.instanceId + " could not check-in: " + e.message, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CheckinTask::class.java)
    }
}
