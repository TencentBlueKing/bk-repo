<template>
    <bk-collapse class="permission-config-container" v-model="activeName" v-bkloading="{ isLoading }">
        <bk-collapse-item v-for="section in [admin, user]" :key="section.name" :name="section.name">
            <header class="section-header">
                <div class="flex-align-center">
                    <Icon class="mr10" size="20" :name="section.icon" />
                    <span>{{ section.title }}</span>
                    <span class="mr5 permission-actions">（{{ getActions(section.name) }}）</span>
                    <Icon v-if="section === user" class="hover-btn" size="24" name="icon-edit" @click.native.stop="editActionsDialogHandler(section)" />
                </div>
            </header>
            <template #content><div class="section-main">
                <template v-for="part in ['users', 'roles']">
                    <header :key="part + 'header'" class="section-sub-title flex-align-center">
                        <span class="mr20">{{section[part].title}}</span>
                        <bk-tag-input
                            style="width: 300px"
                            v-model="section[part].addList"
                            :list="filterSelectOptions(section[part], part)"
                            :search-key="['id', 'name']"
                            :title="section[part].addList.map(u => userList[u] ? userList[u].name : u)"
                            placeholder="请输入，按Enter键确认"
                            trigger="focus"
                            allow-create>
                        </bk-tag-input>
                        <i v-if="section[part].addList.length"
                            class="section-sub-add-btn devops-icon icon-check-1"
                            @click="() => {
                                submit('add', part, section)
                            }">
                        </i>
                    </header>
                    <div :key="part + 'data'" class="section-sub">
                        <div class="permission-tag" v-for="tag in filterDeleteTagList(section[part])" :key="tag">
                            {{ getName(part, tag) }}
                            <i class="devops-icon icon-close-circle-shape" @click="handleDeleteTag(tag, part, section)"></i>
                        </div>
                    </div>
                </template>
            </div></template>
        </bk-collapse-item>
        <canway-dialog
            :value="editActionsDialog.show"
            width="400"
            height-num="450"
            :title="editActionsDialog.title"
            @cancel="editActionsDialog.show = false">
            <bk-checkbox-group class="vertical-checkbox" v-model="editActionsDialog.actions">
                <bk-checkbox v-for="action in actionList" :key="action.id" :value="action.id" :disabled="action.id === 'READ'">
                    <span>{{ action.name }}</span>
                    <div class="checkbox-tip">
                        <div v-for="tip in action.tips" :key="tip">{{ tip }}</div>
                    </div>
                </bk-checkbox>
            </bk-checkbox-group>
            <template #footer>
                <bk-button theme="default" @click.stop="editActionsDialog.show = false">{{$t('cancel')}}</bk-button>
                <bk-button class="ml10" :loading="editActionsDialog.loading" theme="primary" @click.stop.prevent="handleActionPermission">{{$t('submit')}}</bk-button>
            </template>
        </canway-dialog>
    </bk-collapse>
