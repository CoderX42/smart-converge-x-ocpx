import { flushPromises, mount } from '@vue/test-utils';

import ElementPlus, { ElMessageBox } from 'element-plus';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { generateOcpxBusinessCode } from '#/api/ocpx';

import AutoCodeInput from './auto-code-input.vue';

vi.mock('#/api/ocpx', () => ({
  generateOcpxBusinessCode: vi.fn(),
}));

describe('oCPX auto code input', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(generateOcpxBusinessCode).mockResolvedValue({
      code: 'adv_0123456789abcdef',
      resourceType: 'ADVERTISER',
    });
  });

  it('generates by default and keeps the current code when switching to manual', async () => {
    const wrapper = mount(AutoCodeInput, {
      global: { plugins: [ElementPlus] },
      props: {
        generationKey: 'create-request-0001',
        modelValue: '',
        resourceType: 'ADVERTISER',
      },
    });
    await flushPromises();

    expect(generateOcpxBusinessCode).toHaveBeenCalledWith('ADVERTISER');
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([
      'adv_0123456789abcdef',
    ]);
    await wrapper.setProps({ modelValue: 'adv_0123456789abcdef' });
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('改为手动'))
      ?.trigger('click');
    expect(wrapper.find('input').attributes('readonly')).toBeUndefined();
    expect(wrapper.find('input').element.value).toBe('adv_0123456789abcdef');
  });

  it('confirms before replacing a manually edited code', async () => {
    const confirm = vi
      .spyOn(ElMessageBox, 'confirm')
      .mockImplementation(async () => undefined as never);
    const wrapper = mount(AutoCodeInput, {
      global: { plugins: [ElementPlus] },
      props: {
        generationKey: '',
        modelValue: 'manual_code',
        resourceType: 'ADVERTISER',
      },
    });

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('改为手动'))
      ?.trigger('click');
    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('改为自动'))
      ?.trigger('click');
    await flushPromises();

    expect(confirm).toHaveBeenCalled();
    expect(generateOcpxBusinessCode).toHaveBeenCalledWith('ADVERTISER');
  });

  it('shows retry and manual fallback when generation fails', async () => {
    vi.mocked(generateOcpxBusinessCode).mockRejectedValueOnce(
      new Error('network'),
    );
    const wrapper = mount(AutoCodeInput, {
      global: { plugins: [ElementPlus] },
      props: {
        generationKey: 'create-request-0002',
        modelValue: '',
        resourceType: 'ADVERTISER',
      },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('自动编码生成失败');
    expect(wrapper.text()).toContain('重试');
    expect(wrapper.text()).toContain('手动输入');
  });
});
