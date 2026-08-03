<script setup>
const notification = useOperatingSystemNotification()

const testNotification = async () => {
  const success = await notification.showNotification(
    'سلام',
    'برای ورود به گوگل روی این اعلان کلیک کنید.',
    'https://www.google.com'
  )

  if (!success) {
    alert('نمایش نوتیفیکیشن ناموفق بود. کنسول را برای جزئیات بررسی کنید.')
  }
}
</script>

<template>
  <div>
    <P>
      <UButton @click="testNotification">
        نمایش نوتیفیکیشن
      </UButton>
      با Edge یا Chrome تست شود در فایرفاکس باید کاربر تنظیم انجام دهد
    </P>
  </div>
</template>
