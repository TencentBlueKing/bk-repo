<template>
    <div class="file-share" v-bkloading="{ isLoading }">
        <div class="top-section">
            <div class="file-info">
                <Icon class="file-icon" size="28" :name="getIconName(file.name)" />
                <div class="file-details">
                    <div class="file-name">{{ file.name }}</div>
                    <div class="file-meta">
                        <span>{{ $t('size') }}: {{ convertFileSize(file.size) }}</span>
                        <span>{{ $t('shareUser')}}: {{ file.shareUserId }}</span>
                    </div>
                </div>
            </div>
            <button class="download-btn white-bg" v-if="download" @click="downloadFile">{{ $t('download') }}</button>
            <button class="download-btn white-bg" v-else @click="applyDownload">{{ $t('applyDownload') }}</button>
        </div>
        <div class="bottom-section">
            <div class="file-preview">
                <Icon class="file-icon" size="120" :name="getIconName(file.name)" />
                <div class="file-details">
                    <div class="file-name">{{ file.name }}</div>
                    <div class="file-size">{{ convertFileSize(file.size) }}</div>
                    <button class="download-btn" v-if="download" @click="downloadFile">{{ $t('download') }}</button>
                    <button class="download-btn" v-else @click="applyDownload">{{ $t('applyDownload') }}</button>
                </div>
            </div>
        </div>
        <itsm-dialog :visible.sync="showItsmDialog" :show-data="showData"></itsm-dialog>
    </div>
</template>
<script>
    import { mapState, mapActions } from 'vuex'
    import { convertFileSize, formatDate } from '@repository/utils'
    import { getIconName } from '@repository/store/publicEnum'
    import itsmDialog from '@repository/components/ItsmDialog'
    import cookies from 'js-cookie'
    export default {
        name: 'share',
        components: {
            itsmDialog
        },
        data () {
            return {
                isLoading: false,
                projectId: '',
                shareId: '',
                userId: '',
                shareInfo: null,
                file: null,
                download: false,
                approvalId: null,
                showItsmDialog: false,
                showData: {}
            }
        },
        computed: {
            ...mapState(['userInfo']),
            currentLanguage () {
                return cookies.get('blueking_language') || 'zh-cn'
            }
        },
        created () {
            this.fetchFile()
        },
        methods: {
            convertFileSize,
            formatDate,
            getIconName,
            ...mapActions([
                'getShareInfo', 'getShareNodeInfo', 'getShareConfig', 'getShareDownloadUrl', 'createApproval', 'getApprovalStatus'
            ]),
            checkApprovalStatus () {
                this.getApprovalStatus({
                    shareId: this.$route.params.shareId,
                    userId: this.userInfo.username
                }).then(approved => {
                    this.download = approved
                }).catch(e => {
                    if (e.status !== 404) {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('getApprovalStatusFailed')
                        })
                    }
                })
            },
            applyDownload () {
                this.createApproval({
                    shareId: this.$route.params.shareId
                }).then(itsmTicket => {
                    this.approvalId = itsmTicket.sn
                    this.showItsmDialog = true
                    this.showData = {
                        id: this.approvalId,
                        url: itsmTicket.ticket_url
                    }
                }).catch(() => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('createApprovalFailed')
                    })
                })
            },
            checkSizeLimit (nodeInfo) {
                this.getShareConfig({
                    projectId: this.$route.params.projectId,
                    repoName: nodeInfo.repoName
                }).then(config => {
                    if (nodeInfo.size < config.sizeLimit) {
                        this.download = true
                    } else {
                        this.checkApprovalStatus()
                    }
                }).catch(e => {
                    console.log(e)
                    if (e.status === 404) {
                        this.download = true
                    } else {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('getShareConfigFailed')
                        })
                    }
                }).finally(() => {
                    console.log(this.download)
                    this.setFile(nodeInfo)
                })
            },
            setFile (nodeInfo) {
                this.file = {
                    name: nodeInfo.name,
                    size: nodeInfo.size,
                    date: nodeInfo.createdDate,
                    shareUserId: this.shareInfo.createBy
                }
            },
            fetchFile () {
                this.isLoading = true
                this.getShareInfo({
                    projectId: this.$route.params.projectId,
                    shareId: this.$route.params.shareId
                }).then(shareInfo => {
                    this.shareInfo = shareInfo
                    return this.getShareNodeInfo({
                        projectId: this.shareInfo.projectId,
                        repoName: this.shareInfo.repoName,
                        path: this.shareInfo.path
                    })
                }).then(res => {
                    if (res.count < 1) {
                        this.$bkMessage({
                            theme: 'error',
                            message: this.$t('shareFileDeleted')
                        })
                        return
                    }
                    const nodeInfo = res.records[0]
                    this.checkSizeLimit(nodeInfo)
                }).catch(error => {
                    console.log(error)
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('getShareFileFailed') + ':' + error.message
                    })
                }).finally(() => {
                    this.isLoading = false
                })
            },
            downloadFile () {
                this.isLoading = true
                this.getShareDownloadUrl({
                    body: { id: this.$route.params.shareId }
                }).then(res => {
                    const url = res.urls[0]
                    const headUrl = url.slice('/web'.length)
                    this.$ajax.head(headUrl).then(() => {
                        window.open(url, '_self')
                    }).catch(e => {
                        const message = e.status === 403 ? this.$t('fileDownloadError', [this.$route.params.projectId]) : this.$t('fileError')
                        this.$bkMessage({
                            theme: 'error',
                            message
                        })
                    })
                }).catch(error => {
                    this.$bkMessage({
                        theme: 'error',
                        message: this.$t('getDownloadUrlFailed') + ':' + error.message
                    })
                }).finally(() => {
                    this.isLoading = false
                })
            }
        }
    }
</script>
<style lang="scss" scoped>
.file-share {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.top-section {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 60px;
    padding: 0 20px;
    background-color: #f5f5f5;
    border-bottom: 1px solid #ddd;
}

.file-info {
    display: flex;
    align-items: center;
}

.file-icon {
    font-size: 24px;
    margin-right: 10px;
}

.file-details {
    display: flex;
    flex-direction: column;
}

.file-name {
    font-weight: bold;
}

.file-meta span {
    margin-right: 10px;
}

.download-btn {
    background-color: #007bff;
    color: white;
    border: none;
    padding: 5px 10px;
    cursor: pointer;
    border-radius: 4px;
}

.download-btn.white-bg {
    background-color: white;
    color: #007bff;
    border: 1px solid #007bff;
}

.bottom-section {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    background-color: #fff;
}

.file-preview {
    display: flex;
    align-items: center;
}

.thumbnail {
    max-width: 150px;
    height: auto;
    margin-right: 20px;
}

.file-details {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
}

.file-details .file-name {
    font-size: 1.2em;
}

.file-size {
    margin-bottom: 10px;
}
</style>
