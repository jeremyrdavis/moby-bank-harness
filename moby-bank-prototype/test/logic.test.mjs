// Unit tests for the component's behavior, with a fake API. No browser, no backend.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createComponent, fakeFetch, recordingToasts, response } from './harness.mjs';

const USER = { id: 'demo-analyst', name: 'Hermione Granger', role: 'Credit Research', initials: 'HG' };
const SUMMARY = (id, title, location, group) => ({ id, title, location, status: 'idle', group, updatedAt: '2026-09-30T12:00:00Z' });
const DETAIL = (id, overrides = {}) => ({ id, title: 'One', location: 'local', status: 'idle', moveTarget: null, group: 'Today',
  messages: [], files: [], ...overrides });
const MESSAGE = (id, role, extra = {}) => ({ id, role, text: '', files: [], steps: [], paragraphs: [], table: null,
  createdAt: '2026-09-30T12:00:00Z', ...extra });

function component(routes = {}, options = {}) {
  const fetch = fakeFetch(routes);
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast, ...options });
  return { c, fetch, toast };
}

// --- API access ----------------------------------------------------------------------------------------------

test('the API lives where ?api= says, else window.HARNESS_API, else this host on port 8080', () => {
  assert.equal(createComponent({ location: { search: '?api=http://10.0.0.5:9000/', hostname: 'x', protocol: 'http:' } }).apiBase(), 'http://10.0.0.5:9000');
  assert.equal(createComponent({ location: { search: '', hostname: 'ui.example', protocol: 'https:' } }).apiBase(), 'https://ui.example:8080');
  const c = createComponent();
  c.apiBase(); // default location is app.example over http
  assert.equal(c.apiBase(), 'http://app.example:8080');
});

test('an unreachable API produces a message that names the address', async () => {
  const c = createComponent({ fetch: async () => { throw new TypeError('fetch failed'); } });
  await assert.rejects(() => c.api('/api/me'), /Cannot reach the harness API at http:\/\/app\.example:8080/);
});

test('an API error surfaces the server\'s message and status', async () => {
  const { c } = component({ 'GET /api/x': () => response(409, { error: 'conflict', message: 'Session is RUNNING' }) });
  await assert.rejects(() => c.api('/api/x'), error => error.message === 'Session is RUNNING' && error.status === 409);
});

// --- starting up ---------------------------------------------------------------------------------------------

test('boot loads the user, history and folders, then opens the most recent conversation', async () => {
  const { c, fetch } = component({
    'GET /api/me': USER,
    'GET /api/sessions': [SUMMARY('s1', 'One', 'local', 'Today'), SUMMARY('s2', 'Two', 'cloud', 'Earlier')],
    'GET /api/folders': [{ id: 'f1', name: 'Earnings', path: 'A / B', fileCount: 3, connected: true }],
    'GET /api/sessions/s1': DETAIL('s1', { messages: [MESSAGE('m1', 'user', { text: 'hi' })] })
  });

  await c.boot();

  assert.equal(c.state.loaded, true);
  assert.equal(c.state.activeId, 's1');
  assert.equal(c.state.user.name, 'Hermione Granger');
  assert.equal(c.renderVals().messages.length, 1);
  assert.deepEqual(fetch.calls.map(call => `${call.method} ${call.path}`).sort(),
    ['GET /api/folders', 'GET /api/me', 'GET /api/sessions', 'GET /api/sessions/s1']);
});

test('a backend that is down shows a banner with a retry instead of an empty app', async () => {
  const c = createComponent({ fetch: async () => { throw new TypeError('fetch failed'); } });
  await c.boot();
  const vals = c.renderVals();
  assert.equal(vals.hasApiError, true);
  assert.match(vals.apiError, /Cannot reach the harness API/);
  assert.equal(typeof vals.retry, 'function');
});

test('with no conversations yet the empty state shows', async () => {
  const { c } = component({ 'GET /api/me': USER, 'GET /api/sessions': [], 'GET /api/folders': [] });
  await c.boot();
  assert.equal(c.renderVals().isEmpty, true);
  assert.equal(c.state.activeId, null);
});

