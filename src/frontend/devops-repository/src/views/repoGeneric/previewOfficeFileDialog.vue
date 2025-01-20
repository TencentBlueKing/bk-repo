<template>
    <bk-dialog
        class="preview-office-file-dialog"
        v-model="previewDialog.show"
        :width="dialogWidth"
        :show-footer="false"
        :title="($t('preview') + ' - ' + previewDialog.title)"
        @cancel="cancel"
    >
        <div ref="showData">
            <iframe v-if="showFrame" :src="pageUrl" frameborder="0" style="width: 100%; height: 100%"></iframe>
            <vue-office-excel
                v-if="previewExcel"
                :src="dataSource"
                :options="excelOptions"
                style="height: 100vh;"
            />
            <div v-if="csvShow" id="csvTable"></div>
            <div v-if="loading" class="mainBody">
                <div class="iconBody">
                    <Icon name="loading" size="80" class="svg-loading" />
                    <span class="mainMessage">{{ $t('fileLoadingTip') }}</span>
                </div>
            </div>
        </div>
    </bk-dialog>
</template>

<script>
    import VueOfficeExcel from '@vue-office/excel'
    import {
        customizePreviewLocalOfficeFile,
        customizePreviewOfficeFile,
        customizePreviewRemoteOfficeFile,
        getPreviewLocalOfficeFileInfo, getPreviewRemoteOfficeFileInfo
    } from '@repository/utils/previewOfficeFile'
    import cookies from 'js-cookie'
    import { isHtmlType } from '@repository/utils/file'
    import Papa from 'papaparse'
    import Table from '@wolf-table/table'

    export default {
        name: 'PreviewOfficeFileDialog',
        components: { VueOfficeExcel },
        data () {
            return {
                projectId: '',
                repoName: '',
                filePath: '',
                dataSource: '',
                previewDialog: {
                    title: '',
                    show: false,
                    isLoading: true
                },
                dialogWidth: window.innerWidth - 1000,
                excelOptions: {
                    xls: false, // 预览xlsx文件设为false；预览xls文件设为true
                    minColLength: 0, // excel最少渲染多少列，如果想实现xlsx文件内容有几列，就渲染几列，可以将此值设置为0.
                    minRowLength: 0, // excel最少渲染多少行，如果想实现根据xlsx实际函数渲染，可以将此值设置为0.
                    widthOffset: 10, // 如果渲染出来的结果感觉单元格宽度不够，可以在默认渲染的列表宽度上再加 Npx宽
                    heightOffset: 10, // 在默认渲染的列表高度上再加 Npx高
                    beforeTransformData: (workbookData) => {
                        return workbookData
                    }, // 底层通过exceljs获取excel文件内容，通过该钩子函数，可以对获取的excel文件内容进行修改，比如某个单元格的数据显示不正确，可以在此自行修改每个单元格的value值。
                    transformData: (workbookData) => {
                        return workbookData
                    } // 将获取到的excel数据进行处理之后且渲染到页面之前，可通过transformData对即将渲染的数据及样式进行修改，此时每个单元格的text值就是即将渲染到页面上的内容
                },
                previewExcel: false,
                pageUrl: '',
                extraParam: '',
                repoType: '',
                showFrame: false,
                csvShow: false,
                loading: false
            }
        },
        methods: {
            setData () {
                if (this.repoType === 'local') {
                    getPreviewLocalOfficeFileInfo(this.projectId, this.repoName, '/' + this.filePath).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                } else {
                    getPreviewRemoteOfficeFileInfo(this.extraParam).then(res => {
                        if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                            this.initWaterMark(res.data.data.watermark)
                        }
                    })
                }
                if (this.filePath.endsWith('.xlsx')) {
                    this.$refs.showData.style.removeProperty('height')
                    customizePreviewOfficeFile(this.projectId, this.repoName, this.filePath).then(res => {
                        this.previewExcel = true
                        this.dataSource = res.data
                        this.previewDialog.isLoading = false
                    }).catch((e) => {
                        this.cancel()
                        const vm = window.repositoryVue
                        vm.$bkMessage({
                            theme: 'error',
                            message: e.message
                        })
                    })
                } else if (this.filePath.endsWith('.csv')) {
                    this.$refs.showData.style.removeProperty('height')
                    customizePreviewOfficeFile(this.projectId, this.repoName, this.filePath).then(res => {
                        this.csvShow = true
                        this.dealCsv(res)
                        this.previewDialog.isLoading = false
                    }).catch((e) => {
                        this.cancel()
                        const vm = window.repositoryVue
                        vm.$bkMessage({
                            theme: 'error',
                            message: e.message
                        })
                    })
                } else {
                    this.loading = true
                    this.$refs.showData.style.height = '800px'
                    if (this.repoType === 'local') {
                        customizePreviewLocalOfficeFile(this.projectId, this.repoName, this.filePath).then(res => {
                            this.dealDate(res)
                        }).catch((e) => {
                            this.cancel()
                            const vm = window.repositoryVue
                            vm.$bkMessage({
                                theme: 'error',
                                message: e.message
                            })
                        })
                    } else {
                        customizePreviewRemoteOfficeFile(this.extraParam).then(res => {
                            this.dealDate(res)
                        }).catch((e) => {
                            this.cancel()
                            const vm = window.repositoryVue
                            vm.$bkMessage({
                                theme: 'error',
                                message: e.message
                            })
                        })
                    }
                }
            },
            setDialogData (data) {
                this.previewDialog = {
                    ...data
                }
            },
            cancel () {
                this.loading = false
                this.previewDialog.show = false
                this.filePath = ''
                this.projectId = ''
                this.repoName = ''
                this.dataSource = ''
                this.previewExcel = false
                this.pageUrl = ''
                this.showFrame = false
                window.resetWaterMark()
            },
            initWaterMark (param) {
                window.initWaterMark(param)
            },
            dealDate (res) {
                const language = cookies.get('blueking_language') || 'zh-cn'
                const targetLanguage = language === 'zh-cn' ? 'zh-CN' : 'en-US'
                this.loading = false
                let url
                if (!isHtmlType(this.filePath)) {
                    // 注意后面的参数，参数都是自定义的，修改了pdfjs文件的viewer.mjs和view.html的
                    // 语言切换需注意public/web/local下的语言，需一致
                    // 各版本pdfjs的viewer.html有差异, 更改viewer.mjs下的方法做匹配显示需注意
                    url = location.origin + '/ui/web/viewer.html?file=' + URL.createObjectURL(res.data) + '&language=' + targetLanguage + '&disableopenfile=true&disableprint=true&disabledownload=true&disablebookmark=false'
                } else {
                    url = URL.createObjectURL(res.data)
                }
                this.showFrame = true
                this.pageUrl = url
            },
            dealCsv (res) {
                const csvData = []
                let count = 0
                const url = URL.createObjectURL(res.data)
                Papa.parse(url, {
                    download: true,
                    step: function (row) {
                        for (let i = 0; i < row.data.length; i++) {
                            const ele = []
                            ele.push(count)
                            ele.push(i)
                            ele.push(row.data[i])
                            csvData.push(ele)
                        }
                        count = count + 1
                    },
                    complete: function () {
                        Table.create(
                            '#csvTable',
                            () => 1500,
                            () => 600,
                            {
                                scrollable: true,
                                resizable: true,
                                selectable: true,
                                editable: false,
                                copyable: true
                            }
                        )
                            .formulaParser((v) => `${v}-formula`)
                            .data({
                                cells: csvData
                            })
                            .render()
                    }
                })
            }
        }
    }
</script>

<style lang="scss" scoped>
@import '@vue-office/docx/lib/index.css';
@import '@vue-office/excel/lib/index.css';
    .preview-file-tips {
        margin-bottom: 10px;
        color: #707070;
    }

.mainBody {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    .svg-loading {
        margin-right: 10px;
        animation: rotate-loading 1s linear infinite;
    }
    .iconBody{
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .mainMessage{
        font-size: 16px;
    }
    @keyframes rotate-loading {
        0% {
            transform: rotateZ(0);
        }

        100% {
            transform: rotateZ(360deg);
        }
    }
}
</style>
