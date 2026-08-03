<script setup>
useHead({
  meta: [
    {name: 'viewport', content: 'width=device-width, initial-scale=1'}
  ],
  link: [
    {rel: 'icon', href: '/favicon.ico'}
  ],
  htmlAttrs: {
    dir: 'rtl',
    lang: 'fa'
  }
})

const title = 'J2OS'
const description = 'An open source organization.'
useSeoMeta({
  title,
  description,
  ogTitle: title,
  ogDescription: description,
  ogImage: 'https://ui.nuxt.com/assets/templates/nuxt/starter-light.png',
  twitterCard: 'summary_large_image'
})
</script>

<template>
  <UApp dir="rtl">
    <UHeader>
      <template #left>
        <UUser
          to="https://j2os.org/"
          target="_blank"
          name="امیرسام بهادر"
          description="معمار جاوا"
          :avatar="{
            src: 'https://j2os.org/images/Amirsam-Bahador-Iran-Java-Developer-Architect.jpg'
          }"
        />
      </template>

      <template #default>
        <DesktopMenu/>
      </template>

      <template #right>
        <PanelMenu/>
        <UColorModeButton/>
        <UButton
          to="https://github.com/nuxt-ui-templates/starter"
          target="_blank"
          icon="i-simple-icons-github"
          aria-label="GitHub"
          color="neutral"
          variant="ghost"
        />
      </template>

      <template #body>
        <MobileMenu/>
      </template>
    </UHeader>

    <UMain>
      <div class="p-6 space-y-4">
        <NuxtLayout>
          <NuxtPage/>
        </NuxtLayout>
      </div>
    </UMain>

    <USeparator/>

    <UFooter>
      <PageFooter/>
    </UFooter>
  </UApp>
</template>
