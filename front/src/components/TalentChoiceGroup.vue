<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number, Array], default: '' },
  label: { type: String, required: true },
  options: { type: Array, default: () => [] },
  required: { type: Boolean, default: false },
  attempted: { type: Boolean, default: false },
  multiple: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'change'])
const normalized = computed(() => props.options.map(option => typeof option === 'object' ? option : { label: option, value: option }))
function selected(value) { return props.multiple ? (props.modelValue || []).includes(value) : props.modelValue === value }
function choose(value) {
  let next = value
  if (props.multiple) {
    const current = Array.isArray(props.modelValue) ? [...props.modelValue] : []
    next = current.includes(value) ? current.filter(item => item !== value) : [...current, value]
  }
  emit('update:modelValue', next)
  emit('change', next)
}
</script>

<template>
  <fieldset class="talent-choice" :class="{ missing: attempted && required && (multiple ? !modelValue?.length : !modelValue), compact }">
    <legend>{{ label }} <span v-if="required">必填</span></legend>
    <div class="talent-choice-options">
      <button v-for="option in normalized" :key="option.value" type="button" :class="{ selected: selected(option.value) }" :aria-pressed="selected(option.value)" @click="choose(option.value)">{{ option.label }}</button>
    </div>
  </fieldset>
</template>

<style scoped>
.talent-choice{min-width:0;margin:20px 0 0;padding:0;border:0}.talent-choice legend{margin-bottom:9px;padding:0;color:#4e5969;font-size:12px;font-weight:550}.talent-choice legend span{display:inline-flex;margin-left:5px;padding:2px 6px;border-radius:999px;background:#fff0ed;color:#c75d4b;font-size:9px;font-weight:700}.talent-choice-options{display:flex;align-items:center;flex-wrap:wrap;gap:7px}.talent-choice button{min-height:34px;padding:8px 12px;border:1px solid #d9dce3;border-radius:10px;background:#f7f8fa;color:#646a73;font-size:12px;line-height:1.3;transition:background .18s,border-color .18s,color .18s,box-shadow .18s}.talent-choice button:hover{background:#eef2ff;color:#245bdb}.talent-choice button.selected{background:#e8f0ff;border-color:#8fb0ff;color:#245bdb;box-shadow:0 0 0 2px rgba(51,112,255,.1)}.talent-choice button:focus-visible{outline:2px solid #8fb0ff;outline-offset:2px}.talent-choice.compact button{padding-inline:24px}.talent-choice.missing{padding:12px;border:1px solid #df7667;border-radius:12px;background:#fff3f1;animation:required-flash .42s ease 2}@keyframes required-flash{50%{box-shadow:0 0 0 4px rgba(213,89,70,.17)}}
</style>
