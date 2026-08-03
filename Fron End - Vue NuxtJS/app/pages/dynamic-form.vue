<script setup>
const countryOptions = [
  { label: 'ایران', value: 'iran' },
  { label: 'ترکیه', value: 'turkey' }
]

const citiesByCountry = {
  iran: [
    { label: 'تهران', value: 'tehran' },
    { label: 'مشهد', value: 'mashhad' },
    { label: 'اصفهان', value: 'isfahan' }
  ],
  turkey: [
    { label: 'استانبول', value: 'istanbul' },
    { label: 'آنکارا', value: 'ankara' }
  ]
}

// اضافه: این تابع هر دو نسخه NuxtUI را پوشش می‌ده.
function extractValue(val) {
  return val && typeof val === 'object' && 'value' in val ? val.value : val
}

// گزینه‌های شهر بر اساس کشورِ انتخاب‌شده در همون ردیف
function cityOptionsFor(country) {
  return citiesByCountry[country] || []
}

const dynamicForm = useDynamicForm(() => ({
  firstName: '',
  lastName: '',
  age: '',
  country: '',
  city: ''
}))

const handleSubmit = () => {
  const values = dynamicForm.getAllValues()
  alert(JSON.stringify(values, null, 2))
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div>
      <UButton
        label="افزودن ردیف جدید"
        icon="i-lucide-table-2"
        color="primary"
        variant="solid"
        @click="dynamicForm.showForm"
      />
    </div>

    <div
      v-if="dynamicForm.isVisible.value"
      class="flex flex-col gap-2"
    >
      <div
        v-for="row in dynamicForm.rows.value"
        :key="row.id"
        class="flex gap-2"
      >
        <UInput
          v-model="row.firstName"
          placeholder="نام"
        />
        <UInput
          v-model="row.lastName"
          placeholder="فامیلی"
        />
        <UInput
          v-model="row.age"
          placeholder="سن"
        />

        <USelectMenu
          :model-value="row.country"
          :items="countryOptions"
          placeholder="کشور"
          @update:model-value="(val) => { row.country = extractValue(val); row.city = '' }"
        />

        <!-- اصلاح: تا کشور انتخاب نشده، combo شهر غیرفعاله و گزینه‌ای نداره؛
             با عوض شدن کشور، مقدار قبلی شهر (که ممکنه متعلق به کشور قبلی
             باشه) هم توسط هندلر بالا ریست می‌شه -->
        <USelectMenu
          :model-value="row.city"
          :items="cityOptionsFor(row.country)"
          :disabled="!row.country"
          placeholder="شهر"
          @update:model-value="(val) => { row.city = extractValue(val) }"
        />

        <div class="flex gap-1">
          <UButton
            icon="i-lucide-plus"
            color="success"
            variant="solid"
            @click="dynamicForm.addRow"
          />
          <UButton
            icon="i-lucide-minus"
            color="error"
            variant="solid"
            @click="dynamicForm.removeRow(row.id)"
          />
        </div>
      </div>

      <div class="flex gap-2 mt-2">
        <UButton
          label="ارسال"
          icon="i-lucide-send"
          color="primary"
          variant="solid"
          @click="handleSubmit"
        />
        <UButton
          label="پاک کردن"
          icon="i-lucide-rotate-ccw"
          color="warning"
          variant="outline"
          @click="dynamicForm.resetForm"
        />
        <UButton
          label="بستن"
          icon="i-lucide-x"
          color="error"
          variant="outline"
          @click="dynamicForm.closeForm"
        />
      </div>
    </div>
  </div>
</template>
