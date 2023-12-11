<template>
    <div class="leak-list">
        <div class="flex-align-center">
            <bk-input
                class="input-common"
                v-model.trim="filter.vulId"
                clearable
                :placeholder="$t('bugSearchHolder')"
                right-icon="bk-icon icon-search"
                @enter="handlerPaginationChange()"
                @clear="handlerPaginationChange()">
            </bk-input>
            <bk-select
                class="ml10 input-level"
                v-model="filter.severity"
                :placeholder="$t('vulnerabilityLevel')"
                @change="handlerPaginationChange()">
                <bk-option v-for="[id] in Object.entries(leakLevelEnum)" :key="id" :id="id" :name="$t(`leakLevelEnum.${id}`)"></bk-option>
            </bk-select>
            <bk-select
                v-if="subtaskOverview.scannerType === 'standard'"
                class="ml10 input-common"
                :clearable="false"
                v-model="filter.ignored"
                @change="handlerPaginationChange()">
                <bk-option :id="true" :name="$t('ignoredVul')"></bk-option>
                <bk-option :id="false" :name="$t('activeVul')"></bk-option>
            </bk-select>
            <div class="flex-1 flex-end-center">
                <bk-button class="mr10" theme="default" @click="exportReport">{{$t('exportReport')}}</bk-button>
                <bk-button theme="default" @click="$emit('rescan')">{{$t('rescan')}}</bk-button>
            </div>
        </div>
        <bk-table
            class="mt10 leak-table"
            height="calc(100% - 100px)"
            :data="leakList"
            :outer-border="false"
            :row-border="false"
            row-key="leakKey"
            size="small">
            <template #empty>
                <empty-data
                    :is-loading="isLoading"
                    :search="Boolean(filter.vulId || filter.severity)"
                    :title="$t('noVulnerabilityTitle')">
                </empty-data>
            </template>
            <bk-table-column type="expand" width="40">
                <template #default="{ row }">
                    <template v-if="row.path && row.path.length > 0 || row.versionsPaths && row.versionsPaths.length > 0">
                        <div class="leak-title">{{ $t('vulnerabilityPathTitle') }}</div>
                        <div v-if="row.versionsPaths && row.versionsPaths.length > 0">
                            <div v-for="(versionPaths,index) in row.versionsPaths" :key="versionPaths.version">
                                <br v-if="index !== 0 && row.versionsPaths.length > 1" />
                                <div v-if="row.versionsPaths.length > 1" class="leak-tip">
                                    {{ $t('installedVersion') }}: {{ versionPaths.version }}
                                </div>
                                <div class="leak-tip" v-for="(path, pathIndex) in versionPaths.paths" :key="path">
                                    {{ path }}
                                    <bk-divider v-if="pathIndex !== versionPaths.paths.length - 1" style="margin: 0 0 3px;" type="dashed" />
                                </div>
                            </div>
                        </div>
                        <div v-else class="leak-tip">{{ row.path }}</div>
                    </template>
                    <div class="leak-title">{{ row.title }}</div>
                    <div class="leak-tip">{{ row.description || '/' }}</div>
                    <div class="leak-title">{{$t('fixSuggestion')}}</div>
                    <div class="leak-tip">{{ row.officialSolution || '/' }}</div>
                    <template v-if="row.reference && row.reference.length">
                        <div class="leak-title">{{ $t('relatedInfo') }}</div>
                        <div class="leak-tip" v-for="url in row.reference" :key="url">
                            <a :href="url" target="_blank">{{ url }}</a>
                        </div>
                    </template>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('vulnerability') + 'ID'" show-overflow-tooltip>
                <template #default="{ row }">
                    {{ row.cveId || row.vulId }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('vulnerabilityLevel')">
                <template #default="{ row }">
                    <div class="status-sign" :class="row.severity" :data-name="$t(`leakLevelEnum.${row.severity}`)"></div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('dependPackage')" prop="pkgName" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('installedVersion')" show-overflow-tooltip>
                <template #default="{ row }">
                    {{ row.installedVersion }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" v-if="subtaskOverview.scannerType === 'standard'">
                <template slot-scope="props" v-if="!filter.ignored">
                    <bk-button theme="primary" text @click="ignoreVul(props.row.cveId || props.row.vulId)">{{ $t('ignore') }}</bk-button>
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
        <create-or-update-ignore-rule-dialog
            @success="handlerPaginationChange()"
            :plan-id="planId"
            :project-id="projectId"
            :updating-rule="creatingIgnoreRule"
            :visible.sync="createOrUpdateDialogVisible">
        </create-or-update-ignore-rule-dialog>
    </div>
</template>
<script>
    import { mapActions } from 'vuex'
    import { FILTER_RULE_IGNORE, leakLevelEnum } from '@repository/store/publicEnum'
    import CreateOrUpdateIgnoreRuleDialog from '../scanConfig/createOrUpdateIgnoreRuleDialog'
    export default {
        name: 'leak',
        components: { CreateOrUpdateIgnoreRuleDialog },
        props: {
            subtaskOverview: Object,
            projectId: String,
            viewType: String
        },
        data () {
            return {
                leakLevelEnum,
                isLoading: false,
                leakList: [],
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                },
                createOrUpdateDialogVisible: false,
                creatingIgnoreRule: {},
                filter: {
                    vulId: '',
                    severity: '',
                    ignored: false
                }
            }
        },
        watch: {
            subtaskOverview () {
                this.handlerPaginationChange()
            }
        },
        created () {
            if (this.subtaskOverview && this.subtaskOverview.recordId) {
                this.handlerPaginationChange()
            }
        },
        methods: {
            ...mapActions(['getLeakList']),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                this.getLeakListHandler()
            },
            getLeakListHandler () {
                this.isLoading = true
                return this.getLeakList({
                    projectId: this.projectId,
                    recordId: this.subtaskOverview.recordId,
                    viewType: this.viewType,
                    vulId: this.filter.vulId,
                    severity: this.filter.severity,
                    ignored: this.filter.ignored,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.leakList = records.map(v => ({
                        ...v,
                        leakKey: `${v.vulId}${v.pkgName}${v.installedVersion}`
                    }))
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            ignoreVul (vulId) {
                this.creatingIgnoreRule = {
                    type: FILTER_RULE_IGNORE,
                    name: `IGNORE-${this.generateId(10)}`,
                    projectId: this.projectId,
                    repoName: this.subtaskOverview.repoName,
                    planId: this.$route.params.planId,
                    vulIds: [vulId]
                }
                if (this.subtaskOverview.repoType === 'GENERIC') {
                    this.creatingIgnoreRule.fullPath = this.subtaskOverview.fullPath
                } else {
                    this.creatingIgnoreRule.packageKey = this.subtaskOverview.packageKey
                    this.creatingIgnoreRule.packageVersion = this.subtaskOverview.version
                }
                this.createOrUpdateDialogVisible = true
            },
            generateId (len) {
                const randomArr = window.crypto.getRandomValues(new Uint8Array((len || 40) / 2))
                return Array.from(randomArr, n => n.toString(16).padStart(2, '0')).join('').toUpperCase()
            },
            exportReport () {
                this.$bkNotify({
                    title: this.$t('exportLeakReportInfo'),
                    position: 'bottom-right',
                    theme: 'success'
                })
                const url = `/web/analyst/api/scan/export/artifact/leak/${this.projectId}/${this.subtaskOverview.recordId}`
                window.open(url, '_self')
            }
        }
    }
</script>
<style lang="scss" scoped>
.leak-list {
    flex: 1;
    height: 100%;
    .leak-title {
        padding: 5px 20px 0;
        font-weight: 800;
    }
    .leak-tip {
        padding: 0 20px 5px;
        color: var(--fontDisableColor);
    }
    .input-common{
        width: 220px;
    }
    .input-level{
        width: 150px;
    }
}
</style>
