<template>
    <div>
        <vue-office-excel
            v-if="previewExcel"
            :src="dataSource"
            :options="excelOptions"
            style="max-height: 100vh; overflow-y: auto"
        />
        <iframe v-if="showFrame" :src="pageUrl" frameborder="0" style="width: 100%; height: 100%"></iframe>
        <div v-if="pdfShow" class="pdf-container-wrapper"">
            <canvas
                v-for="page in pdfPages"
                :key="page"
                :id="'pdfCanvas' + page"
            ></canvas>
        </div>
        <div v-if="previewBasic" class="flex-column flex-center">
            <div class="preview-file-tips">{{ $t('previewFileTips') }}</div>
            <textarea id="basicFileText" v-model="basicFileText" class="textarea" readonly></textarea>
        </div>
        <div v-if="imgShow" style="width: 100%; height: 100%">
            <img id="image" :src="imgUrl" alt="Picture" style="max-width: 100%; max-height: 100%;">
        </div>
        <div v-if="csvShow" id="csvTable"></div>
        <div v-if="hasError" class="empty-data-container flex-center" style="background-color: white; height: 100%">
            <div class="flex-column flex-center">
                <img width="480" height="240" style="float: left;margin-right: 3px" src="/ui/440.svg" />
                <span class="mt5 error-data-title">{{ $t('previewErrorTip') }}</span>
            </div>
        </div>
        <div v-if="loading" class="mainBody">
            <div class="iconBody">
                <Icon name="loading" size="80" class="svg-loading" />
                <span class="mainMessage">{{ $t('fileLoadingTip') }}</span>
            </div>
        </div>
    </div>
