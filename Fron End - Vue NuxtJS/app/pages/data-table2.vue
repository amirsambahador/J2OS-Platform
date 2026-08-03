<script setup>
//  <!-- https://icones.js.org/collection/lucide -->
const router = useRouter()
const selectedRow = ref()// رفرنس ردیف انتخاب شده
const tableRef = ref()// رفرنس جدول برای ریلود
const config = useRuntimeConfig()
const security = useSecurity()
const API_BASE = config.public.API_BASE
const URL = ref(`${API_BASE}/personFilter?`)// آدرس API
const columns = [// ستون های جدول
  {
    field: 'id',
    label: 'شناسه ⇅️',
    sortable: true
  },
  {
    field: 'firstName',
    label: 'نام کوچک ⇅️',
    sortable: true
  },
  {
    field: 'fullName',
    label: 'نام کامل',
    sortable: false
  },
  {
    field: 'car',
    label: 'نام ماشین ⇅️',
    sortable: true,
    sortName: 'car.name',
    processor: getCarName
  },
  {
    field: 'car',
    label: 'کارخانه ⇅️',
    sortable: true,
    sortName: 'car.factory.name',
    processor: getCarFactoryName,
    render: true
  }
]
function getCarName(data) {
  return security.protectStrictXSS(data.name)
}

function getCarFactoryName(data) {
  return `<p>${security.protectStrictXSS(data.factory.name)}</p><br/>`
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
  tableRef.value?.reload()// لود مجدد جدول
}

const showSelectedRow = () => {
  alert(selectedRow.value.id)
  reloadTable()// لود مجدد جدول
}

const editSelectedRow = () => {
  alert(selectedRow.value.id)
  reloadTable()// لود مجدد جدول
}

const deleteSelectedRow = () => {
  alert(selectedRow.value.id)
  reloadTable()// لود مجدد جدول
}

watch(selectedRow, (newValue) => {
  if (!newValue) return
  console.log(newValue.id)// شنوده تغییر مقدار رکورد انتخاب شده
})
</script>

<template>
  <div>
    <h1>محصولات طلا و جواهرات</h1>
    <br>
    <DataTable
      ref="tableRef"
      :url="URL"
      :columns="columns"
      default-sort="id"
      row-key="id"
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
