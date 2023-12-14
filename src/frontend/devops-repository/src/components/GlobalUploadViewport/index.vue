<template>
    <div class="upload-viewport-container" :class="{ 'visible': show, 'file-visible': showFileList }">
        <div class="viewport-header flex-between-center">
            <div class="header-title flex-align-center">
                <span class="mr10">{{$t('artifactUploadTask')}}</span>
                <span v-if="!showFileList && upLoadTaskQueue.length"
                    class="spin-icon"
                    style="font-size:0;">
                    <Icon size="16" name="scan-running" />
                </span>
            </div>
            <div class="header-operation flex-align-center">
                <i v-if="showFileList" class="devops-icon icon-minus" @click="showFileList = !showFileList"></i>
                <Icon v-else size="14" name="icon-expand" @click.native="showFileList = !showFileList" />
                <i class="ml20 devops-icon icon-close" @click="closeViewport"></i>
            </div>
        </div>
        <div class="pt10 viewport-table" v-show="showFileList">
            <bk-table
                :data="fileList"
                height="100%"
                :outer-border="false"
                :row-border="false"
                :virtual-render="fileList.length > 3000"
                size="small">
                <bk-table-column :label="$t('fileName')" min-width="300" show-overflow-tooltip>
                    <template #default="{ row }">
                        <bk-popover placement="top">
                            {{row.file.name}}
                            <template #content>
                                <div>{{$t('project')}}：{{ (projectList.find(p => p.id === row.projectId)).name }}</div>
                                <div>{{$t('repository')}}：{{ replaceRepoName(row.repoName) }}</div>
                                <div>{{$t('fileStoragePath')}}：{{ row.fullPath }}</div>
                            </template>
                        </bk-popover>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('status')" width="140">
                    <template #default="{ row }">
                        <span v-if="row.status === 'UPLOADING'"
                            v-bk-tooltips="{ content: row.progressDetail, placements: ['bottom'] }">
                            <span class="spin-icon inline-block"
                                style="font-size:0;vertical-align:-2px;">
                                <Icon size="14" name="scan-running" />
                            </span>
                            <span>{{ row.progressPercent }}</span>
                        </span>
                        <span v-else class="repo-tag" :class="row.status"
                            v-bk-tooltips="{ disabled: row.status !== 'FAILED' || !row.errMsg, content: row.errMsg, placements: ['bottom'] }">
                            {{ uploadStatus[row.status].label }}
                        </span>
                    </template>
                </bk-table-column>
                <bk-table-column :label="$t('operation')" width="90">
                    <template #default="{ row }">
                        <bk-button v-if="row.status === 'INIT' || row.status === 'UPLOADING'"
                            text theme="primary" @click="cancelUpload(row)">{{$t('cancel')}}</bk-button>
                        <bk-button v-else-if="row.status === 'SUCCESS'"
                            text theme="primary" @click="$router.push({
                                name: 'repoGeneric',
                                params: { projectId: row.projectId },
                                query: { repoName: row.repoName, path: row.fullPath }
                            })">{{$t('view')}}</bk-button>
                        <bk-button v-else-if="row.status === 'CANCEL' || row.status === 'FAILED'"
                            text theme="primary" @click="reUpload(row)">{{$t('upload')}}</bk-button>
                    </template>
                </bk-table-column>
            </bk-table>
        </div>
        <selected-files-dialog
            ref="selectedFilesDialog"
            :root-data="rootData"
            @confirm="addFilesToFileList">
        </selected-files-dialog>
        <iam-deny-dialog :visible.sync="showIamDenyDialog" :show-data="showData"></iam-deny-dialog>
    </div>
