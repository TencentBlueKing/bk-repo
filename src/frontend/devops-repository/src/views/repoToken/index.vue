<template>
    <div class="repo-token-container" v-bkloading="{ isLoading }">
        <bk-button class="ml20 mt10" icon="plus" theme="primary" @click="createToken">{{ $t('newToken') }}</bk-button>
        <bk-table
            class="mt10"
            :data="tokenList"
            height="calc(100% - 50px)"
            :outer-border="false"
            :row-border="false"
            size="small">
            <template #empty>
                <empty-data :is-loading="isLoading"></empty-data>
            </template>
            <bk-table-column :label="$t('name')" prop="name" show-overflow-tooltip></bk-table-column>
            <bk-table-column :label="$t('createdDate')">
                <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('expire')">
                <template #default="{ row }">{{ transformFormatDate(row.expiredAt) }}</template>
            </bk-table-column>
            <bk-table-column :label="$t('operation')" width="100">
                <template #default="{ row }">
                    <Icon class="hover-btn" size="24" name="icon-delete"
                        @click.native.stop="deleteTokenHandler(row)" />
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
                this.$refs.createToken.userName = this.userInfo.username
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
