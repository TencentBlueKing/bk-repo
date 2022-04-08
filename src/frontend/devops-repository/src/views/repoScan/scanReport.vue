<template>
    <div class="scan-report-container">
        <bk-button class="start-scan" theme="default" @click="startScanHandler">扫描</bk-button>
        <div class="report-overview flex-align-center display-block" data-title="报告总览">
            <div class="base-info-item flex-column"
                v-for="item in baseInfoList" :key="item.key">
                <span class="base-info-key">{{ item.label }}</span>
                <span class="base-info-value" :style="{ color: item.color }">{{ segmentNumberThree(baseInfo[item.key] || 0) }}</span>
            </div>
        </div>
        <div class="report-list display-block" data-title="扫描制品列表">
            <!-- <i class="devops-icon icon-filter-shape" @click="filter.show = true"></i> -->
            <bk-button class="report-filter" theme="default" @click="filter.show = true">筛选</bk-button>
            <bk-table
                height="calc(100% - 60px)"
                :data="scanList"
                :outer-border="false"
                :row-border="false"
                row-key="recordId"
                size="small">
                <template #empty>
                    <empty-data :is-loading="isLoading"></empty-data>
                </template>
                <bk-table-column label="制品名称" show-overflow-tooltip>
                    <template #default="{ row }">
                        <span v-if="row.groupId" class="mr5 repo-tag" :data-name="row.groupId"></span>
                        <span>{{ row.name }}</span>
                    </template>
                </bk-table-column>
                <bk-table-column label="制品版本/存储路径" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.version || row.fullPath }}</template>
                </bk-table-column>
                <bk-table-column label="所属仓库" show-overflow-tooltip>
                    <template #default="{ row }">
                        <Icon class="table-svg" size="16" :name="row.repoType.toLowerCase()" />
                        <span class="ml10">{{replaceRepoName(row.repoName)}}</span>
                    </template>
                </bk-table-column>
                <bk-table-column label="风险等级">
                    <template #default="{ row }">
                        <div v-if="row.highestLeakLevel" class="status-sign" :class="row.highestLeakLevel"
                            :data-name="leakLevelEnum[row.highestLeakLevel]">
                        </div>
                    </template>
                </bk-table-column>
                <bk-table-column label="扫描状态">
                    <template #default="{ row }">
                        <span class="repo-tag" :class="row.status">{{scanStatusEnum[row.status]}}</span>
                    </template>
                </bk-table-column>
                <bk-table-column label="扫描完成时间" width="150">
                    <template #default="{ row }">{{formatDate(row.finishTime)}}</template>
                </bk-table-column>
                <bk-table-column :label="$t('operation')" width="70">
                    <template #default="{ row }">
                        <operation-list
                            :list="[
                                row.status === 'RUNNING' && { label: '中止', clickEvent: () => stopScanHandler(row) },
                                row.status === 'SUCCESS' && { label: '详情', clickEvent: () => showArtiReport(row) },
                                (row.status === 'SUCCESS' || row.status === 'STOP' || row.status === 'FAILED') && { label: '扫描', clickEvent: () => startScanSingleHandler(row) }
                            ].filter(Boolean)"></operation-list>
                    </template>
                </bk-table-column>
            </bk-table>
            <bk-pagination
                class="p10"
                size="small"
                align="right"
                show-total-count
                @change="current => handlerPaginationChange({ current })"
                @limit-change="limit => handlerPaginationChange({ limit })"
                :current.sync="pagination.current"
                :limit="pagination.limit"
                :count="pagination.count"
                :limit-list="pagination.limitList">
            </bk-pagination>
        </div>
        <bk-sideslider
            :is-show.sync="filter.show"
            title="筛选"
            @click.native.stop="() => {}"
            :quick-close="true">
            <template #content>
                <bk-form class="p20" form-type="vertical">
                    <bk-form-item label="制品名称">
                        <bk-input v-model="filter.name"></bk-input>
                    </bk-form-item>
                    <bk-form-item label="所属仓库">
                        <bk-select
                            v-model="filter.repoName"
                            searchable>
                            <bk-option-group
                                v-for="(list, type) in repoGroupList"
                                :name="type.toLowerCase()"
                                :key="type"
                                show-collapse>
                                <bk-option v-for="option in list"
                                    :key="option.name"
                                    :id="option.name"
                                    :name="option.name">
                                </bk-option>
                            </bk-option-group>
                        </bk-select>
                    </bk-form-item>
                    <bk-form-item label="风险等级">
                        <bk-select
                            v-model="filter.highestLeakLevel">
                            <bk-option v-for="[id, name] in Object.entries(leakLevelEnum)" :key="id" :id="id" :name="name"></bk-option>
                        </bk-select>
                    </bk-form-item>
                    <bk-form-item label="扫描状态">
                        <bk-select
                            v-model="filter.status">
                            <bk-option v-for="[id, name] in Object.entries(scanStatusEnum)" :key="id" :id="id" :name="name"></bk-option>
                        </bk-select>
                    </bk-form-item>
                    <bk-form-item label="扫描完成时间">
                        <bk-date-picker
                            style="--long-width:360px;"
                            v-model="filter.time"
                            :shortcuts="shortcuts"
                            type="datetimerange"
                            transfer
                            placeholder="请选择日期时间范围">
                        </bk-date-picker>
                    </bk-form-item>
                    <bk-form-item>
                        <bk-button class="mt10" theme="primary" @click="filterHandler()">筛选</bk-button>
                        <bk-button class="ml20 mt10" theme="default" @click="reset()">重置</bk-button>
                    </bk-form-item>
                </bk-form>
            </template>
        </bk-sideslider>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapState, mapActions } from 'vuex'
    import { formatDate, segmentNumberThree } from '@repository/utils'
    import { scanStatusEnum, leakLevelEnum } from '@repository/store/publicEnum'
    const nowTime = new Date()
    export default {
        name: 'scanReport',
        components: { OperationList },
        data () {
            return {
                scanStatusEnum,
                leakLevelEnum,
                baseInfoList: [
                    { key: 'artifactCount', label: '累计扫描制品' },
                    { key: 'total', label: '累计漏洞数' },
                    { key: 'critical', label: '危急漏洞', color: '#EA3736' },
                    { key: 'high', label: '高风险漏洞', color: '#FFB549' },
                    { key: 'medium', label: '中风险漏洞', color: '#3A84FF' },
                    { key: 'low', label: '低风险漏洞', color: '#979BA5' }
                ],
                baseInfo: {},
                isLoading: false,
                scanList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                filter: {
                    show: false,
                    name: '',
                    repoName: '',
                    highestLeakLevel: '',
                    status: '',
                    time: []
                },
                shortcuts: [
                    {
                        text: '近7天',
                        value () {
                            return [new Date(nowTime.getTime() - 3600 * 1000 * 24 * 7), nowTime]
                        }
                    },
                    {
                        text: '近15天',
                        value () {
                            return [new Date(nowTime.getTime() - 3600 * 1000 * 24 * 15), nowTime]
                        }
                    },
                    {
                        text: '近30天',
                        value () {
                            return [new Date(nowTime.getTime() - 3600 * 1000 * 24 * 30), nowTime]
                        }
                    }
                ]
            }
        },
        computed: {
            ...mapState(['repoListAll']),
            projectId () {
                return this.$route.params.projectId
            },
            planId () {
                return this.$route.params.planId
            },
            repoGroupList () {
                return this.repoListAll
                    .filter(r => r.type === this.baseInfo.planType)
                    .reduce((target, repo) => {
                        if (!target[repo.type]) target[repo.type] = []
                        target[repo.type].push(repo)
                        return target
                    }, {})
            }
        },
        created () {
            this.getRepoListAll({ projectId: this.projectId })
            this.scanReportOverview({
                projectId: this.projectId,
                id: this.planId
            }).then(res => {
                this.baseInfo = res
            })
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            segmentNumberThree,
            ...mapActions([
                'getRepoListAll',
                'scanReportOverview',
                'scanReportList',
                'stopScan',
                'startScanSingle'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getReportListHandler()
            },
            getReportListHandler () {
                this.isLoading = true
                let [startTime, endTime] = this.filter.time
                startTime = startTime instanceof Date ? startTime.toISOString() : undefined
                endTime = endTime instanceof Date ? endTime.toISOString() : undefined
                const { name, highestLeakLevel, repoName, status } = this.filter
                return this.scanReportList({
                    id: this.planId,
                    name,
                    highestLeakLevel,
                    projectId: this.projectId,
                    repoName,
                    status,
                    current: this.pagination.current,
                    limit: this.pagination.limit,
                    startTime,
                    endTime
                }).then(({ records, totalRecords }) => {
                    this.scanList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            filterHandler () {
                this.filter.show = false
                this.handlerPaginationChange()
            },
            reset () {
                this.filter = {
                    show: false,
                    name: '',
                    repoName: '',
                    highestLeakLevel: '',
                    status: '',
                    time: []
                }
            },
            stopScanHandler ({ recordId }) {
                this.stopScan({
                    projectId: this.projectId,
                    recordId
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '中止扫描成功'
                    })
                    this.getReportListHandler()
                })
            },
            showArtiReport ({ recordId, name }) {
                this.$router.push({
                    name: 'artiReport',
                    params: {
                        ...this.$route.params,
                        recordId
                    },
                    query: {
                        ...this.$route.query,
                        artiName: name
                    }
                })
            },
            startScanHandler () {
                this.$router.push({
                    name: 'startScan',
                    query: this.$route.query
                })
            },
            startScanSingleHandler ({ repoType, repoName, name, fullPath, packageKey, version }) {
                this.startScanSingle({
                    projectId: this.projectId,
                    id: this.planId,
                    repoType,
                    repoName,
                    name,
                    fullPath,
                    packageKey,
                    version
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '已添加到扫描队列'
                    })
                    this.handlerPaginationChange()
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-report-container {
    position: relative;
    height: 100%;
    padding: 0 20px 10px;
    overflow: hidden;
    background-color: white;
    .report-overview {
        height: 130px;
        padding-bottom: 10px;
        .base-info-item {
            position: relative;
            height: 100%;
            padding: 20px;
            flex: 1;
            justify-content: space-around;
            border: solid var(--borderColor);
            border-width: 1px 0;
            background-color: var(--bgColor);
            .base-info-value {
                font-size: 30px;
                font-weight: 600;
            }
            &:first-child {
                margin-right: 20px;
                color: white;
                border-width: 0;
                border-radius: 2px;
                background-color: var(--primaryColor);
                background-image: url("data:image/svg+xml,%3Csvg width='80' height='80' viewBox='0 0 80 80' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3ClinearGradient x1='21.842%25' y1='2.983%25' y2='137.901%25' id='a'%3E%3Cstop stop-color='%23FFF' offset='0%25'/%3E%3Cstop stop-color='%23FFF' stop-opacity='0' offset='100%25'/%3E%3C/linearGradient%3E%3C/defs%3E%3Cpath d='M36.805.134l.01.099.447 5.61A33.863 33.863 0 0 0 26.667 8.43a34.256 34.256 0 0 0-10.89 7.346 34.059 34.059 0 0 0-7.347 10.89C6.64 30.882 5.736 35.375 5.736 40c0 4.626.904 9.11 2.694 13.333a34.256 34.256 0 0 0 7.346 10.89c3.141 3.15 6.81 5.62 10.89 7.347 4.215 1.79 8.708 2.694 13.334 2.694 4.626 0 9.11-.904 13.333-2.694a34.256 34.256 0 0 0 10.89-7.346c3.15-3.141 5.62-6.81 7.347-10.89 1.79-4.215 2.694-8.708 2.694-13.334 0-4.626-.904-9.11-2.694-13.333a34.002 34.002 0 0 0-3.266-5.987l4.662-3.203c.01 0 .01-.01.018-.01l.036-.035C77.423 23.857 79.991 31.624 80 40c0 22.094-17.906 40-40 40S0 62.085 0 40C0 18.98 16.206 1.754 36.805.134zm1.352 17.1l.456 5.71a17.063 17.063 0 0 0-10.73 4.975 17.064 17.064 0 0 0-5.028 12.144c0 4.59 1.79 8.903 5.029 12.143a17.064 17.064 0 0 0 12.143 5.029c4.59 0 8.904-1.79 12.143-5.03a17.064 17.064 0 0 0 5.03-12.142c0-3.526-1.057-6.89-3.017-9.727l4.725-3.23a22.795 22.795 0 0 1 4.018 12.957c0 12.644-10.255 22.899-22.9 22.899-12.643 0-22.898-10.255-22.898-22.9 0-12.017 9.252-21.879 21.029-22.827zM40 0c12.564 0 23.767 5.799 31.096 14.864-.009 0-.009.008-.018.008l-.035.036-28.85 23.374a2.86 2.86 0 0 1-2.22 4.671 2.86 2.86 0 0 1-2.863-2.864 2.86 2.86 0 0 1 2.863-2.863V0z' fill='url(%23a)' fill-rule='nonzero' opacity='.2'/%3E%3C/svg%3E");
                background-repeat: no-repeat;
                background-position: calc(100% + 6px) calc(100% + 8px);
            }
            &:not(:first-child):before {
                content: '';
                position: absolute;
                width: 1px;
                height: calc(100% - 40px);
                left: 0;
                top: 20px;
                background-color: var(--borderColor);
            }
            &:nth-child(2):before {
                height: 100%;
                top: 0;
            }
            &:last-child {
                border-right-width: 1px;
            }
            :not(:first-child).base-info-key {
                color: var(--subsidiaryColor)
            }
        }
    }
    .report-list {
        height: calc(100% - 220px);
        &:before {
            top: -30px;
        }
        &:after {
            top: -35px
        }
        .report-filter {
            position: absolute;
            top: -40px;
            right: 0;
        }
        .icon-filter-shape {
            position: absolute;
            padding: 5px;
            margin-top: -37px;
            margin-left: 95px;
            font-size: 20px;
            color: var(--subsidiaryColor);
            cursor: pointer;
            &:hover {
                color: var(--primaryColor);
            }
        }
    }
    .start-scan {
        position: absolute;
        top: 15px;
        right: 20px;
    }
}
</style>
