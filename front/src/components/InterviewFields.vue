<template>
  <section class="interview-fields">
    <div v-if="mode !== 'evaluation'" class="interview-grid">
      <label>面试时间 <small v-if="optional">选填</small><input :value="value.scheduledAt || ''" type="datetime-local" @input="set('scheduledAt',$event.target.value)" /></label>
      <label>面试官 <small v-if="optional">选填</small><HrPicker :model-value="value.owner || ''" :employees="employees" placeholder="输入姓名搜索面试官" @update:model-value="set('owner',$event)" /></label>
      <fieldset><legend>轮次</legend><button v-for="item in ['初面','二面','三面','终面']" :key="item" type="button" :class="{active:value.round===item}" @click="set('round',item)">{{item}}</button></fieldset>
      <fieldset><legend>方式</legend><button v-for="item in ['现场','视频','电话','作品测试']" :key="item" type="button" :class="{active:value.method===item}" @click="set('method',item)">{{item}}</button></fieldset>
    </div>
    <div v-if="mode !== 'schedule'" class="interview-evaluation">
      <fieldset><legend>当前面试结果 <small v-if="optional">可不填</small></legend><button v-for="item in ['强烈推荐','推荐','保留','不推荐']" :key="item" type="button" :class="{active:value.result===item}" @click="set('result',value.result===item?'':item)">{{item}}</button></fieldset>
      <fieldset v-if="value.result"><legend>综合评分</legend><button v-for="item in [1,2,3,4,5]" :key="item" type="button" :class="{active:Number(value.score)===item}" @click="set('score',item)">{{item}}</button></fieldset>
      <label v-if="value.result">事实、评价与依据<textarea :value="value.feedback || ''" rows="3" maxlength="4000" placeholder="记录具体表现、优势、风险和判断依据" @input="set('feedback',$event.target.value)" /></label>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import HrPicker from '@/components/HrPicker.vue'
const props = defineProps({ modelValue: { type:Object, required:true }, employees:{type:Array,default:()=>[]}, mode:{type:String,default:'schedule'}, optional:Boolean })
const emit = defineEmits(['update:modelValue'])
const value = computed(() => props.modelValue || {})
function set(key, next) { emit('update:modelValue', { ...value.value, [key]: next }) }
function hasValue() { return ['scheduledAt','owner','round','method','result','score','feedback'].some(key => String(value.value[key] ?? '').trim()) }
function validate() {
  if (props.optional && !hasValue()) return ''
  if (props.mode !== 'evaluation' && (!value.value.scheduledAt || !value.value.owner || !value.value.round || !value.value.method)) return '请把当前面试的时间、面试官、轮次和方式填写完整，或全部留空。'
  if (props.mode !== 'schedule' && value.value.result && (!value.value.score || !String(value.value.feedback || '').trim())) return '填写面试结果时，请同时选择 1—5 分并填写评价依据。'
  if (props.mode === 'evaluation' && !value.value.result) return '请选择面试结论。'
  return ''
}
defineExpose({ validate, hasValue })
</script>

<style scoped>
.interview-fields{display:grid;gap:18px;grid-column:1/-1;padding:16px;border:1px solid #d9e2ec;border-radius:13px;background:#f8fafc}.interview-grid{display:grid;grid-template-columns:1fr 1fr;gap:15px}.interview-fields label{display:grid;gap:8px;color:#475467;font-size:12px;font-weight:600}.interview-fields label small,.interview-fields legend small{color:#98a2b3;font-weight:400}.interview-fields input,.interview-fields textarea{box-sizing:border-box;width:100%;min-height:42px;border:1px solid #cfd8e3;border-radius:10px;padding:10px 12px;background:#fff;color:#172033;font:inherit}.interview-fields textarea{resize:vertical}.interview-fields fieldset{margin:0;padding:0;border:0}.interview-fields legend{margin-bottom:8px;color:#475467;font-size:12px;font-weight:600}.interview-fields button{margin:0 6px 6px 0;border:1px solid #d7dee8;border-radius:9px;padding:8px 12px;background:#fff;color:#667085}.interview-fields button.active{border-color:#8eabe0;background:#eaf1ff;color:#245bdb}.interview-evaluation{display:grid;gap:14px}@media(max-width:600px){.interview-grid{grid-template-columns:1fr}}
</style>
