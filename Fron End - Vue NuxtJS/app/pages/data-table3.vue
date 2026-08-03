<script setup>
//  <!-- https://icones.js.org/collection/lucide -->
const config = useRuntimeConfig()
const API_BASE = config.public.API_BASE

const isHumanError = ref(false)
const isInformationError = ref(false)

//////////////////////////////////////////////////////////////////////////////////////////
const humanSelectedRow = ref()// رفرنس ردیف انتخاب شده
const humanTableRef = ref()// رفرنس جدول برای ریلود
const HUMAN_URL = ref(`${API_BASE}/getHuman?`)// آدرس API
const humanColumns = [
  {
    field: 'humanId',
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
//////////////////////////////////////////////////////////////////////////////////////////
const informationSelectedRow = ref()// رفرنس ردیف انتخاب شده
const informationTableRef = ref()// رفرنس جدول برای ریلود
const INFORMATION_URL = ref('')// آدرس API
const informationColumns = [
  {
    field: 'informationId',
    label: 'شناسه ⇅️',
    sortable: true
  },
  {
    field: 'content',
    label: 'نام کوچک ⇅️',
    sortable: true
  }
]
const informationReloadTable = () => {
  informationSelectedRow.value = null
  informationTableRef.value?.reload()// لود مجدد جدول
}

const showInformationSelectedRow = () => {
  alert(informationSelectedRow.value.informationId)
  informationReloadTable()// لود مجدد جدول
}
//////////////////////////////////////////////////////////////////////////////////////////
watch(humanSelectedRow, (newValue) => {
  informationSelectedRow.value = null
  INFORMATION_URL.value = newValue
    ? `${API_BASE}/getInformation?humanId=${newValue.humanId}&`
    : ''
})

function onHumanError(e) {
  isHumanError.value = true
  console.error('خطا در جدول انسان‌ها:', e)
  // مثلاً toast نمایش بده
}

function onInformationError(e) {
  isInformationError.value = true
  console.error('خطا در جدول اطلاعات:', e)
  // مثلاً toast نمایش بده
}
</script>

<template>
  <div>
    <div v-if="!isHumanError">
      <h1>جدول انسان ها</h1>
      <br>
      <DataTable
        ref="humanTableRef"
        :url="HUMAN_URL"
        :columns="humanColumns"
        default-sort="humanId"
        row-key="humanId"
        empty-text="داده‌ای وجود نداشت"
        @error="onHumanError"
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

    <template v-if="!isHumanError">
      <br>
      <h1>جدول اطلاعات با حذف وضعیت حتی با تغییر جدول انسان و رفرش جدول فرزند</h1>
      <br>
      <div v-if="!isInformationError">
        <DataTable
          ref="informationTableRef"
          :url="INFORMATION_URL"
          :columns="informationColumns"
          default-sort="informationId"
          row-key="informationId"
          empty-text="اطلاعاتی برای نمایش وجود ندارد"
          @error="onInformationError"
          @selected="informationSelectedRow = $event"
        >
          <template #before-search>
            <UButton
              color="j2os-info"
              icon="i-lucide-eye"
              :disabled="!informationSelectedRow"
              @click="showInformationSelectedRow"
            />
          </template>

          <template #after-search>
            <UButton
              variant="outline"
              icon="i-lucide-refresh-ccw"
              @click="informationReloadTable"
            />
          </template>
        </DataTable>
      </div>
      <div v-else>
        با عرض پوزش خطایی در جدول اطلاعات رخ داده است!
      </div>
    </template>
  </div>
</template>
