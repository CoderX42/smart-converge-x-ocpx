<script setup lang="ts" name="wngEditor">
import type { IDomEditor } from '@wangeditor/editor';

import {
  nextTick,
  onBeforeUnmount,
  reactive,
  ref,
  shallowRef,
  watch,
} from 'vue';

import { Editor, Toolbar } from '@wangeditor/editor-for-vue';

import ImgDialog from '#/components/select-material/img-dialog/index.vue';

// https://www.wangeditor.com/v5/for-frame.html#vue3
import '@wangeditor/editor/dist/css/style.css';

type InsertFnType = (url: string, alt: string, href: string) => void;
// 定义父组件传过来的值
const props = defineProps({
  shopId: {
    type: String,
    default: () => '',
  },
  // 是否禁用
  disable: {
    type: Boolean,
    default: () => false,
  },
  // 内容框默认 placeholder
  placeholder: {
    type: String,
    default: () => '请输入内容...',
  },
  // https://www.wangeditor.com/v5/getting-started.html#mode-%E6%A8%A1%E5%BC%8F
  // 模式，可选 <default|simple>，默认 default
  mode: {
    type: String,
    default: () => 'default',
  },
  // 高度
  height: {
    type: String,
    default: () => '310px',
  },
  // 宽度
  width: {
    type: String,
    default: () => '100%',
  },
  // 双向绑定，用于获取 editor.getHtml()
  getHtml: {
    type: String,
    default: () => '',
  },
  // 双向绑定，用于获取 editor.getText()
  getText: {
    type: String,
    default: () => '',
  },
});

// 定义子组件向父组件传值/事件
const emit = defineEmits(['update:getHtml', 'update:getText']);

// 定义变量内容
const editorRef = shallowRef();
const imgDialogRef = ref();
const state = reactive({
  editorConfig: {
    placeholder: props.placeholder,
    MENU_CONF: [],
  },
  editorVal: props.getHtml,
});
state.editorConfig.MENU_CONF.uploadImage = {
  // 自定义选择图片
  customBrowseAndUpload(insertFn: InsertFnType) {
    imgDialogRef.value.openDialog();
    // 自己选择文件
    // 自己上传文件，并得到图片 url alt href
    // 最后插入图片
    insertFn('', '', '');
  },
};
/**
 * 素材选择器
 * @param urls 选中得图片
 */
const changeImg = (urls: Array<string>) => {
  const editor = editorRef.value;
  if (editor === null) return;
  if (urls) {
    const node = {
      type: 'paragraph',
      children: [
        {
          type: 'image',
          src: urls[0],
          alt: '',
          href: '',
          style: '',
          children: [{ text: '' }],
        },
      ],
    };
    // 在选区插入一个节点
    editor.insertNode(node);
    // 聚焦到最后
    nextTick(() => {
      editor.focus(true);
    });
  }
};
// 编辑器回调函数
const handleCreated = (editor: IDomEditor) => {
  editorRef.value = editor;
};
// 编辑器内容改变时
const handleChange = (editor: IDomEditor) => {
  try {
    emit('update:getHtml', editor.getHtml());
    emit('update:getText', editor.getText());
  } catch (error) {
    console.error('编辑器内容变化错误', error);
  }
};
// 页面销毁时
onBeforeUnmount(() => {
  const editor = editorRef.value;
  if (!editor) return;
  try {
    editor.destroy();
  } catch (error) {
    console.warn('editor destroy error', error);
  }
});

// 监听是否禁用改变
// https://gitee.com/lyt-top/vue-next-admin/issues/I4LM7I
watch(
  () => props.disable,
  (bool) => {
    const editor = editorRef.value;
    if (!editor) return;
    if (bool) editor.disable();
    else editor.enable();
  },
);

// 监听双向绑定值改变，用于回显
watch(
  () => props.getHtml,
  (val) => {
    if (val !== state.editorVal) {
      state.editorVal = val || '';
    }
  },
);
</script>

<template>
  <div class="editor-container">
    <Toolbar
      z-index="200"
      style="border-bottom: 1px solid #ccc"
      :editor="editorRef"
      :mode="mode"
    />
    <Editor
      z-index="200"
      :mode="mode"
      :default-config="state.editorConfig"
      :style="{ height, width }"
      v-model="state.editorVal"
      @on-created="handleCreated"
      @on-change="handleChange"
    />
    <ImgDialog
      ref="imgDialogRef"
      :shop-id="shopId"
      :can-choose-images-num="1"
      @change-img="changeImg"
    />
  </div>
</template>
<style lang="scss" scoped>
.editor-container {
  z-index: 200 !important;
  border: 1px solid #ccc;
}
</style>
