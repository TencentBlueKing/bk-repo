<template>
    <div class="user-info-container">
        <bk-tab class="user-manage-tab page-tab" type="unborder-card" :active.sync="tabName">
            <bk-tab-panel name="user" :label="$t('baseInfo')" v-if="!ciMode">
                <bk-form class="mt50" :label-width="180">
                    <bk-form-item v-for="item in formItem" :key="item.key" :label="item.label">
                        <div v-if="!editItem.key || editItem.key !== item.key" class="flex-align-center">
                            <span>{{ transPrivacy(userInfo[item.key], item.label)}}</span>
                            <bk-button class="ml20 flex-align-center"
                                v-if="!editItem.key"
                                text
                                @click="editUserInfo(item)">
                                {{$t('modify')}}
                            </bk-button>
                        </div>
                        <div v-else class="flex-align-center">
                            <bk-input class="w250" v-focus v-model.trim="editItem.value" maxlength="32" show-word-limit></bk-input>
                            <bk-button class="ml10" theme="default" @click="cancelEdit">{{$t('cancel')}}</bk-button>
                            <bk-button class="ml10" theme="primary" @click="confirmEdit">{{$t('confirm')}}</bk-button>
                        </div>
                    </bk-form-item>
                    <bk-form-item :label="$t('password') + '：'">
                        <div class="flex-align-center">
                            <span>******</span>
                            <bk-button class="ml20 flex-align-center"
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
    import { mapState, mapActions } from 'vuex'
    import userRelated from './userRelated'
    import modifyPasswordDialog from '@repository/views/userCenter/modifyPasswordDialog'
    import { transformEmail, transformPhone } from '@repository/utils/privacy'
    export default {
        name: 'userInfo',
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
                ciMode: MODE_CONFIG === 'ci'
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
            editUserInfo (item) {
                this.editItem = {
                    key: item.key,
                    value: this.userInfo[item.key]
                }
            },
            confirmEdit () {
                this.editUser({
                    body: {
                        userId: this.userInfo.username,
                        [this.editItem.key]: this.editItem.value
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('edit') + this.$t('success')
                    })
                    this.cancelEdit()
                    this.getUserInfo({
                        userId: this.userInfo.username
                    })
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
