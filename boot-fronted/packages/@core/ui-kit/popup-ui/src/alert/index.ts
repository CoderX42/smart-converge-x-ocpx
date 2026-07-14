export type {
  AlertProps,
  BeforeCloseScope,
  IconType,
  PromptProps,
} from './alert';
export { useAlertContext } from './alert';
export { default as Alert } from './alert.vue';
export {
  bootAlert as alert,
  clearAllAlerts,
  bootConfirm as confirm,
  bootPrompt as prompt,
} from './AlertBuilder';
