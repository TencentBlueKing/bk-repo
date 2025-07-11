<template>
    <canway-dialog
        v-model="showDialog"
        theme="primary"
        width="800"
        class="import-user-dialog"
        height-num="800"
        :title="$t('import') + $t('space') + $t('userGroup')"
        @cancel="cancel">
        <div>
            <div style="display: flex">
                <bk-input v-model="importTitle" readonly style="width: 290px" :placeholder="$t('pleaseInput')"/>
                <bk-select style="width: 360px"
                    :placeholder="$t('pleaseSelect')"
                    @change="changeImport"
                    v-model="selectedType">
                    <bk-option v-for="option in importType"
                        :key="option.id"
                        :id="option.id"
                        :name="option.name">
                    </bk-option>
                </bk-select>
            </div>
            <bk-divider />
            <div style="display: flex" v-if="selectedType">
                <bk-input v-model="importUserGroupTitle" readonly style="width: 290px" />
                <bk-select style="width: 360px;margin-bottom: 10px"
                    :placeholder="$t('pleaseSelect')"
                    searchable
                    selected-style="checkbox"
                    @change="changeUserGroup"
                >
                    <bk-option v-for="option in importUserGroups"
                        :key="option.roleId"
                        :id="option.roleId"
                        :name="option.name">
                    </bk-option>
                </bk-select>
            </div>
            <div class="import_table" v-if="selectedUserGroup.length > 0 && selectedType">
                <div class="permission-name">{{$t('roleName')}}</div>
                <div class="permission-users">{{$t('user')}}</div>
            </div>
            <draggable v-if="selectedUserGroup.length > 0 && selectedType" :options="{ animation: 200 }">
                <div class="proxy-item" v-for="(row,index) in selectedUserGroup" :key="index">
                    <div class="permission-name">{{row.name}}</div>
                    <div class="permission-users"><bk-tag v-for="(name,userIndex) in row.userList" :key="userIndex">{{ name }}</bk-tag></div>
                </div>
            </draggable>
        </div>
        <template #footer>
            <bk-button @click="cancel">{{ $t('cancel') }}</bk-button>
            <bk-button class="ml10" theme="primary" @click="confirm" :disabled="selectedUserGroup.length === 0 ">{{ $t('confirm') }}</bk-button>
        </template>
    </canway-dialog>
</template>

<script>
    import { mapActions } from 'vuex'

    export default {
        name: 'addUserDialog',
        props: {
            visible: Boolean
        },
        data () {
            return {
                showDialog: this.visible,
                openImport: false,
                importTitle: this.$t('userSource'),
                importUserGroupTitle: this.$t('userGroup'),
                selectedUserGroup: [],
                isLoading: false,
                importType: [
                    {
                        id: 'DEVOPS',
                        name: this.$t('bkci')
                    }
                ],
                selectedType: '',
                importUserGroups: []
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
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
            ...mapActions([
                'getUserGroupByExternal',
                'createRole',
                'editRole'
            ]),
            cancel () {
                this.selectedType = ''
                this.selectedUserGroup = []
                this.showDialog = false
                this.$emit('update:visible', false)
            },
            confirm () {
                this.showDialog = false
                const body = {
                    roleId: this.selectedUserGroup[0].roleId,
                    name: this.selectedUserGroup[0].name,
                    type: 'PROJECT',
                    projectId: this.projectId,
                    admin: false,
                    description: '',
                    source: this.selectedType
                }
                this.createRole({ body: body }).then(res => {
                    this.editRole({
                        id: res,
                        body: {
                            userIds: this.selectedUserGroup[0].userList
                        }
                    }).then(_ => {
                        this.$bkMessage({
                            theme: 'success',
                            message: this.$t('import') + this.$t('space') + this.$t('success')
                        })
                        this.$emit('update:visible', false)
                        this.$emit('complete')
                    })
                })
            },
            changeImport () {
                if (this.selectedType) {
                    this.getUserGroupByExternal({
                        projectId: this.projectId,
                        sourceId: this.selectedType
                    }).then(res => {
                        this.importUserGroups = res
                    })
                } else {
                    this.selectedUserGroup = []
                }
            },
            changeUserGroup (newVal, oldVal) {
                this.selectedUserGroup = []
                for (let i = 0; i < this.importUserGroups.length; i++) {
                    if (newVal.includes(this.importUserGroups[i].roleId)) {
                        this.selectedUserGroup.push(this.importUserGroups[i])
                    }
                }
            }
        }
    }
</script>

<style lang="scss" scoped>
.import-user-dialog {
    .proxy-item,
    .import_table {
        display: flex;
        align-items: center;
        height: auto;
        min-height: 40px;
        border-bottom: 1px solid var(--borderColor);
        .permission-index {
            flex-basis: 50px;
        }
        .permission-name {
            flex:4;
        }
        .permission-users {
            flex: 6;
        }
    }
    .import_table {
        color: var(--fontSubsidiaryColor);
        background-color: var(--bgColor);
    }
    .proxy-item {
        cursor: move;
    }
}
</style>