// --- what the template is given ------------------------------------------------------------------------------

function withSession(detail, extraState = {}) {
  const { c, fetch, toast } = component();
  c.state = { ...c.state, loaded: true, activeId: detail.id, details: { [detail.id]: detail }, ...extraState };
  return { c, fetch, toast };
}

test('messages are shaped for the template: file names, step summary, table rows', () => {
  const { c } = withSession(DETAIL('s1', { messages: [
    MESSAGE('m1', 'user', { text: 'Summarize', files: [{ name: 'a.pdf', source: 'onedrive' }, { name: 'b.xlsx', source: 'upload' }] }),
    MESSAGE('m2', 'assistant', { paragraphs: ['One.', 'Two.'],
      steps: [{ kind: 'read', label: 'a.pdf' }, { kind: 'read', label: 'b.xlsx' }, { kind: 'compute', label: 'x()' }],
      table: { cols: ['Metric', 'Value'], rows: [['Leverage', '2.9x'], ['Margin', '17%']] } })
  ] }));

  const [user, agent] = c.renderVals().messages;

  assert.equal(user.isUser, true);
  assert.deepEqual(user.files, ['a.pdf', 'b.xlsx']);
  assert.equal(user.hasFiles, true);
  assert.equal(agent.isAgent, true);
  assert.equal(agent.stepSummary, 'Read 2 files, ran 1 calculation');
  assert.deepEqual(agent.paras, ['One.', 'Two.']);
  assert.equal(agent.hasTable, true);
  assert.deepEqual(agent.cols, ['Metric', 'Value']);
  assert.deepEqual(agent.rows, [{ cells: ['Leverage', '2.9x'] }, { cells: ['Margin', '17%'] }]);
});

test('the files shared in a conversation come from its messages, so they stay current as messages arrive', () => {
  const { c } = withSession(DETAIL('s1', { files: [] }));
  assert.deepEqual(c.renderVals().sessionFiles, []);
  assert.equal(c.renderVals().noSessionFiles, true);

  c.handleEvent('message', { sessionId: 's1', message: MESSAGE('m1', 'user', { files: [{ name: 'a.pdf', source: 'onedrive' }, { name: 'b.xlsx', source: 'upload' }] }) });
  c.handleEvent('message', { sessionId: 's1', message: MESSAGE('m2', 'user', { files: [{ name: 'a.pdf', source: 'onedrive' }] }) });

  assert.deepEqual(c.renderVals().sessionFiles, ['a.pdf', 'b.xlsx'], 'distinct, in the order first shared');
  assert.equal(c.renderVals().noSessionFiles, false);
});

test('a single read and no calculations is summarised in the singular', () => {
  const { c } = withSession(DETAIL('s1', { messages: [MESSAGE('m1', 'assistant', { paragraphs: ['x'], steps: [{ kind: 'read', label: 'a' }] })] }));
  assert.equal(c.renderVals().messages[0].stepSummary, 'Read 1 file');
});

test('steps expand and collapse per message', () => {
  const { c } = withSession(DETAIL('s1', { messages: [MESSAGE('m1', 'assistant', { paragraphs: ['x'], steps: [{ kind: 'read', label: 'a' }] })] }));
  assert.equal(c.renderVals().messages[0].stepsOpen, false);
  c.renderVals().messages[0].toggleSteps();
  assert.equal(c.renderVals().messages[0].stepsOpen, true);
});

test('the sidebar lists connected folders only, and the history by group in order', () => {
  const { c } = withSession(DETAIL('s1'), {
    library: [{ id: 'f1', name: 'Earnings', path: 'A', fileCount: 24, connected: true }, { id: 'f2', name: 'Peers', path: 'B', fileCount: 5, connected: false }],
    convos: [SUMMARY('s3', 'Old', 'local', 'Earlier'), SUMMARY('s1', 'One', 'local', 'Today'), SUMMARY('s2', 'Two', 'cloud', 'Today')]
  });

  const vals = c.renderVals();

  assert.deepEqual(vals.folders.map(f => [f.name, f.count]), [['Earnings', 24]]);
  assert.deepEqual(vals.historyGroups.map(g => g.label), ['Today', 'Earlier']);
  assert.deepEqual(vals.historyGroups[0].items.map(i => [i.title, i.cloud]), [['One', false], ['Two', true]]);
});

