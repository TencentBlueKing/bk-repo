<template>
    <div class="move-split-bar" draggable="false" :style="{ width: !Number(width) ? width : `${width}px` }"></div>
</template>
<script>
    export default {
        name: 'moveSplitBar',
        model: {
            prop: 'value',
            event: 'change'
        },
        props: {
            value: {
                type: Number,
                default: 0
            },
            minValue: {
                type: Number,
                default: 0
            },
            width: {
                type: [Number, String],
                default: 10
            }
        },
        data () {
            return {
                startDrag: false,
                offset: 0
            }
        },
        mounted () {
            this.$el.addEventListener('mousedown', this.dragDown)
            window.addEventListener('mousemove', this.dragMove)
            window.addEventListener('mouseup', this.dragUp)
        },
        beforeDestroy () {
            window.removeEventListener('mousemove', this.dragMove)
            window.removeEventListener('mouseup', this.dragUp)
        },
        methods: {
            dragDown (e) {
                this.startDrag = true
                this.offset = e.clientX - this.$el.getBoundingClientRect().left
            },
            dragMove (e) {
                if (!this.startDrag) return
                const clientX = e.clientX - this.offset
                if (clientX > this.minValue) this.$emit('change', clientX)
            },
            dragUp () {
                this.startDrag = false
            }
        }
    }
</script>
<style lang="scss" scoped>
.move-split-bar {
    height: 100%;
    cursor: col-resize;
}
</style>
