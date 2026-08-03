<script setup>
//  <!-- https://icones.js.org/collection/lucide -->
const router = useRouter()
const selectedRow = ref()// رفرنس ردیف انتخاب شده
const wikiTableRef = ref()// رفرنس جدول برای ریلود
const config = useRuntimeConfig()
const API_BASE = config.public.API_BASE
const URL = ref(`${API_BASE}/getWikiFilter?`)// آدرس API
const columns = [// ستون های جدول
  {
    field: 'wikiId',
    label: 'شناسه ⇅️',
    sortable: true
  },
  {
    field: 'title',
    label: 'عنوان ⇅️',
    sortable: true
  },
  {
    field: 'content',
    label: 'محتوا ⇅️',
    sortable: true
  },
  {
    field: 'fieldiKeAzServerNemiad',
    label: 'نویسنده',
    sortable: false,
    sortName: 'car.name',
    processor: getCarName
  },
  {
    field: 'userPublisher',
    label: 'نویسنده',
    sortable: false
  }
]

function getCarName(data) {
  return data.name
}

const addRow = () => {
  // alert('کاربر به صفحه درج وارد شود')
  router.push({
    path: '/',
    query: { param1: 'amirsam' }
  })
  // reloadTable()// لود مجدد جدول
}

const reloadTable = () => {
  selectedRow.value = null
  wikiTableRef.value?.reload()// لود مجدد جدول
}

const showSelectedRow = () => {
  alert(selectedRow.value.wikiId)
  reloadTable()// لود مجدد جدول
}

const editSelectedRow = () => {
  alert(selectedRow.value.content)
  reloadTable()// لود مجدد جدول
}

const deleteSelectedRow = () => {
  alert(selectedRow.value.wikiId)
  reloadTable()// لود مجدد جدول
}

watch(selectedRow, (newValue) => {
  if (!newValue) return
  console.log(newValue.wikiId)// شنوده تغییر مقدار رکورد انتخاب شده
})
</script>

<template>
  <div>
    <h1>محصولات طلا و جواهرات 1</h1>
    <br>
    <DataTable
      ref="wikiTableRef"
      :url="URL"
      :columns="columns"
      default-sort="wikiId"
      row-key="wikiId"
      empty-text="داده‌ای وجود نداشت"
      @selected="selectedRow = $event"
    >
      <template #before-search>
        <UButton
          color="j2os-success"
          icon="i-lucide-plus"
          @click="addRow"
        />
      </template>

      <template #after-search>
        <UButton
          variant="outline"
          icon="i-lucide-refresh-ccw"
          @click="reloadTable"
        />

        <UButton
          color="j2os-info"
          icon="i-lucide-eye"
          :disabled="!selectedRow"
          @click="showSelectedRow"
        />

        <UButton
          icon="i-lucide-pencil"
          :disabled="!selectedRow"
          @click="editSelectedRow"
        />

        <UButton
          color="j2os-warning"
          icon="i-lucide-trash"
          :disabled="!selectedRow"
          @click="deleteSelectedRow"
        />
      </template>
    </DataTable>
  </div>
</template>
