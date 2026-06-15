<script setup lang="ts">
// 多选下拉 + chip 显示。对齐 ACC 大货运单的"服务/快线"多选筛选风格。
// 用法:
//   <MultiSelect v-model="filters.channels" :options="channelOpts" placeholder="选择产品" />
// options: [{ value, label }]
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps<{
  modelValue: string[]
  options: { value: string; label: string }[]
  placeholder?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>()

const open = ref(false)
const search = ref('')
const wrapEl = ref<HTMLElement | null>(null)

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return props.options
  return props.options.filter(
    o => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q),
  )
})

function toggle(v: string) {
  const arr = props.modelValue.slice()
  const i = arr.indexOf(v)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(v)
  emit('update:modelValue', arr)
}
function remove(v: string) {
  emit('update:modelValue', props.modelValue.filter(x => x !== v))
}
function clearAll() {
  emit('update:modelValue', [])
}
function labelOf(v: string): string {
  return props.options.find(o => o.value === v)?.label ?? v
}
function onDocClick(e: MouseEvent) {
  if (wrapEl.value && !wrapEl.value.contains(e.target as Node)) open.value = false
}
onMounted(() => document.addEventListener('click', onDocClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocClick))
</script>

<template>
  <div ref="wrapEl" class="ms-wrap">
    <div class="ms-trigger" :class="{ open }" @click="open = !open">
      <span v-if="!modelValue.length" class="ms-placeholder">{{ placeholder || '请选择' }}</span>
      <span v-for="v in modelValue" :key="v" class="ms-chip" @click.stop>
        {{ labelOf(v) }}
        <span class="ms-chip-x" @click.stop="remove(v)">✕</span>
      </span>
      <span v-if="modelValue.length" class="ms-clear" @click.stop="clearAll" title="清空">×</span>
      <span class="ms-caret">▾</span>
    </div>
    <div v-if="open" class="ms-pop">
      <input
        v-model="search"
        class="ms-search"
        placeholder="搜索..."
        @click.stop
      />
      <div class="ms-list">
        <label v-for="opt in filtered" :key="opt.value" class="ms-opt">
          <input
            type="checkbox"
            :checked="modelValue.includes(opt.value)"
            @change="toggle(opt.value)"
          />
          {{ opt.label }}
        </label>
        <div v-if="!filtered.length" class="ms-empty">无匹配项</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ms-wrap { position: relative; min-width: 180px; }
.ms-trigger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  min-height: 30px;
  padding: 4px 22px 4px 8px;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  position: relative;
}
.ms-trigger.open { border-color: #3b82f6; box-shadow: 0 0 0 2px rgba(59,130,246,0.2); }
.ms-placeholder { color: #9ca3af; padding: 1px 2px; }
.ms-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #dbeafe;
  color: #1e40af;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
  line-height: 1.4;
}
.ms-chip-x { cursor: pointer; opacity: 0.7; font-size: 10px; }
.ms-chip-x:hover { opacity: 1; color: #b91c1c; }
.ms-clear {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: #94a3b8;
  cursor: pointer;
  padding: 0 4px;
}
.ms-clear:hover { color: #475569; }
.ms-caret {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 10px;
  color: #94a3b8;
  pointer-events: none;
}
.ms-pop {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 2px;
  min-width: 100%;
  max-width: 360px;
  background: #fff;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
  z-index: 50;
  max-height: 320px;
  display: flex;
  flex-direction: column;
}
.ms-search {
  padding: 6px 8px;
  border: none;
  border-bottom: 1px solid #e2e8f0;
  font-size: 12px;
  outline: none;
}
.ms-list {
  overflow-y: auto;
  flex: 1;
  padding: 4px 0;
}
.ms-opt {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}
.ms-opt:hover { background: #f1f5f9; }
.ms-opt input { margin: 0; }
.ms-empty { padding: 8px; text-align: center; color: #94a3b8; font-size: 12px; }
</style>
