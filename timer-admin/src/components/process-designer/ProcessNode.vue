<template>
  <div class="process-node" :class="[`nt-${data.dslType}`, { selected }]" :style="{ '--ntc': meta.color }">
    <Handle
      v-if="data.dslType !== 'START'"
      type="target"
      :position="Position.Left"
      class="pf-handle"
    />
    <div class="pn-icon">{{ meta.glyph }}</div>
    <div class="pn-body">
      <div class="pn-name">{{ data.name || meta.label }}</div>
      <div class="pn-id">{{ id }}</div>
    </div>
    <Handle
      v-if="data.dslType !== 'END'"
      type="source"
      :position="Position.Right"
      class="pf-handle"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { Handle, Position } from "@vue-flow/core";
import type { NodeProps } from "@vue-flow/core";
import { nodeTypeMeta } from "@/utils/flowDsl";

const props = defineProps<NodeProps>();

const meta = computed(() => nodeTypeMeta(props.data?.dslType));
</script>

<style scoped>
.process-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 180px;
  max-width: 220px;
  padding: 10px 12px;
  background: #ffffff;
  border: 1.5px solid #e2e8f0;
  border-left: 4px solid var(--ntc);
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
  cursor: grab;
  transition:
    box-shadow 0.15s,
    border-color 0.15s;
}

.process-node:hover {
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.12);
}

.process-node.selected {
  border-color: #3b82f6;
  border-left-color: var(--ntc);
  box-shadow:
    0 0 0 2px rgba(59, 130, 246, 0.25),
    0 4px 12px rgba(15, 23, 42, 0.12);
}

.pn-icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: var(--ntc);
  font-size: 13px;
  flex-shrink: 0;
}

.pn-body {
  min-width: 0;
}

.pn-name {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
}

.pn-id {
  font-size: 11px;
  color: #94a3b8;
  font-family: monospace;
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 150px;
}

.pf-handle {
  width: 9px;
  height: 9px;
  background: #ffffff;
  border: 2px solid #94a3b8;
  transition:
    background 0.15s,
    border-color 0.15s;
}

.pf-handle:hover {
  background: #3b82f6;
  border-color: #3b82f6;
}
</style>
