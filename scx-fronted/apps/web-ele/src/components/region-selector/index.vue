<script setup lang="ts">
import { reactive, ref, watch } from 'vue';

import { ElButton, ElCheckbox, ElCheckboxGroup, ElDialog } from 'element-plus';

interface RegionItem {
  code: string;
  name: string;
  children: RegionItem[];
  checked?: boolean;
  indeterminate?: boolean;
}

const props = withDefaults(
  defineProps<{
    disabledCities?: string[];
    modelValue: string[];
    options: RegionItem[];
  }>(),
  {
    disabledCities: () => [],
    modelValue: () => [],
    options: () => [],
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string[]): void;
}>();

const state = reactive({
  dialog: false,
  selectedCities: [] as string[],
  // 添加折叠状态管理，默认所有省份都收起
  collapsedProvinces: new Set<string>(),
});

const selectedText = ref();

// 切换省份折叠状态
const toggleProvinceCollapse = (provinceCode: string) => {
  if (state.collapsedProvinces.has(provinceCode)) {
    state.collapsedProvinces.delete(provinceCode);
  } else {
    state.collapsedProvinces.add(provinceCode);
  }
};

// 检查省份是否折叠
const isProvinceCollapsed = (provinceCode: string) => {
  return state.collapsedProvinces.has(provinceCode);
};

const handleProvinceChange = (province: RegionItem, checked: any) => {
  const cityCodes = province.children.map((city) => city.code);
  if (checked) {
    state.selectedCities.push(
      ...cityCodes.filter((code) => !state.selectedCities.includes(code)),
    );
  } else {
    state.selectedCities = state.selectedCities.filter(
      (code) => !cityCodes.includes(code),
    );
  }
  province.indeterminate = false;
};

const handleCitiesChange = (province: RegionItem, value: any[]) => {
  const provinceCityCodes = new Set(province.children.map((city) => city.code));
  const selectedProvinceCities = value.filter((code) =>
    provinceCityCodes.has(code),
  );

  province.checked = selectedProvinceCities.length === province.children.length;
  province.indeterminate =
    selectedProvinceCities.length > 0 &&
    selectedProvinceCities.length < province.children.length;
};

const handleRegion = () => {
  state.dialog = true;
  // 初始化省份选中状态，并默认收起所有省份
  props.options.forEach((province) => {
    if (province.children && province.children.length > 0) {
      const cityCodes = province.children.map((city) => city.code);
      const selectedCount = cityCodes.filter((code) =>
        state.selectedCities.includes(code),
      ).length;

      province.checked = selectedCount === province.children.length;
      province.indeterminate =
        selectedCount > 0 && selectedCount < province.children.length;

      // 默认收起所有省份
      state.collapsedProvinces.add(province.code);
    } else {
      province.checked = false;
      province.indeterminate = false;
    }
  });
};

const onSubmit = () => {
  emit('update:modelValue', state.selectedCities);
  if (state.selectedCities.length === 0) {
    return;
  }
  const cityNames = props.options
    .filter((province) => province.children && province.children.length > 0)
    .flatMap((province) =>
      province.children
        .filter((city) => state.selectedCities.includes(city.code))
        .map((city) => city.name),
    );
  selectedText.value = cityNames.join(',');
  state.dialog = false;
};

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  (newVal) => {
    state.selectedCities = [...newVal];
  },
  { immediate: true },
);

watch(
  () => props.options,
  (newVal) => {
    if (!newVal || newVal.length === 0) {
      selectedText.value = null;
    } else {
      const cityNames = props.options
        .filter((province) => province.children && province.children.length > 0)
        .flatMap((province) =>
          province.children
            .filter((city) => state.selectedCities.includes(city.code))
            .map((city) => city.name),
        );
      selectedText.value = cityNames.join(',');
    }
  },
  { immediate: true },
);
</script>

<template>
  <div>
    <div @click="handleRegion" class="select-trigger">
      {{ selectedText || '请选择地区' }}
    </div>
    <ElDialog
      v-model="state.dialog"
      width="50%"
      title="地区选择器"
      append-to-body
    >
      <div class="region-wrapper">
        <div
          v-for="province in props.options"
          :key="province.code"
          class="province-item"
        >
          <div class="province-header">
            <ElCheckbox
              v-model="province.checked"
              :indeterminate="province.indeterminate"
              @change="(val) => handleProvinceChange(province, val)"
              :disabled="
                province.children.every((city) =>
                  props.disabledCities?.includes(city.code),
                )
              "
            >
              {{ province.name }}
            </ElCheckbox>
            <ElButton
              v-if="province.children && province.children.length > 0"
              type="text"
              size="small"
              @click="toggleProvinceCollapse(province.code)"
              class="collapse-btn"
            >
              <span v-if="isProvinceCollapsed(province.code)">展开</span>
              <span v-else>收起</span>
            </ElButton>
          </div>
          <div
            v-if="province.children && province.children.length > 0"
            class="city-list"
            :class="{ collapsed: isProvinceCollapsed(province.code) }"
          >
            <ElCheckboxGroup
              v-model="state.selectedCities"
              @change="(val) => handleCitiesChange(province, val)"
            >
              <ElCheckbox
                v-for="city in province.children"
                :key="city.code"
                :value="city.code"
                :disabled="props.disabledCities?.includes(city.code)"
              >
                {{ city.name }}
              </ElCheckbox>
            </ElCheckboxGroup>
          </div>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <ElButton @click="state.dialog = false">取消</ElButton>
          <ElButton type="primary" @click="onSubmit">确定</ElButton>
        </span>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.select-trigger {
  min-height: 20px;
  padding: 5px 0;
  cursor: pointer;
}

.region-wrapper {
  height: 500px;
  padding: 20px;
  overflow-y: auto;

  .province-item {
    padding-bottom: 15px;
    margin-bottom: 20px;
    border-bottom: 1px solid #ebeef5;

    &:last-child {
      border-bottom: none;
    }

    .province-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;

      .collapse-btn {
        padding: 0;
        font-size: 12px;
        color: #409eff;

        &:hover {
          color: #66b1ff;
        }
      }
    }

    .city-list {
      padding-left: 25px;
      margin-top: 10px;
      overflow: hidden;
      transition: all 0.3s ease;

      &.collapsed {
        max-height: 0;
        padding-top: 0;
        padding-bottom: 0;
        margin-top: 0;
      }

      .el-checkbox {
        width: 120px;
        margin-right: 15px;
        margin-bottom: 10px;
      }
    }
  }
}
</style>
