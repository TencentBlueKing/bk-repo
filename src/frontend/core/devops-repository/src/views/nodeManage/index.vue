<template>
    <div class="node-container" v-bkloading="{ isLoading }">
        <div class="ml20 mr20 mt10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="showCreateNode">{{ $t('create') }}</bk-button>
            <div class="node-search flex-align-center">
                <bk-input
                    class="w250"
                    v-model.trim="search.name"
                    clearable
                    :placeholder="$t('nodeName')"
                    right-icon="bk-icon icon-search">
                </bk-input>
                <bk-select
                    class="ml10 w250"
                    v-model="search.type"
                    :placeholder="$t('nodeType')">
                    <bk-option
                        v-for="(label, type) in nodeTypeEnum"
                        :key="type"
                        :id="type"
                        :name="$t(`nodeTypeEnum.${type}`)">
                    </bk-option>
                </bk-select>
            </div>
        </div>
        <bk-table
            class="mt10 node-table"
            height="calc(100% - 100px)"
            :data="filterClusterList"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading" :search="Boolean(search.name || search.type)"></empty-data>
            </template>
            <bk-table-column :label="$t('status')" width="100">
                <template #default="{ row }">
                    <div class="status-sign" :class="row.status" :data-name="row.status === 'HEALTHY' ? $t('normal') : $t('abnormal')"></div>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('nodeName')" prop="name" width="250" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('nodeType')" width="120">
                <template #default="{ row }">{{ $t(`nodeTypeEnum.${row.type}`) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('address')">
                <template #default="{ row }">
                    <a class="hover-btn" :href="row.url" target="_blank">{{ row.url }}</a>
                </template>
            </bk-table-column>
            <bk-table-column :label="$t('account')" prop="username" width="120" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('createdDate')" width="200">
                <template #default="{ row }">{{formatDate(row.createdDate)}}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <operation-list
                        v-if="row.type !== 'CENTER'"
                        :list="[
                            { label: $t('edit'), clickEvent: () => showEditNode(row) },
                            { label: $t('delete'), clickEvent: () => deleteClusterHandler(row) }
                        ]">
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
            :title="editNodeDialog.add ? $t('createNode') : $t('editNode')"
            width="600"
            height-num="402"
            @cancel="editNodeDialog.show = false">
            <bk-form class="mr50" :label-width="110" :model="editNodeDialog" :rules="rules" ref="editNodeDialog">
                <bk-form-item :label="$t('type')" property="type">
                    <bk-radio-group v-model="editNodeDialog.type">
                        <bk-radio class="mr20" value="STANDALONE" :disabled="!editNodeDialog.add && editNodeDialog.type !== 'STANDALONE'">{{ $t('nodeTypeEnum.STANDALONE') }}</bk-radio>
                        <bk-radio class="mr20" value="EDGE" :disabled="!editNodeDialog.add && editNodeDialog.type !== 'EDGE'">{{ $t('nodeTypeEnum.EDGE') }}</bk-radio>
                        <bk-radio class="mr20" value="REMOTE" :disabled="!editNodeDialog.add && editNodeDialog.type !== 'REMOTE'">{{ $t('nodeTypeEnum.REMOTE') }}</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item :label="$t('name')" :required="true" property="name" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.name" :disabled="!editNodeDialog.add" maxlength="32" show-word-limit :placeholder="$t('pleaseInput')"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('address')" :required="true" property="url" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.url" :placeholder="$t('pleaseInput')"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('Certificate')" property="certificate" error-display-type="normal">
                    <bk-input type="textarea" v-model.trim="editNodeDialog.certificate"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('udpPort')" property="udpPort" error-display-type="normal" v-if="editNodeDialog.type !== 'REMOTE'">
                    <bk-input type="number" :max="65535" :min="1" v-model.trim="editNodeDialog.udpPort"></bk-input>
                </bk-form-item>
                <bk-form-item :label="$t('verificationMethod')">
                    <bk-radio-group v-model="createType" :change="changeValidateType()">
                        <bk-radio class="mr20" value="user">{{ $t('username') + '/' + $t('password') }}</bk-radio>
                        <bk-radio class="mr20" value="appId">AppID/AK/SK</bk-radio>
                    </bk-radio-group>
                </bk-form-item>
                <bk-form-item v-if="createType === 'user'" :label="$t('account')" :required="true" property="username" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.username"></bk-input>
                </bk-form-item>
                <bk-form-item v-if="createType === 'user'" :label="$t('password')" :required="true" property="password" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.password" type="password"></bk-input>
                </bk-form-item>
                <bk-form-item v-if="createType === 'appId'" label="appId" :required="true" property="appId" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.appId"></bk-input>
                </bk-form-item>
                <bk-form-item v-if="createType === 'appId'" label="accessKey" :required="true" property="accessKey" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.accessKey"></bk-input>
                </bk-form-item>
                <bk-form-item v-if="createType === 'appId'" label="secretKey" :required="true" property="secretKey" error-display-type="normal">
                    <bk-input v-model.trim="editNodeDialog.secretKey"></bk-input>
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
    import { nodeTypeEnum } from '@repository/store/publicEnum'
    export default {
        name: 'nodeManage',
        components: { OperationList },
        data () {
            return {
                nodeTypeEnum,
                isLoading: false,
                search: {
                    name: '',
                    type: ''
                },
                createType: 'user',
                editNodeDialog: {
                    show: false,
                    loading: false,
                    add: true,
                    // CENTER中心节点,EDGE边缘节点,STANDALONE独立节点
                    type: 'STANDALONE',
                    name: '',
                    url: '',
                    username: null,
                    password: null,
                    appId: null,
                    accessKey: null,
                    secretKey: null,
                    certificate: null,
                    udpPort: null
                },
                rules: {
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('nodeName'),
                            trigger: 'blur'
                        },
                        {
                            validator: this.asynCheckNodeName,
                            message: this.$t('nodeName') + this.$t('space') + this.$t('exist'),
                            trigger: 'blur'
                        }
                    ],
                    udpPort: [
                        {
                            validator: this.asynCheckUdpPort,
                            message: this.$t('portTip'),
                            trigger: 'blur'
                        }
                    ],
                    url: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('address'),
                            trigger: 'blur'
                        },
                        {
                            regex: /^https?:\/\//,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('legit') + this.$t('space') + this.$t('address'),
                            trigger: 'blur'
                        }
                    ],
                    username: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('account'),
                            trigger: 'blur'
                        }
                    ],
                    password: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + this.$t('password'),
                            trigger: 'blur'
                        }
                    ],
                    appId: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + 'appId',
                            trigger: 'blur'
                        }
                    ],
                    accessKey: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + 'accessKey',
                            trigger: 'blur'
                        }
                    ],
                    secretKey: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('space') + 'secretKey',
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
                'deleteCluster',
                'updateCluster'
            ]),
            asynCheckNodeName () {
                if (this.editNodeDialog.add) {
                    return this.checkNodeName({
                        name: this.editNodeDialog.name
                    }).then(res => !res)
                } else {
                    return true
                }
            },
            asynCheckUdpPort () {
                return !isNaN(this.editNodeDialog.udpPort)
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
                    username: null,
                    password: null,
                    appId: null,
                    accessKey: null,
                    secretKey: null,
                    certificate: null,
                    udpPort: null
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
                if (this.createType === 'user') {
                    this.editNodeDialog.appId = null
                    this.editNodeDialog.accessKey = null
                    this.editNodeDialog.secretKey = null
                }
                if (this.createType === 'appId') {
                    this.editNodeDialog.username = null
                    this.editNodeDialog.password = null
                }
                if (this.editNodeDialog.certificate === '') {
                    this.editNodeDialog.certificate = null
                }
                if (this.editNodeDialog.udpPort === '') {
                    this.editNodeDialog.udpPort = null
                }
                const { type, name, url, username, password, appId, accessKey, secretKey, certificate, udpPort } = this.editNodeDialog
                if (this.editNodeDialog.add) {
                    this.createCluster({
                        body: {
                            type,
                            name,
                            url,
                            username,
                            password,
                            appId,
                            accessKey,
                            secretKey,
                            certificate,
                            udpPort
                        }
                    }).then(() => {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('createNode') + this.$t('space') + this.$t('success')
                        })
                        this.editNodeDialog.show = false
                        this.createType = 'user'
                        this.getClusterListHandler()
                    }).finally(() => {
                        this.editNodeDialog.loading = false
                    })
                } else {
                    this.updateCluster({
                        body: {
                            type,
                            name,
                            url,
                            username,
                            password,
                            appId,
                            accessKey,
                            secretKey,
                            certificate,
                            udpPort
                        }
                    }).then(() => {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('editNode') + this.$t('space') + this.$t('success')
                        })
                        this.editNodeDialog.show = false
                        this.createType = 'user'
                        this.getClusterListHandler()
                    }).finally(() => {
                        this.editNodeDialog.loading = false
                    })
                }
            },
            deleteClusterHandler (row) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteNodeMsg', { 0: row.name }),
                    confirmFn: () => {
                        return this.deleteCluster({
                            id: row.id
                        }).then(() => {
                            this.getClusterListHandler()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('space') + this.$t('success')
                            })
                        })
                    }
                })
            },
            changeValidateType () {
                if (this.editNodeDialog.show) {
                    this.$refs.editNodeDialog.clearError()
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.node-container {
    height: 100%;
    overflow: hidden;
    background-color: white;
}
</style>
