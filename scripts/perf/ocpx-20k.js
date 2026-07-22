import crypto from 'k6/crypto';
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const baseUrl = __ENV.SCX_BASE_URL;
const clientId = __ENV.SCX_CHANNEL_CLIENT_ID;
const keyVersion = __ENV.SCX_CHANNEL_KEY_VERSION;
const secret = __ENV.SCX_CHANNEL_SECRET;
const taskCode = __ENV.SCX_TASK_CODE;
const deviceSeed = __ENV.SCX_DEVICE_SEED;
const dedupDeviceHash = __ENV.SCX_DEDUP_DEVICE_HASH;
const runId = __ENV.SCX_RUN_ID;

if (![baseUrl, clientId, keyVersion, secret, taskCode, deviceSeed, dedupDeviceHash, runId].every(Boolean)) {
	throw new Error('SCX_BASE_URL、渠道凭据、SCX_TASK_CODE、SCX_DEVICE_SEED、SCX_DEDUP_DEVICE_HASH、SCX_RUN_ID 均必须通过环境变量提供');
}

const smoke = __ENV.SCX_PERF_PROFILE === 'smoke';

export const options = {
	discardResponseBodies: true,
	summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
	scenarios: smoke ? {
		smoke: arrival('15s', 10, '0s'),
		smoke_dedup: arrival('15s', 2, '0s', 'dedupTraffic'),
	} : {
		warmup: arrival('1m', 1950, '0s'),
    warmup_dedup: arrival('1m', 50, '0s', 'dedupTraffic'),
		steady: arrival('5m', 19900, '1m'),
    steady_dedup: arrival('5m', 100, '1m', 'dedupTraffic'),
		burst: arrival('30s', 39900, '6m'),
    burst_dedup: arrival('30s', 100, '6m', 'dedupTraffic'),
		recovery: arrival('1m', 1950, '6m30s'),
    recovery_dedup: arrival('1m', 50, '6m30s', 'dedupTraffic'),
	},
  thresholds: {
    http_req_failed: ['rate<0.001'],
    'http_req_duration{entry:ingress}': ['p(99)<100'],
    'http_req_duration{entry:rta}': ['p(99)<80'],
    dropped_iterations: ['count==0'],
  },
};

function arrival(duration, rate, startTime, executor = 'traffic') {
  return {
    executor: 'constant-arrival-rate', duration, rate, startTime,
    timeUnit: '1s', preAllocatedVUs: Math.max(500, Math.ceil(rate / 20)),
		maxVUs: Math.max(2000, Math.ceil(rate / 5)), exec: executor,
  };
}

function headers(method, path, canonical) {
  const timestamp = Date.now().toString();
  const nonce = `${exec.scenario.name}-${exec.scenario.iterationInTest}-${exec.vu.idInTest}-${timestamp}`;
  const payloadHash = crypto.sha256(canonical, 'hex');
  const signingInput = [method, path, timestamp, nonce, payloadHash].join('\n');
  return {
    'Content-Type': 'application/json',
    'X-Client-Id': clientId,
    'X-Key-Version': keyVersion,
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Sign': crypto.hmac('sha256', secret, signingInput, 'hex'),
  };
}

function device(unique) {
  const idHash = crypto.sha256(`${deviceSeed}:${unique}`, 'hex');
  return { platform: 'ANDROID', idType: 'OAID', hashAlgorithm: 'SHA256', idHash, idVersion: null };
}

function eventBody(eventType) {
  const unique = `${exec.scenario.name}-${exec.scenario.iterationInTest}-${exec.vu.idInTest}`;
  return {
		schemaVersion: '1.1', eventId: `${runId}-${eventType.toLowerCase()}-${unique}`, taskCode,
    eventType, occurredAt: new Date().toISOString(), device: device(unique),
    businessEventKey: eventType === 'CLICK' ? null : `business-${unique}`,
    callbackRef: null, callbackUrl: null, attributes: {},
  };
}

export function traffic() {
  const selector = exec.scenario.iterationInTest % 10;
  if (selector < 7) {
    sendEvent('/open-api/events/v1/clicks', eventBody('CLICK'));
  } else if (selector < 9) {
    sendEvent('/open-api/events/v1/conversions', eventBody('ACTIVATE'));
  } else {
    const path = '/open-api/rta/v1/decisions';
    const body = JSON.stringify({
			schemaVersion: '1.1', requestId: `${runId}-rta-${exec.scenario.name}-${exec.scenario.iterationInTest}`,
      taskCode, device: device(`rta-${exec.scenario.name}-${exec.scenario.iterationInTest}`), attributes: {},
    });
    const response = http.post(`${baseUrl}${path}`, body,
      { headers: headers('POST', path, body), tags: { entry: 'rta' } });
    check(response, { 'RTA HTTP 200': (value) => value.status === 200 });
  }
}

export function dedupTraffic() {
  const path = '/open-api/events/v1/clicks';
  const unique = `${exec.scenario.name}-${exec.scenario.iterationInTest}`;
  const value = eventBody('CLICK');
	value.eventId = `${runId}-dedup-${unique}`;
  value.device.idHash = dedupDeviceHash;
  sendEvent(path, value);
}

function sendEvent(path, value) {
  const body = JSON.stringify(value);
  const response = http.post(`${baseUrl}${path}`, body,
    { headers: headers('POST', path, body), tags: { entry: 'ingress' } });
  check(response, { '事件 HTTP 202': (result) => result.status === 202 });
}