</template>
<script>
    import Vue from 'vue'
    import iamDenyDialog from '@repository/components/IamDenyDialog/IamDenyDialog'
    import selectedFilesDialog from './selectedFilesDialog'
    import { mapState, mapActions } from 'vuex'
    import { convertFileSize } from '@repository/utils'
    export default {
        name: 'globalUploadViewport',
        components: { selectedFilesDialog, iamDenyDialog },
        data () {
            return {
                show: false,
                showFileList: true,
                rootData: {
                    projectId: '',
                    repoName: '',
                    folder: false,
                    fullPath: ''
                },
                fileList: [],
                upLoadTaskQueue: [],
                showIamDenyDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['projectList', 'userInfo']),
            uploadStatus () {
                return {
                    UPLOADING: { label: this.$t('uploading'), power: 1 },
                    INIT: { label: this.$t('waitingUpload'), power: 2 },
                    FAILED: { label: this.$t('uploadFailed'), power: 3 },
                    CANCEL: { label: this.$t('cancelled'), power: 4 },
                    SUCCESS: { label: this.$t('uploadCompleted'), power: 5 }
                }
            }
        },
        mounted () {
            Vue.prototype.$globalUploadFiles = this.selectFiles
        },
        methods: {
            ...mapActions([
                'uploadArtifactory',
                'getPermissionUrl'
            ]),
            selectFiles (data = {}) {
                this.rootData = {
                    ...this.rootData,
                    ...data
                }
                this.$refs.selectedFilesDialog.selectFiles()
            },
            addFilesToFileList ({ overwrite, selectedFiles }) {
                const fileList = selectedFiles.map(file => this.getUploadObj(file, overwrite))
                this.sortFileList(fileList)
                this.addToUpLoadTaskQueue()
                this.show = true
            },
            getUploadObj (file, overwrite) {
                const { projectId, repoName, fullPath: path } = this.rootData
                // TODO
                const fullPath = `${path}/${this.rootData.folder ? file.webkitRelativePath : file.name}`
                return {
                    xhr: new XMLHttpRequest(),
                    projectId,
                    repoName,
                    fullPath,
                    file,
                    overwrite,
                    status: 'INIT'
                }
            },
            sortFileList (extFiles = []) {
                this.fileList = [...this.fileList, ...extFiles].sort((a, b) => {
                    return this.uploadStatus[a.status].power - this.uploadStatus[b.status].power
                })
            },
            closeViewport () {
                if (!this.upLoadTaskQueue.length) {
                    this.show = false
                    this.showFileList = true
                    this.fileList = []
                    return
                }
                this.$confirm({
                    theme: 'danger',
                    message: this.$t('confirmCancelsTasks'),
                    confirmFn: () => {
                        this.show = false
                        this.showFileList = true
                        this.fileList.forEach(this.cancelUpload)
                        this.fileList = []
                    }
                })
            },
            addToUpLoadTaskQueue () {
                const wait = this.fileList.find(f => f.status === 'INIT')
                if (this.upLoadTaskQueue.length > 3 || !wait) return
                this.$set(wait, 'status', 'UPLOADING')
                const { xhr, projectId, repoName, fullPath, file, overwrite } = wait
                this.uploadArtifactory({
                    xhr,
                    projectId,
                    repoName,
                    fullPath,
                    body: file,
                    progressHandler: ($event) => {
                        const { progressDetail, progressPercent } = this.getProgress($event)
                        this.$set(wait, 'progressDetail', progressDetail)
                        this.$set(wait, 'progressPercent', progressPercent)
                    },
                    headers: {
                        'Content-Type': file.type || 'application/octet-stream',
                        'X-BKREPO-OVERWRITE': overwrite,
                        'X-BKREPO-EXPIRES': 0
                    }
                }).then(() => {
                    this.$set(wait, 'status', 'SUCCESS')
                    window.repositoryVue.$emit('upload-refresh', fullPath)
                }).catch(e => {
                    if (wait.status === 'CANCEL') return
                    if (e.status === 403) {
                        this.getPermissionUrl({
                            body: {
                                projectId: projectId,
                                action: 'WRITE',
                                resourceType: 'REPO',
                                uid: this.userInfo.name,
                                repoName: repoName
                            }
                        }).then(res => {
                            if (res !== '') {
                                this.showIamDenyDialog = true
                                this.showData = {
                                    projectId: projectId,
                                    repoName: repoName,
                                    action: 'WRITE',
                                    url: res
                                }
                            }
                        })
                    }
                    e && this.$set(wait, 'errMsg', e.message || e)
                    this.$set(wait, 'status', 'FAILED')
                }).finally(() => {
                    this.upLoadTaskQueue = this.upLoadTaskQueue.filter(task => task !== wait)
                    setTimeout(this.addToUpLoadTaskQueue, 500)
                    this.sortFileList()
                })
                this.upLoadTaskQueue.push(wait)
                setTimeout(this.addToUpLoadTaskQueue, 500)
            },
            cancelUpload (row) {
                this.$set(row, 'status', 'CANCEL')
                row.xhr.abort()
            },
            reUpload (row) {
                this.$set(row, 'status', 'INIT')
                this.sortFileList()
                this.addToUpLoadTaskQueue() // 开启队列
            },
            getProgress ({ loaded, total }) {
                const progressDetail = `(${convertFileSize(loaded)}/${convertFileSize(total)})`
                const progressPercent = parseInt(100 * loaded / total) + '%'
                return { progressDetail, progressPercent }
            }
        }
    }
</script>
<style lang="scss" scoped>
.upload-viewport-container {
    display: none;
    position: fixed;
    right: 40px;
    bottom: 60px;
    width: 520px;
    z-index: 1999;
    border-radius: 3px;
    box-shadow: 0px 0px 20px 0px rgba(8, 30, 64, 0.2);
    &.visible {
        display: initial;
    }
    .viewport-header {
        height: 50px;
        padding: 0 20px;
        background-color: var(--bgHoverColor);
        .header-title {
            font-size: 14px;
            font-weight: 500;
        }
        .header-operation {
            color: var(--fontSubsidiaryColor);
            svg,
            .devops-icon {
                cursor: pointer;
            }
        }
    }
    .viewport-table {
        height: 325px;
        border-top: 1px solid var(--borderColor);
        background-color: white;
    }
    &.file-visible {
        .viewport-header {
            background-color: white;
        }
    }
}
</style>
