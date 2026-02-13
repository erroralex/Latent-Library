<script setup>
/**
 * @file CustomContextMenu.vue
 * @description A highly customizable, programmatic context menu component.
 *
 * This component is designed to be triggered manually from parent components. It uses Vue's Teleport
 * feature to render the menu at the body level, ensuring it bypasses any parent container clipping
 * or z-index issues.
 *
 * Key functionalities:
 * - Programmatic Control: Exposed `show` and `hide` methods allow precise control over menu visibility and positioning.
 * - Dynamic Positioning: Automatically positions itself based on the mouse event coordinates.
 * - Auto-closing: Implements global listeners to close the menu on clicks, scrolls, or subsequent context menu actions.
 * - Flexible Schema: Supports labels, icons, separators, and conditional visibility for menu items.
 */
import {ref, onBeforeUnmount} from 'vue';
import CustomContextSubMenu from './CustomContextSubMenu.vue';

const props = defineProps({
  model: {
    type: Array,
    default: () => []
  }
});

const visible = ref(false);
const x = ref(0);
const y = ref(0);
const menuRef = ref(null);

const show = (event) => {
  if (!event) {
    console.error("CustomContextMenu: No event passed to show()");
    return;
  }

  event.preventDefault();
  event.stopPropagation();

  x.value = event.clientX;
  y.value = event.clientY;
  visible.value = true;

  setTimeout(() => {
    window.addEventListener('click', closeMenu);
    window.addEventListener('contextmenu', closeMenu);
    window.addEventListener('scroll', hide, {capture: true});
  }, 50);
};

const hide = () => {
  visible.value = false;
  window.removeEventListener('click', closeMenu);
  window.removeEventListener('contextmenu', closeMenu);
  window.removeEventListener('scroll', hide, {capture: true});
};

const closeMenu = () => {
  hide();
};

onBeforeUnmount(() => {
  hide();
});

defineExpose({show, hide});
</script>

<template>
  <Teleport to="body">
    <div
        v-if="visible"
        ref="menuRef"
        class="custom-context-menu"
        :style="{ top: y + 'px', left: x + 'px' }"
        @contextmenu.prevent
    >
      <CustomContextSubMenu :model="model" @execute="hide" />
    </div>
  </Teleport>
</template>

<style>
.custom-context-menu {
  position: fixed;
  z-index: 999999 !important;
  min-width: 200px;
  background: var(--bg-panel-opaque);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: 1px solid var(--border-input);
  border-radius: 8px;
  box-shadow: var(--shadow-panel);
  padding: 6px 0;
  color: var(--text-primary);
  font-family: var(--font-family, sans-serif);
  font-size: 14px;
  overflow: visible;
  user-select: none;
}
</style>