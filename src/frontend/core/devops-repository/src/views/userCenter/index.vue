<template>
    <div class="user-info-container">
        <bk-tab class="user-manage-tab page-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="user" :label="$t('baseInfo')" v-if="!ciMode">
                <bk-form class="mt50" :label-width="180">
                    <bk-form-item v-for="item in formItem" :key="item.key" :label="item.label">
                        <div v-if="!editItem.key || editItem.key !== item.key" class="flex-align-center">
                            <span v-if="item.key !== 'name'">{{ transPrivacy(userInfo[item.key], item.label)}}</span>
                            <bk-user-display-name v-else-if="multiMode" ref="displayName" :user-id="userInfo['name']"></bk-user-display-name>
                            <span v-else>{{ transPrivacy(userInfo[item.key], item.label)}}</span>
                            <bk-button
                                class="ml20 flex-align-center"
                                v-if="!editItem.key"
                                text
                                @click="editUserInfo(item)">
                                {{$t('modify')}}
                            </bk-button>
                        </div>
                        <div v-else class="flex-align-center">
                            <bk-input class="w250" v-focus v-model.trim="editItem.value" maxlength="32" show-word-limit :placeholder="$t('pleaseInput')"></bk-input>
                            <bk-button class="ml10" theme="default" @click="cancelEdit">{{$t('cancel')}}</bk-button>
                            <bk-button class="ml10" theme="primary" @click="confirmEdit">{{$t('confirm')}}</bk-button>
                        </div>
                    </bk-form-item>
                    <bk-form-item :label="$t('password') + '：'">
                        <div class="flex-align-center">
                            <span>******</span>
                            <bk-button
                                class="ml20 flex-align-center"
                                v-if="!editItem.key"
                                text
                                @click="showModifyPwd()">
                                {{$t('modify')}}
                            </bk-button>
                        </div>
                    </bk-form-item>
                </bk-form>
                <modify-password-dialog ref="modifyPassword"></modify-password-dialog>
            </bk-tab-panel>
            <bk-tab-panel render-directive="if" name="userRelated" :label="$t('relatedUsers')" v-if="userInfo.manage">
                <userRelated />
            </bk-tab-panel>
        </bk-tab>
    </div>
</template>
<script>
    import { transformEmail, transformPhone } from '@repository/utils/privacy'
    import modifyPasswordDialog from '@repository/views/userCenter/modifyPasswordDialog'
    import { mapActions, mapState } from 'vuex'
    import userRelated from './userRelated'
    export default {
        name: 'UserInfo',
        components: { userRelated, modifyPasswordDialog },
        directives: {
            focus: {
                inserted (el) {
                    el.querySelector('input').focus()
                }
            }
        },
        data () {
            return {
                formItem: [
                    { label: this.$t('chineseName'), key: 'name' },
                    { label: this.$t('telephone'), key: 'phone' },
                    { label: this.$t('email'), key: 'email' }
                ],
                editItem: {
                    key: '',
                    value: ''
                },
                tabName: 'user',
                ciMode: MODE_CONFIG === 'ci',
                multiMode: BK_REPO_ENABLE_MULTI_TENANT_MODE === 'true'
            }
        },
        computed: {
            ...mapState(['userInfo'])
        },
        methods: {
            ...mapActions([
                'logout',
                'editUser',
                'getUserInfo'
            ]),
            async editUserInfo (item) {
                if (item.key === 'name' && this.multiMode) {
                    const displayName = this.$refs.displayName[0].innerText
                    this.editItem = {
                        key: item.key,
                        value: displayName
                    }
                    return
                }
                this.editItem = {
                    key: item.key,
                    value: this.userInfo[item.key]
                }
            },
            confirmEdit () {
                const originUserId = this.userInfo.username
                this.editUser({
                    body: {
                        userId: originUserId,
                        [this.editItem.key]: this.editItem.value
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('edit') + this.$t('success')
                    })
                    this.cancelEdit()
                    this.getUserInfo(originUserId)
                })
            },
            cancelEdit () {
                this.editItem = {
                    key: '',
                    value: ''
                }
            },
            showModifyPwd () {
                this.$refs.modifyPassword.userId = this.userInfo.username
                this.$refs.modifyPassword.showDialogHandler()
            },
            transPrivacy (key, label) {
                if (key === null || key === '') return '/'
                if (label.toString() === this.$t('email')) {
                    return transformEmail(key)
                } else if (label.toString() === this.$t('telephone')) {
                    return transformPhone(key)
                } else {
                    return key
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.user-info-container {
    background-color: white;
    .user-manage-tab {
        height: 100%;
        ::v-deep .bk-tab-section {
            height: calc(100% - 60px);
            padding: 0;
            .bk-tab-content {
                height: 100%;
                overflow: hidden;
            }
        }
    }
}
</style>
