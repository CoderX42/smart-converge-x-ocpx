import crypto from 'k6/crypto';
import exec from 'k6/execution';
import http from 'k6/http';
import { check } from 'k6';

const target = required('OCPX_TARGET');
const mediaCode = required('OCPX_MEDIA_CODE');
const monitorTaskId = required('OCPX_MONITOR_TASK_ID');
const secret = required('OCPX_MEDIA_SECRET');
const keyVersion = __ENV.OCPX_KEY_VERSION || '1';
const rate = Number(__ENV.OCPX_RATE || '10');
const duration = __ENV.OCPX_DURATION || '30s';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    accepted_feedback: {
      executor: 'constant-arrival-rate',
      duration,
      maxVUs: Number(__ENV.OCPX_MAX_VUS || '100'),
      preAllocatedVUs: Number(__ENV.OCPX_PREALLOCATED_VUS || '20'),
      rate,
      timeUnit: '1s',
    },
  },
  thresholds: {
    checks: ['rate>=0.999'],
    http_req_duration: ['p(95)<=3000'],
    http_req_failed: ['rate<=0.001'],
  },
};

export default function () {
  const unique = `${exec.instance.idInTest}-${exec.scenario.iterationInTest}`;
  const eventId = `load-${unique}`;
  const body = JSON.stringify({
    schema_version: '2.0',
    event_id: eventId,
    event: 'click',
    monitor_task_id: monitorTaskId,
    click_id: eventId,
    event_time: Date.now(),
    device: {
      model: 'capacity-test',
      oaid: crypto.sha256(`device-${unique}`, 'hex'),
      os: 'android',
    },
    extensions: {
      creative_id: 'capacity-creative',
      ip: '203.0.113.10',
      ua: 'SCX-OCPX-Capacity/2.0',
    },
  });
  const timestamp = String(Date.now());
  const nonce = crypto.sha256(`nonce-${unique}-${timestamp}`, 'hex').slice(0, 32);
  const path = new URL(target).pathname;
  const canonical = `POST\n${path}\n${timestamp}\n${nonce}\n${crypto.sha256(body, 'hex')}`;
  const signature = crypto.hmac('sha256', secret, canonical, 'hex');

  const response = http.post(target, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-OCPX-Key-Version': keyVersion,
      'X-OCPX-Nonce': nonce,
      'X-OCPX-Signature': signature,
      'X-OCPX-Timestamp': timestamp,
    },
    tags: { endpoint: `ocpx-feedback-${mediaCode}` },
  });

  check(response, {
    'feedback accepted': (result) => result.status === 202,
  });
}

function required(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}