</template>
<script>
    import VueOfficeExcel from '@vue-office/excel'
    import {
        customizePreviewRemoteOfficeFile,
        getPreviewRemoteOfficeFileInfo
    } from '@repository/utils/previewOfficeFile'
    import { mapActions } from 'vuex'
    import { Base64 } from 'js-base64'
    import { isExcel, isHtmlType, isOutDisplayType, isPic, isText } from '@repository/utils/file'
    import Papa from 'papaparse'
    import Table from '@wolf-table/table'
    import Viewer from 'viewerjs'

    const PDFJS = require('pdfjs-dist')
    PDFJS.GlobalWorkerOptions.isEvalSupported = false
    PDFJS.GlobalWorkerOptions.workerSrc = location.origin + '/ui/pdf.worker.js'

    export default {
        name: 'OutsideFilePreview',
        components: { VueOfficeExcel },
        props: {
            extraParam: String
        },
        data () {
            return {
                dataSource: '',
                previewDialog: {
                    title: '',
                    show: false,
                    isLoading: false
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
                previewBasic: false,
                basicFileText: '',
                hasError: false,
                pageUrl: '',
                showFrame: false,
                loading: false,
                imgShow: false,
                imgUrl: '',
                csvShow: false,
                pdfShow: false,
                pdfPages: [], // 页数
                pdfWidth: '', // 宽度
                pdfSrc: '', // 地址
                pdfDoc: '', // 文档内容
                pdfScale: 1.5 // 放大倍数
            }
        },
        computed: {
            projectId () {
                return this.$route.params.projectId
            },
            enableMultipleTypeFilePreview () {
                return RELEASE_MODE === 'community' || RELEASE_MODE === 'tencent'
            }
        },
        async created () {
            this.loading = true
            if (!this.enableMultipleTypeFilePreview) {
                this.showError()
                return
            }
            try {
                const param = Base64.decode(decodeURIComponent(this.extraParam))
                await getPreviewRemoteOfficeFileInfo(Base64.encode(param)).then(res => {
                    // 需解析传递参数，如果传递参数里面携带，优先渲染传递的水印
                    const obj = JSON.parse(param)
                    if (obj.watermarkTxt) {
                        const watermark = {
                            watermarkTxt: obj.watermarkTxt,
                            watermark_x_space: obj.watermarkXSpace ? Number(obj.watermarkXSpace) : 0,
                            watermark_y_space: obj.watermarkYSpace ? Number(obj.watermarkYSpace) : 0,
                            watermark_font: obj.watermarkFont ? obj.watermarkFont : '',
                            watermark_fontsize: obj.watermarkFontsize ? obj.watermarkFontsize : '',
                            watermark_color: obj.watermarkColor ? obj.watermarkColor : '',
                            watermark_alpha: obj.watermarkAlpha ? obj.watermarkAlpha : '',
                            watermark_width: obj.watermarkWidth ? Number(obj.watermarkWidth) : 0,
                            watermark_height: obj.watermarkHeight ? Number(obj.watermarkHeight) : 0,
                            watermark_angle: obj.watermarkHeight ? Number(obj.watermarkAngle) : 0
                        }
                        this.initWaterMark(watermark)
                    } else if (res.data.data.watermark && res.data.data.watermark.watermarkTxt && res.data.data.watermark.watermarkTxt != null) {
                        this.initWaterMark(res.data.data.watermark)
                    }
                    if (isOutDisplayType(res.data.data.suffix)) {
                        customizePreviewRemoteOfficeFile(Base64.encode(Base64.decode(this.extraParam))).then(fileDate => {
                            this.loading = false
                            if (isExcel(res.data.data.suffix)) {
                                this.previewExcel = true
                                this.excelOptions.xls = res.data.data.suffix.endsWith('xls')
                                this.dataSource = fileDate.data
                            } else if (isHtmlType(res.data.data.suffix)) {
                                const url = URL.createObjectURL(fileDate.data)
                                this.showFrame = true
                                this.pageUrl = url
                            } else if (isText(res.data.data.suffix)) {
                                this.previewBasic = true
                                const reader = new FileReader()
                                reader.onload = function (event) {
                                    // 读取的文本内容,强行赋值渲染
                                    document.getElementById('basicFileText').value = Base64.decode(event.target.result)
                                }
                                reader.readAsText(fileDate.data)
                            } else if (isPic(res.data.data.suffix)) {
                                this.imgShow = true
                                this.imgUrl = URL.createObjectURL(fileDate.data)
                                this.$nextTick(() => {
                                    const viewer = new Viewer(document.getElementById('image'), {
                                        inline: true,
                                        viewed () {
                                            viewer.zoomTo(1)
                                        }
                                    })
                                })
                            } else if (res.data.data.suffix.endsWith('csv')) {
                                this.csvShow = true
                                this.dealCsv(fileDate)
                            } else {
                                this.pdfShow = true
                                this.loadFile(URL.createObjectURL(fileDate.data))
                            }
                        })
                    } else {
                        this.showError()
                    }
                })
            } catch (error) {
                console.error(error)
                this.showError()
            }
        },
        destroyed () {
            this.cancel()
        },
        methods: {
            ...mapActions([
                'previewBasicFile'
            ]),
            showError () {
                this.loading = false
                this.hasError = true
            },
            cancel () {
                this.dataSource = ''
                this.previewExcel = false
                this.previewBasic = false
                this.showFrame = false
                this.pageUrl = ''
                this.imgShow = false
                this.imgUrl = ''
                this.hasError = false
                this.csvShow = false
                this.excelOptions.xls = false
                window.resetWaterMark()
            },
            initWaterMark (param) {
                window.initWaterMark(param)
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
                            () => window.innerWidth,
                            () => 900,
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
@import 'viewerjs/dist/viewer.css';
/* 添加到现有项目中 */
.pdf-container-wrapper {
    position: relative !important;
    width: 100% !important;
    height: 100vh !important;
    overflow: auto !important;
    background: #f5f5f5 !important;
}

/* 关键：绝对定位居中 */
.pdf-container {
    position: absolute !important;
    left: 50% !important;
    transform: translateX(-50%) !important;
    top: 0 !important;
    padding: 20px !important;
    min-width: 100% !important;
    box-sizing: border-box !important;
}

/* Canvas样式重置 */
canvas {
    display: block !important;
    margin: 0 auto 20px auto !important;
    max-width: 100% !important;
    height: auto !important;
    background: white !important;
}
.preview-file-tips {
    margin-bottom: 10px;
    color: #707070;
}
.textarea {
    resize: none;
    width: 100%;
    height: 700px;
    max-height: 700px;
    overflow-y: auto;
    border: 1px solid #ccc;
    padding: 0 5px;
}
.error-data-title {
    margin-top: 18px;
    font-size: 24px;
    line-height: 32px;
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