test('the header offers Move to cloud for a local session and Move to local for a cloud one', () => {
  const local = withSession(DETAIL('s1', { location: 'local' })).c.renderVals();
  assert.deepEqual([local.isLocal, local.isCloud], [true, false]);
  const cloud = withSession(DETAIL('s1', { location: 'cloud' })).c.renderVals();
  assert.deepEqual([cloud.isLocal, cloud.isCloud], [false, true]);
});

test('the connect dialog marks connected folders and the attach dialog lists their documents', () => {
  const { c } = withSession(DETAIL('s1'), {
    library: [{ id: 'f1', name: 'Earnings', path: 'A / B', fileCount: 24, connected: true }, { id: 'f2', name: 'Peers', path: 'C', fileCount: 5, connected: false }],
    attachFiles: [{ folderId: 'f1', folderName: 'Earnings', folderPath: 'A / B', name: 'a.pdf' }],
    pickerMode: 'connect'
  });
  let items = c.renderVals().pickerItems;
  assert.deepEqual(items.map(i => [i.name, i.note, i.disabled, i.checked]), [['Earnings', 'Connected', true, true], ['Peers', '5 files', false, false]]);

  c.state.pickerMode = 'attach';
  items = c.renderVals().pickerItems;
  assert.deepEqual(items.map(i => [i.name, i.meta]), [['a.pdf', 'A / B']]);
});

test('the picker button counts what is selected, and a connected folder cannot be toggled', () => {
  const { c } = withSession(DETAIL('s1'), { pickerMode: 'connect', library: [
    { id: 'f1', name: 'E', path: 'A', fileCount: 1, connected: true }, { id: 'f2', name: 'P', path: 'B', fileCount: 1, connected: false }] });
  assert.equal(c.renderVals().pickerCta, 'Connect');
  assert.equal(c.renderVals().pickerEmpty, true);
  c.renderVals().pickerItems[0].toggle();
  assert.deepEqual(c.state.pickerSel, [], 'already connected');
  c.renderVals().pickerItems[1].toggle();
  assert.deepEqual(c.state.pickerSel, ['f2']);
  assert.equal(c.renderVals().pickerCta, 'Connect 1');
});

// --- sending -------------------------------------------------------------------------------------------------

test('sending posts the text and attachments and shows the stored message', async () => {
  const fetch = fakeFetch({
    'POST /api/sessions/s1/messages': () => response(202, MESSAGE('m1', 'user', { text: 'Summarize', files: [{ name: 'a.pdf', source: 'onedrive' }] })),
    'GET /api/sessions': []
  });
  const c = createComponent({ fetch });
  c.state = { ...c.state, loaded: true, activeId: 's1', details: { s1: DETAIL('s1') }, draft: '  Summarize  ',
    pending: [{ name: 'a.pdf', sourceKey: 'onedrive', source: 'OneDrive' }, { name: 'n.docx', sourceKey: 'upload', source: 'Upload' }] };

  await c.send();

  const post = fetch.calls.find(call => call.method === 'POST');
  assert.deepEqual(post.body, { text: 'Summarize', files: [{ name: 'a.pdf', source: 'onedrive' }, { name: 'n.docx', source: 'upload' }] });
  assert.equal(c.state.draft, '');
  assert.deepEqual(c.state.pending, []);
  assert.equal(c.state.details.s1.messages.length, 1);
});

test('nothing is sent when there is no text and no attachment, or while the agent is working', async () => {
  const { c, fetch } = withSession(DETAIL('s1'), { draft: '   ' });
  await c.send();
  c.state.draft = 'hello'; c.state.working = { s1: { label: 'Working…', step: '' } };
  await c.send();
  assert.equal(fetch.calls.length, 0);
  assert.equal(c.renderVals().cantSend, true);
});

