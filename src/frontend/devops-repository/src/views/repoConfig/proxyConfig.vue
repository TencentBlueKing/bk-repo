<template>
    <div class="proxy-config-container">
        <div class="mb10 flex-between-center">
            <bk-button icon="plus" theme="primary" @click="addProxy"><span class="mr5">{{ $t('add') }}</span></bk-button>
            <span class="proxy-config-tips">{{$t('proxyConfigTips')}}</span>
        </div>
        <div class="proxy-head">
            <div class="proxy-index"></div>
            <div class="proxy-origin">{{$t('name')}}</div>
            <div class="proxy-type">{{$t('type')}}</div>
            <div class="proxy-address">{{$t('address')}}</div>
            <div class="proxy-operation">{{$t('operation')}}</div>
        </div>
        <draggable v-if="proxyList.length" v-model="proxyList" :options="{ animation: 200 }" @update="debounceSaveProxy">
            <div class="proxy-item" v-for="proxy in proxyList" :key="proxy.name + Math.random()">
                <div class="proxy-index flex-center">
                    <Icon name="drag" size="16" />
                </div>
                <div class="proxy-origin">{{proxy.name}}</div>
                <div class="proxy-type">{{proxy.public ? $t('publicProxy') : $t('privateProxy')}}</div>
                <div class="proxy-address">{{proxy.url}}</div>
                <div class="flex-align-center proxy-operation">
                    <i v-if="!proxy.public" class="mr10 devops-icon icon-edit hover-btn" @click.stop.prevent="editProxy(proxy)"></i>
                    <i class="devops-icon icon-delete hover-btn hover-danger" @click.stop.prevent="deleteProxy(proxy)"></i>
                </div>
            </div>
        </draggable>
        <empty-data v-else ex-style="margin-top:80px;"
            :config="{
                imgSrc: '/ui/no-proxy.png',
                title: '暂无代理源配置',
                subTitle: '请尝试添加公有代理源或配置私有代理源'
            }">
        </empty-data>
        <proxy-origin-dialog :show="showProxyDialog" :public-proxy="filterPublicProxy" :proxy-data="proxyData" @confirm="confirmProxyData" @cancel="cancelProxy"></proxy-origin-dialog>
    </div>
</template>
<script>
    import draggable from 'vuedraggable'
    import proxyOriginDialog from './proxyOriginDialog'
    import { mapActions } from 'vuex'
    import { debounce } from '@repository/utils'
    export default {
        name: 'proxyConfig',
        components: { draggable, proxyOriginDialog },
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
                publicProxy: [],
                debounceSaveProxy: null
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
            },
            filterPublicProxy () {
                return this.publicProxy.filter(v => {
                    return !this.proxyList.find(w => w.channelId === v.channelId)
                })
            }
        },
        watch: {
            baseData () {
                this.proxyList = this.baseData.configuration.proxy.channelList
            }
        },
        created () {
            this.getPublicProxy({
                repoType: this.repoType
            }).then(res => {
                this.publicProxy = res.map(v => {
                    return {
                        ...v,
                        channelId: v.id
                    }
                })
            })
            this.debounceSaveProxy = debounce(this.saveProxy)
        },
        methods: {
            ...mapActions(['updateRepoInfo', 'getPublicProxy']),
            addProxy () {
                this.showProxyDialog = true
                this.proxyData = {
                    type: 'add'
                }
            },
            editProxy (row) {
                this.showProxyDialog = true
                this.proxyData = {
                    ...row,
                    type: 'edit',
                    ticket: Boolean(row.username && row.username.length)
                }
            },
            deleteProxy (row) {
                this.proxyList.splice(this.proxyList.findIndex(v => v.name === row.name), 1)
                this.debounceSaveProxy()
            },
            confirmProxyData ({ name, data }) {
                // 添加公有源
                if (data.type === 'add' && data.proxyType === 'publicProxy') {
                    this.proxyList.push(this.publicProxy.find(v => v.channelId === data.channelId))
                // 添加私有源
                } else if (data.type === 'add' && data.proxyType === 'privateProxy') {
                    this.proxyList.push({
                        public: false,
                        name: data.name,
                        url: data.url,
                        ...(data.ticket
                            ? {
                                username: data.username,
                                password: data.password
                            }
                            : {})
                    })
                // 编辑私有源
                } else if (data.type === 'edit' && data.proxyType === 'privateProxy') {
                    this.proxyList.splice(this.proxyList.findIndex(v => v.name === name), 1, {
                        public: false,
                        name: data.name,
                        url: data.url,
                        ...(data.ticket
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
                }).then(res => {
                    this.$emit('refresh')
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                }).finally(() => {
                    this.saveLoading = false
                })
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
        background-color: #3a84ff1a;
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
}
</style>
