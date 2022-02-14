
<template>
    <canvas ref="qrcode"></canvas>
</template>

<script>
    import QRCode from 'qrcode'

    export default {
        props: {
            text: {
                type: String,
                required: true
            },
            size: {
                type: Number,
                required: false,
                default: 128
            },
            color: {
                type: String,
                required: false,
                default: '#000'
            },
            bgColor: {
                type: String,
                required: false,
                default: '#FFF'
            }
        },
        watch: {
            text () {
                this.createQRCode()
            }
        },
        mounted () {
            this.createQRCode()
        },
        methods: {
            createQRCode () {
                if (!this.text) return
                QRCode.toCanvas(this.$refs.qrcode, this.text, {
                    errorCorrectionLevel: 'L',
                    width: this.size,
                    height: this.size,
                    color: {
                        dark: this.color,
                        light: this.bgColor
                    }
                })
            }
        }
    }
</script>
