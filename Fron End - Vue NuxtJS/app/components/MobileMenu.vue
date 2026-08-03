<script setup>
const menuItems = ref([
  { label: 'موجودی سریع', icon: 'i-lucide-warehouse', to: '/' },
  { label: 'گزارش روزانه', icon: 'i-lucide-bar-chart' },
  { label: 'فاکتور جدید', icon: 'i-lucide-file-plus' },
  { label: 'تنظیمات', icon: 'i-lucide-settings' }
])
</script>

<template>
  <div
    class="flex flex-col gap-3"
  >
    <!-- عنوان بخش -->
    <div class="text-sm font-medium text-gray-500">
      منوهای حواله
    </div>
    <!-- لیست دکمه‌ها -->
    <div class="flex flex-col gap-2">
      <UButton
        v-for="item in menuItems"
        :key="item.label"
        :label="item.label"
        :icon="item.icon"
        :to="item.to"
        color="neutral"
        variant="soft"
        class="w-full justify-start"
      />
    </div>

    <!-- عنوان بخش -->
    <div class="text-sm font-medium text-gray-500">
      منوهای پایا
    </div>
    <!-- لیست دکمه‌ها -->
    <div class="flex flex-col gap-2">
      <UButton
        v-for="item in menuItems"
        :key="item.label"
        :label="item.label"
        :icon="item.icon"
        :to="item.to"
        color="neutral"
        variant="soft"
        class="w-full justify-start"
      />
    </div>
  </div>
</template>
