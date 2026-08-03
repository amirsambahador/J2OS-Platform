<script setup>
const menuItems = ref([
  {
    label: 'کامپوننت های پیش فرض',
    icon: 'i-lucide-book-open',
    children: [
      {
        label: 'نمونه کارت',
        description: 'در این مثال دو نمونه کارت نمایش داده شده است',
        icon: 'i-lucide-house',
        to: '/card'
      },
      {
        label: 'فرم',
        icon: 'i-lucide-smile',
        description: 'در این مثال انواع فرم نمایش داده شده است',
        to: '/form'
      },
      {
        label: 'تب',
        icon: 'i-lucide-smile',
        description: 'در این مثال نحوه ایجاد تب نمایش داده شده است',
        to: '/tab'
      },
      {
        label: 'درخت',
        icon: 'i-lucide-smile',
        description: 'در این مثال نحوه ایجاد درخت نمایش داده شده است',
        to: '/tree'
      },
      {
        label: 'ویرایشگر متن',
        icon: 'i-lucide-smile',
        description: 'در این مثال نحوه ایجاد ویرایشگر متن نمایش داده شده است',
        to: '/rich-text'
      },
      {
        label: 'ویرایشگر کد',
        icon: 'i-lucide-smile',
        description: 'در این مثال نحوه ایجاد ویرایشگر کد نمایش داده شده است',
        to: '/code-editor'
      },
      {
        label: 'کاربر',
        icon: 'i-lucide-swatch-book',
        description: 'در این مثال نحوه نمایش نام کاربر و مشخصات وی نمایش داده شده است',
        to: '/user'
      }
    ]
  },
  {
    label: 'جداول',
    icon: 'i-lucide-book-open',
    children: [
      {
        label: 'جدول زوج و فرد',
        description: 'در این مثال نحوه ایجاد جدول زوج و فرد نمایش داده شده است',
        icon: 'i-lucide-house',
        to: '/data-table'
      },
      {
        label: 'جدول رنگی',
        description: 'در این مثال نحوه ایجاد جدول با ردیف های رنگی نمایش داده شده است',
        icon: 'i-lucide-cloud-download',
        to: '/data-table5'
      },
      {
        label: 'جدول پیچیده از لیست سرور',
        description: 'در این مثال نحوه ایجاد جدول پیچیده از لیست سرور نمایش داده شده است',
        icon: 'i-lucide-cloud-download',
        to: '/data-table2'
      },
      {
        label: 'مدیریت خطا و جداول مستر و دیتیل',
        description: 'در این مثال نحوه مدیریت خطا و ایجاد جداول مستر و دیتیل نمایش داده شده است',
        icon: 'i-lucide-cloud-download',
        to: '/data-table3'
      },
      {
        label: 'جدولی که مستقیم از SQL ایجاد شده است',
        description: 'در این مثال نحوه ایجاد جداول مبتنی بر SQL نمایش داده شده است',
        icon: 'i-lucide-cloud-download',
        to: '/data-table4'
      }
    ]
  },
  {
    label: 'مودال و توست',
    icon: 'i-lucide-database',
    children: [
      {
        label: 'توست با تاخیر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه ایجاد توست با تاخیر نمایش داده شده است',
        to: '/toast-delay'
      },
      {
        label: 'توست بدون تاخیر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه ایجاد توست بدون تاخیر نمایش داده شده است',
        to: '/toast'
      },
      {
        label: 'مودال',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه ایجاد مودال نمایش داده شده است',
        to: '/modal'
      }
    ]
  },
  {
    label: 'برنامه نویسی',
    icon: 'i-lucide-database',
    children: [
      {
        label: 'ارسال پارامتر به صفحه دیگر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال ارسال و دریافت پارامتر به صفحه دیگر نمایش داده شده است',
        to: '/request-page'
      },
      {
        label: 'کاپوزیبل',
        description: 'در این مثال نحوه کار کامپوزیبل نمایش داده شده است',
        icon: 'i-lucide-cloud-download',
        to: '/composables'
      },
      {
        label: 'شنونده کیبورد',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه تعریف شنونده کیبورد نمایش داده شده است',
        to: '/key-listener'
      },
      {
        label: 'دیتابیس مرورگر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه تعامل با دیتابیس مرورگر نمایش داده شده است',
        to: '/local-storage'
      },
      {
        label: 'استفاده از فایل های خارجی CSS و JS',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه استفاده از فایل های CSS و JS بیرونی واقع در پوشه public آموزش داده شده است',
        to: '/import-file'
      },
      {
        label: 'بررسی وضعیت اینترنت',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه استفاده بررسی اتصال یا عدم اتصال به اینترنت نمایش داده شده است',
        to: '/network'
      },
      {
        label: 'اعداد',
        icon: 'i-lucide-file-text',
        description: 'کار با عدد به زبان فارسی در این مثال نمایش داده شده است',
        to: '/number'
      },
      {
        label: 'تاریخ',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با تاریخ نمایش داده شده است',
        to: '/date'
      },
      {
        label: 'QR',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با QR نمایش داده شده است',
        to: '/qr'
      },
      {
        label: 'اعتبار سنجی',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه اعتبار سنجی نمایش داده شده است',
        to: '/validation'
      },
      {
        label: 'فرم داینامیک',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با فرم داینامیک نمایش داده شده است',
        to: '/dynamic-form'
      },
      {
        label: 'مختصات جغرافیایی',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با مختصات جغرافیایی نمایش داده شده است',
        to: '/location'
      },
      {
        label: 'نوتیفیکیشن سیستم عامل',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با نوتیفیکیشن سیستم عامل نمایش داده شده است',
        to: '/os-notification'
      },
      {
        label: 'شناسایی صدا',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با شناسایی صوت کاربر نمایش داده شده است',
        to: '/speech-recognition'
      },
      {
        label: 'امضای دیجیتال',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با امضای دیجیتال نمایش داده شده است',
        to: '/digital-signature'
      },
      {
        label: 'ضبط تصویر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با ضبط تصویر نمایش داده شده است',
        to: '/screen-recorder'
      },
      {
        label: 'فول اسکرین',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با فول اسکرین نمایش داده شده است',
        to: '/full-screen'
      },
      {
        label: 'مترجم',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با مترجم نمایش داده شده است',
        to: '/translator'
      },
      {
        label: 'خطای سفارشی',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با خطای سفارشی نمایش داده شده است',
        to: '/create-error'
      },
      {
        label: 'شنونده بر روی تغییر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با شنونده بر روی تغییر نمایش داده شده است',
        to: '/watch'
      },
      {
        label: 'شنونده بر روی تغییر و مقدار دادن متغیر',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با شنونده بر روی تغییر و مقدار دادن متغیر نمایش داده شده است',
        to: '/computed'
      },
      {
        label: 'Layout سفارشی',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با Layout سفارشی نمایش داده شده است',
        to: '/layout'
      },
      {
        label: 'کار با کامپوننت',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه کار با کامپوننت نمایش داده شده است',
        to: '/inject-component'
      },
      {
        label: 'فراخوانی متد صفحه از طریق کامپوننت',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه فراخوانی متد صفحه از طریق کامپوننت نمایش داده شده است',
        to: '/invoke-function-component'
      },
      {
        label: 'فراخوانی متد کامپوننت از طریق صفحه',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه فراخوانی متد کامپوننت از طریق صفحه نمایش داده شده است',
        to: '/invoke-function-component-from-page'
      },
      {
        label: 'نگهداشت وضعیت',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوه نگهداشت وضعیت نمایش داده شده است',
        to: '/state1'
      },
      {
        label: 'مقابله با XSS',
        icon: 'i-lucide-file-text',
        description: 'در این مثال نحوهمقابله با XSS نمایش داده شده است',
        to: '/xss'
      }

    ]
  }
])
</script>

<template>
  <UNavigationMenu
    :items="menuItems"
  />
</template>
