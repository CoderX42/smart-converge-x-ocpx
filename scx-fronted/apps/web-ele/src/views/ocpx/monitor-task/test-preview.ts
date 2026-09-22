import type {
  MonitorTaskTestOs,
  MonitorTaskTestParameter,
  MonitorTaskTestPreview,
} from '#/api/ocpx';

export function testParameterSnapshot(
  os: MonitorTaskTestOs,
  parameters: MonitorTaskTestParameter[],
) {
  return JSON.stringify([
    os,
    parameters.map(({ key, selected, value }) => [key, selected, value ?? '']),
  ]);
}

export function testLinkAvailability({
  error,
  os,
  preview,
  validatedParameters,
  validating,
}: {
  error: string;
  os: MonitorTaskTestOs;
  preview?: MonitorTaskTestPreview;
  validatedParameters: string;
  validating: boolean;
}) {
  let message = '';
  if (validating) {
    message = '正在校验测试参数，请稍候';
  } else if (error) {
    message = error;
  } else if (!preview) {
    message = '请先生成并校验测试参数';
  } else if (
    os !== preview.os ||
    validatedParameters !== testParameterSnapshot(os, preview.parameters)
  ) {
    message = '测试参数已修改，请先点击“校验当前参数”';
  } else if (!preview.executable || preview.blockers.length > 0) {
    message = preview.blockers.join('；') || '测试预检未通过，请修正参数或配置';
  } else if (!preview.testUrl) {
    message = '未生成有效测试链接，请重新校验当前参数';
  }
  return { message, url: message ? '' : (preview?.testUrl ?? '') };
}
