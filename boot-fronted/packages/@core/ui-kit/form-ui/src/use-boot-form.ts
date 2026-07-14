import type {
  BaseFormComponentType,
  ExtendedFormApi,
  BootFormProps,
} from './types';

import { defineComponent, h, isReactive, onBeforeUnmount, watch } from 'vue';

import { useStore } from '@boot-core/shared/store';

import { FormApi } from './form-api';
import BootUseForm from './boot-use-form.vue';

export function useBootForm<
  T extends BaseFormComponentType = BaseFormComponentType,
>(options: BootFormProps<T>) {
  const IS_REACTIVE = isReactive(options);
  const api = new FormApi(options);
  const extendedApi: ExtendedFormApi = api as never;
  extendedApi.useStore = (selector) => {
    return useStore(api.store, selector);
  };

  const Form = defineComponent(
    (props: BootFormProps, { attrs, slots }) => {
      onBeforeUnmount(() => {
        api.unmount();
      });
      api.setState({ ...props, ...attrs });
      return () =>
        h(BootUseForm, { ...props, ...attrs, formApi: extendedApi }, slots);
    },
    {
      name: 'BootUseForm',
      inheritAttrs: false,
    },
  );
  // Add reactivity support
  if (IS_REACTIVE) {
    watch(
      () => options.schema,
      () => {
        api.setState({ schema: options.schema });
      },
      { immediate: true },
    );
  }

  return [Form, extendedApi] as const;
}
