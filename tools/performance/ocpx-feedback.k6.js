import crypto from 'k6/crypto';
import exec from 'k6/execution';
import http from 'k6/http';
import { check } from 'k6';

const target = required('OCPX_TARGET');
const channelCode = required('OCPX_CHANNEL_CODE');
const monitorId = required('OCPX_MONITOR_ID');
const secret = required('OCPX_CHANNEL_SECRET');
const keyVersion = __ENV.OCPX_KEY_VERSION || '1';
const rate = Number(__ENV.OCPX_RATE || '20000');
const duration = __ENV.OCPX_DURATION || '30m';

export const options = {
  discardResponseBodies: true,
  scenarios: {
    accepted_feedback: {
      executor: 'constant-arrival-rate',
      duration,
      maxVUs: Number(__ENV.OCPX_MAX_VUS || '5000'),
      preAllocatedVUs: Number(__ENV.OCPX_PREALLOCATED_VUS || '1000'),
      rate,
      timeUnit: '1s',
    },
  },
  thresholds: {
    checks: ['rate>=0.999'],
    http_req_duration: ['p(95)<=50', 'p(99)<=100'],
    http_req_failed: ['rate<=0.001'],
  },
};

export default function () {
  const unique = `${exec.instance.idInTest}-${exec.scenario.iterationInTest}`;
  const eventId = `load-${unique}`;
  const body = JSON.stringify({
    schema_version: '1.0',
    event_id: eventId,
    event: 'click',
    monitor_id: monitorId,
    clickid: eventId,
    oaid: crypto.sha256(`device-${unique}`, 'hex'),
    ip: '203.0.113.10',
    ua: 'SCX-OCPX-Capacity/1.0',
    os: 'android',
    model: 'capacity-test',
    creative_id: 'capacity-creative',
    event_time: Date.now(),
  });
  const timestamp = String(Date.now());
  const nonce = crypto.sha256(`nonce-${unique}-${timestamp}`, 'hex').slice(0, 32);
  const path = new URL(target).pathname;
  const canonical = `POST\n${path}\n${timestamp}\n${nonce}\n${crypto.sha256(body, 'hex')}`;
  const signature = crypto.hmac('sha256', secret, canonical, 'hex');

  const response = http.post(target, body, {
    headers: {
      'Content-Type': 'application/json',
      'X-Client-Id': channelCode,
      'X-Key-Version': keyVersion,
      'X-Nonce': nonce,
      'X-Sign': signature,
      'X-Timestamp': timestamp,
    },
    tags: { endpoint: 'ocpx-feedback' },
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