</template>
<script>
    import { mapActions } from 'vuex'
    export default {
        name: 'permissionConfig',
        data () {
            return {
                isLoading: false,
                activeName: ['admin', 'user'],
                editActionsDialog: {
                    show: false,
                    loading: false,
                    id: '',
                    name: '',
                    title: '',
                    actions: []
                },
                admin: {
                    name: 'admin',
                    loading: false,
                    title: this.$t('admin'),
                    icon: 'perm-controller',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: this.$t('user'),
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: this.$t('userGroup'),
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                user: {
                    name: 'user',
                    loading: false,
                    title: this.$t('users'),
                    icon: 'perm-user',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: this.$t('user'),
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: this.$t('userGroup'),
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                userList: {},
                roleList: {},
                actionList: [
                    {
                        id: 'READ',
                        name: this.$t('view'),
                        tips: [
                            this.$t('readActionTip')
                        ]
                    },
                    {
                        id: 'WRITE',
                        name: this.$t('upload'),
                        tips: [
                            this.$t('writeActionTip1'),
                            this.$t('writeActionTip2')
                        ]
                    },
                    {
                        id: 'UPDATE',
                        name: this.$t('modify'),
                        tips: [
                            this.$t('updateActionTip1'),
                            this.$t('updateActionTip2'),
                            this.$t('updateActionTip3')
                        ]
                    },
                    {
                        id: 'DELETE',
                        name: this.$t('delete'),
                        tips: [
                            this.$t('deleteActionTip')
                        ]
                    }
                ]
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.repoName
            },
            getName () {
                return (part, tag) => {
                    const map = {
                        users: this.userList,
                        roles: this.roleList
                    }[part]
                    return map[tag] ? map[tag].name : tag
                }
            },
            filterDeleteTagList () {
                return (target) => {
                    return target.data.filter(v => !target.deleteList.find(w => w === v))
                }
            }
        },
        created () {
            this.getProjectRoleList({
                projectId: this.projectId
            }).then(res => {
                this.roleList = res.reduce((target, item) => {
                    target[item.id] = item
                    return target
                }, {})
            })
            this.getProjectUserList({
                projectId: this.projectId
            }).then(res => {
                this.userList = res.reduce((target, item) => {
                    target[item.userId] = {
                        id: item.userId,
                        name: item.name
                    }
                    return target
                }, {})
            })
            this.handlePermissionDetail()
        },
        methods: {
            ...mapActions([
                'getPermissionDetail',
                'getProjectUserList',
                'getProjectRoleList',
                'setUserPermission',
                'setRolePermission',
                'setActionPermission'
            ]),
            getActions (name) {
                const actionsName = ['READ', ...this[name].actions.data].map(id => this.actionList.find(action => action.id === id)?.name)
                switch (name) {
                    case 'admin':
                        return this.$t('adminPermission')
                    case 'user':
                        return this.$t('normalPermission') + `：${actionsName.join('，')}`
                }
            },
            filterSelectOptions (target, part) {
                const list = Object.values({ users: this.userList, roles: this.roleList }[part])
                return list
                    .filter(v => v.id !== 'anonymous')
                    .filter(v => !~target.data.findIndex(w => w === v.id))
            },
            handleDeleteTag (tag, part, section) {
                section[part].deleteList.push(tag)
                this.submit('delete', part, section)
            },
            handlePermissionDetail (target, origin, id) {
                this.isLoading = true
                return this.getPermissionDetail({
                    projectId: this.projectId,
                    repoName: this.repoName
                }).then(res => {
                    if (target && origin && id) {
                        this[origin][target].data = res.find(v => v.id === id)[target]
                    } else {
                        res.forEach(part => {
                            const perm = this[part.permName.replace(/^.*_([^_]+)$/, '$1')]
                            perm.id = part.id
                            perm.users.data = part.users
                            perm.roles.data = part.roles
                            perm.actions.data = part.actions
                        })
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            submit (type, part, section) {
                if (section.loading) return
                section.loading = true
                const fn = {
                    users: this.setUserPermission,
                    roles: this.setRolePermission
                }[part]
                const key = {
                    users: 'userId',
                    roles: 'rId'
                }[part]
                const value = {
                    add: [...section[part].data, ...section[part].addList],
                    delete: section[part].data.filter(v => !section[part].deleteList.find(w => w === v))
                }[type]
                fn({
                    body: {
                        permissionId: section.id,
                        [key]: value
                    }
                }).then(() => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (type === 'add' ? this.$t('add') : this.$t('delete')) + this.$t('success')
                    })
                    this.handlePermissionDetail(part, section.name, section.id).then(() => {
                        section[part][`${type}List`] = []
                    })
                }).finally(() => {
                    section.loading = false
                })
            },
            cancel (target) {
                target.deleteList = []
            },
            editActionsDialogHandler (data) {
                this.editActionsDialog = {
                    show: true,
                    loading: false,
                    id: data.id,
                    name: data.name,
                    title: data.title + '权限配置',
                    actions: ['READ', ...JSON.parse(JSON.stringify(data.actions.data))]
                }
            },
            handleActionPermission () {
                if (this.editActionsDialog.loading) return
                this.editActionsDialog.loading = true
                this.setActionPermission({
                    body: {
                        permissionId: this.editActionsDialog.id,
                        actions: this.editActionsDialog.actions.filter(a => a !== 'READ')
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                    this.editActionsDialog.show = false
                    this.handlePermissionDetail('actions', this.editActionsDialog.name, this.editActionsDialog.id)
                }).finally(() => {
                    this.editActionsDialog.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.permission-config-container {
    margin: 0 -10px;
    ::v-deep .bk-collapse-item {
        margin-bottom: 20px;
        .bk-collapse-item-detail {
            color: inherit;
        }
        .bk-collapse-item-header {
            position: relative;
            height: 42px;
            line-height: 40px;
            .icon-angle-right {
                padding: 0 15px;
            }
        }
    }
    .section-header {
        padding-left: 20px;
        color: var(--fontPrimaryColor);
        background-color: var(--bgColor);
        border: 1px solid var(--borderColor);
        .permission-actions {
            font-size: 12px;
            font-weight: normal;
            color: var(--fontSubsidiaryColor);
        }
    }
    .section-main {
        border: solid var(--borderColor);
        border-width: 0 1px 1px;
        ::v-deep .bk-select-empty {
            display: none;
        }
        .section-sub-title {
            padding: 20px 10px 10px;
            > :first-child {
                flex-basis: 45px;
                text-align: right;
            }
            .section-sub-add-btn {
                position: relative;
                z-index: 1;
                padding: 9px;
                color: white;
                margin-left: -2px;
                border-radius: 0 2px 2px 0;
                background-color: var(--primaryColor);
                cursor: pointer;
                &:hover {
                    background-color: var(--primaryHoverColor);
                }
            }
        }
        .section-sub {
            display: flex;
            flex-wrap: wrap;
            padding-left: 75px;
            padding-bottom: 10px;
            border-bottom: 1px solid var(--borderColor);
            margin-bottom: -1px;
            .permission-tag {
                position: relative;
                margin-right: 15px;
                margin-bottom: 10px;
                padding: 7px 20px;
                background-color: var(--bgHoverLighterColor);
                .icon-close-circle-shape {
                    display: none;
                    position: absolute;
                    top: -5px;
                    right: -5px;
                    color: var(--dangerColor);
                    cursor: pointer;
                }
                &:hover .icon-close-circle-shape {
                    display: block;
                }
            }
        }
    }
}
.vertical-checkbox {
    .bk-form-checkbox {
        display: flex;
        align-items: flex-start;
        margin-bottom: 20px;
        .checkbox-tip {
            margin-top: 10px;
            color: var(--fontSubsidiaryColor);
            line-height: 1.5;
        }
    }
}
</style>