test('a refused message gives the analyst their text and files back and explains why', async () => {
  const fetch = fakeFetch({ 'POST /api/sessions/s1/messages': () => response(409, { error: 'conflict', message: 'Session s1 is MOVING' }) });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  const pending = [{ name: 'a.pdf', sourceKey: 'onedrive', source: 'OneDrive' }];
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1') }, draft: 'hello', pending };

  await c.send();

  assert.equal(c.state.draft, 'hello');
  assert.deepEqual(c.state.pending, pending);
  assert.deepEqual(toast.calls.map(t => [t.kind, t.message]), [['error', 'Session s1 is MOVING']]);
});

test('sending with no conversation open starts one first', async () => {
  const fetch = fakeFetch({
    'POST /api/sessions': () => response(201, DETAIL('new1')),
    'POST /api/sessions/new1/messages': () => response(202, MESSAGE('m1', 'user', { text: 'hello' })),
    'GET /api/sessions': []
  });
  const c = createComponent({ fetch });
  c.state = { ...c.state, loaded: true, draft: 'hello' };

  await c.send();

  assert.equal(c.state.activeId, 'new1');
  assert.equal(fetch.calls[0].body.location, 'local');
  assert.ok(fetch.calls.some(call => call.path === '/api/sessions/new1/messages'));
});

// --- the live event stream -----------------------------------------------------------------------------------

test('thinking, steps and the reply move the conversation along', () => {
  const { c } = withSession(DETAIL('s1', { messages: [MESSAGE('m1', 'user', { text: 'hi' })] }));
  c.refreshList = async () => {};

  c.handleEvent('thinking', { sessionId: 's1', label: 'Reading 1 file on local model…' });
  assert.equal(c.renderVals().thinking, true);
  assert.equal(c.renderVals().thinkingLabel, 'Reading 1 file on local model…');
  assert.equal(c.renderVals().cantSend, true);

  c.handleEvent('step', { sessionId: 's1', step: { kind: 'read', label: 'a.pdf' } });
  assert.equal(c.renderVals().thinkingLabel, 'Reading 1 file on local model… · read a.pdf');
  c.handleEvent('step', { sessionId: 's1', step: { kind: 'compute', label: 'leverage()' } });
  assert.equal(c.renderVals().thinkingLabel, 'Reading 1 file on local model… · compute leverage()');

  c.handleEvent('message', { sessionId: 's1', message: MESSAGE('m2', 'assistant', { paragraphs: ['Done.'] }) });
  assert.equal(c.renderVals().thinking, false);
  assert.equal(c.renderVals().messages.length, 2);
});

test('a message that arrives twice (response and stream) is shown once', () => {
  const { c } = withSession(DETAIL('s1'));
  const message = MESSAGE('m1', 'user', { text: 'hi' });
  c.handleEvent('message', { sessionId: 's1', message });
  c.handleEvent('message', { sessionId: 's1', message });
  assert.equal(c.state.details.s1.messages.length, 1);
});

test('events for a conversation that is not open are ignored', () => {
  const { c } = withSession(DETAIL('s1'));
  c.handleEvent('message', { sessionId: 'other', message: MESSAGE('m1', 'user', { text: 'hi' }) });
  assert.deepEqual(Object.keys(c.state.details), ['s1']);
});

test('the stream reconnecting (ready) re-reads the conversation to catch up', async () => {
  const fetch = fakeFetch({ 'GET /api/sessions/s1': DETAIL('s1', { messages: [MESSAGE('m1', 'user', { text: 'missed while offline' })] }) });
  const c = createComponent({ fetch });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1') } };

  c.handleEvent('ready', { sessionId: 's1' });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(c.state.details.s1.messages.length, 1);
});

test('the history refresh gives an open conversation the title the server derived from its first message', async () => {
  const fetch = fakeFetch({ 'GET /api/sessions': [SUMMARY('s1', 'Summarize these for me', 'local', 'Today')] });
  const c = createComponent({ fetch });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1', { title: 'New conversation' }) } };
  assert.equal(c.renderVals().activeTitle, 'New conversation');

  await c.refreshList();

  assert.equal(c.renderVals().activeTitle, 'Summarize these for me');
  assert.equal(c.state.convos[0].title, 'Summarize these for me');
});

