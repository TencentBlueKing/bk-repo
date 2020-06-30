package com.tencent.bkrepo.common.quartz

import com.mongodb.MongoException
import com.mongodb.client.MongoCollection
import com.tencent.bkrepo.common.quartz.cluster.CheckinExecutor
import com.tencent.bkrepo.common.quartz.cluster.CheckinTask
import com.tencent.bkrepo.common.quartz.cluster.RecoveryTriggerFactory
import com.tencent.bkrepo.common.quartz.cluster.TriggerRecoverer
import com.tencent.bkrepo.common.quartz.converter.JobConverter
import com.tencent.bkrepo.common.quartz.converter.JobDataConverter
import com.tencent.bkrepo.common.quartz.converter.TriggerConverter
import com.tencent.bkrepo.common.quartz.dao.CalendarDao
import com.tencent.bkrepo.common.quartz.dao.JobDao
import com.tencent.bkrepo.common.quartz.dao.LocksDao
import com.tencent.bkrepo.common.quartz.dao.PausedJobGroupsDao
import com.tencent.bkrepo.common.quartz.dao.PausedTriggerGroupsDao
import com.tencent.bkrepo.common.quartz.dao.SchedulerDao
import com.tencent.bkrepo.common.quartz.dao.TriggerDao
import com.tencent.bkrepo.common.quartz.handler.JobCompleteHandler
import com.tencent.bkrepo.common.quartz.handler.MisfireHandler
import com.tencent.bkrepo.common.quartz.manager.LockManager
import com.tencent.bkrepo.common.quartz.manager.TriggerAndJobManager
import com.tencent.bkrepo.common.quartz.manager.TriggerRunner
import com.tencent.bkrepo.common.quartz.manager.TriggerStateManager
import com.tencent.bkrepo.common.quartz.util.Clock
import com.tencent.bkrepo.common.quartz.util.ExpiryCalculator
import com.tencent.bkrepo.common.quartz.util.Keys
import org.bson.Document
import org.quartz.Calendar
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobPersistenceException
import org.quartz.SchedulerConfigException
import org.quartz.Trigger
import org.quartz.Trigger.CompletedExecutionInstruction
import org.quartz.TriggerKey
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.spi.ClassLoadHelper
import org.quartz.spi.JobStore
import org.quartz.spi.OperableTrigger
import org.quartz.spi.SchedulerSignaler
import org.quartz.spi.TriggerFiredResult

class MongoJobStore : JobStore {

    private val mongoDatabase = QuartzMongoConfiguration.mongoDatabase
    private var schedulerName: String = "quartz-scheduler"
    private var clustered: Boolean = false
    private var jobDataAsBase64: Boolean = false
    private var collectionPrefix: String = "quartz_"
    private var instanceId: String = ""
    var clusterCheckinIntervalMillis: Long = 7500L
    var jobTimeoutMillis: Long = 10 * 60 * 1000L
    var triggerTimeoutMillis: Long = 10 * 60 * 1000L
    var misfireThreshold: Long = 5000L


    private lateinit var jobDao: JobDao
    private lateinit var calendarDao: CalendarDao
    private lateinit var locksDao: LocksDao
    private lateinit var pausedJobGroupsDao: PausedJobGroupsDao
    private lateinit var pausedTriggerGroupsDao: PausedTriggerGroupsDao
    private lateinit var schedulerDao: SchedulerDao
    private lateinit var triggerDao: TriggerDao
    private lateinit var triggerAndJobManager: TriggerAndJobManager
    private lateinit var jobCompleteHandler: JobCompleteHandler
    private lateinit var lockManager: LockManager
    private lateinit var triggerStateManager: TriggerStateManager
    private lateinit var misfireHandler: MisfireHandler
    private lateinit var triggerRecoverer: TriggerRecoverer
    private lateinit var triggerRunner: TriggerRunner
    private lateinit var checkinExecutor: CheckinExecutor

