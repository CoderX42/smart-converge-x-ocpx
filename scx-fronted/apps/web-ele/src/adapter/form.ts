import type {
  BootFormSchema as FormSchema,
  BootFormProps,
} from '@boot/common-ui';

import type { ComponentType } from './component';

import { setupBootForm, useBootForm as useForm, z } from '@boot/common-ui';
import { $t } from '@boot/locales';

async function initSetupBootForm() {
  setupBootForm<ComponentType>({
    config: {
      modelPropNameMap: {
        Upload: 'fileList',
        CheckboxGroup: 'model-value',
      },
    },
    defineRules: {
      required: (value, _params, ctx) => {
        if (value === undefined || value === null || value.length === 0) {
          return $t('ui.formRules.required', [ctx.label]);
        }
        return true;
      },
      selectRequired: (value, _params, ctx) => {
        if (value === undefined || value === null) {
          return $t('ui.formRules.selectRequired', [ctx.label]);
        }
        return true;
      },
    },
  });
}

const useBootForm = useForm<ComponentType>;

export { initSetupBootForm, useBootForm, z };

export type BootFormSchema = FormSchema<ComponentType>;
export type { BootFormProps };
