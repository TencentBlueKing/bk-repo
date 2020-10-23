<template>
    <div class="repo-config-container">
        <header class="repo-config-header">
            <div class="flex-center">
                <div class="mr5 hover-btn flex-center" @click="toRepoDetail">
                    <icon size="24" :name="repoType" />
                    <span class="ml10">{{repoName}}</span>
                </div>
                <i class="devops-icon icon-angle-right"></i>
                <span class="ml5">{{$t('repoConfig')}}</span>
            </div>
            <div class="repo-config-operation">
                <bk-button theme="default" @click="toRepoList">
                    {{$t('returnBack')}}
                </bk-button>
            </div>
        </header>
        <main class="repo-config-main" v-bkloading="{ isLoading }">
            <bk-tab class="repo-config-tab" type="unborder-card">
                <bk-tab-panel name="baseInfo" :label="$t('repoBaseInfo')">
                    <div class="repo-base-info">
                        <bk-form :label-width="100">
                            <bk-form-item :label="$t('repoName')">
                                <div class="flex-align-center">
                                    <icon size="24" :name="repoBaseInfo.repoType || repoType" />
                                    <span class="ml10">{{repoBaseInfo.name || repoName}}</span>
                                </div>
                            </bk-form-item>
                            <bk-form-item :label="$t('repoAddress')">
                                <span>{{repoAddress}}</span>
                            </bk-form-item>
                            <bk-form-item :label="$t('description')">
                                <bk-input type="textarea"
                                    maxlength="200"
                                    v-model="repoBaseInfo.description"
                                    :placeholder="$t('repoDescriptionPlacehodler')">
                                </bk-input>
                            </bk-form-item>
                            <bk-form-item>
                                <bk-button :loading="repoBaseInfo.loading" theme="primary" @click.stop.prevent="saveBaseInfo">{{$t('save')}}</bk-button>
                            </bk-form-item>
                        </bk-form>
                    </div>
                </bk-tab-panel>
                <bk-tab-panel name="proxyConfig" :label="$t('proxyConfig')" v-if="showProxyConfigTab">
                    <span class="proxy-config-tips">{{$t('proxyConfigTips')}}</span>
                    <div class="proxy-item">
                        <div class="proxy-index"></div>
                        <div class="proxy-origin">{{$t('name')}}</div>
                        <div class="proxy-type">{{$t('type')}}</div>
                        <div class="proxy-address">{{$t('address')}}</div>
                        <div class="proxy-operation">{{$t('operation')}}</div>
                    </div>
                    <draggable v-model="proxyList" :options="{ animation: 200 }">
                        <div class="proxy-item" v-for="proxy in proxyList" :key="proxy.name + Math.random()">
                            <div class="proxy-index flex-align-center">
                                <i class="devops-icon icon-more"></i>
                                <i class="devops-icon icon-more" style="margin-left:-5px"></i>
                            </div>
                            <div class="proxy-origin">{{proxy.name}}</div>
                            <div class="proxy-type">{{proxy.public ? $t('publicProxy') : $t('privateProxy')}}</div>
                            <div class="proxy-address">{{proxy.url}}</div>
                            <div class="flex-align-center proxy-operation">
                                <i v-if="!proxy.public" class="devops-icon icon-edit hover-btn" @click.stop.prevent="editProxy(proxy)"></i>
                                <i class="ml10 devops-icon icon-delete hover-btn" @click.stop.prevent="deleteProxy(proxy)"></i>
                            </div>
                        </div>
                    </draggable>
                    <div class="proxy-add flex-align-center" @click="addProxy">
                        <i class="mr10 devops-icon icon-plus-square"></i>
                        <span>{{$t('addProxy')}}</span>
                    </div>
                    <bk-button class="mt20 ml20" :loading="editProxyData.loading" theme="primary" @click.stop.prevent="saveProxy">{{$t('save')}}</bk-button>
                </bk-tab-panel>
            </bk-tab>
        </main>
        <bk-dialog
            v-model="showProxyDialog"
            width="600"
            :title="editProxyData.type === 'add' ? $t('addProxy') : $t('editProxy')"
            :mask-close="false"
            :close-icon="false"
        >
            <bk-tab class="repo-config-tab"
                :active.sync="editProxyData.proxyType"
                @tab-change="proxyTabChange"
                type="unborder-card">
                <bk-tab-panel v-if="editProxyData.type === 'add'" name="publicProxy" :label="$t('publicProxy')">
                    <bk-form ref="publicProxy" :label-width="100" :model="editProxyData" :rules="rules">
                        <bk-form-item :label="$t('name')" :required="true" property="channelId">
                            <bk-select v-model="editProxyData.channelId">
                                <bk-option
                                    v-for="option in publicProxy"
                                    :key="option.channelId"
                                    :id="option.channelId"
                                    :name="option.name">
                                </bk-option>
                            </bk-select>
                        </bk-form-item>
                        <bk-form-item :label="$t('address')">
                            <span>{{ selectedPublicProxy.url || '' }}</span>
                        </bk-form-item>
                    </bk-form>
                </bk-tab-panel>
                <bk-tab-panel name="privateProxy" :label="$t('privateProxy')">
                    <bk-form ref="privateProxy" :label-width="100" :model="editProxyData" :rules="rules">
                        <bk-form-item :label="$t('privateProxy') + $t('name')" :required="true" property="name">
                            <bk-input v-model="editProxyData.name"></bk-input>
                        </bk-form-item>
                        <bk-form-item :label="$t('privateProxy') + $t('address')" :required="true" property="url">
                            <bk-input v-model="editProxyData.url"></bk-input>
                        </bk-form-item>
                        <bk-form-item :label="$t('ticket')" property="ticket">
                            <bk-checkbox v-model="editProxyData.ticket"></bk-checkbox>
                        </bk-form-item>
                        <bk-form-item v-if="editProxyData.ticket" :label="$t('account')" :required="true" property="username">
                            <bk-input v-model="editProxyData.username"></bk-input>
                        </bk-form-item>
                        <bk-form-item v-if="editProxyData.ticket" :label="$t('password')" :required="true" property="password">
                            <bk-input type="password" v-model="editProxyData.password"></bk-input>
                        </bk-form-item>
                        <!-- <bk-form-item>
                            <bk-button text theme="primary" @click="testPrivateProxy">{{$t('test') + $t('privateProxy')}}</bk-button>
                        </bk-form-item> -->
                    </bk-form>
                </bk-tab-panel>
            </bk-tab>
            <div slot="footer">
                <bk-button theme="primary" @click="confirmProxyData">{{$t('submit')}}</bk-button>
                <bk-button @click="cancelProxy">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>
    </div>