    private lateinit var signaler: SchedulerSignaler

    override fun initialize(loadHelper: ClassLoadHelper, signaler: SchedulerSignaler) {
        this.signaler = signaler
        initDao(loadHelper)
        ensureIndexes()
        if (isClustered) {
            try {
                triggerRecoverer.recover()
            } catch (e: JobPersistenceException) {
                throw SchedulerConfigException("Cannot recover triggers", e)
            }
            checkinExecutor.start()
        }
    }

    override fun releaseAcquiredTrigger(trigger: OperableTrigger) {
        lockManager.unlockAcquiredTrigger(trigger)
    }

    override fun removeCalendar(calName: String): Boolean {
        return calendarDao.remove(calName)
    }

    override fun getEstimatedTimeToReleaseAndAcquireTrigger(): Long {
        // this will vary...
        return 200
    }

    override fun triggersFired(triggers: MutableList<OperableTrigger>): List<TriggerFiredResult> {
        return triggerRunner.triggersFired(triggers)
    }

    override fun checkExists(jobKey: JobKey): Boolean {
        return jobDao.exists(jobKey)
    }

    override fun checkExists(triggerKey: TriggerKey): Boolean {
        return triggerDao.exists(Keys.toFilter(triggerKey))
    }

    override fun storeJobAndTrigger(newJob: JobDetail, newTrigger: OperableTrigger) {
        triggerAndJobManager.storeJobAndTrigger(newJob, newTrigger)
    }

    override fun resumeAll() {
        triggerStateManager.resumeAll()
    }

    override fun removeJobs(jobKeys: List<JobKey>): Boolean {
        return triggerAndJobManager.removeJobs(jobKeys)
    }

    override fun triggeredJobComplete(
        trigger: OperableTrigger,
        jobDetail: JobDetail,
        triggerInstCode: CompletedExecutionInstruction
    ) {
        jobCompleteHandler.jobComplete(trigger, jobDetail, triggerInstCode)
    }

    override fun pauseJobs(groupMatcher: GroupMatcher<JobKey>): Collection<String> {
        return triggerStateManager.pauseJobs(groupMatcher)
    }

    override fun getTriggerKeys(matcher: GroupMatcher<TriggerKey>): Set<TriggerKey> {
        return triggerDao.getTriggerKeys(matcher)
    }

    override fun pauseJob(jobKey: JobKey) {
        triggerStateManager.pauseJob(jobKey)
    }

    override fun pauseTriggers(matcher: GroupMatcher<TriggerKey>): Collection<String> {
        return triggerStateManager.pause(matcher)
    }

    override fun getNumberOfJobs(): Int {
        return jobDao.getCount().toInt()
    }

    override fun resetTriggerFromErrorState(triggerKey: TriggerKey) {
        triggerStateManager.resetTriggerFromErrorState(triggerKey)
    }

    override fun schedulerResumed() {
        // No-op
    }

    override fun resumeTriggers(matcher: GroupMatcher<TriggerKey>): Collection<String> {
        return triggerStateManager.resume(matcher)
    }

    override fun resumeJobs(matcher: GroupMatcher<JobKey>): Collection<String> {
        return triggerStateManager.resumeJobs(matcher)
    }

    override fun schedulerPaused() {
        // No-op
    }

    override fun setInstanceName(schedName: String) {
        schedulerName = schedName
    }

    override fun retrieveCalendar(calName: String?): Calendar? {
        return calendarDao.retrieveCalendar(calName)
    }

    override fun storeJob(newJob: JobDetail, replaceExisting: Boolean) {
        jobDao.storeJobInMongo(newJob, replaceExisting)
    }

    override fun removeTriggers(triggerKeys: List<TriggerKey>): Boolean {
        return triggerAndJobManager.removeTriggers(triggerKeys)
    }

    override fun getNumberOfCalendars(): Int {
        return calendarDao.getCount().toInt()
    }