// --- moving --------------------------------------------------------------------------------------------------

test('moving posts the target, shows a progress toast and ends with a success toast', async () => {
  const fetch = fakeFetch({
    'POST /api/sessions/s1/move': () => response(202, DETAIL('s1', { status: 'moving', moveTarget: 'cloud' })),
    'GET /api/sessions/s1': () => response(200, DETAIL('s1', { location: 'cloud', status: 'idle' })),
    'GET /api/sessions': []
  });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1', { messages: [MESSAGE('m1', 'user', { files: [{ name: 'a.pdf', source: 'upload' }] })] }) } };

  await c.moveToCloud();
  assert.equal(c.renderVals().moving, true);
  assert.equal(c.renderVals().cantSend, true);
  assert.deepEqual(fetch.calls.find(call => call.method === 'POST').body, { target: 'cloud' });
  assert.equal(toast.calls[0].kind, 'loading');
  assert.equal(toast.calls[0].message, 'Moving session to Moby Private Cloud…');
  assert.equal(toast.calls[0].description, 'Preparing · 1 messages · 1 files');

  c.handleEvent('move-progress', { sessionId: 's1', stage: 'packaging', detail: 'Packaging context · 1 message · 1 file' });
  assert.equal(toast.calls.at(-1).description, 'Packaging context · 1 message · 1 file');
  assert.equal(toast.calls.at(-1).id, 'toast-1', 'progress updates the same toast instead of opening a new one');

  c.handleEvent('moved', { sessionId: 's1', location: 'cloud' });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(c.renderVals().moving, false);
  assert.equal(c.state.details.s1.location, 'cloud');
  assert.equal(c.renderVals().isCloud, true);
  assert.deepEqual(toast.calls.filter(t => t.kind === 'success').map(t => t.message), ['Session moved to cloud']);
});

test('moving back to local says so', async () => {
  const fetch = fakeFetch({
    'POST /api/sessions/s1/move': () => response(202, DETAIL('s1', { location: 'cloud', status: 'moving', moveTarget: 'local' })),
    'GET /api/sessions/s1': () => response(200, DETAIL('s1', { location: 'local' })),
    'GET /api/sessions': []
  });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1', { location: 'cloud' }) } };

  await c.moveToLocal();
  c.handleEvent('moved', { sessionId: 's1', location: 'local' });

  assert.equal(toast.calls[0].message, 'Moving session to the local model…');
  assert.deepEqual(toast.calls.filter(t => t.kind === 'success').map(t => t.message), ['Session moved to local']);
});

test('a failed move shows an error and leaves the session where it was', async () => {
  const fetch = fakeFetch({
    'POST /api/sessions/s1/move': () => response(202, DETAIL('s1', { status: 'moving', moveTarget: 'cloud' })),
    'GET /api/sessions/s1': () => response(200, DETAIL('s1', { location: 'local', status: 'idle' }))
  });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1') } };

  await c.moveToCloud();
  c.handleEvent('move-failed', { sessionId: 's1', reason: 'private link down' });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(c.renderVals().moving, false);
  assert.equal(c.state.details.s1.location, 'local');
  const error = toast.calls.find(t => t.kind === 'error');
  assert.equal(error.message, 'Move failed');
  assert.equal(error.description, 'private link down');
});

test('a move the server refuses (409) is reported and does not leave the UI stuck', async () => {
  const fetch = fakeFetch({ 'POST /api/sessions/s1/move': () => response(409, { error: 'conflict', message: 'already runs there' }) });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1') } };

  await c.moveToCloud();

  assert.equal(c.renderVals().moving, false);
  assert.equal(toast.calls.at(-1).kind, 'error');
  assert.equal(toast.calls.at(-1).description, 'already runs there');
});

test('moving to where the session already is, or while it is busy, does nothing', async () => {
  const { c, fetch } = withSession(DETAIL('s1', { location: 'local' }));
  await c.moveToLocal();
  c.state.working = { s1: { label: 'Working…', step: '' } };
  await c.moveToCloud();
  assert.equal(fetch.calls.length, 0);
});

