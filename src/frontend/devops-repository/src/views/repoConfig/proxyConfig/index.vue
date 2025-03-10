<template>
    <div class="proxy-config-container">
        <div class="mb10 flex-between-center">
            <div class="flex-center">
                <bk-button icon="plus" theme="primary" @click="addProxy">{{ $t('add') }}</bk-button>
                <span style="margin-left: 10px" class="package-card-description text-overflow">{{ $t('totalProxyCount', [proxyList.length]) }}</span>
            </div>
            <span class="proxy-config-tips">{{$t('proxyConfigTips')}}</span>
        </div>
        <div class="proxy-card-list">
            <!-- 有数据 -->
            <template v-if="proxyList.length">
                <infinite-scroll
                    ref="infiniteScroll"
                    :is-loading="isLoading"
                    :has-next="proxyList.length < pagination.count">
                    <proxy-card
                        class="mb10"
                        v-for="proxy in proxyList"
                        :key="proxy.name"
                        :card-data="proxy"
                        :sync-status="syncRecord"
                        @refresh-sync-record="refreshSyncRecord"
                        @delete-card="deleteProxy(proxy)"
                        @update-card="editProxy(proxy)"
                    >
                    </proxy-card>
                </infinite-scroll>
            </template>
            <!-- 无数据 -->
            <template v-else>
                <empty-data ex-style="margin-top:130px;" :title="$t('noProxySourceConfigTitle')" :sub-title="$t('noProxySourceConfigSubTitle')"></empty-data>
            </template>
        </div>
        <proxy-origin-dialog
            :show="showProxyDialog"
            :name-list="nameList"
            :proxy-data="proxyData"
            :repo-type="repoType"
            @confirm="confirmProxyData"
            @cancel="cancelProxy"
        ></proxy-origin-dialog>
    </div>
</template>
<script>
    import proxyOriginDialog from './proxyOriginDialog'
    import { mapActions } from 'vuex'
    import { debounce } from '@repository/utils'
    import proxyCard from '@repository/components/ProxyCard/index'
    import InfiniteScroll from '@repository/components/InfiniteScroll/index'
    export default {
        name: 'ProxyConfig',
        components: { InfiniteScroll, proxyCard, proxyOriginDialog },
        props: {
            baseData: Object
        },
        data () {
            return {
                showProxyDialog: false,
                saveLoading: false,
                // 当前仓库的代理源
                proxyList: [],
                proxyData: {},
                debounceSaveProxy: null,
                pagination: {
                    current: 1,
                    limit: 10000,
                    count: 0,
                    limitList: [10, 20, 40]
                },
                isLoading: false,
                syncRecord: undefined,
                nameList: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            repoType () {
                return this.$route.params.repoType
            }
        },
        watch: {
            baseData: {
                handler (val) {
                    this.proxyList = val.configuration && val.configuration.proxy ? val.configuration.proxy.channelList : []
                    this.nameList = this.proxyList.length > 0 ? this.proxyList.map(v => v.name) : []
                },
                deep: true,
                immediate: true
            }
        },
        created () {
            this.debounceSaveProxy = debounce(this.saveProxy)
        },
        methods: {
            ...mapActions(['updateRepoInfo', 'listRepoProxyChannel']),
            addProxy () {
                this.showProxyDialog = true
                this.proxyData = {
                    type: 'add'
                }
            },
            editProxy (row) {
                this.showProxyDialog = true
                this.proxyData = {
                    type: 'edit',
                    ...row
                }
            },
            deleteProxy (row) {
                this.proxyList.splice(this.proxyList.findIndex(v => v.name === row.name), 1)
                this.debounceSaveProxy()
            },
            confirmProxyData ({ name, data }) {
                // 添加
                if (data.type === 'add') {
                    this.proxyList.push({
                        public: data.proxyType === 'publicProxy',
                        name: data.name,
                        url: data.url,
                        ...(data.username
                            ? {
                                username: data.username,
                                password: data.password
                            }
                            : {})
                    })
                // 编辑
                } else if (data.type === 'edit') {
                    this.proxyList.splice(this.proxyList.findIndex(v => v.name === name), 1, {
                        public: data.proxyType === 'publicProxy',
                        name: data.name,
                        url: data.url,
                        ...(data.username
                            ? {
                                username: data.username,
                                password: data.password
                            }
                            : {})
                    })
                }
                this.cancelProxy()
                this.debounceSaveProxy()
            },
            cancelProxy () {
                this.showProxyDialog = false
            },
            saveProxy () {
                const names = this.proxyList.map(v => v.name)
                if (names.length !== new Set(names).size) {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('sameProxyExist')
                    })
                    return
                }
                if (this.saveLoading) return
                this.saveLoading = true
                this.updateRepoInfo({
                    projectId: this.projectId,
                    name: this.repoName,
                    body: {
                        configuration: {
                            ...this.baseData.configuration,
                            proxy: {
                                channelList: this.proxyList
                            }
                        }
                    }
                }).then(() => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('space') + this.$t('success')
                    })
                }).finally(() => {
                    this.saveLoading = false
                })
            },
            getProxyChannelInfo () {
                this.listRepoProxyChannel({ type: this.repoType, projectId: this.projectId, repoName: this.repoName }).then(res => {
                    this.syncRecord = res
                })
            },
            refreshSyncRecord () {
                this.$bkMessage({
                    theme: 'success',
                    message: this.$t('syncRepo') + this.$t('space') + this.$t('success')
                })
                this.getProxyChannelInfo()
            }
        }
    }
</script>
<style lang="scss" scoped>
.proxy-config-container {
    .proxy-config-tips {
        padding: 5px 20px;
        font-size: 12px;
        color: var(--primaryHoverColor);
        background-color: var(--bgHoverLighterColor);
    }
    .proxy-item,
    .proxy-head {
        display: flex;
        align-items: center;
        height: 40px;
        line-height: 40px;
        border-bottom: 1px solid var(--borderColor);
        .proxy-index {
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
        }
    }
    .proxy-head {
        color: var(--fontSubsidiaryColor);
        background-color: var(--bgColor);
    }
    .proxy-item {
        cursor: move;
    }
    .proxy-card-list {
        height: calc(100% - 120px);
        padding: 0 20px;
        background-color: white;
        .list-count {
            font-size: 12px;
            color: var(--fontSubsidiaryColor);
        }
    }
}
</style>
