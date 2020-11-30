<template>
    <bk-collapse class="permission-config-container" v-model="activeName" v-bkloading="{ isLoading }">
        <bk-collapse-item v-for="section in [admin, user, viewer]" :key="section.name" :name="section.name">
            <header class="section-header">
                <div class="flex-align-center">
                    <icon class="mr10" size="20" :name="section.icon"></icon>
                    <span>{{ section.title }}</span>
                    <span class="mr10 permission-actions">（{{ getActions(section.actions.data) }}）</span>
                    <i v-if="section !== admin" class="devops-icon icon-edit hover-btn" @click.stop="editActionsDialogHandler(section)"></i>
                </div>
            </header>
            <div slot="content" class="section-main">
                <template v-for="part in ['users', 'roles', ...(section !== admin ? ['departments'] : [])]">
                    <header :key="part + 'header'" class="section-sub-title flex-align-center">
                        <span>{{section[part].title}}</span>
                        <i class="ml10 devops-icon hover-btn"
                            :class="section[part].showAddArea ? 'icon-minus-square' : 'icon-plus-square'"
                            @click="handleShowAddArea(section[part])">
                        </i>
                        <div v-show="section[part].showAddArea" :key="part + 'operation'" class="ml15 flex-align-center">
                            <template v-if="part === 'departments'">
                                <bk-select
                                    style="min-width: 350px"
                                    searchable
                                    multiple
                                    v-model="section[part].addList"
                                    :remote-method="(keyword) => $refs[`${section.name}Tree`][0].filter(keyword)"
                                    :display-tag="true"
                                    :tag-fixed-height="false"
                                    :show-empty="false"
                                    @toggle="show => toggleTreeSelect(show, $refs[`${section.name}Tree`][0], section[part].data)"
                                    @tab-remove="({ id }) => $refs[`${section.name}Tree`][0].setChecked(id, { emitEvent: true, checked: false })"
                                    @clear="$refs[`${section.name}Tree`][0].removeChecked({ emitEvent: false })">
                                    <bk-big-tree
                                        :ref="`${section.name}Tree`"
                                        show-checkbox
                                        :check-strictly="false"
                                        show-link-line
                                        :lazy-method="(node) => handleDepartmentTreeNode(node, $refs[`${section.name}Tree`][0], section[part].data)"
                                        @check-change="ids => changeAddDepartments(section[part], ids)">
                                    </bk-big-tree>
                                </bk-select>
                            </template>
                            <template v-else>
                                <bk-select
                                    style="min-width: 250px"
                                    v-model="section[part].addList"
                                    multiple
                                    display-tag
                                    searchable
                                    enable-virtual-scroll
                                    :list="filterSelectOptions(section[part], part)">
                                </bk-select>
                            </template>
                            <i v-if="section[part].addList.length"
                                class="section-sub-add-btn devops-icon icon-check-1"
                                @click="() => {
                                    submit('add', part, section)
                                }">
                            </i>
                        </div>
                    </header>
                    <div :key="part + 'data'" class="section-sub">
                        <div class="section-sub-main mt10">
                            <div class="permission-tag" v-for="tag in filterDeleteTagList(section[part])" :key="tag">
                                {{ getName(part, tag) }}
                                <i class="devops-icon icon-close-circle-shape" @click="handleDeleteTag(section[part], tag)"></i>
                            </div>
                        </div>
                        <div v-if="section[part].deleteList && section[part].deleteList.length">
                            <bk-button :loading="section.loading" theme="primary" @click="submit('delete', part, section)">{{$t('save')}}</bk-button>
                            <bk-button class="ml10" theme="default" @click="cancel(section[part])">{{$t('cancel')}}</bk-button>
                        </div>
                    </div>
                </template>
            </div>
        </bk-collapse-item>
        <bk-dialog
            v-model="editActionsDialog.show"
            :title="editActionsDialog.title"
            width="410"
            :close-icon="false"
            :quick-close="false"
            :draggable="false">
            <bk-checkbox-group v-model="editActionsDialog.actions">
                <bk-checkbox v-for="action in actionList" :key="action.id" class="m20" :value="action.id">{{ action.name }}</bk-checkbox>
            </bk-checkbox-group>
            <div slot="footer">
                <bk-button :loading="loading" theme="primary" @click.stop.prevent="handleActionPermission">{{$t('submit')}}</bk-button>
                <bk-button theme="default" @click.stop="editActionsDialog.show = false">{{$t('cancel')}}</bk-button>
            </div>
        </bk-dialog>
    </bk-collapse>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    export default {
        name: 'permissionConfig',
        data () {
            return {
                isLoading: false,
                loading: false,
                activeName: ['admin', 'user', 'viewer'],
                editActionsDialog: {
                    show: false,
                    id: '',
                    name: '',
                    title: '',
                    actions: []
                },
                admin: {
                    name: 'admin',
                    loading: false,
                    title: '管理者',
                    icon: 'perm-controller',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: '用户',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: '用户组',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    departments: {
                        title: '组织',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                user: {
                    name: 'user',
                    loading: false,
                    title: '使用者',
                    icon: 'perm-user',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: '用户',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: '用户组',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    departments: {
                        title: '组织',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                viewer: {
                    name: 'viewer',
                    loading: false,
                    title: '查看者',
                    icon: 'perm-viewer',
                    id: '',
                    actions: {
                        data: []
                    },
                    users: {
                        title: '用户',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    roles: {
                        title: '用户组',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    },
                    departments: {
                        title: '组织',
                        showAddArea: false,
                        data: [],
                        addList: [],
                        deleteList: []
                    }
                },
                userList: {},
                roleList: {},
                flatDepartment: {},
                departmentTree: [],
                actionList: [
                    { id: 'MANAGE', name: '管理' },
                    { id: 'READ', name: '查看' },
                    { id: 'WRITE', name: '新增' },
                    { id: 'DELETE', name: '删除' },
                    { id: 'UPDATE', name: '修改' }
                ]
            }
        },
        computed: {
            ...mapState(['userInfo']),
            projectId () {
                return this.$route.params.projectId
            },
            repoName () {
                return this.$route.query.name
            },
            getName () {
                return (part, tag) => {
                    const map = {
                        users: this.userList,
                        roles: this.roleList,
                        departments: this.flatDepartment
                    }[part]
                    return map[tag] ? map[tag].name : tag
                }
            },
            getActions () {
                return (actions) => {
                    return actions.map(v => this.actionList.find(w => w.id === v).name).join('，')
                }
            },
            filterDeleteTagList () {
                return (target) => {
                    return target.data.filter(v => !target.deleteList.find(w => w === v))
                }
            },
            filterSelectOptions () {
                return (target, part) => {
                    const list = Object.values({ users: this.userList, roles: this.roleList }[part])
                    return list.filter(v => !target.data.find(w => w === v.id))
                }
            }
        },
        created () {
            this.getRepoUserList().then(res => {
                this.userList = res.reduce((target, item) => {
                    target[item.userId] = {
                        id: item.userId,
                        name: item.name
                    }
                    return target
                }, {})
            })
            this.getRepoRoleList({
                projectId: this.projectId,
                repoName: this.repoName
            }).then(res => {
                this.roleList = res.reduce((target, item) => {
                    target[item.id] = item
                    return target
                }, {})
            })
            this.handleDepartmentTreeNode()
            this.handlePermissionDetail()
        },
        methods: {
            ...mapActions([
                'getPermissionDetail',
                'getRepoUserList',
                'getRepoRoleList',
                'getRepoDepartmentList',
                'setUserPermission',
                'setRolePermission',
                'setDepartmentPermission',
                'setActionPermission',
                'getRepoDepartmentDetail'
            ]),
            handleShowAddArea (target) {
                target.showAddArea = !target.showAddArea
            },
            handleDeleteTag (target, tag) {
                target.deleteList.push(tag)
            },
            toggleTreeSelect (show, treeTarget, disabled) {
                show && treeTarget.setData(this.departmentTree)
                disabled.forEach(id => {
                    treeTarget.setChecked(id)
                    treeTarget.setDisabled(id)
                })
            },
            changeAddDepartments (treeTarget, ids) {
                treeTarget.addList = ids.filter(id => !treeTarget.data.find(exist => id === exist))
            },
            handleFlatDepartment (departments) {
                departments.forEach(v => {
                    this.$set(this.flatDepartment, v.id, v)
                })
            },
            async handleDepartmentTreeNode (node, root, disabled) {
                if (!node) {
                    // 初始化
                    this.getRepoDepartmentList({
                        username: this.userInfo.username
                    }).then(res => {
                        this.handleFlatDepartment(res)
                        this.departmentTree = res.map(v => ({ ...v, has_children: true }))
                    })
                } else {
                    // 初始化
                    if (!node.data.has_children) return ({ data: [], leaf: [] })
                    const res = await this.getRepoDepartmentList({
                        username: this.userInfo.username,
                        departmentId: node.id
                    })
                    this.handleFlatDepartment(res)
                    this.$nextTick(() => {
                        let target = this.departmentTree
                        node.parents.forEach(parent => {
                            target = (target.children || target).find(v => v.id === parent.id).children
                        })
                        target = target.find(v => v.id === node.id)
                        target.children = res
                        if (root) {
                            root.setData(this.departmentTree)
                            root.setExpanded([node.id])
                            disabled.forEach(id => {
                                root.setChecked(id)
                                root.setDisabled(id)
                            })
                        }
                    })
                    return {
                        data: [],
                        leaf: res.filter(v => !v.has_children).map(w => w.id)
                    }
                }
            },
            async handlePermissionDetail (target, origin, id) {
                this.isLoading = true
                await this.getPermissionDetail({
                    projectId: this.projectId,
                    repoName: this.repoName
                }).then(res => {
                    if (target && origin && id) {
                        this[origin][target].data = res.find(v => v.id === id)[target]
                    } else {
                        let departments = []
                        res.forEach(part => {
                            const perm = this[part.permName.replace(/^.*_([^_]+)$/, '$1')]
                            perm.id = part.id
                            perm.users.data = part.users
                            perm.roles.data = part.roles
                            perm.departments.data = part.departments
                            perm.actions.data = part.actions
                            departments = departments.concat(part.departments)
                        })
                        departments = Array.from(new Set(departments)).filter(v => !this.flatDepartment.hasOwnProperty(v))
                        departments.length && this.getRepoDepartmentDetail({ body: departments }).then(this.handleFlatDepartment)
                    }
                }).finally(() => {
                    this.isLoading = false
                })
            },
            submit (type, part, section) {
                this.loading = true
                const fn = {
                    users: this.setUserPermission,
                    roles: this.setRolePermission,
                    departments: this.setDepartmentPermission
                }[part]
                const key = {
                    users: 'userId',
                    roles: 'rId',
                    departments: 'departmentId'
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
                }).then(async res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: (type === 'add' ? this.$t('add') : this.$t('save')) + this.$t('success')
                    })
                    await this.handlePermissionDetail(part, section.name, section.id)
                    section[part][`${type}List`] = []
                }).finally(() => {
                    this.loading = false
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
                    actions: JSON.parse(JSON.stringify(data.actions.data))
                }
            },
            handleActionPermission () {
                this.loading = true
                this.setActionPermission({
                    body: {
                        permissionId: this.editActionsDialog.id,
                        actions: this.editActionsDialog.actions
                    }
                }).then(res => {
                    this.$bkMessage({
                        theme: 'success',
                        message: this.$t('save') + this.$t('success')
                    })
                    this.editActionsDialog.show = false
                    this.handlePermissionDetail('actions', this.editActionsDialog.name, this.editActionsDialog.id)
                }).finally(() => {
                    this.loading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
@import '@/scss/conf';
.permission-config-container {
    /deep/ .bk-collapse-item {
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
    section + section {
        margin-top: 20px;
    }
    .section-header {
        padding-left: 10px;
        color: $fontBoldColor;
        background-color: #f2f2f2;
        border: 1px solid #d5d5d5;
        font-size: 14px;
        font-weight: normal;
        .icon-edit {
            font-size: 14px;
        }
        .permission-actions {
            font-size: 12px;
            color: $fontColor;
        }
    }
    .section-main {
        padding: 10px;
        border: solid #d5d5d5;
        border-width: 0 1px 1px;
        /deep/ .bk-select-empty {
            display: none;
        }
        .section-sub-title {
            height: 52px;
            padding: 10px;
            border-bottom: 1px solid #d5d5d5;
            .section-sub-add-btn {
                position: relative;
                z-index: 1;
                padding: 9px;
                color: white;
                margin-left: -2px;
                border-radius: 0 2px 2px 0;
                background-color: #3a84ff;
                cursor: pointer;
                &:hover {
                    background-color: #699df4;
                }
            }
        }
        .section-sub {
            margin-bottom: 20px;
            .section-sub-main {
                display: flex;
                flex-wrap: wrap;
                .permission-tag {
                    position: relative;
                    margin-right: 15px;
                    margin-bottom: 10px;
                    padding: 7px 20px;
                    background-color: #f2f2f2;
                    .icon-close-circle-shape {
                        display: none;
                        position: absolute;
                        top: -5px;
                        right: -5px;
                        color: $dangerColor;
                        cursor: pointer;
                    }
                    &:hover .icon-close-circle-shape {
                        display: block;
                    }
                }
            }
        }
    }
}
</style>
