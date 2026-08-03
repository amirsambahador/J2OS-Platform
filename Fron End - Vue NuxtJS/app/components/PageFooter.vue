<script setup>
const rightColumns = [
  {
    label: 'محصولات حسابداری',
    children: [
      {
        label: 'زررین 64',
        to: 'https://masteringnuxt.com/nuxt3?ref=nuxt',
        target: '_blank'
      },
      {
        label: 'زررین 128',
        to: 'https://github.com/nuxt',
        target: '_blank'
      }
    ]
  },
  {
    label: 'شبکه های مجازی',
    children: [
      {
        label: 'اینستاگرام',
        to: 'https://content.nuxt.com/',
        target: '_blank'
      },
      {
        label: 'تلگرام',
        to: 'https://devtools.nuxt.com/',
        target: '_blank'
      }
    ]
  }
]
const middleColumns = [
  {
    label: 'سایر محصولات',
    children: [
      {
        label: 'زرین CRM',
        to: 'https://nuxters.nuxt.com',
        target: '_blank'
      },
      {
        label: 'هوش مصنوعی زرین',
        to: 'https://github.com/nuxt',
        target: '_blank'
      }
    ]
  },
  {
    label: 'همکاران',
    children: [
      {
        label: 'طلا آی آر',
        to: 'https://content.nuxt.com/',
        target: '_blank'
      },
      {
        label: 'مدرسه جاوا',
        to: 'https://devtools.nuxt.com/',
        target: '_blank'
      }
    ]
  }
]
const leftColumns = [
  {
    label: 'پشتیبانی',
    children: [
      {
        label: 'از طریق بله',
        to: 'https://nuxters.nuxt.com',
        target: '_blank'
      },
      {
        label: 'از طریق تلفن',
        to: 'https://github.com/nuxt',
        target: '_blank'
      }
    ]
  },
  {
    label: 'منابع',
    children: [
      {
        label: 'گیت هاب',
        to: 'https://content.nuxt.com/',
        target: '_blank'
      },
      {
        label: 'ربات زرین',
        to: 'https://devtools.nuxt.com/',
        target: '_blank'
      }
    ]
  }
]
</script>

<template>
  <UFooterColumns :columns="rightColumns"/>
  <UFooterColumns :columns="middleColumns"/>
  <UFooterColumns :columns="leftColumns"/>
</template>
