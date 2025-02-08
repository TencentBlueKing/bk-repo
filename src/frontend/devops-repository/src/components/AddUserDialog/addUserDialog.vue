<template>
    <canway-dialog
        v-model="showDialog"
        theme="primary"
        width="800"
        class="update-user-dialog"
        height-num="603"
        :title="$t('add') + $t('space') + $t('user')"
        @cancel="cancel">
        <div style="display: flex">
            <div class="ml10 mr10 mt10" style="width: 50%; text-align: center">
                <div>
                    <div style="align-items: center">
                        <bk-input :type="'textarea'" :placeholder="$t('userGroupPlaceholder')" v-model="editUserConfig.newUser" class="w350 usersTextarea" />
                    </div>
                    <div class="mt5" style="display: flex">
                        <div class="mr10" style="text-align: left">
                            <bk-button style="width: 240px" theme="primary" @click="parseFn">{{ $t('add') }}</bk-button>
                        </div>
                        <div style="text-align: left">
                            <bk-button style="width: 110px" @click="editUserConfig.newUser = ''">{{ $t('clear') }}</bk-button>
                        </div>
                    </div>
                </div>
            </div>
            <div class="ml10 mr10 mt10" style="width: 50%;text-align: center">
                <div style="display: flex">
                    <bk-input v-model="editUserConfig.search" :placeholder="$t('search')" style="width: 280px" @change="filterUsers" />
                    <bk-button class="ml10" theme="primary" @click="copy">{{ $t('copyAll') }}</bk-button>
                </div>
                <div v-show="editUserConfig.users.length" class="mt10 update-user-list">
                    <div class="pl10 pr10 update-user-item flex-between-center" v-for="(user, index) in editUserConfig.users" :key="index">
                        <div class="flex-align-center">
                            <span class="update-user-name text-overflow" :title="user">{{ user }}</span>
                        </div>
                        <Icon class="ml10 hover-btn" size="24" name="icon-delete" @click.native="deleteUser(index)" />
                    </div>
                </div>
            </div>
        </div>
        <template #footer>
            <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" theme="primary" @click="confirm">{{ $t('confirm') }}</bk-button>
        </template>
    </canway-dialog>
</template>

<script>
    import { copyToClipboard } from '@/utils'

    export default {
        name: 'addUserDialog',
        props: {
            visible: Boolean,
            showData: {
                type: Object,
                default: undefined
            }
        },
        data () {
            return {
                showDialog: this.visible,
                editUserConfig: {
                    users: [],
                    search: '',
                    newUser: '',
                    originUsers: []
                }
            }
        },
        watch: {
            visible: function (newVal) {
                if (newVal) {
                    this.showDialog = true
                } else {
                    this.cancel()
                }
            }
        },
        methods: {
            filterUsers () {
                this.editUserConfig.users = this.editUserConfig.originUsers
                if (this.editUserConfig.search !== '') {
                    this.editUserConfig.users = this.editUserConfig.users.filter(user => user.toLowerCase().includes(this.editUserConfig.search.toLowerCase()))
                }
            },
            parseFn () {
                const data = this.editUserConfig.newUser
                if (data !== '') {
                    const temp = []
                    this.editUserConfig.search = ''
                    const users = data.toString().replace(/\n/g, ',').replace(/\s/g, ',').split(',')
                    for (let i = 0; i < users.length; i++) {
                        users[i] = users[i].toString().trim()
                        if (users[i] !== '') {
                            temp.push(users[i])
                        }
                    }
                    for (let i = 0; i < this.editUserConfig.originUsers.length; i++) {
                        temp.push(this.editUserConfig.originUsers[i])
                    }
                    this.editUserConfig.users = Array.from(new Set(temp))
                    this.editUserConfig.originUsers = temp
                    this.editUserConfig.newUser = ''
                }
            },
            deleteUser (index) {
                const temp = []
                for (let i = 0; i < this.editUserConfig.users.length; i++) {
                    if (i !== index) {
                        temp.push(this.editUserConfig.users[i])
                    }
                }
                this.editUserConfig.users = temp
                this.editUserConfig.originUsers = temp
            },
            copy () {
                const text = this.editUserConfig.originUsers.join('\n')
                copyToClipboard(text).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('copy') + this.$t('space') + this.$t('success')
                    })
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('copy') + this.$t('space') + this.$t('fail')
                    })
                })
            },
            cancel () {
                this.showDialog = false
                this.$emit('update:visible', false)
            },
            confirm () {
                this.showDialog = false
                this.$emit('update:visible', false)
                this.$emit('complete', this.editUserConfig.originUsers)
            }
        }
    }
</script>

<style lang="scss" scoped>
.update-user-dialog {
    ::v-deep .usersTextarea .bk-textarea-wrapper .bk-form-textarea{
        height: 500px;
    }
    .update-user-list {
        display: grid;
        grid-template: auto / repeat(1, 1fr);
        gap: 10px;
        max-height: 500px;
        overflow-y: auto;
        .update-user-item {
            height: 32px;
            border: 1px solid var(--borderWeightColor);
            background-color: var(--bgLighterColor);
            .update-user-name {
                max-width: 100px;
                margin-left: 5px;
            }
        }
    }
}

</style>