    override fun getJobKeys(matcher: GroupMatcher<JobKey>): Set<JobKey> {
        return jobDao.getJobKeys(matcher)
    }

    override fun acquireNextTriggers(noLaterThan: Long, maxCount: Int, timeWindow: Long): List<OperableTrigger> {
        return triggerRunner.acquireNext(noLaterThan, maxCount, timeWindow)
    }

    override fun getJobGroupNames(): List<String> {
        return jobDao.getGroupNames()
    }

    override fun pauseTrigger(triggerKey: TriggerKey) {
        triggerStateManager.pause(triggerKey)
    }

    override fun storeJobsAndTriggers(triggersAndJobs: Map<JobDetail, Set<Trigger>>, replace: Boolean) {
        triggersAndJobs.forEach { (newJob, triggers) ->
            jobDao.storeJobInMongo(newJob, replace)
            // Store all triggers of the job.
            for (newTrigger in triggers) {
                // Simply cast to OperableTrigger as in QuartzScheduler.scheduleJobs
                // http://www.programcreek.com/java-api-examples/index.php?api=org.quartz.spi.OperableTrigger
                triggerAndJobManager.storeTrigger(newTrigger as OperableTrigger, replace)
            }
        }
    }

    override fun getNumberOfTriggers(): Int {
        return triggerDao.getCount().toInt()
    }

    override fun supportsPersistence(): Boolean {
        return true
    }

    override fun setThreadPoolSize(poolSize: Int) {
        // No-op
    }

    override fun setInstanceId(schedInstId: String) {
        this.instanceId = schedInstId
    }

    override fun getAcquireRetryDelay(failureCount: Int): Long {
        return 1000L
    }

    override fun removeTrigger(triggerKey: TriggerKey): Boolean {
        return triggerAndJobManager.removeTrigger(triggerKey)
    }

    override fun storeTrigger(newTrigger: OperableTrigger, replaceExisting: Boolean) {
        triggerAndJobManager.storeTrigger(newTrigger, replaceExisting)
    }

    override fun getTriggerGroupNames(): List<String> {
        return triggerDao.getGroupNames()
    }

    override fun retrieveTrigger(triggerKey: TriggerKey): OperableTrigger? {
        return triggerDao.getTrigger(triggerKey)
    }

    override fun replaceTrigger(triggerKey: TriggerKey, newTrigger: OperableTrigger): Boolean {
        return triggerAndJobManager.replaceTrigger(triggerKey, newTrigger)
    }

    override fun retrieveJob(jobKey: JobKey): JobDetail? {
        return jobDao.retrieveJob(jobKey)
    }

    override fun storeCalendar(name: String?, calendar: Calendar?, replaceExisting: Boolean, updateTriggers: Boolean) {
        // TODO implement updating triggers
        if (updateTriggers) {
            throw UnsupportedOperationException("Updating triggers is not supported.")
        }
        calendarDao.store(name, calendar)
    }

    override fun resumeTrigger(triggerKey: TriggerKey) {
        triggerStateManager.resume(triggerKey)
    }

    override fun isClustered(): Boolean {
        return clustered
    }

    override fun removeJob(jobKey: JobKey): Boolean {
        return triggerAndJobManager.removeJob(jobKey)
    }

    override fun shutdown() {
        checkinExecutor.shutdown()
    }

    override fun pauseAll() {
        triggerStateManager.pauseAll()
    }

    override fun getTriggersForJob(jobKey: JobKey): List<OperableTrigger> {
        return triggerAndJobManager.getTriggersForJob(jobKey)
    }

    override fun getPausedTriggerGroups(): Set<String> {
        return triggerStateManager.getPausedTriggerGroups()
    }

    override fun clearAllSchedulingData() {
        jobDao.clear()
        triggerDao.clear()
        calendarDao.clear()
        pausedJobGroupsDao.remove()
        pausedTriggerGroupsDao.remove()
    }

    override fun resumeJob(jobKey: JobKey) {
        triggerStateManager.resume(jobKey)
    }

