<script setup>

</script>

<template>
  <UPageCard
    title="تب ها"
    variant="soft"
  >
    <UTabs
      :items="[
        { label: 'حساب کاربری', icon: 'i-lucide-user', slot: 'account' },
        { label: 'تنظیمات', icon: 'i-lucide-settings', slot: 'settings' },
        { label: 'اعلان‌ها', icon: 'i-lucide-bell', slot: 'notifications' }
      ]"
    >
      <template #account>
        <div class="p-6 space-y-4">
          محتوای حساب کاربری
          Amirsam Bahador
          <div>شماره کارت: ۶۰۳۷ ۹۹۱۲ ۳۴۵۶ ۷۸۹۰</div>
          <div>مبلغ: ۱۲٬۳۴۵٬۶۷۸ تومان</div>
        </div>
      </template>

      <template #settings>
        <div class="p-6 space-y-4">
          محتوای تنظیمات
        </div>
      </template>

      <template #notifications>
        <div class="p-6 space-y-4">
          محتوای اعلان‌ها
        </div>
      </template>
    </UTabs>
  </UPageCard>
</template>