test('opening a conversation that is mid-move (after a reload) shows it as moving', async () => {
  const fetch = fakeFetch({ 'GET /api/sessions/s1': DETAIL('s1', { status: 'moving', moveTarget: 'cloud', messages: [MESSAGE('m1', 'user')] }) });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.watch = () => {};

  await c.fetchSession('s1');

  assert.equal(c.state.moving.s1, true);
  assert.equal(toast.calls[0].kind, 'loading');
});

// --- attachments and folders ---------------------------------------------------------------------------------

test('attaching from OneDrive adds the chosen documents as OneDrive attachments', async () => {
  const { c } = withSession(DETAIL('s1'), { pickerMode: 'attach', pickerOpen: true, pickerSel: ['a.pdf', 'b.pdf'] });
  await c.confirmPicker();
  assert.deepEqual(c.state.pending, [{ name: 'a.pdf', sourceKey: 'onedrive', source: 'OneDrive' }, { name: 'b.pdf', sourceKey: 'onedrive', source: 'OneDrive' }]);
  assert.equal(c.state.pickerOpen, false);
});

test('connecting folders posts their ids and shows the updated library', async () => {
  const fetch = fakeFetch({ 'POST /api/folders/connect': () => response(200, [{ id: 'f2', name: 'Peers', path: 'B', fileCount: 5, connected: true }]) });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.state = { ...c.state, pickerMode: 'connect', pickerOpen: true, pickerSel: ['f2'] };

  await c.confirmPicker();

  assert.deepEqual(fetch.calls[0].body, { folderIds: ['f2'] });
  assert.equal(c.state.library[0].connected, true);
  assert.equal(c.state.pickerOpen, false);
  assert.deepEqual(toast.calls.map(t => t.message), ['1 folder connected']);
});

test('opening the attach dialog loads the documents of connected folders', async () => {
  const files = [{ folderId: 'f1', folderName: 'E', folderPath: 'A', name: 'a.pdf' }];
  const { c } = component({ 'GET /api/folders/connected/files': files });
  await c.openAttach();
  assert.deepEqual(c.state.attachFiles, files);
  assert.equal(c.state.pickerMode, 'attach');
});

test('an uploaded file becomes an upload attachment under the name the server stored', async () => {
  const fetch = fakeFetch({ 'POST /api/sessions/s1/uploads': (call) => {
    assert.ok(call.raw.body instanceof FormData, 'sent as multipart form data');
    assert.equal(call.raw.headers, undefined, 'the browser sets the multipart boundary itself');
    return response(201, { name: 'notes.docx', source: 'upload' });
  } });
  const c = createComponent({ fetch });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1') } };

  await c.onFiles({ target: { files: [new File(['hello'], '../notes.docx')], value: 'x' } });

  assert.deepEqual(c.state.pending, [{ name: 'notes.docx', sourceKey: 'upload', source: 'Upload' }]);
});

test('a failed upload is reported and adds nothing', async () => {
  const fetch = fakeFetch({ 'POST /api/sessions/s1/uploads': () => response(400, { error: 'bad_request', message: 'invalid file name' }) });
  const toast = recordingToasts();
  const c = createComponent({ fetch, toast });
  c.state = { ...c.state, activeId: 's1', details: { s1: DETAIL('s1') } };

  await c.onFiles({ target: { files: [new File(['x'], 'bad')], value: '' } });

  assert.deepEqual(c.state.pending, []);
  assert.match(toast.calls[0].message, /Couldn’t upload bad: invalid file name/);
});

test('the Enter key sends, Shift+Enter does not', async () => {
  const { c } = withSession(DETAIL('s1'), { draft: 'hi' });
  let sent = 0;
  c.send = () => { sent++; };
  let prevented = 0;
  c.onKey({ key: 'Enter', shiftKey: false, preventDefault: () => prevented++ });
  c.onKey({ key: 'Enter', shiftKey: true, preventDefault: () => prevented++ });
  c.onKey({ key: 'a', shiftKey: false, preventDefault: () => prevented++ });
  assert.equal(sent, 1);
  assert.equal(prevented, 1);
});
