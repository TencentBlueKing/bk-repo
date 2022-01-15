<template>
    <div class="node-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showCreateNode"><span class="mr5">{{ $t('create') }}</span></bk-button>
            <div class="node-search flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="search.name"
                    clearable
                    placeholder="节点名称"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="search.type"
                    placeholder="节点类型">
                    <bk-option
                        v-for="(label, type) in typeMap"
                        :key="type"
                        :id="type"
                        :name="label">
                    </bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10 node-table"
            height="calc(100% - 104px)"
            :data="filterClusterList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(search.name || search.type)">
                    <template v-if="!Boolean(search.name || search.type)">
                        <span class="ml10">暂无节点数据，</span>
                        <bk-button text @click="showCreateNode">即刻创建</bk-button>
                    </template>
                </empty-data>
            </template>
            <bk-table-column label="状态" width="100">
                <template #default="{ row }">
                    <div class="flex-align-center">
                        <i class="status-icon" :class="row.status"></i>
                        <span class="ml5" :class="row.status">{{ row.status === 'HEALTHY' ? '正常' : '异常' }}</span>
                    </div>
                </template>
            </bk-table-column>
            <bk-table-column label="节点名称" prop="name" width="250"></bk-table-column>
            <bk-table-column label="节点类型" width="120">
                <template #default="{ row }">
                    {{ typeMap[row.type] }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('address')">
                <template #default="{ row }">
                    <a class="hover-btn" :href="row.url" target="_blank">{{ row.url }}</a>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('account')" prop="username" width="120"></bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="200">
                <template #default="{ row }">{{formatDate(row.createdDate)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="70">
                <template #default="{ row }">
                    <operation-list
                        v-if="row.type !== 'CENTER'"
                        :list="[
                            // { label: '编辑', clickEvent: () => showEditNode(row) },
                            { label: '删除', clickEvent: () => deleteClusterHandler(row) }
                        ].filter(Boolean)">
                    </operation-list>
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
            :count="clusterList.length"
            :limit-list="pagination.limitList">
        </bk-pagination>
        <canway-dialog
            v-model="editNodeDialog.show"
            :title="editNodeDialog.add ? '新建节点' : '编辑节点'"
            width="600"
            height-num="402"
            @cancel="editNodeDialog.show = false">
            <bk-form class="mr50" :label-width="110" :model="editNodeDialog" :rules="rules" ref="editNodeDialog">
                <bk-form-item :label="$t('type')" property="type">
                    <bk-radio-group v-model="editNodeDialog.type">
                        <bk-radio class="mr20" value="STANDALONE">独立节点</bk-radio>
                        <bk-radio class="mr20" value="EDGE">边缘节点</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.name" :disabled="!editNodeDialog.add" maxlength="32" show-word-limit></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('address')" :required="true" property="url" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.url" :disabled="!editNodeDialog.add"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('account')" :required="true" property="username" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.username"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('password')" :required="true" property="password" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.password" type="password"></bk-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button theme="default" @click.stop="editNodeDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="editNodeDialog.loading" theme="primary" @click.stop.prevent="confirm">{{$t('confirm')}}</bk-button>
            </template>
        </canway-dialog>
    </div>
</template>
<script>
    import OperationList from '@repository/components/OperationList'
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@repository/utils'
    export default {
        name: 'nodeManage',
        components: { OperationList },
        data () {
            return {
                isLoading: false,
                search: {
                    name: '',
                    type: ''
                },
                editNodeDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    // CENTER中心节点,EDGE边缘节点,STANDALONE独立节点
                    type: 'STANDALONE',
                    name: '',
                    url: '',
                    username: '',
                    password: ''
                },
                typeMap: {
                    STANDALONE: '独立节点',
                    EDGE: '边缘节点',
                    CENTER: '中心节点'
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + '节点名称',
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckNodeName,
                            message: '节点名称已存在',
                            trigger: 'blur'
                        }
                    ],
                    url: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('address'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^https?:\/\//,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('address'),
                            trigger: 'blur'
                        }
                    ],
                    username: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('account'),
                            trigger: 'blur'
                        }
                    ],
                    password: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('password'),
                            trigger: 'blur'
                        }
                    ]
                },
                pagination: {
                    current: 1,
                    limit: 20,
                    limitList: [10, 20, 40]
                }
            }
        },
        computed: {
            ...mapState(['clusterList']),
            filterClusterList () {
                const { current, limit } = this.pagination
                return this.clusterList.filter(v => {
                    return v.name.toLowerCase().indexOf(this.search.name.toLowerCase()) !== -1
                        && (!this.search.type || v.type === this.search.type)
                }).slice((current - 1) * limit, current * limit)
            }
        },
        created () {
            this.getClusterListHandler()
        },
        methods: {
            formatDate,
            ...mapActions([
                'getClusterList',
                'checkNodeName',
                'createCluster',
                'deleteCluster'
            ]),
            asynCheckNodeName () {
                return this.checkNodeName({
                    name: this.editNodeDialog.name
                }).then(res => !res)
            },
            handlerPaginationChange ({ current = 1, limit = this.pagination.limit } = {}) {
                this.pagination.current = current
                this.pagination.limit = limit
            },
            getClusterListHandler () {
                this.isLoading = true
                this.getClusterList().finally(() => {
                    this.isLoading = false
                })
            },
            showCreateNode () {
                this.$refs.editNodeDialog && this.$refs.editNodeDialog.clearError()
                this.editNodeDialog = {
                    show: true,
                    loading: false,
                    add: true,
                    type: 'STANDALONE',
                    name: '',
                    url: '',
                    username: '',
                    password: ''
                }
            },
            showEditNode (row) {
                this.$refs.editNodeDialog && this.$refs.editNodeDialog.clearError()
                this.editNodeDialog = {
                    show: true,
                    loading: false,
                    add: false,
                    ...row,
                    password: ''
                }
            },
            async confirm () {
                await this.$refs.editNodeDialog.validate()
                this.editNodeDialog.loading = true
                const { type, name, url, username, password } = this.editNodeDialog
                this.createCluster({
                    body: {
                        type,
                        name,
                        url,
                        username,
                        password
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (this.editNodeDialog.add ? '新建节点' : '编辑节点') + this.$t('success')
                    })
                    this.editNodeDialog.show = false
                    this.getClusterListHandler()
                }).finally(() => {
                    this.editNodeDialog.loading = false
                })
            },
            deleteClusterHandler (row) {
                this.$confirm({
                    theme: 'danger',
                    message: `确认删除节点 ${row.name} ？`,
                    confirmFn: () => {
                        return this.deleteCluster({
                            id: row.id
                        }).then(() => {
                            this.getClusterListHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.node-container {
    height: 100%;
    overflow: hidden;
    background-color: white;
    .node-table {
        .HEALTHY {
            color: var(--successColor);
        }
        .UNHEALTHY {
            color: var(--warningColor);
        }
        .status-icon {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            &.HEALTHY {
                background-color: var(--successColor);
            }
            &.UNHEALTHY {
                background-color: var(--warningColor);
            }
        }
    }
}
</style>