    override fun getCalendarNames(): List<String> {
        return calendarDao.retrieveCalendarNames()
    }

    override fun schedulerStarted() {
        // No-op
    }

    override fun getTriggerState(triggerKey: TriggerKey): Trigger.TriggerState {
        return triggerStateManager.getState(triggerKey)
    }

    fun setIsClustered(isClustered: Boolean) {
        this.clustered = isClustered
    }

    fun setJobDataAsBase64(jobDataAsBase64: Boolean) {
        this.jobDataAsBase64 = jobDataAsBase64
    }

    fun setCollectionPrefix(prefix: String) {
        collectionPrefix = prefix + "_"
    }

    private fun initDao(loadHelper: ClassLoadHelper) {
        val jobDataConverter = JobDataConverter(jobDataAsBase64)
        val jobConverter = JobConverter(loadHelper, jobDataConverter)
        jobDao = JobDao(getCollection("jobs"), jobConverter)

        val triggerConverter = TriggerConverter(jobDao, jobDataConverter)
        triggerDao = TriggerDao(getCollection("triggers"), triggerConverter)
        calendarDao = CalendarDao(getCollection("calendars"))
        locksDao = LocksDao(getCollection("locks"), Clock.SYSTEM_CLOCK, instanceId)
        pausedJobGroupsDao = PausedJobGroupsDao(getCollection("paused_job_groups"))
        pausedTriggerGroupsDao = PausedTriggerGroupsDao(getCollection("paused_trigger_groups"))
        schedulerDao = SchedulerDao(getCollection("schedulers"), schedulerName, instanceId, clusterCheckinIntervalMillis, Clock.SYSTEM_CLOCK)

        triggerAndJobManager = TriggerAndJobManager(triggerDao, jobDao, triggerConverter)
        jobCompleteHandler = JobCompleteHandler(triggerAndJobManager, signaler, jobDao, locksDao, triggerDao)

        val expiryCalculator = ExpiryCalculator(schedulerDao, Clock.SYSTEM_CLOCK, jobTimeoutMillis, triggerTimeoutMillis)
        lockManager = LockManager(locksDao, expiryCalculator)

        triggerStateManager = TriggerStateManager(triggerDao, jobDao, pausedJobGroupsDao, pausedTriggerGroupsDao)
        misfireHandler = MisfireHandler(calendarDao, signaler, misfireThreshold)

        val recoveryTriggerFactory = RecoveryTriggerFactory(instanceId)
        triggerRecoverer = TriggerRecoverer(locksDao, triggerAndJobManager, lockManager, triggerDao, jobDao, recoveryTriggerFactory, misfireHandler)
        triggerRunner = TriggerRunner(triggerAndJobManager, triggerDao, jobDao, locksDao, calendarDao,
            misfireHandler, triggerConverter, lockManager, triggerRecoverer)

        val checkinTask = CheckinTask(schedulerDao)
        checkinExecutor = CheckinExecutor(checkinTask, clusterCheckinIntervalMillis, instanceId)
    }

    @Throws(SchedulerConfigException::class)
    private fun ensureIndexes() {
        try {
            /*
             * Indexes are to be declared as group then name.  This is important as the quartz API allows
             * for the searching of jobs and triggers using a group matcher.  To be able to use the compound
             * index using group alone (as the API allows), group must be the first key in that index.
             *
             * To be consistent, all such indexes are ensured in the order group then name.  The previous
             * indexes are removed after we have "ensured" the new ones.
             */
            jobDao.createIndex()
            triggerDao.createIndex()
            locksDao.createIndex(isClustered)
            calendarDao.createIndex()
            schedulerDao.createIndex()
        } catch (e: MongoException) {
            throw SchedulerConfigException("Error while initializing the indexes", e)
        }
    }

    private fun getCollection(name: String): MongoCollection<Document> {
        return mongoDatabase.getCollection(collectionPrefix + name)
    }
}