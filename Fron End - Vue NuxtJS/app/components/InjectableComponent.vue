<template>
  <div>
    این متن خود کامپوننت است
    <slot name="header" />
  </div>

  <div>
    <slot name="sidebar" />
  </div>

  <div>
    <slot />
  </div>
</template>
