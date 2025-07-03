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
            <div v-if="showFrame" :style="`margin:0 auto;width:${pdfWidth};overflow-y:auto;height:700px`">
                <canvas
                    v-for="page in pdfPages"
                    :key="page"
                    :id="'pdfCanvas' + page"
                ></canvas>
            </div>
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
    import { isHtmlType } from '@repository/utils/file'
    import Papa from 'papaparse'
    import Table from '@wolf-table/table'

    const PDFJS = require('pdfjs-dist')
    PDFJS.GlobalWorkerOptions.isEvalSupported = false
    PDFJS.GlobalWorkerOptions.workerSrc = location.origin + '/ui/pdf.worker.js'

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
                loading: false,
                pdfPages: [], // 页数
                pdfWidth: '', // 宽度
                pdfSrc: '', // 地址
                pdfDoc: '', // 文档内容
                pdfScale: 1.5 // 放大倍数
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
                this.previewDialog.show = false
                this.loading = false
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
                this.loading = false
                let url
                if (!isHtmlType(this.filePath)) {
                    this.loadFile(URL.createObjectURL(res.data))
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
            },
            loadFile (url) {
                const loadingTask = PDFJS.getDocument(url)
                loadingTask.promise.then(pdf => {
                    this.pdfDoc = pdf
                    this.pdfPages = pdf.numPages
                    this.$nextTick(() => {
                        this.renderPage(1)
                    })
                })
            },
            renderPage (num) {
                this.pdfDoc.getPage(num).then(page => {
                    const canvas = document.getElementById('pdfCanvas' + num)
                    if (canvas === null) return
                    const ctx = canvas.getContext('2d')
                    const dpr = window.devicePixelRatio || 1
                    const viewport = page.getViewport({ scale: this.pdfScale })
                    canvas.width = viewport.width * dpr
                    canvas.height = viewport.height * dpr
                    canvas.style.width = viewport.width + 'px'
                    this.pdfWidth = viewport.width + 'px'
                    canvas.style.height = viewport.height + 'px'
                    ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
                    // 将 PDF 页面渲染到 canvas 上下文中
                    const renderContext = {
                        canvasContext: ctx,
                        viewport: viewport
                    }
                    page.render(renderContext)
                    if (this.pdfPages > num) {
                        this.renderPage(num + 1)
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
