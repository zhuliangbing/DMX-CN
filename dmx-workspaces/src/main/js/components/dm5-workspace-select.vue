<template>
  <div class="dm5-workspace-select">
    <el-select v-model="workspaceId">
      <el-option-group label="选择工作空间">
        <el-option v-for="topic in workspaceTopics" :label="topic.value" :value="topic.id" :key="topic.id"></el-option>
      </el-option-group>
    </el-select>
    <el-button type="text" class="fa fa-info-circle" title="显示工作空间主题" @click="revealWorkspaceTopic">
    </el-button>
  </div>
</template>

<script>
export default {

  computed: {

    workspaceId: {
      get () {
        return this.$store.state.workspaces.workspaceId
      },
      set (id) {
        this.$store.dispatch('selectWorkspace', id)
      }
    },

    workspaceTopics () {
      return this.$store.state.workspaces.workspaceTopics
    }
  },

  methods: {
    revealWorkspaceTopic () {
      this.$store.dispatch('revealTopicById', this.workspaceId)
    }
  }
}
</script>

<style>
.dm5-workspace-select .el-button {
  padding-left:  2px !important;
  padding-right: 2px !important;
}
</style>
