<template>
  <BaseEdge :id="id" :path="path" :marker-end="markerEnd" :style="edgeStyle" />
  <EdgeLabelRenderer v-if="condText">
    <div
      class="cond-edge-label"
      :class="{ selected }"
      :style="{
        transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
      }"
    >
      {{ condText }}
    </div>
  </EdgeLabelRenderer>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { BaseEdge, EdgeLabelRenderer, getSmoothStepPath } from "@vue-flow/core";
import type { EdgeProps } from "@vue-flow/core";

const props = defineProps<EdgeProps>();

const smoothStep = computed(() =>
  getSmoothStepPath({
    sourceX: props.sourceX,
    sourceY: props.sourceY,
    sourcePosition: props.sourcePosition,
    targetX: props.targetX,
    targetY: props.targetY,
    targetPosition: props.targetPosition,
  }),
);

const path = computed(() => smoothStep.value[0]);
const labelX = computed(() => smoothStep.value[1]);
const labelY = computed(() => smoothStep.value[2]);

const condText = computed(() => String(props.data?.conditionExpression || ""));

const edgeStyle = computed(() => ({
  stroke: props.selected ? "#3b82f6" : "#94a3b8",
  strokeWidth: props.selected ? 2 : 1.5,
}));
</script>

<style scoped>
.cond-edge-label {
  position: absolute;
  pointer-events: all;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #475569;
  font-size: 11px;
  font-family: monospace;
  padding: 1px 6px;
  border-radius: 4px;
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cond-edge-label.selected {
  border-color: #3b82f6;
  color: #2563eb;
}
</style>
