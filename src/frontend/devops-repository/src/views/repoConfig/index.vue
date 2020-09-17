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
                                    <icon size="24" :name="repoBaseInfo.type || repoType" />
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
                <!-- <bk-tab-panel name="proxyConfig" :label="$t('proxyConfig')" v-if="repoType !== 'generic'">
                    <span class="proxy-config-tips">{{$t('proxyConfigTips')}}</span>
                    <div class="proxy-item">
                        <div class="proxy-index"></div>
                        <div class="proxy-origin">{{$t('origin')}}</div>
                        <div class="proxy-address">{{$t('address')}}</div>
                        <div class="proxy-operation">{{$t('operation')}}</div>
                    </div>
                    <draggable v-model="proxyList" :move="checkMove" :options="{ animation: 200 }">
                        <div class="proxy-item" v-for="proxy in proxyList" :key="proxy.origin">
                            <div class="proxy-index flex-align-center">
                                <i class="devops-icon icon-more"></i>
                                <i class="devops-icon icon-more" style="margin-left:-5px"></i>
                            </div>
                            <div class="proxy-origin">{{proxy.origin}}</div>
                            <div class="proxy-address">{{proxy.address}}</div>
                            <div class="flex-align-center proxy-operation">
                                <i class="mr10 devops-icon icon-edit hover-btn" @click.stop.prevent="editProxy(proxy)"></i>
                                <i class="devops-icon icon-delete hover-btn" @click.stop.prevent="deleteProxy(proxy)"></i>
                            </div>
                        </div>
                    </draggable>
                    <div class="proxy-add flex-align-center" @click="addProxy">
                        <i class="mr10 devops-icon icon-plus-square"></i>
                        <span>{{$t('addProxy')}}</span>
                    </div>
                    <bk-button class="mt20 ml20" :loading="editProxyData.loading" theme="primary" @click.stop.prevent="saveProxy">{{$t('save')}}</bk-button>
                </bk-tab-panel> -->
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
                :active="editProxyData.proxyType"
                @tab-change="proxyTabChange"
                type="unborder-card">
                <bk-tab-panel v-for="tab in ['public', 'private']"
                    :key="tab" :name="tab" :label="$t(`${tab}Proxy`)">
                    <bk-form :label-width="100" :model="editProxyData" :rules="rules" :ref="tab">
                        <bk-form-item :label="$t('address')" :required="true" property="address" error-display-type="normal">
                            <bk-select v-if="tab === 'public'" v-model="editProxyData.address">
                                <bk-option
                                    v-for="option in addressList"
                                    :key="option"
                                    :id="option"
                                    :name="option">
                                </bk-option>
                            </bk-select>
                            <bk-input v-else-if="tab === 'private'" v-model="editProxyData.address"></bk-input>
                        </bk-form-item>
                        <bk-form-item :label="$t('origin')" :required="true" property="origin" error-display-type="normal">
                            <bk-input v-model="editProxyData.origin"></bk-input>
                        </bk-form-item>
                        <bk-form-item v-if="tab === 'private'" :label="$t('ticket')">
                            <bk-select v-model="editProxyData.ticket">
                                <bk-option
                                    v-for="option in [1, 2, 3]"
                                    :key="option"
                                    :id="option"
                                    :name="option">
                                </bk-option>
                            </bk-select>
                        </bk-form-item>
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
    // import draggable from 'vuedraggable'
    import { mapActions } from 'vuex'
    export default {
        name: 'repoConfig',
        // components: { draggable },
        data () {
            return {
                isLoading: false,
                repoBaseInfo: {
                    loading: false,
                    name: this.$route.query.name,
                    type: this.$route.params.type,
                    description: ''
                },
                publicProxy: [
                    {
                        'id': '5f48b52fdf23460c0e2251e9',
                        'public': true,
                        'name': 'maven-center',
                        'url': 'http://http://center.maven.com',
                        'repoType': 'MAVEN'
                    }
                ],
                proxyList: [
                    {
                        id: 1,
                        origin: 'cnpm',
                        address: 'https://registry.npm.taobao.org/',
                        proxyType: 'public'
                    },
                    {
                        id: 2,
                        origin: 'npmjs',
                        address: 'https://registry.npmjs.org/',
                        proxyType: 'public'
                    },
                    {
                        id: 3,
                        origin: 'yarn',
                        address: 'https://registry.yarnpkg.com',
                        proxyType: 'public'
                    },
                    {
                        id: 4,
                        origin: 'tencent',
                        address: 'https://registry.tencent.com',
                        proxyType: 'private'
                    },
                    {
                        id: 5,
                        origin: 'canway',
                        address: 'https://registry.canway.com',
                        proxyType: 'private'
                    }
                ],
                addressList: ['https://registry.npm.taobao.org/', 'https://registry.yarnpkg.com'],
                showProxyDialog: false,
                editProxyData: {
                    loading: false,
                    type: '',
                    origin: '',
                    address: '',
                    proxyType: '',
                    ticket: ''
                },
                rules: {
                    address: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('address'),
                            trigger: 'blur'
                        }
                    ],
                    origin: [
                        {
                            required: true,
                            message: this.$t('pleaseInput') + this.$t('origin'),
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
                return this.$route.params.type
            },
            repoAddress () {
                return location.origin + `/${this.repoBaseInfo.type}/${this.projectId}/${this.repoBaseInfo.name}/`
            }
        },
        created () {
            if (!this.repoName || !this.repoType) this.toRepoList()
            this.getRepoInfoHandler()
            // this.getPublicProxy({ type: this.repoType.toUpperCase() }).then(res => {
            //     this.publicProxy = res
            // })
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
                    name: this.repoType,
                    query: {
                        name: this.repoName
                    }
                })
            },
            getRepoInfoHandler () {
                this.isLoading = true
                this.getRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    type: this.repoType.toUpperCase()
                }).then(res => {
                    this.repoBaseInfo = {
                        ...res,
                        type: res.type.toLowerCase()
                    }
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
            proxyTabChange (name) {
                this.editProxyData.proxyType = name
                this.editProxyData.address = ''
                this.$refs[this.editProxyData.proxyType][0].clearError()
            },
            addProxy () {
                this.showProxyDialog = true
                this.editProxyData = {
                    type: 'add',
                    proxyType: 'public',
                    origin: '',
                    address: '',
                    ticket: ''
                }
                this.$refs.public[0].clearError()
            },
            editProxy (row) {
                this.showProxyDialog = true
                this.editProxyData = {
                    type: 'edit',
                    ...row
                }
            },
            deleteProxy (row) {
                this.proxyList.splice(this.proxyList.findIndex(v => v.id === row.id), 1)
            },
            async confirmProxyData () {
                await this.$refs[this.editProxyData.proxyType][0].validate()
                if (!this.editProxyData.id) {
                    this.proxyList.push({
                        id: Math.random(),
                        origin: this.editProxyData.origin,
                        address: this.editProxyData.address,
                        proxyType: this.editProxyData.proxyType,
                        ticket: this.editProxyData.ticket
                    })
                } else {
                    this.proxyList.splice(this.proxyList.findIndex(v => v.id === this.editProxyData.id), 1, {
                        id: this.editProxyData.id,
                        origin: this.editProxyData.origin,
                        address: this.editProxyData.address,
                        proxyType: this.editProxyData.proxyType,
                        ticket: this.editProxyData.ticket
                    })
                }
                this.cancelProxy()
            },
            cancelProxy () {
                this.showProxyDialog = false
                this.$refs[this.editProxyData.proxyType][0].clearError()
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
                    flex:3;
                }
                .proxy-address {
                    flex: 6;
                }
                .proxy-operation {
                    flex:1;
                    .icon-delete {
                        font-size: 14px;
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
