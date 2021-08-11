<template>
    <div class="repo-config-container">
        <header class="repo-config-header">
            <div class="flex-center">
                <div class="mr5 hover-btn flex-center" @click="toRepoDetail">
                    <icon size="24" :name="repoType" />
                    <span class="ml10">{{replaceRepoName(repoName)}}</span>
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
            <bk-tab class="repo-config-tab" type="unborder-card" :active.sync="tabName">
                <bk-tab-panel name="baseInfo" :label="$t('repoBaseInfo')">
                    <div class="repo-base-info">
                        <bk-form ref="repoBaseInfo" :label-width="150" :model="repoBaseInfo" :rules="rules">
                            <bk-form-item :label="$t('repoName')">
                                <div class="flex-align-center">
                                    <icon size="24" :name="repoBaseInfo.repoType || repoType" />
                                    <span class="ml10">{{replaceRepoName(repoBaseInfo.name || repoName)}}</span>
                                </div>
                            </bk-form-item>
                            <bk-form-item :label="$t('repoAddress')">
                                <span>{{repoAddress}}</span>
                            </bk-form-item>
                            <bk-form-item :label="$t('publicRepo')" :required="true" property="public">
                                <bk-checkbox v-model="repoBaseInfo.public">{{ repoBaseInfo.public ? $t('publicRepoDesc') : '' }}</bk-checkbox>
                            </bk-form-item>
                            <template v-if="repoType === 'rpm'">
                                <bk-form-item :label="$t('enabledFileLists')">
                                    <bk-checkbox v-model="repoBaseInfo.enabledFileLists"></bk-checkbox>
                                </bk-form-item>
                                <bk-form-item :label="$t('repodataDepth')" property="repodataDepth">
                                    <bk-input v-model.trim="repoBaseInfo.repodataDepth"></bk-input>
                                </bk-form-item>
                                <bk-form-item :label="$t('groupXmlSet')" property="groupXmlSet">
                                    <bk-tag-input
                                        :value="repoBaseInfo.groupXmlSet"
                                        @change="(val) => {
                                            repoBaseInfo.groupXmlSet = val.map(v => {
                                                return v.replace(/^([^.]*)(\.xml)?$/, '$1.xml')
                                            })
                                        }"
                                        :list="[]"
                                        trigger="focus"
                                        :clearable="false"
                                        allow-create
                                        has-delete-icon>
                                    </bk-tag-input>
                                </bk-form-item>
                            </template>
                            <bk-form-item :label="$t('description')">
                                <bk-input type="textarea"
                                    maxlength="200"
                                    v-model.trim="repoBaseInfo.description"
                                    :placeholder="$t('repoDescriptionPlacehodler')">
                                </bk-input>
                            </bk-form-item>
                            <bk-form-item>
                                <bk-button :loading="repoBaseInfo.loading" theme="primary" @click.stop.prevent="saveBaseInfo">{{$t('save')}}</bk-button>
                            </bk-form-item>
                        </bk-form>
                    </div>
                </bk-tab-panel>
                <bk-tab-panel v-if="showProxyConfigTab" name="proxyConfig" :label="$t('proxyConfig')">
                    <proxy-config :base-data="repoBaseInfo" @refresh="getRepoInfoHandler"></proxy-config>
                </bk-tab-panel>
                <bk-tab-panel v-if="MODE_CONFIG !== 'ci'" render-directive="if" name="permissionConfig" :label="$t('permissionConfig')">
                    <permission-config></permission-config>
                </bk-tab-panel>
            </bk-tab>
        </main>
    </div>
</template>
<script>
    import proxyConfig from './proxyConfig'
    import permissionConfig from './permissionConfig'
    import { mapActions } from 'vuex'
    export default {
        name: 'repoConfig',
        components: { proxyConfig, permissionConfig },
        data () {
            return {
                MODE_CONFIG,
                tabName: 'baseInfo',
                isLoading: false,
                repoBaseInfo: {
                    loading: false,
                    repoName: '',
                    public: false,
                    repoType: '',
                    enabledFileLists: false,
                    repodataDepth: 0,
                    groupXmlSet: [],
                    description: ''
                },
                rules: {
                    repodataDepth: [
                        {
                            regex: /^(0|[1-9][0-9]*)$/,
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('repodataDepth'),
                            trigger: 'blur'
                        }
                    ],
                    groupXmlSet: [
                        {
                            validator: arr => {
                                return arr.every(v => {
                                    return /\.xml$/.test(v)
                                })
                            },
                            message: this.$t('pleaseInput') + this.$t('legit') + this.$t('groupXmlSet') + `(.xml${this.$t('type')})`,
                            trigger: 'change'
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
                return !['generic', 'docker', 'helm', 'rpm'].includes(this.repoType)
            },
            repoAddress () {
                return location.origin + `/${this.repoBaseInfo.repoType}/${this.projectId}/${this.repoBaseInfo.name}/`
            }
        },
        created () {
            if (!this.repoName || !this.repoType) this.toRepoList()
            this.getRepoInfoHandler()
        },
        methods: {
            ...mapActions(['getRepoInfo', 'updateRepoInfo']),
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
                        ...this.repoBaseInfo,
                        ...res,
                        ...res.configuration.settings,
                        repoType: res.type.toLowerCase()
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            async saveBaseInfo () {
                const body = {
                    public: this.repoBaseInfo.public,
                    description: this.repoBaseInfo.description
                }
                if (this.repoType === 'rpm') {
                    await this.$refs.repoBaseInfo.validate()
                    body.configuration = {
                        ...this.repoBaseInfo.configuration,
                        settings: {
                            enabledFileLists: this.repoBaseInfo.enabledFileLists,
                            repodataDepth: this.repoBaseInfo.repodataDepth,
                            groupXmlSet: this.repoBaseInfo.groupXmlSet
                        }
                    }
                }
                this.repoBaseInfo.loading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body
                }).then(res => {
                    this.getRepoInfoHandler()
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                }).finally(() => {
                    this.repoBaseInfo.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.repo-config-container {
    height: 100%;
    .repo-config-header {
        height: 50px;
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
        height: calc(100% - 70px);
        margin-top: 20px;
        padding: 20px;
        display: flex;
        background-color: white;
        overflow-y: auto;
        .repo-config-tab {
            flex: 1;
            ::v-deep .bk-tab-section {
                height: calc(100% - 42px);
                overflow-y: auto;
            }
            .repo-base-info {
                max-width: 800px;
            }
        }
    }
}
</style>
