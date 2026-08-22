// dsh-mobile protocol verification — the exact flow a phone client runs:
// list sessions → create → prompt → stream events (WS mux) → observe turn lifecycle
// → verify goals/subagents/workspace/llm surfaces.
// Usage: node scripts/verify-protocol.mjs <base-url> [--prompt "msg"] [--dump /tmp/frames.jsonl]
import { randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';

const base = process.argv[2] ?? 'http://127.0.0.1:3080';
const promptText = (() => {
  const i = process.argv.indexOf('--prompt');
  return i >= 0 ? process.argv[i + 1] : 'Reply with exactly: OK';
})();
const dumpPath = (() => {
  const i = process.argv.indexOf('--dump');
  return i >= 0 ? process.argv[i + 1] : null;
})();

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log((ok ? 'PASS' : 'FAIL') + '  ' + name + (detail ? '  — ' + detail : ''));
}

async function unary(method, payload) {
  const res = await fetch(base + '/api/' + method, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ type: 'client-request', rpcId: randomUUID(), method, payload }),
  });
  const json = await res.json().catch(() => null);
  return { res, json, ok: !!res.ok && !!json && json.type === 'server-response' && !!json.rpcId };
}

async function call(method, payload, name) {
  const r = await unary(method, payload);
  const good = r.ok && r.json.result.ok === true;
  check(name, good, good ? 'ok' : 'HTTP ' + r.res.status + ' ' + JSON.stringify(r.json?.result?.error ?? ''));
  return r.json?.result?.value;
}

const wsv = await call('workspace.list', {}, 'workspace.list');
check('workspace.list has workspace', (wsv?.items?.length ?? 0) > 0, (wsv?.items?.length ?? 0) + ' workspace(s)');
const sl = await call('session.list', {}, 'session.list');
check('session.list returns items', Array.isArray(sl?.items), (sl?.items?.length ?? 0) + ' session(s)');
const llm = await call('llm.providers', {}, 'llm.providers');
check('llm.providers active', (llm?.providers ?? []).some(p => p.active), 'active provider present');
await call('llm.models', {}, 'llm.models');
const host = await call('host.describe', {}, 'host.describe');
check('host.describe returns home', typeof host?.home === 'string', host?.home ?? '?');

const bogus = await unary('session.cancel', { sessionId: 'no-such-session' });
check('session.cancel endpoint routes', bogus.res.ok, 'HTTP ' + bogus.res.status);

const created = await call('session.create', {}, 'session.create');
const sessionId = created?.sessionId;
check('session.create returns sessionId', typeof sessionId === 'string', sessionId ?? '?');

const skills = await call('skill.list', { sessionId }, 'skill.list');
check('skill.list returns skills', Array.isArray(skills?.skills), (skills?.skills?.length ?? 0) + ' skills');
const subs = await call('subagent.list', { parentSessionId: sessionId }, 'subagent.list');
check('subagent.list returns entries', Array.isArray(subs?.entries), (subs?.entries?.length ?? 0) + ' subagents');
const sModels = await call('session.models', { sessionId }, 'session.models');
check('session.models current', typeof sModels?.current?.model === 'string', sModels?.current?.model ?? '?');

if (sessionId) {
  const wsUrl = base.replace(/^http/, 'ws') + '/api/events.mux';
  const seen = [];
  let ws = null;
  try {
    ws = new WebSocket(wsUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('ws open failed')); });
    check('events.mux WS opens', true, wsUrl);
  } catch (e) {
    check('events.mux WS opens', false, String(e));
  }
  ws.onmessage = (ev) => { if (typeof ev.data === 'string') seen.push(ev.data); };

  const hist = await call('session.history', { sessionId, maxMessages: 20 }, 'session.history (new)');
  check('session.history (new) no user msg', !(hist?.events ?? []).some(e => JSON.stringify(e).includes('user/message')), (hist?.events?.length ?? 0) + ' events, no user msg');

  const t0 = Date.now();
  const prompt = await call('session.prompt',
    { sessionId, mode: 'queue', content: [{ type: 'text', text: promptText }] },
    'session.prompt accepted');
  check('session.prompt returns accepted', prompt?.accepted === true, 'accepted=' + prompt?.accepted + ' (' + (Date.now() - t0) + 'ms)');

  await new Promise(res => setTimeout(res, 30000));
  const all = seen.join('\n');
  if (dumpPath) writeFileSync(dumpPath, seen.join('\n') + '\n');
  const types = [...new Set([...all.matchAll(/"type":"([^"]+)"/g)].map(m => m[1]))];
  check('mux streamed event frames', types.length > 0, types.slice(0, 12).join(','));
  check('session subscribed', types.includes('session/subscribed'), 'session/subscribed frame');
  check('turn lifecycle seen', all.includes('turn/start'), 'turn/start present');
  check('delta frames seen', /delta/.test(all), 'delta frames present');
  check('session event frames seen', types.includes('session/event'), 'session/event frame');

  const hist2 = await call('session.history', { sessionId, maxMessages: 5 }, 'session.history after turn');
  const evN = hist2?.events?.length ?? 0;
  const hasAssistant = (hist2?.events ?? []).some(e => JSON.stringify(e).includes('assistant'));
  check('assistant reply persisted', evN >= 1 && hasAssistant, evN + ' events, assistant=' + hasAssistant);
  if (ws) ws.close();
}

console.log('\n=== SUMMARY ===');
const fails = results.filter(r => !r.ok);
console.log(JSON.stringify({ base, passed: results.length - fails.length, failed: fails.length, results }, null, 2));
process.exit(fails.length ? 1 : 0);
