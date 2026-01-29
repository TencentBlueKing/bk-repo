<template>
  <el-dialog :title="title" :visible.sync="showDialog" :before-close="close">
    <div style="width: calc(100% - 2px);height:calc(100vh);">
      <RelationGraph ref="graphRef1" :options="userGraphOptions" />
    </div>
  </el-dialog>
</template>

<script>
import RelationGraph from 'relation-graph'

export default {
  name: 'DetailDialog',
  components: { RelationGraph },
  props: {
    visible: Boolean,
    /**
     * 仅在更新模式时有值
     */
    sub: {
      type: Array,
      default: () => []
    },
    title: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      showDialog: this.visible,
      targetJson: {
        'nodes': [],
        'lines': []
      },
      userGraphOptions: {
        moveToCenterWhenRefresh: true,
        zoomToFitWhenRefresh: true,
        // defaultLineShape: 4,
        placeOtherGroup: true,
        defaultNodeWidth: 150,
        defaultNodeHeight: 30,
        // debug: true,
        // defaultExpandHolderPosition: 'right',
        reLayoutWhenExpandedOrCollapsed: true,
        useAnimationWhenExpanded: true,
        backgrounImage: '',
        backgrounImageNoRepeat: true,
        layout:
          {
            label: '树',
            layoutName: 'tree',
            layoutClassName: 'seeks-layout-center',
            from: 'top',
            // 通过这4个属性来调整 tree-层级距离&节点距离
            min_per_width: 200,
            max_per_width: 200,
            min_per_height: 40,
            max_per_height: undefined,
            levelDistance: '' // 如果此选项有值，则优先级高于上面那4个选项
          },
        defaultNodeBorderWidth: 0,
        defaultNodeShape: 1,
        // 'allowShowMiniToolBar': false,
        useAnimationWhenRefresh: true,
        defaultJunctionPoint: 'tb',
        defaultLineShape: 2
      }
    }
  },
  watch: {
    visible: function(newVal) {
      if (newVal) {
        this.showDialog = true
        // 对话框显示后再初始化图表
        this.$nextTick(() => {
          setTimeout(() => {
            this.setSecondData()
          }, 100)
        })
      } else {
        this.close()
      }
    }
  },
  mounted() {
  },
  methods: {
    setSecondData() {
      this.makeJson()
      this.$nextTick(() => {
        this.$refs.graphRef1.setJsonData(this.targetJson, (graphInstance) => {
          const nodes = graphInstance.getNodes()
          nodes.forEach(node => {
            if (this.targetJson.nodes.some(n => n.fixed && n.id === node.id)) {
              node.x = graphInstance.graphData.rootNode.x + node.x
              node.y = graphInstance.graphData.rootNode.y + node.y
            }
          })
        })
      })
    },
    makeJson() {
      const mainNode = {
        text: this.title,
        id: this.title,
        nodeShape: 1,
        width: 130,
        height: 35,
        expandHolderPosition: 'bottom'
      }
      this.targetJson.nodes.push(mainNode)
      for (let i = 0; i < this.sub.length; i++) {
        const node = {
          text: this.sub[i].name,
          id: this.sub[i].name,
          nodeShape: 1,
          width: 130,
          height: 35
        }
        const line = {
          from: this.title,
          to: this.sub[i].name
        }
        this.targetJson.nodes.push(node)
        this.targetJson.lines.push(line)
      }
    },
    close() {
      this.showDialog = false
      this.targetJson = {
        'nodes': [],
        'lines': []
      }
      this.$emit('update:visible', false)
    }
  }
}

</script>

<style scoped>

</style>
