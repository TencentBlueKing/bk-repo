<template>
    <div class="repo-token-container">
        <header class="repo-token-header">
            <div class="flex-center">
                {{ $t('token') }}
            </div>
            <div class="repo-token-operation">
                <bk-button theme="default" @click="toRepo">
                    {{$t('returnBack')}}
                </bk-button>
            </div>
        </header>
        <main class="repo-token-main" v-bkloading="{ isLoading }">
            <div class="mb20 repo-token-operation">
                <bk-button theme="primary" @click="createToken">{{ $t('createToken') }}</bk-button>
            </div>
            <bk-table
                :data="tokenList"
                height="calc(100% - 52px)"
                stripe
                :outer-border="false"
                :row-border="false"
                size="small"
            >
                <bk-table-column :label="$t('name')" prop="name"></bk-table-column>
                <bk-table-column :label="$t('createdDate')">
                    <template slot-scope="props">
                        {{ formatDate(props.row.createdAt) }}
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('expiress')">
                    <template slot-scope="props">
                        {{ transformFormatDate(props.row.expiredAt) }}
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('operation')" width="100">
                    <template slot-scope="props">
                        <i class="hover-btn devops-icon icon-delete" @click="deleteTokenHandler(props.row)"></i>
                    </template>
                </bk-table-column>
            </bk-table>
        </main>
        <create-token-dialog ref="createToken" @refresh="getToken"></create-token-dialog>
    </div>
</template>
<script>
    import createTokenDialog from './createTokenDialog'
    import { formatDate } from '@/utils'
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'repoToken',
        components: { createTokenDialog },
        data () {
            return {
                isLoading: false,
                tokenList: []
            }
        },
        computed: {
            ...mapState(['userInfo'])
        },
        watch: {
            userInfo () {
                this.getToken()
            }
        },
        created () {
            this.userInfo.username && this.getToken()
        },
        methods: {
            formatDate,
            ...mapActions(['getTokenList', 'deleteToken']),
            transformFormatDate (time) {
                if (!time) {
                    return this.$t('neverExpires')
                } else if (new Date(time) < new Date()) {
                    return this.$t('expired')
                } else {
                    return formatDate(time)
                }
            },
            toRepo () {
                const route = this.$route
                const projectId = route.params.projectId
                const repositoryHistory = JSON.parse(localStorage.getItem('repositoryHistory') || '{}')[projectId] || { type: 'generic', name: 'custom' }
                this.$router.push({
                    name: 'repoCommon',
                    params: {
                        ...route.params,
                        repoType: repositoryHistory.type
                    },
                    query: {
                        name: repositoryHistory.name
                    }
                })
            },
            getToken () {
                this.isLoading = true
                this.getTokenList({
                    username: this.userInfo.username
                }).then(list => {
                    this.tokenList = list
                }).finally(() => {
                    this.isLoading = false
                })
            },
            deleteTokenHandler (row) {
                this.$bkInfo({
                    type: 'error',
                    title: this.$t('deleteTokenTitle'),
                    showFooter: true,
                    confirmFn: () => {
                        this.deleteToken({
                            username: this.userInfo.username,
                            name: row.name
                        }).then(data => {
                            this.getToken()
                            this.$bkMessage({
                                theme: 'success',
                                message: this.$t('delete') + this.$t('success')
                            })
                        })
                    }
                })
            },
            createToken () {
                this.$refs.createToken.showDialogHandler()
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.repo-token-container {
    height: 100%;
    .repo-token-header {
        height: 60px;
        padding: 0 20px;
        display: flex;
        align-items: center;
        font-size: 14px;
        background-color: white;
    }
    .repo-token-operation {
        flex: 1;
        display: flex;
        justify-content: flex-end;
        align-items: center;
    }
    .repo-token-main {
        height: calc(100% - 80px);
        margin-top: 20px;
        padding: 20px;
        background-color: white;
        .icon-delete {
            font-size: 16px;
        }
    }
}
</style>
