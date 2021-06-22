<template>
    <div class="node-container" v-bkloading="{ isLoading }">
        <div class="mb20 flex-between-center">
            <div class="node-search flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="search.name"
                    clearable
                    placeholder="请输入节点名称"
                    @input="handlerPaginationChange()">
                </bk-input>
                <bk-select
                    class="ml20 w250"
                    v-model="search.type"
                    placeholder="请选择节点类型"
                    @change="handlerPaginationChange()">
                    <bk-option
                        v-for="(label, type) in typeMap"
                        :key="type"
                        :id="type"
                        :name="label">
                    </bk-option>
                </bk-select>
            </div>
            <bk-button theme="primary" @click.stop="showCreateNode">新增节点</bk-button>
        </div>
        <bk-table
            class="node-table"
            height="calc(100% - 84px)"
            :data="filterClusterList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <bk-table-column label="状态" width="50">
                <template #default="{ row }">
                    <i class="status-icon" :class="row.status"></i>
                </template>
            </bk-table-column>
            <bk-table-column label="节点名称" prop="name" width="250"></bk-table-column>
            <bk-table-column label="节点类型" width="100">
                <template #default="{ row }">
                    {{ typeMap[row.type] }}
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('address')">
                <template #default="{ row }">
                    <a class="hover-btn" :href="row.url" target="_blank">{{ row.url }}</a>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('account')" prop="username" width="100"></bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="150">
                <template #default="{ row }">{{formatDate(row.createdDate)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <div v-if="row.type !== 'CENTER'" class="flex-align-center">
                        <!-- <i class="mr20 devops-icon icon-edit hover-btn" @click="showEditNode(row)"></i> -->
                        <i class="devops-icon icon-delete hover-btn" @click="deleteClusterHandler(row)"></i>
                    </div>
                </template>
            </bk-table-column>
        </bk-table>
        <bk-pagination
            class="mt10"
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
        <bk-dialog
            v-model="editNodeDialog.show"
            :title="editNodeDialog.add ? '新建节点' : '编辑节点'"
            width="600"
            :close-icon="false"
            :quick-close="false"
            :draggable="false">
            <bk-form class="mr50" :label-width="110" :model="editNodeDialog" :rules="rules" ref="editNodeDialog">
                <bk-form-item :label="$t('type')" :required="true" property="type">
                    <bk-radio-group v-model="editNodeDialog.type">
                        <bk-radio class="mr20" value="STANDALONE">独立节点</bk-radio>
                        <bk-radio class="mr20" value="EDGE">边缘节点</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item :label="$t('name')" :required="true" property="name">
                    <bk-input v-model.trim="editNodeDialog.name" :disabled="!editNodeDialog.add" maxlength="32"></bk-input>
                </bk-form-item>
                <bk-form-item class="mt30" :label="$t('address')" :required="true" property="url">
                    <bk-input v-model.trim="editNodeDialog.url" :disabled="!editNodeDialog.add"></bk-input>
                </bk-form-item>
                <bk-form-item class="mt30" :label="$t('account')" :required="true" property="username">
                    <bk-input v-model.trim="editNodeDialog.username"></bk-input>
                </bk-form-item>
                <bk-form-item class="mt30" :label="$t('password')" :required="true" property="password">
                    <bk-input v-model.trim="editNodeDialog.password" type="password"></bk-input>
                </bk-form-item>
            </bk-form>
            <template #footer>
                <bk-button :loading="editNodeDialog.loading" theme="primary" @click.stop.prevent="confirm">{{$t('submit')}}</bk-button>
                <bk-button theme="default" @click.stop="editNodeDialog.show = false">{{$t('cancel')}}</bk-button>
            </template>
        </bk-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { formatDate } from '@/utils'
    export default {
        name: 'node',
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
                this.$bkInfo({
                    type: 'error',
                    title: `确认删除节点 ${row.name} ？`,
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteCluster({
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
    .node-search-btn {
        position: relative;
        z-index: 1;
        padding: 9px;
        color: white;
        margin-left: -2px;
        border-radius: 0 2px 2px 0;
        background-color: #3a84ff;
        cursor: pointer;
        &:hover {
            background-color: #699df4;
        }
    }
    .node-table {
        .icon-edit {
            font-size: 14px;
        }
        .icon-delete {
            font-size: 16px;
        }
        .status-icon {
            display: block;
            width: 12px;
            height: 12px;
            margin: auto;
            border-radius: 50%;
            &.HEALTHY {
                background-color: #2dcb56;
            }
            &.UNHEALTHY {
                background-color: #ff9c01;
            }
        }
    }
}
</style>
