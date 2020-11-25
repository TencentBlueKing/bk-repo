<template>
    <div class="proxy-config-container">
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
                    <i v-if="!proxy.public" class="mr10 devops-icon icon-edit hover-btn" @click.stop.prevent="editProxy(proxy)"></i>
                    <i class="devops-icon icon-delete hover-btn" @click.stop.prevent="deleteProxy(proxy)"></i>
                </div>
            </div>
        </draggable>
        <div class="proxy-add flex-align-center" @click="addProxy">
            <i class="mr10 devops-icon icon-plus-square"></i>
            <span>{{$t('addProxy')}}</span>
        </div>
        <bk-button class="mt20 ml20" :loading="saveLoading" theme="primary" @click.stop.prevent="saveProxy">{{$t('save')}}</bk-button>
        <proxy-origin-dialog :show="showProxyDialog" :public-proxy="filterPublicProxy" :proxy-data="proxyData" @confirm="confirmProxyData" @cancel="cancelProxy"></proxy-origin-dialog>
    </div>
</template>
<script>
    import draggable from 'vuedraggable'
    import proxyOriginDialog from './proxyOriginDialog'
    import { mapActions } from 'vuex'
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
                publicProxy: []
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
            filterPublicProxy () {
                return this.publicProxy.filter(v => {
                    return !this.proxyList.find(w => w.channelId === v.channelId)
                })
            }
        },
        watch: {
            baseData (val) {
                this.proxyList = this.baseData.configuration.proxy.channelList
            }
        },
        created () {
            this.getPublicProxy({
                repoType: this.repoType.toUpperCase()
            }).then(res => {
                this.publicProxy = res.map(v => {
                    return {
                        ...v,
                        channelId: v.id
                    }
                })
            })
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
            },
            async confirmProxyData ({ name, data }) {
                // 添加公有源
                if (data.type === 'add' && data.proxyType === 'publicProxy') {
                    this.proxyList.push(this.publicProxy.find(v => v.channelId === data.channelId))
                // 添加私有源
                } else if (data.type === 'add' && data.proxyType === 'privateProxy') {
                    this.proxyList.push({
                        public: false,
                        name: data.name,
                        url: data.url,
                        ...(data.ticket ? {
                            username: data.username,
                            password: data.password
                        } : {})
                    })
                // 编辑私有源
                } else if (data.type === 'edit' && data.proxyType === 'privateProxy') {
                    this.proxyList.splice(this.proxyList.findIndex(v => v.name === name), 1, {
                        public: false,
                        name: data.name,
                        url: data.url,
                        ...(data.ticket ? {
                            username: data.username,
                            password: data.password
                        } : {})
                    })
                }
                this.cancelProxy()
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
@import '@/scss/conf';
.proxy-config-container {
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
</style>
