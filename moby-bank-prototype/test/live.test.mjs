// End to end: the real component logic against a real harness backend, over HTTP and server-sent events.
// Skipped unless HARNESS_API points at one, for example:
//   (cd ../moby-bank-quarkus && ./mvnw quarkus:dev -Dharness.fake.agent-delay-ms=300 -Dharness.fake.move-packaging-ms=300 -Dharness.fake.move-transfer-ms=300)
//   HARNESS_API=http://localhost:8080 npm run test:live
import assert from 'node:assert/strict';
import { after, test } from 'node:test';
import { NodeEventSource } from './event-source.mjs';
import { createComponent, recordingToasts } from './harness.mjs';

const API = process.env.HARNESS_API;
const skip = API ? false : 'set HARNESS_API to run the live test against a real backend';

const toast = recordingToasts();
const ui = API && createComponent({
  fetch: globalThis.fetch,
  EventSource: NodeEventSource,
  toast,
  location: { search: `?api=${API}`, hostname: 'localhost', protocol: 'http:' }
});

after(() => ui && ui.componentWillUnmount());

/** Waits until the condition holds, failing with a description if it never does. */
async function until(description, condition, timeoutMs = 10000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    if (condition()) return Date.now() - start;
    await new Promise(resolve => setTimeout(resolve, 25));
  }
  throw new Error(`timed out waiting for: ${description}`);
}

const active = () => ui.state.details[ui.state.activeId];

test('the app loads the analyst, the history and the folders from the backend', { skip }, async () => {
  await ui.boot();

  assert.equal(ui.state.apiError, '');
  assert.equal(ui.state.user.name, 'Hermione Granger');
  assert.equal(ui.state.user.initials, 'HG');
  assert.ok(ui.state.convos.length >= 5, 'the seeded conversations');
  assert.ok(ui.state.library.length >= 6, 'the folder library');
  await until('the newest conversation to open', () => active() && active().messages.length > 0);

  const vals = ui.renderVals();
  assert.ok(vals.historyGroups.some(g => g.label === 'Today'));
  assert.equal(vals.historyGroups.flatMap(g => g.items).length, ui.state.convos.length);
  assert.ok(vals.folders.length >= 3, 'the demo connects three folders');
  assert.ok(vals.messages.some(m => m.isAgent && m.hasTable), 'the seeded analysis has its table');
});

test('a new conversation takes a message with an upload and an OneDrive document, and the reply arrives live', { skip }, async () => {
  await ui.newChat();
  assert.ok(ui.state.activeId);
  assert.equal(active().messages.length, 0);

  // the stream confirms it is connected with `ready`, which re-reads the conversation
  await new Promise(resolve => setTimeout(resolve, 300));

  await ui.onFiles({ target: { files: [new File(['quarterly notes'], 'notes.txt')], value: '' } });
  assert.deepEqual(ui.state.pending.map(p => [p.name, p.sourceKey]), [['notes.txt', 'upload']]);

  await ui.openAttach();
  const document = ui.state.attachFiles.find(f => f.name === 'Fathom_Q2_2026_10-Q.pdf');
  assert.ok(document, 'an attachable document from a connected folder');
  ui.togglePick(document.name);
  await ui.confirmPicker();
  assert.deepEqual(ui.state.pending.map(p => p.sourceKey), ['upload', 'onedrive']);

  ui.setState({ draft: 'Summarize these for me' });
  const sentAt = Date.now();
  await ui.send();

  assert.equal(ui.state.draft, '');
  assert.deepEqual(ui.state.pending, []);
  assert.equal(ui.renderVals().cantSend, true, 'sending is blocked while the agent works');

  const arrivedAfter = await until('the assistant reply', () => active().messages.length === 2 && !ui.renderVals().thinking)
    .then(() => Date.now() - sentAt);
  assert.ok(arrivedAfter < 1800,
    `the reply must arrive over the event stream (took ${arrivedAfter} ms; the fallback poll only fires after 2000 ms)`);

  const [question, answer] = ui.renderVals().messages;
  assert.equal(question.text, 'Summarize these for me');
  assert.deepEqual(question.files, ['notes.txt', 'Fathom_Q2_2026_10-Q.pdf']);
  assert.equal(answer.isAgent, true);
  assert.equal(answer.stepSummary, 'Read 2 files, ran 1 calculation');
  assert.equal(answer.hasTable, true);
  assert.deepEqual(answer.cols, ['Metric', 'Current', 'Prior year', 'Change']);
  await until('the header to show the conversation title', () => ui.renderVals().activeTitle === 'Summarize these for me');
  assert.equal(ui.renderVals().cantSend, true, 'no draft yet');
  assert.deepEqual(ui.renderVals().sessionFiles, ['notes.txt', 'Fathom_Q2_2026_10-Q.pdf']);
  await until('the history to show the new title', () => ui.state.convos.some(c => c.title === 'Summarize these for me'));
});

test('the session moves to the cloud and back to local, with progress and a result each way', { skip }, async () => {
  assert.equal(active().location, 'local');
  toast.calls.length = 0;

  await ui.moveToCloud();
  await until('the session to settle in the cloud', () => active().location === 'cloud' && !ui.renderVals().moving);
  assert.equal(ui.renderVals().isCloud, true);
  await until('the history row to show the cloud icon', () => ui.state.convos.find(c => c.id === ui.state.activeId).location === 'cloud');

  await ui.moveToLocal();
  await until('the session to settle locally', () => active().location === 'local' && !ui.renderVals().moving);
  assert.equal(ui.renderVals().isLocal, true);
  await until('both success toasts', () => toast.calls.filter(t => t.kind === 'success').length >= 2);

  const titles = toast.calls.filter(t => t.kind === 'loading').map(t => t.message);
  assert.ok(titles.includes('Moving session to Moby Private Cloud…'), titles.join(' | '));
  assert.ok(titles.includes('Moving session to the local model…'), titles.join(' | '));
  assert.ok(toast.calls.some(t => t.kind === 'loading' && /Packaging/.test(t.description ?? '')), 'progress from the backend reached the toast');
  assert.deepEqual(toast.calls.filter(t => t.kind === 'success').map(t => t.message), ['Session moved to cloud', 'Session moved to local']);
  assert.equal(toast.calls.filter(t => t.kind === 'error').length, 0);
});

test('after the moves the conversation still works', { skip }, async () => {
  ui.setState({ draft: 'One more question' });
  await ui.send();
  await until('the second reply', () => active().messages.length === 4 && !ui.renderVals().thinking);
  assert.equal(active().status, 'idle');
});

test('connecting another folder makes its documents attachable', { skip }, async () => {
  await ui.openConnect();
  const unconnected = ui.state.library.find(f => !f.connected);
  assert.ok(unconnected, 'a folder that is not connected yet');
  ui.togglePick(unconnected.id);
  await ui.confirmPicker();

  assert.equal(ui.state.library.find(f => f.id === unconnected.id).connected, true);
  assert.ok(ui.renderVals().folders.some(f => f.id === unconnected.id));
  await ui.openAttach();
  assert.ok(ui.state.attachFiles.some(f => f.folderId === unconnected.id), 'its documents are offered');
});

test('opening another conversation shows its history and switches the stream', { skip }, async () => {
  const other = ui.state.convos.find(c => c.id !== ui.state.activeId);
  await ui.select(other.id);
  assert.equal(ui.state.activeId, other.id);
  assert.equal(ui.streamId, other.id);
  assert.ok(active().messages.length > 0);
});
