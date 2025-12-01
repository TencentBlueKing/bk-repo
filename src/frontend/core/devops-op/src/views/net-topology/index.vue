<template>
  <div ref="mypage">
    <div v-loading="g_loading" style="width: calc(100% - 2px);height:calc(100vh);">
      <RelationGraph ref="graphRef" :options="userGraphOptions">
        <template #node="{node}">
          <div v-if="node.data.type === 'total-service'" style="display: flex; justify-content: center; align-items: center; height: 100%" @click="showNodeTips(node, $event)">
            <div style="text-align: center;" @mouseout="hideNodeTips(node, $event)">
              {{ node.text }}
            </div>
          </div>
        </template>
      </RelationGraph>
    </div>
    <detail-dialog :visible.sync="showDialog" :sub="sub" :title="title" />
  </div>
</template>

<script>

import RelationGraph from 'relation-graph'
import DetailDialog from '@/views/net-topology/components/detail.vue'
import { credentials } from '@/api/storage'
import { queryByLevel, queryRelationByName } from '@/api/nt'

export default {
  name: 'NT',
  components: { DetailDialog, RelationGraph },
  data() {
    return {
      newData: [{
        name: '流量', subset: []
      }],
      top: ['流量'],
      gateway: [],
      sortedGateWay: ['idc', 'devnet', 'devx'],
      service: [],
      targetJson: {
        'nodes': [],
        'lines': []
      },
      showDialog: false,
      sub: [],
      title: '',
      isShowCodePanel: false,
      isShowNodeTipsPanel: false,
      nodeMenuPanelPosition: { x: 0, y: 0 },
      currentNode: {},
      g_loading: true,
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
            min_per_width: 300,
            max_per_width: 400,
            min_per_height: 40,
            max_per_height: undefined,
            levelDistance: '' // 如果此选项有值，则优先级高于上面那4个选项
          },
        defaultNodeBorderWidth: 0,
        defaultNodeShape: 1,
        // 'allowShowMiniToolBar': false,
        useAnimationWhenRefresh: true,
        defaultJunctionPoint: 'border',
        defaultLineShape: 2,
        defaultLineColor: '#000000'
      }
    }
  },
  created() {
    this.getData()
  },
  methods: {
    async getData() {
      const gateway = (await queryByLevel('GATEWAY')).data
      const service = (await queryByLevel('SERVICE')).data
      this.service = service
      this.gateway = gateway
      const resultGateWay = []
      for (let i = 0; i < this.sortedGateWay.length; i++) {
        const index = gateway.findIndex((e) => (e.name.toLowerCase()).includes(this.sortedGateWay[i]))
        if (index > -1) {
          resultGateWay.push(gateway[index])
        }
      }
      for (let i = 0; i < gateway.length; i++) {
        if (!resultGateWay.find((e) => e.name === gateway[i].name)) {
          resultGateWay.push(gateway[i])
        }
      }
      for (let i = 0; i < service.length; i++) {
        this.newData.push({
          name: (service[i].tag === '') ? service[i].name : service[i].name + '<br>' + service[i].tag,
          subset: []
        })
      }
      for (let i = 0; i < resultGateWay.length; i++) {
        const gatewayName = (resultGateWay[i].tag === '') ? resultGateWay[i].name : resultGateWay[i].name + '<br>' + resultGateWay[i].tag
        this.newData.push({
          name: gatewayName,
          subset: []
        })
        this.newData[0].subset.push({
          name: gatewayName,
          desc: ''
        })
        // 现阶段只有gateway有具体关系在此表
        const relation = (await queryRelationByName(resultGateWay[i].name)).data
        for (let j = 0; j < relation.length; j++) {
          const index = this.newData.findIndex((e) => e.name === gatewayName)
          const next = this.service.findIndex((e) => e.name === relation[j].next) >= 0
            ? this.service.find((e) => e.name === relation[j].next) : this.gateway.find((e) => e.name === relation[j].next)
          if (next) {
            const nextName = (next.tag === '') ? next.name : next.name + '<br>' + next.tag
            this.newData[index].subset.push({
              name: nextName,
              desc: relation[j].forwardTip
            })
          }
        }
      }
      this.getStorage()
    },
    getStorage() {
      credentials().then(res => {
        for (let i = 0; i < this.service.length; i++) {
          const serviceName = (this.service[i].tag === '') ? this.service[i].name : this.service[i].name + '<br>' + this.service[i].tag
          const index = this.newData.findIndex((e) => e.name === serviceName)
          const region = this.service[i].region
          for (let j = 0; j < res.data.length; j++) {
            if (res.data[j].storageRegion === region) {
              this.newData[index].subset.push({
                name: res.data[j].key,
                desc: ''
              })
              if (!this.newData.some((e) => e.name === res.data[j].key)) {
                this.newData.push({
                  name: res.data[j].key,
                  subset: []
                })
              }
            }
          }
        }
        this.setGraphData()
      })
    },
    dealJson() {
      const relation = this.findNodesWithMultipleParents(this.newData)
      for (let i = 0; i < this.newData.length; i++) {
        // 构造data.type === 'total-service'的节点逻辑尚未明确,此为点击展开详情的
        const node = {
          text: this.newData[i].name,
          id: this.newData[i].name,
          nodeShape: 1,
          width: 180,
          height: 50
        }
        let expand = false
        let hasMulti = false
        if (this.newData[i].subset.length > 0) {
          expand = true
          for (let j = 0; j < this.newData[i].subset.length; j++) {
            const line = {
              from: this.newData[i].name,
              to: this.newData[i].subset[j].name,
              text: this.newData[i].subset[j].desc
            }
            const parents = relation.get(this.newData[i].subset[j].name)
            // 判断子类是否多父，且父类超过2个是同级的
            if (parents.size > 1 && (
              this.checkHasSameLevelParent(parents, this.gateway) ||
              this.checkHasSameLevelParent(parents, this.service)
            )) {
              line.lineShape = 6
              hasMulti = true
            }
            if (this.newData[i].subset[j].desc !== '') {
              // line.animation = 1
              line.lineShape = 6
              line.useTextPath = true
              line.color = '#00ced1'
            }
            this.targetJson.lines.push(line)
          }
        }
        if (expand && !hasMulti) {
          if (this.userGraphOptions.layout.from === 'top') {
            node.expandHolderPosition = 'bottom'
          } else {
            node.expandHolderPosition = 'right'
          }
        }
        this.targetJson.nodes.push(node)
      }
    },
    checkHasSameLevelParent(nameSet, objectList) {
      if (nameSet.size < 2) return false
      const objectNameSet = new Set(objectList.map(obj => obj.name))
      const intersection = new Set(
        [...nameSet].filter(name => objectNameSet.has(name))
      )
      return intersection.size >= 2
    },
    findNodesWithMultipleParents(data) {
      const childToParents = new Map()
      data.forEach(parentNode => {
        if (parentNode.subset && parentNode.subset.length > 0) {
          parentNode.subset.forEach(childNode => {
            const childName = childNode.name
            if (!childToParents.has(childName)) {
              childToParents.set(childName, new Set())
            }
            childToParents.get(childName).add(parentNode.name)
          })
        }
      })
      return childToParents
    },
    setGraphData() {
      this.dealJson()
      this.g_loading = false
      this.$refs.graphRef.setJsonData(this.targetJson, (graphInstance) => {
        const nodes = graphInstance.getNodes()
        nodes.forEach(node => {
          if (this.targetJson.nodes.some(n => n.fixed && n.id === node.id)) {
            node.x = graphInstance.graphData.rootNode.x + node.x
            node.y = graphInstance.graphData.rootNode.y + node.y
          }
        })
      })
    },
    showNodeTips(nodeObject, $event) {
      this.currentNode = nodeObject
      this.sub = nodeObject.data.sub
      this.title = nodeObject.text
      this.showDialog = true
    },
    hideNodeTips(nodeObject, $event) {
      this.isShowNodeTipsPanel = false
    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
</style>
