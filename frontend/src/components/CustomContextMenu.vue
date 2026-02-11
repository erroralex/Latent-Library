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

const execute = (item) => {
  if (!item.disabled && item.command) {
    item.command();
  }
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
      <ul class="custom-menu-list">
        <template v-for="(item, index) in model" :key="index">
          <li v-if="item.separator" class="menu-separator"></li>

          <li v-else-if="item.visible !== false"
              class="menu-item"
              :class="{ 'disabled': item.disabled }"
              @click.stop="execute(item)"
          >
            <div class="menu-item-content">
              <span v-if="item.icon" :class="['menu-icon', item.icon]"></span>
              <span class="menu-label">{{ item.label }}</span>
            </div>
          </li>
        </template>
      </ul>
    </div>
  </Teleport>
</template>

<style>
.custom-context-menu {
  position: fixed;
  z-index: 999999 !important;
  min-width: 200px;
  background: rgba(10, 12, 16, 0.95);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 8px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.8);
  padding: 6px 0;
  color: #e0e0e0;
  font-family: var(--font-family, sans-serif);
  font-size: 14px;
  overflow: hidden;
  user-select: none;
}

.custom-menu-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.menu-item {
  padding: 10px 16px;
  cursor: pointer;
  transition: background 0.1s;
  display: flex;
  align-items: center;
}

.menu-item:hover {
  background: var(--app-grad-hover);
  color: #000000;
}

.menu-item.disabled {
  opacity: 0.5;
  cursor: default;
  pointer-events: none;
}

.menu-item-content {
  display: flex;
  align-items: center;
  width: 100%;
}

.menu-icon {
  margin-right: 12px;
  font-size: 14px;
  color: #66fcf1;
  width: 16px;
  text-align: center;
  transition: color 0.1s;
}

.menu-item:hover .menu-icon {
  color: #000000;
}

.menu-label {
  flex-grow: 1;
  font-weight: 500;
}

.menu-separator {
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
  margin: 4px 0;
}
</style>