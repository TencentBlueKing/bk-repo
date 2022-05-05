<template>
    <span class="repo-tag scan-tag-container"
        :class="status"
        @click.stop="showScanList"
        v-bk-clickoutside="handleClickOutSide"
        :data-name="scanStatusEnum[status] || '未扫描'">
        <bk-dialog
            class="scan-list-dialog"
            v-model="visible"
            :position="position"
            :width="300"
            :show-mask="false"
            :close-icon="false"
            :show-footer="false"
            :draggable="false">
            <div class="scan-item flex-between-center"
                v-for="scan in scanList"
                :key="scan.id">
                <span class="text-overflow" :class="{ 'hover-btn': scan.status === 'SUCCESS' }" style="max-width:150px;"
                    @click="toReport(scan)" :title="scan.name">{{ scan.name }}</span>
                <span class="repo-tag" :class="scan.status">{{scanStatusEnum[scan.status]}}</span>
            </div>
        </bk-dialog>
    </span>
</template>
<script>
    import { scanStatusEnum } from '@repository/store/publicEnum'
    import { mapActions } from 'vuex'
    export default {
        name: 'scanTag',
        props: {
            status: String,
            repoType: String,
            fullPath: String
        },
        data () {
            return {
                visible: false,
                position: {
                    top: 0,
                    left: 0
                },
                isLoading: false,
                scanStatusEnum,
                scanList: []
            }
        },
        methods: {
            ...mapActions(['getArtiScanList']),
            showScanList (e) {
                if (!this.status) return
                this.openScanList(e)
                this.isLoading = true
                const { projectId, repoType = this.repoType } = this.$route.params
                const { repoName, version } = this.$route.query
                this.getArtiScanList({
                    projectId,
                    repoType,
                    repoName,
                    packageKey: this.$route.query.packageKey || undefined,
                    version: version || undefined,
                    fullPath: this.fullPath || undefined
                }).then(res => {
                    this.scanList = res
                }).finally(() => {
                    this.isLoading = false
                })
            },
            openScanList (e) {
                this.position = {
                    top: e.clientY,
                    left: e.clientX
                }
                this.visible = true
            },
            handleClickOutSide () {
                this.visible = false
            },
            toReport ({ id, recordId, status }) {
                if (status === 'SUCCESS') {
                    this.$router.push({
                        name: 'artiReport',
                        params: {
                            planId: id,
                            recordId
                        },
                        query: {
                            repoType: this.repoType,
                            ...this.$route.params,
                            ...this.$route.query
                        }
                    })
                }
            }
        }
    }
</script>
<style lang="scss" scoped>
.scan-tag-container {
    cursor: pointer;
    &:before {
        content: attr(data-name);
    }
}
</style>
<style lang="scss">
.scan-list-dialog {
    .bk-dialog-wrapper {
        .bk-dialog {
            margin: initial;
            .bk-dialog-tool {
                display: none;
            }
            .bk-dialog-body {
                padding: 20px;
                max-height: 200px;
                min-height: auto;
                overflow-y: auto;
            }
        }
    }
    .scan-item {
        margin-top: 10px;
        padding: 10px 20px;
        font-size: 12px;
        border: 1px solid var(--borderColor);
        &:first-child {
            margin: 0;
        }
    }
}
</style>
