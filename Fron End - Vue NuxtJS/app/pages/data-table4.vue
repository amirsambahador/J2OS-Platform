<script setup>
//  <!-- https://icones.js.org/collection/lucide -->
const config = useRuntimeConfig()
const API_BASE = config.public.API_BASE
const isError = ref(false)
//////////////////////////////////////////////////////////////////////////////////////////
const humanSelectedRow = ref()// رفرنس ردیف انتخاب شده
const humanTableRef = ref()// رفرنس جدول برای ریلود
const HUMAN_URL = ref(`${API_BASE}/getHumanBySQL?`)// آدرس API
const humanColumns = [
  {
    field: 'human_id',
    label: 'شناسه ⇅️',
    sortable: true
  },
  {
    field: 'name',
    label: 'نام کوچک ⇅️',
    sortable: true
  },
  {
    field: 'family',
    label: 'نام کامل',
    sortable: false
  }
]
const humanReloadTable = () => {
  humanSelectedRow.value = null
  humanTableRef.value?.reload()// لود مجدد جدول
}

function onError(e) {
  isError.value = true
  console.error('خطا در جدول :', e)
  // مثلاً toast نمایش بده
}
</script>

<template>
  <div>
    <div v-if="!isError">
      <h1>جدول انسان ها</h1>
      <br>
      <DataTable
        ref="humanTableRef"
        :url="HUMAN_URL"
        :columns="humanColumns"
        default-sort="human_id"
        row-key="human_id"
        empty-text="داده‌ای وجود نداشت"
        @error="onError"
        @selected="humanSelectedRow = $event"
      >
        <template #before-search/>

        <template #after-search>
          <UButton
            variant="outline"
            icon="i-lucide-refresh-ccw"
            @click="humanReloadTable"
          />
        </template>
      </DataTable>
    </div>
    <div v-else>
      با عرض پوزش خطایی رخ داده است!
    </div>
  </div>
</template>
