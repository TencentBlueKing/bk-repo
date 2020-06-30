package com.tencent.bkrepo.common.quartz.manager

import com.tencent.bkrepo.common.quartz.STATE_BLOCKED
import com.tencent.bkrepo.common.quartz.STATE_COMPLETE
import com.tencent.bkrepo.common.quartz.STATE_DELETED
import com.tencent.bkrepo.common.quartz.STATE_ERROR
import com.tencent.bkrepo.common.quartz.STATE_PAUSED
import com.tencent.bkrepo.common.quartz.STATE_PAUSED_BLOCKED
import com.tencent.bkrepo.common.quartz.STATE_WAITING
import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.dao.PausedJobGroupsDao
import com.tencent.bkrepo.common.quartz.dao.PausedTriggerGroupsDao
import com.tencent.bkrepo.common.quartz.dao.TriggerDao
import com.tencent.bkrepo.common.quartz.util.GroupHelper
import com.tencent.bkrepo.common.quartz.util.TriggerGroupHelper
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher

class TriggerStateManager(
    private val triggerDao: TriggerDao, 
    private val jobDao: JobDao,
    private val pausedJobGroupsDao: PausedJobGroupsDao,
    private val pausedTriggerGroupsDao: PausedTriggerGroupsDao
) {

    fun getPausedTriggerGroups(): Set<String> {
        return pausedTriggerGroupsDao.getPausedGroups()
    }

    fun getState(triggerKey: TriggerKey): Trigger.TriggerState {
        return getTriggerState(triggerDao.getState(triggerKey))
    }

    fun pause(triggerKey: TriggerKey) {
        triggerDao.setState(triggerKey, STATE_PAUSED)
    }

    fun pause(matcher: GroupMatcher<TriggerKey>): Collection<String> {
        triggerDao.setStateInMatching(matcher, STATE_PAUSED)
        val groupHelper = GroupHelper(triggerDao.triggerCollection)
        val set = groupHelper.groupsThatMatch(matcher)
        pausedTriggerGroupsDao.pauseGroups(set)
        return set
    }

    fun pauseAll() {
        val groupHelper = GroupHelper(triggerDao.triggerCollection)
        triggerDao.setStateInAll(STATE_PAUSED)
        pausedTriggerGroupsDao.pauseGroups(groupHelper.allGroups())
    }

    fun pauseJob(jobKey: JobKey) {
        val jobId = jobDao.getJob(jobKey)?.getObjectId("_id") ?: return
        val groupHelper = TriggerGroupHelper(triggerDao.triggerCollection)
        val groups = groupHelper.groupsForJobId(jobId)
        triggerDao.setStateByJobId(jobId, STATE_PAUSED)
        pausedTriggerGroupsDao.pauseGroups(groups)
    }

    fun pauseJobs(groupMatcher: GroupMatcher<JobKey>): Collection<String> {
        val groupHelper = TriggerGroupHelper(triggerDao.triggerCollection)
        val groups = groupHelper.groupsForJobIds(jobDao.idsOfMatching(groupMatcher))
        triggerDao.setStateInGroups(groups, STATE_PAUSED)
        pausedJobGroupsDao.pauseGroups(groups)
        return groups
    }

    fun resume(triggerKey: TriggerKey) {
        // TODO: port blocking behavior and misfired triggers handling from StdJDBCDelegate in Quartz
        triggerDao.setState(triggerKey, STATE_WAITING)
    }

    fun resume(matcher: GroupMatcher<TriggerKey>): Collection<String> {
        triggerDao.setStateInMatching(matcher, STATE_WAITING)
        val groupHelper = GroupHelper(triggerDao.triggerCollection)
        val set: Set<String> = groupHelper.groupsThatMatch(matcher)
        pausedTriggerGroupsDao.unpauseGroups(set)
        return set
    }

    fun resume(jobKey: JobKey) {
        val jobId = jobDao.getJob(jobKey)?.getObjectId("_id") ?: return
        // TODO: port blocking behavior and misfired triggers handling from StdJDBCDelegate in Quartz
        triggerDao.setStateByJobId(jobId, STATE_WAITING)
    }

    fun resumeAll() {
        val groupHelper = GroupHelper(triggerDao.triggerCollection)
        triggerDao.setStateInAll(STATE_WAITING)
        pausedTriggerGroupsDao.unpauseGroups(groupHelper.allGroups())
    }

    fun resumeJobs(groupMatcher: GroupMatcher<JobKey>): Collection<String> {
        val groupHelper = TriggerGroupHelper(triggerDao.triggerCollection)
        val groups = groupHelper.groupsForJobIds(jobDao.idsOfMatching(groupMatcher))
        triggerDao.setStateInGroups(groups, STATE_WAITING)
        pausedJobGroupsDao.unpauseGroups(groups)
        return groups
    }

    fun resetTriggerFromErrorState(triggerKey: TriggerKey) {
        // Atomic updates cannot be done with the current model - across collections.
        val currentState = triggerDao.getState(triggerKey)
        if (STATE_ERROR != currentState) {
            return
        }
        var newState = STATE_WAITING
        if (pausedTriggerGroupsDao.getPausedGroups().contains(triggerKey.group)) {
            newState = STATE_PAUSED
        }
        triggerDao.transferState(triggerKey, STATE_ERROR, newState)
    }

    private fun getTriggerState(value: String?): Trigger.TriggerState {
        if (value == null) {
            return Trigger.TriggerState.NONE
        }
        if (value == STATE_DELETED) {
            return Trigger.TriggerState.NONE
        }
        if (value == STATE_COMPLETE) {
            return Trigger.TriggerState.COMPLETE
        }
        if (value == STATE_PAUSED) {
            return Trigger.TriggerState.PAUSED
        }
        if (value == STATE_PAUSED_BLOCKED) {
            return Trigger.TriggerState.PAUSED
        }
        if (value == STATE_ERROR) {
            return Trigger.TriggerState.ERROR
        }
        if (value == STATE_BLOCKED) {
            return  Trigger.TriggerState.BLOCKED
        }
        // waiting or acquired
        return Trigger.TriggerState.NORMAL
    }
}
