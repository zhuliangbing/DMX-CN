<template>
  <div class="dm5-topicmap-select">
    <el-select v-model="topicmapId">
      <el-option-group label="选择主题图">
        <el-option v-for="topic in topics" :label="topic.value" :value="topic.id" :key="topic.id"></el-option>
      </el-option-group>
    </el-select>
    <el-button type="text" class="fa fa-info-circle" title="显示主题图主题" @click="revealTopicmapTopic">
    </el-button>
    <el-button type="text" class="fa fa-arrows-alt" title="缩放到合适大小" @click="fitTopicmapViewport"></el-button>
    <el-button type="text" class="fa fa-compress" title="重置缩放并居中" @click="resetTopicmapViewport">
    </el-button>
  </div>
</template>

<script>
export default {

  computed: {

    topicmapId: {
      get () {
        return this.$store.getters.topicmapId
      },
      set (id) {
        this.$store.dispatch('selectTopicmap', id)
      }
    },

    workspaceId () {
      return this.$store.state.workspaces.workspaceId
    },

    topics () {
      // Note 1: while initial rendering no workspace is selected yet
      // Note 2: when the workspace is switched its topicmap topics might not yet loaded
      return this.$store.state.topicmaps.topicmapTopics[this.workspaceId]
    }
  },

  methods: {

    revealTopicmapTopic () {
      this.$store.dispatch('revealTopicById', this.topicmapId)
    },

    fitTopicmapViewport () {
      this.$store.dispatch('fitTopicmapViewport')
    },

    resetTopicmapViewport () {
      this.$store.dispatch('resetTopicmapViewport')
    }
  }
}
</script>

<style>
.dm5-topicmap-select {
  margin-left: 18px;
}

.dm5-topicmap-select .el-button {
  padding-left:  2px !important;
  padding-right: 2px !important;
  margin-left: 0 !important;
}
</style>
