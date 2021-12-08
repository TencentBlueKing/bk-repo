<template>
    <div class="repo-token-container" v-bkloading="{ isLoading }">
        <bk-button class="ml20 mt10" icon="plus" theme="primary" @click="createToken"><span class="mr5">{{ $t('create') }}</span></bk-button>
        <bk-table
            class="mt10"
            :data="tokenList"
            height="calc(100% - 52px)"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading">
                    <span class="ml10">暂无个人令牌数据，</span>
                    <bk-button text @click="createToken">即刻创建</bk-button>
                </empty-data>
            </template>
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
                    <i class="devops-icon icon-delete hover-btn hover-danger" @click="deleteTokenHandler(props.row)"></i>
                </template>
            </bk-table-column>
        </bk-table>
        <create-token-dialog ref="createToken" @refresh="getToken"></create-token-dialog>
    </div>
</template>
<script>
    import createTokenDialog from './createTokenDialog'
    import { formatDate } from '@repository/utils'
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
            deleteTokenHandler ({ name }) {
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('deleteTokenTitle', { name }),
                    confirmFn: () => {
                        return this.deleteToken({
                            username: this.userInfo.username,
                            name
                        }).then(() => {
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
.repo-token-container {
    height: 100%;
    background-color: white;
}
</style>
