<script setup>
import { ref } from 'vue';

const props = defineProps({
  model: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['execute', 'close']);
const activeSubmenu = ref(null);

const onMouseEnter = (index) => {
  activeSubmenu.value = index;
};

const onMouseLeave = () => {
  activeSubmenu.value = null;
};

const execute = (item) => {
  if (!item.disabled) {
    if (item.command) {
      item.command();
    }
    emit('execute');
  }
};
</script>

<template>
  <ul class="custom-menu-list">
    <template v-for="(item, index) in model" :key="index">
      <li v-if="item.separator" class="menu-separator"></li>

      <li v-else-if="item.visible !== false"
          class="menu-item"
          :class="{ 'disabled': item.disabled, 'has-submenu': item.items }"
          @click.stop="execute(item)"
          @mouseenter="onMouseEnter(index)"
          @mouseleave="onMouseLeave"
      >
        <div class="menu-item-content">
          <span v-if="item.icon" :class="['menu-icon', item.icon]"></span>
          <span class="menu-label">{{ item.label }}</span>
          <i v-if="item.items" class="pi pi-angle-right submenu-arrow"></i>
        </div>

        <div v-if="item.items && activeSubmenu === index" class="custom-context-menu submenu">
          <CustomContextSubMenu :model="item.items" @execute="emit('execute')" />
        </div>
      </li>
    </template>
  </ul>
</template>

<style scoped>
/* Re-use styles from parent or define here */
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
  position: relative;
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

.submenu-arrow {
  font-size: 12px;
  margin-left: 10px;
}

.menu-separator {
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
  margin: 4px 0;
}

.submenu {
  position: absolute;
  left: 100%;
  top: 0;
  margin-left: 4px;
}
</style>