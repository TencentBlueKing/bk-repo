<template>
    <div class="audit-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-align-center">
            <bk-select
                v-model="query.projectId"
                class="mr10 w250"
                searchable
                :placeholder="$t('viewProjectLogPlaceHolder')"
                @change="handlerPaginationChange()"
                :enable-virtual-scroll="projectList && projectList.length > 3000"
                :list="projectList">
                <bk-option v-for="option in projectList"
                    :key="option.id"
                    :id="option.id"
                    :name="option.name">
                </bk-option>
            </bk-select>
            <bk-date-picker
                v-model="query.time"
                class="mr10 w250"
                :shortcuts="shortcuts"
                type="daterange"
                :placeholder="$t('selectDatePlaceHolder')"
                @change="handlerPaginationChange()">
            </bk-date-picker>
            <bk-tag-input
                class="mr10 w250"
                v-model="query.user"
                :list="Object.values(userList).filter(user => user.id !== 'anonymous')"
                :search-key="['id', 'name']"
                :placeholder="$t('userInputPlaceHolder')"
                :max-data="1"
                trigger="focus"
                allow-create
                @click.native.capture="query.user = []"
                @select="handlerPaginationChange()"
                @removeAll="handlerPaginationChange()">
            </bk-tag-input>
        </div>
        <bk-table
            class="mt10"
            height="calc(100% - 100px)"
            :data="auditList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(isSearching)"></empty-data>
            </template>
            <bk-table-column :label="$t('project')" show-overflow-tooltip>
                <template #default="{ row }">{{ getProjectName(row.content.projectId) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operatingTime')" width="150">
                <template #default="{ row }">{{ formatDate(row.createdDate) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operator')" show-overflow-tooltip>
                <template #default="{ row }">
                    <bk-user-display-name v-if="multiMode" :user-id="userList[row.userId] ? userList[row.userId].name : row.userId"></bk-user-display-name>
                    <span v-else> {{ userList[row.userId] ? userList[row.userId].name : row.userId }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('event')" show-overflow-tooltip>
                <template #default="{ row }">{{ row.operate }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operationObject')" show-overflow-tooltip>
                <template #default="{ row }">
                    <Icon class="mr5 table-svg" v-if="row.content.repoType"
                        :name="row.content.repoType.toLowerCase()" size="16">
                    </Icon>
                    <span class="mr20" v-for="item in row.content.resKey.split('::').filter(Boolean)" :key="item">
                        {{ row.content.repoType ? item : (userList[item] ? userList[item].name : item) }}
                    </span>
                    <span>{{ row.content.des }}</span>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('client') + 'IP'" prop="clientAddress"></bk-table-column>
            <bk-table-column :label="$t('result')" width="80">
                <template #default="{ row }">
                    <span class="repo-tag" :class="[row.result ? 'SUCCESS' : 'FAILED']">{{ row.result ? $t('success') : $t('fail') }}</span>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="p10"
            size="small"
            align="right"
            show-total-count
            :current.sync="pagination.current"
            :limit="pagination.limit"
            :count="pagination.count"
            :limit-list="pagination.limitList"
            @change="current => handlerPaginationChange({ current })"
            @limit-change="limit => handlerPaginationChange({ limit })">
        </bk-pagination>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    import moment from 'moment'
    import { before, zeroTime } from '@repository/utils/date'
    const nowTime = moment()
    export default {
        name: 'audit',
        data () {
            return {
                isLoading: false,
                query: {
                    projectId: this.$route.params.projectId,
                    user: [],
                    time: [zeroTime(before(7)), nowTime.toDate()]
                },
                auditList: [],
                multiMode: BK_REPO_ENABLE_MULTI_TENANT_MODE === 'true',
                pagination: {
                    count: 0,
                    current: 1,
                    limit: 20,
                    selectionCount: 0,
                    limitList: [10, 20, 40]
                },
                shortcuts: [
                    {
                        text: this.$t('lastSevenDays'),
                        value () {
                            return [zeroTime(before(7)), nowTime.toDate()]
                        }
                    },
                    {
                        text: this.$t('lastFifteenDays'),
                        value () {
                            return [zeroTime(before(15)), nowTime.toDate()]
                        }
                    },
                    {
                        text: this.$t('lastThirtyDays'),
                        value () {
                            return [zeroTime(before(30)), nowTime.toDate()]
                        }
                    }
                ]
            }
        },
        computed: {
            ...mapState(['projectList', 'userList']),
            isSearching () {
                const { startTime, endTime, user } = this.$route.query
                return startTime || endTime || user
            }
        },
        created () {
            const { startTime, endTime, user, projectId } = this.$route.query
            startTime && endTime && (this.query.time = [new Date(startTime), new Date(endTime)])
            user && this.query.user.push(user)
            projectId && (this.query.projectId = projectId)
            this.handlerPaginationChange()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getAuditList'
            ]),
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
                let [startTime, endTime] = this.query.time
                startTime = startTime instanceof Date ? startTime.toISOString() : undefined
                endTime = endTime instanceof Date ? endTime.toISOString() : undefined
                const query = {
                    projectId: this.query.projectId || undefined,
                    startTime,
                    endTime,
                    user: this.query.user[0] || undefined
                }
                this.$router.replace({
                    query: {
                        ...this.$route.query,
                        ...query
                    }
                })
                this.getAuditListHandler(query)
            },
            getAuditListHandler (query) {
                this.isLoading = true
                this.getAuditList({
                    ...query,
                    current: this.pagination.current,
                    limit: this.pagination.limit
                }).then(({ records, totalRecords }) => {
                    this.auditList = records
                    this.pagination.count = totalRecords
                }).finally(() => {
                    this.isLoading = false
                })
            },
            getProjectName (id) {
                const project = this.projectList.find(project => project.id === id)
                return project ? project.name : '/'
            }
        }
    }
</script>
<style lang="scss" scoped>
.audit-container {
    height: 100%;
    background-color: white;
}
</style>
