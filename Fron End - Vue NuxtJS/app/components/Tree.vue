<script setup>
/*
 * @author amirsam bahador, mohammad ghaderi
 * @version 2.3
 */
const props = defineProps({
  expandedKeys: { type: Array, required: true },
  treeData: { type: Array, required: true },
  menu: { type: Boolean, required: true },
  menuX: { type: Number, required: true },
  menuY: { type: Number, required: true },
  selectedItem: { type: Object, default: null },
  visibleActions: { type: Array, required: true },
  isBusy: { type: Function, required: true },
  openMenu: { type: Function, required: true },
  runAction: { type: Function, required: true },
  handleNodeClick: { type: Function, required: true }
})

const emit = defineEmits(['update:expandedKeys', 'update:menu'])

function onItemClick(item, handleSelect, handleToggle) {
  handleSelect()

  if (item.children !== undefined) {
    handleToggle()
    item.onToggle?.()
  } else {
    props.handleNodeClick(item)
  }
}
</script>

<template>
  <div>
    <UTree
      :expanded="expandedKeys"
      :items="treeData"
      :get-key="item => String(item.id)"
      class="w-full"
      @update:expanded="emit('update:expandedKeys', $event)"
    >
      <template #item-wrapper="{ item, expanded, handleToggle, handleSelect, selected }">
        <div
          v-if="!item._dummy"
          class="w-full flex items-center gap-2 px-2 py-1.5 rounded-md cursor-pointer text-sm hover:bg-gray-100 dark:hover:bg-gray-800 group"
          :class="[
            selected ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-500 font-medium' : '',
            isBusy(item.id) ? 'opacity-50 pointer-events-none' : ''
          ]"
          @click="onItemClick(item, handleSelect, handleToggle)"
          @contextmenu.prevent.stop="openMenu($event, item)"
        >
          <UIcon
            v-if="item.children !== undefined"
            :name="expanded ? 'i-lucide-folder-open' : 'i-lucide-folder'"
            class="size-4 shrink-0 text-yellow-400"
          />
          <UIcon
            v-else
            name="i-lucide-file"
            class="size-4 shrink-0"
          />

          <span class="truncate flex-1">{{ item.label }}</span>

          <UIcon
            v-if="isBusy(item.id)"
            name="i-lucide-loader-2"
            class="size-3.5 shrink-0 animate-spin text-gray-400"
          />
        </div>
      </template>
    </UTree>

    <div
      v-if="menu"
      class="fixed inset-0 z-40"
      @click="emit('update:menu', false)"
    />

    <Teleport to="body">
      <div
        v-if="menu"
        class="fixed z-50 w-48 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-xl overflow-hidden"
        :style="{ left: menuX + 'px', top: menuY + 'px' }"
      >
        <div class="px-3 py-2 text-xs text-gray-400 border-b border-gray-100 dark:border-gray-700 truncate">
          {{ selectedItem?.label }}
        </div>

        <template
          v-for="(action, idx) in visibleActions"
          :key="action.key"
        >
          <button
            class="w-full px-3 py-2 text-right text-sm flex items-center gap-2"
            :class="action.hoverClass"
            @click="runAction(action)"
          >
            <UIcon
              :name="action.icon"
              class="size-4"
              :class="action.color"
            />
            <span :class="action.color">{{ action.label }}</span>
          </button>
          <div
            v-if="idx < visibleActions.length - 1"
            class="border-t border-gray-100 dark:border-gray-700"
          />
        </template>
      </div>
    </Teleport>
  </div>
</template>
