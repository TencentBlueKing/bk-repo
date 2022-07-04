<template>
    <div class="arti-report-container">
        <div class="base-info flex-column">
            <div class="pt20 pb20 flex-align-center">
                <Icon class="mr10" size="40" :name="(baseInfo.repoType || '').toLowerCase()" />
                <span class="arti-name text-overflow" :title="baseInfo.name">{{ baseInfo.name }}</span>
            </div>
            <div class="arti-meta">
                <div v-for="item in metaBase.filter(v => baseInfo[v.key])"
                    :key="item.key" class="meta-content">
                    <span style="color:var(--fontSubsidiaryColor);">{{ item.label }}</span>
                    <span class="ml20">{{ baseInfo[item.key] }}</span>
                </div>
            </div>
            <!-- <div v-if="baseInfo.qualityRedLine !== null" class="arti-quality">
                <div class="flex-align-center">
                    <span class="mr20" style="color:var(--fontSubsidiaryColor);">质量规则</span>
                    <span v-if="baseInfo.qualityRedLine" class="repo-tag SUCCESS">通过</span>
                    <span v-else class="repo-tag FAILED">不通过</span>
                </div>
                <div class="status-sign"
                    :class="item.id"
                    v-for="item in qualityList"
                    :key="item.id"
                    :data-name="item.label">
                </div>
            </div> -->
        </div>
        <div class="leak-list">
            <div class="flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="filter.licenseId"
                    clearable
                    placeholder="请输入许可证名称, 按Enter键搜索"
                    right-icon="bk-icon icon-search"
                    @enter="handlerPaginationChange()"
                    @clear="handlerPaginationChange()">
                </bk-input>
                <div class="flex-1 flex-end-center">
                    <bk-button theme="default" @click="startScanSingleHandler">重新扫描</bk-button>
                </div>
            </div>
            <bk-table
                class="mt10 leak-table"
                height="calc(100% - 80px)"
                :data="licenseList"
                :outer-border="false"
                :row-border="false"
                row-key="licenseKey"
                size="small">
                <template #empty>
                    <empty-data
                        :is-loading="isLoading"
                        :search="Boolean(filter.licenseId)"
                        title="未扫描到证书信息">
                    </empty-data>
                </template>
                <bk-table-column type="expand" width="30">
                    <template #default="{ row }">
                        <div class="leak-title">证书信息</div>
                        <div class="leak-tip">
                            <a :href="row.description" target="_blank">{{ row.description || '/' }}</a>
                        </div>
                    </template>
                </bk-table-column>
                <bk-table-column label="名称">
                    <template #default="{ row }">
                        <span v-bk-tooltips="{ content: row.fullName, placements: ['top'] }">{{ row.licenseId }}</span>
                    </template>
                </bk-table-column>
                <bk-table-column label="依赖路径" prop="dependentPath"></bk-table-column>
                <bk-table-column label="OSI认证" width="120">
                    <template #default="{ row }">{{ row.description ? `${row.isOsiApproved ? '已' : '未'}认证` : '/' }}</template>
                </bk-table-column>
                <bk-table-column label="FSF开源" width="120">
                    <template #default="{ row }">{{ row.description ? `${row.isFsfLibre ? '已' : '未'}开源` : '/' }}</template>
                </bk-table-column>
                <bk-table-column label="推荐使用" width="120">
                    <template #default="{ row }">{{ row.description ? `${row.recommended ? '' : '不'}推荐` : '/' }}</template>
                </bk-table-column>
                <bk-table-column label="合规性" width="120">
                    <template #default="{ row }">
                        <span v-if="row.description" class="repo-tag" :class="row.compliance ? 'SUCCESS' : 'FAILED'">{{ `${row.compliance ? '' : '不'}合规` }}</span>
                        <span v-else>/</span>
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
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import { formatDate, segmentNumberThree, formatDuration } from '@repository/utils'
    export default {
        name: 'license',
        data () {
            return {
                metaBase: [
                    { key: 'duration', label: '持续时间' },
                    { key: 'finishTime', label: '完成时间' },
                    { key: 'repoName', label: '所属仓库' },
                    { key: 'groupId', label: 'GroupId' },
                    { key: 'version', label: '制品版本' },
                    { key: 'fullPath', label: '存储路径' }
                ],
                baseInfo: {},
                isLoading: false,
                licenseList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                filter: {
                    licenseId: ''
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            planId () {
                return this.$route.params.planId
            },
            recordId () {
                return this.$route.params.recordId
            }
        },
        created () {
            this.licenseReportOverview({
                projectId: this.projectId,
                recordId: this.recordId,
                taskId: this.$route.query.taskId,
                viewType: this.$route.query.viewType
            }).then(res => {
                this.baseInfo = {
                    ...res,
                    duration: formatDuration(res.duration / 1000),
                    finishTime: formatDate(res.finishTime)
                }
            })
            this.handlerPaginationChange()
        },
        methods: {
            segmentNumberThree,
            ...mapActions([
                'startScanSingle',
                'licenseReportOverview',
                'getLicenseLeakList'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getLicenselicenseListHandler()
            },
            getLicenselicenseListHandler () {
                this.isLoading = true
                return this.getLicenseLeakList({
                    projectId: this.projectId,
                    recordId: this.recordId,
                    viewType: this.$route.query.viewType,
                    licenseId: this.filter.licenseId,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.licenseList = records.map(v => ({
                        ...v,
                        licenseKey: `${v.licenseId}${v.dependentPath}`
                    }))
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
                    show: true,
                    licenseId: ''
                }
            },
            startScanSingleHandler () {
                const { repoType, repoName, fullPath, packageKey, version } = this.baseInfo
                this.startScanSingle({
                    projectId: this.projectId,
                    id: this.planId,
                    repoType,
                    repoName,
                    fullPath,
                    packageKey,
                    version
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: '已添加到扫描队列'
                    })
                    this.back()
                })
            },
            back () {
                const { repoType, repoName } = this.baseInfo
                const { viewType, scanType, scanName, path, packageKey, version } = this.$route.query
                this.$router.push({
                    name: scanName ? 'scanReport' : (this.packageKey ? 'commonPackage' : 'repoGeneric'),
                    params: {
                        projectId: this.projectId,
                        [this.planId ? 'planId' : 'repoType']: this.planId || repoType
                    },
                    query: scanName
                        ? { viewType, scanType, scanName }
                        : (this.packageKey
                            ? { repoName, packageKey, version }
                            : { repoName, path })
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.arti-report-container {
    height: 100%;
    padding: 20px;
    display: flex;
    align-items: flex-start;
    overflow: hidden;
    background-color: white;
    .base-info {
        height: 100%;
        overflow-y: auto;
        padding: 0 20px;
        flex-basis: 300px;
        border: 1px solid var(--borderColor);
        background-color: var(--bgColor);
        .arti-name {
            max-width: 200px;
            font-size: 16px;
            font-weight: 600;
        }
        .arti-meta,
        .arti-quality,
        .arti-leak {
            padding: 20px 0;
            display: grid;
            gap: 20px;
            border-top: 1px solid var(--borderColor);
        }
        .arti-meta .meta-content {
            display: flex;
            span:first-child {
                flex-shrink: 0;
            }
            span:last-child {
                word-break: break-word;
                flex: 1
            }
        }
        .arti-leak {
            grid-template: auto / repeat(2, 1fr);
        }
    }
    .leak-list {
        flex: 1;
        height: 100%;
        margin-left: 20px;
        .leak-title {
            padding: 5px 20px 0;
            font-weight: 800;
        }
        .leak-tip {
            padding: 0 20px 5px;
            color: var(--fontDisableColor);
        }
    }
}
</style>