</template>
<script>
    import draggable from 'vuedraggable'
    import { mapActions } from 'vuex'
    export default {
        name: 'repoConfig',
        components: { draggable },
        data () {
            return {
                isLoading: false,
                repoBaseInfo: {
                    loading: false,
                    repoName: '',
                    repoType: '',
                    description: ''
                },
                // 公共源
                publicProxy: [],
                // 当前仓库的代理源
                proxyList: [],
                showProxyDialog: false,
                editProxyData: {
                    loading: false,
                    proxyType: 'publicProxy', // 公有 or 私有
                    type: '', // 添加 or 编辑
                    channelId: '',
                    name: '',
                    url: '',
                    ticket: false,
                    username: '',
                    password: ''
                },
                rules: {
                    channelId: [
                        {
                            required: true,
                            message: this.$t('pleaseSelect') + this.$t('publicProxy'),
                            trigger: 'blur'
                        }
                    ],
                    name: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('privateProxy') + this.$t('name'),
                            trigger: 'blur'
                        }
                    ],
                    url: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('privateProxy') + this.$t('address'),
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
                }
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            repoType () {
                return this.$route.params.repoType
            },
            showProxyConfigTab () {
                return !['generic', 'docker', 'helm'].includes(this.repoType)
            },
            selectedPublicProxy () {
                return this.publicProxy.find(v => v.channelId === this.editProxyData.channelId) || {}
            },
            repoAddress () {
                return location.origin + `/${this.repoBaseInfo.repoType}/${this.projectId}/${this.repoBaseInfo.name}/`
            }
        },
        created () {
            if (!this.repoName || !this.repoType) this.toRepoList()
            this.getRepoInfoHandler()
            this.getPublicProxy({
                repoType: this.repoType.toUpperCase()
            }).then(res => {
                this.publicProxy = res
            })
        },
        methods: {
            ...mapActions(['getRepoInfo', 'updateRepoInfo', 'getPublicProxy']),
            toRepoList () {
                this.$router.push({
                    name: 'repoList'
                })
            },
            toRepoDetail () {
                this.$router.push({
                    name: 'commonList',
                    params: {
                        projectId: this.projectId,
                        repoType: this.repoType
                    },
                    query: {
                        name: this.repoName
                    }
                })
            },
            getRepoInfoHandler () {
                this.isLoading = true
                this.getRepoInfo({
                    projectId: this.projectId,
                    repoName: this.repoName,
                    repoType: this.repoType.toUpperCase()
                }).then(res => {
                    this.repoBaseInfo = {
                        ...res,
                        repoType: res.type.toLowerCase()
                    }
                    this.proxyList = res.configuration.proxy.channelList
                }).finally(() => {
                    this.isLoading = false
                })
            },
            saveBaseInfo () {
                this.repoBaseInfo.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body: {
                        description: this.repoBaseInfo.description
                    }
                }).then(res => {
                    this.getRepoInfoHandler()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                }).finally(() => {
                    this.repoBaseInfo.loading = false
                })
            },
            proxyTabChange (name = 'publicProxy') {
                this.editProxyData = {
                    ...this.editProxyData,
                    proxyType: name,
                    channelId: '',
                    name: '',
                    url: '',
                    ticket: false,
                    username: '',
                    password: ''
                }
                this.$refs.publicProxy && this.$refs.publicProxy.clearError()
                this.$refs.privateProxy && this.$refs.privateProxy.clearError()
            },
            addProxy () {
                this.showProxyDialog = true
                this.proxyTabChange()
                this.editProxyData = {
                    ...this.editProxyData,
                    type: 'add'
                }
            },
            editProxy (row) {
                this.showProxyDialog = true
                this.proxyTabChange('privateProxy')
                this.editProxyData = {
                    ...this.editProxyData,
                    ...row,
                    type: 'edit',
                    ticket: Boolean(row.username && row.username.length)
                }
            },
            deleteProxy (row) {
                this.proxyList.splice(this.proxyList.findIndex(v => v.name === row.name), 1)
            },
            async confirmProxyData () {
                // 添加公有源
                if (this.editProxyData.type === 'add' && this.editProxyData.proxyType === 'publicProxy') {
                    await this.$refs.publicProxy.validate()
                    this.proxyList.push(this.publicProxy.find(v => v.channelId === this.editProxyData.channelId))
                // 添加私有源
                } else if (this.editProxyData.type === 'add' && this.editProxyData.proxyType === 'privateProxy') {
                    await this.$refs.privateProxy.validate()
                    this.proxyList.push({
                        public: false,
                        name: this.editProxyData.name,
                        url: this.editProxyData.url,
                        ...(this.editProxyData.ticket ? {
                            username: this.editProxyData.username,
                            password: this.editProxyData.password
                        } : {})
                    })
                // 编辑私有源
                } else if (this.editProxyData.type === 'edit' && this.editProxyData.proxyType === 'privateProxy') {
                    await this.$refs.privateProxy.validate()
                    this.proxyList.splice(this.proxyList.findIndex(v => v.name === this.editProxyData.name), 1, {
                        public: false,
                        name: this.editProxyData.name,
                        url: this.editProxyData.url,
                        ...(this.editProxyData.ticket ? {
                            username: this.editProxyData.username,
                            password: this.editProxyData.password
                        } : {})
                    })
                }
                this.cancelProxy()
            },
            cancelProxy () {
                this.showProxyDialog = false
                this.proxyTabChange()
            },
            testPrivateProxy () {},
            saveProxy () {
                const names = this.proxyList.map(v => v.name)
                if (names.length !== new Set(names).size) {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('sameProxyExist')
                    })
                    return
                }
                this.editProxyData.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body: {
                        configuration: {
                            ...this.repoBaseInfo.configuration,
                            proxy: {
                                channelList: this.proxyList
                            }
                        }
                    }
                }).then(res => {
                    this.getRepoInfoHandler()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                }).finally(() => {
                    this.editProxyData.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-config-container {
    height: 100%;
    .repo-config-header {
        height: 60px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        font-size: 14px;
        background-color: white;
        .repo-config-operation {
            flex: 1;
            display: flex;
            justify-content: flex-end;
            align-items: center;
        }
    }
    .repo-config-main {
        height: calc(100% - 80px);
        margin-top: 20px;
        padding: 20px;
        display: flex;
        background-color: white;
        .repo-config-tab {
            flex: 1;
            .repo-base-info {
                max-width: 800px;
            }
            .proxy-config-tips {
                color: $fontWeightColor;
            }
            .proxy-item {
                display: flex;
                align-items: center;
                height: 40px;
                line-height: 40px;
                border-bottom: 1px solid $borderColor;
                .proxy-index {
                    font-size: 16px;
                    flex-basis: 50px;
                }
                .proxy-origin {
                    flex:2;
                }
                .proxy-type {
                    flex: 1;
                }
                .proxy-address {
                    flex: 6;
                }
                .proxy-operation {
                    flex:1;
                    .icon-delete {
                        font-size: 16px;
                    }
                }
            }
            .proxy-add {
                cursor: pointer;
                user-select: none;
                margin: 10px;
                color: $primaryColor
            }
        }
    }
}
</style>
