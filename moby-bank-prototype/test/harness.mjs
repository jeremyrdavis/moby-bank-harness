// Loads the real component out of "Bank Agent Harness.dc.html" and runs it in Node with stand-ins for the browser
// and the dc-runtime, so its state handling and API calls can be tested without a browser.
import { readFileSync } from 'node:fs';

const HTML_URL = new URL('../Bank Agent Harness.dc.html', import.meta.url);

export function html() {
  return readFileSync(HTML_URL, 'utf8');
}

export function scriptSource() {
  const match = /<script type="text\/x-dc" data-dc-script[^>]*>([\s\S]*?)<\/script>/.exec(html());
  if (!match) throw new Error('no <script data-dc-script> in the component');
  return match[1];
}

export function templateSource() {
  const match = /<x-dc>([\s\S]*?)<\/x-dc>/.exec(html());
  if (!match) throw new Error('no <x-dc> template in the component');
  return match[1];
}

/** The part of the dc-runtime's DCLogic the component uses: props, and a synchronous setState. */
class StubLogic {
  constructor(props = {}) {
    this.props = props;
  }

  setState(update, callback) {
    const next = typeof update === 'function' ? update(this.state) : update;
    if (next) this.state = { ...this.state, ...next };
    if (callback) callback();
  }
}

/** Toast calls are recorded instead of drawn. */
export function recordingToasts() {
  const calls = [];
  const record = kind => (message, options = {}) => {
    calls.push({ kind, message, ...options });
    return options.id ?? `toast-${calls.length}`;
  };
  return { calls, loading: record('loading'), success: record('success'), error: record('error') };
}

/**
 * Builds a component. `fetch` and `EventSource` default to ones that fail loudly, so a test that should not touch
 * the network finds out if it does. Timers are unref'd so a forgotten poll never keeps Node alive.
 */
export function createComponent({ fetch, EventSource, location = { search: '', hostname: 'app.example', protocol: 'http:' },
                                  toast = recordingToasts(), props = {} } = {}) {
  const unexpected = what => () => { throw new Error(`unexpected ${what} in this test`); };
  const env = {
    window: { Trident: { toast } },
    fetch: fetch ?? unexpected('fetch'),
    EventSource: EventSource ?? undefined,
    location,
    setTimeout: (fn, ms) => { const t = setTimeout(fn, ms); t.unref?.(); return t; }
  };
  const build = new Function('DCLogic', 'React', 'window', 'fetch', 'EventSource', 'FormData', 'URLSearchParams',
    'location', 'requestAnimationFrame', 'setTimeout', 'console', `${scriptSource()}\nreturn Component;`);
  const Component = build(StubLogic, { createRef: () => ({ current: null }) }, env.window, env.fetch, env.EventSource,
    FormData, URLSearchParams, env.location, () => {}, env.setTimeout, console);
  const component = new Component(props);
  component.toasts = toast;
  return component;
}

/** A fetch that answers from a table of "METHOD /path" -> JSON (or a function), and records every call. */
export function fakeFetch(routes) {
  const calls = [];
  async function fetch(url, options = {}) {
    const path = new URL(url).pathname;
    const method = options.method ?? 'GET';
    calls.push({ method, path, body: typeof options.body === 'string' ? JSON.parse(options.body) : undefined, raw: options });
    const route = routes[`${method} ${path}`];
    if (route === undefined) return response(404, { error: 'not_found', message: `no route for ${method} ${path}` });
    const answer = typeof route === 'function' ? route(calls.at(-1)) : route;
    return answer instanceof Response ? answer : response(200, answer);
  }
  fetch.calls = calls;
  return fetch;
}

export function response(status, body) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
