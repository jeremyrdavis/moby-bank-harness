// A static check of the template: every {{ value }} it uses must exist in what renderVals() returns, and every
// property read off a loop variable must exist on the items of that list. The dc-runtime would otherwise render
// a typo as silently empty, and there is no browser here to notice.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createComponent, templateSource } from './harness.mjs';

const KEYWORDS = new Set(['true', 'false', 'null', 'undefined']);

/** A component with enough data in it that every list in the template has items. */
function populated() {
  const c = createComponent();
  const reply = { id: 'm2', role: 'assistant', text: '', files: [], paragraphs: ['Leverage rose.'],
    steps: [{ kind: 'read', label: 'a.pdf' }, { kind: 'compute', label: 'leverage()' }],
    table: { cols: ['Metric', 'Value'], rows: [['Net leverage', '2.9x']] } };
  const question = { id: 'm1', role: 'user', text: 'Summarize', files: [{ name: 'a.pdf', source: 'onedrive' }], steps: [], paragraphs: [], table: null };
  c.state = {
    ...c.state, loaded: true, activeId: 's1', apiError: 'Cannot reach the harness API',
    user: { name: 'Hermione Granger', role: 'Credit Research', initials: 'HG' },
    convos: [{ id: 's1', title: 'One', location: 'local', status: 'idle', group: 'Today' },
             { id: 's2', title: 'Two', location: 'cloud', status: 'idle', group: 'Earlier' }],
    details: { s1: { id: 's1', title: 'One', location: 'local', status: 'idle', messages: [question, reply], files: [{ name: 'a.pdf', source: 'onedrive' }] } },
    library: [{ id: 'f1', name: 'Earnings', path: 'A / B', fileCount: 3, connected: true },
              { id: 'f2', name: 'Peers', path: 'C', fileCount: 5, connected: false }],
    attachFiles: [{ folderId: 'f1', folderName: 'Earnings', folderPath: 'A / B', name: 'a.pdf' }],
    pending: [{ name: 'notes.docx', sourceKey: 'upload', source: 'Upload' }]
  };
  return c;
}

/**
 * Walks "a.b.c" over a set of sample objects, failing if a segment is missing from all of them. For a loop variable
 * the first segment is the variable itself (the samples), so it is skipped; for a top-level value it is a property
 * of what renderVals() returned.
 */
function follow(path, samples, where, skipFirst) {
  let current = samples;
  for (const segment of skipFirst ? path.slice(1) : path) {
    assert.ok(current.some(item => item != null && typeof item === 'object' && segment in item),
      `${where}: "${path.join('.')}" has no property "${segment}"`);
    current = current.map(item => item?.[segment]).filter(value => value !== undefined);
  }
  return current;
}

function check(layout, template = templateSource()) {
  const component = populated();
  component.props = { layout };
  const vals = component.renderVals();
  const scope = []; // { name, samples }
  const errors = [];
  const tokens = /<\/sc-for>|<sc-for\b([^>]*)>|\{\{\s*([\s\S]*?)\s*\}\}/g;

  function checkExpression(expression, where) {
    const withoutStrings = expression.replace(/'[^']*'|"[^"]*"/g, ' ');
    for (const match of withoutStrings.matchAll(/(?<![\w$.])([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)/g)) {
      const path = match[1].split('.');
      if (KEYWORDS.has(path[0])) continue;
      const local = [...scope].reverse().find(entry => entry.name === path[0]);
      try {
        if (local) follow(path, local.samples, where, true);
        else {
          assert.ok(path[0] in vals, `${where}: "${path[0]}" is not returned by renderVals()`);
          follow(path, [vals], where, false);
        }
      } catch (e) { errors.push(e.message); }
    }
  }

  function listItems(expression) {
    const path = expression.trim().split('.');
    const local = [...scope].reverse().find(entry => entry.name === path[0]);
    const values = local ? follow(path, local.samples, 'loop', true) : follow(path, [vals], 'loop', false);
    return values.flatMap(value => (Array.isArray(value) ? value : []));
  }

  for (const match of template.matchAll(tokens)) {
    const line = template.slice(0, match.index).split('\n').length;
    if (match[0] === '</sc-for>') { scope.pop(); continue; }
    if (match[1] !== undefined) {
      const list = /list="\{\{\s*([^}]*?)\s*\}\}"/.exec(match[1]);
      const name = /\bas="([^"]+)"/.exec(match[1]);
      assert.ok(list && name, `line ${line}: <sc-for> needs list="{{ … }}" and as="…"`);
      let items = [];
      try { items = listItems(list[1]); } catch (e) { errors.push(`line ${line}: ${e.message}`); }
      scope.push({ name: name[1], samples: items });
    } else {
      checkExpression(match[2], `line ${line}`);
    }
  }
  assert.equal(scope.length, 0, 'every <sc-for> is closed');
  return errors;
}

for (const layout of ['sidebar', 'panel']) {
  test(`the ${layout} layout's template only uses values renderVals() provides`, () => {
    assert.deepEqual(check(layout), []);
  });
}

test('the check catches a misspelled top-level value', () => {
  const broken = templateSource().replace('{{ userName }}', '{{ userNmae }}');
  const errors = check('sidebar', broken);
  assert.equal(errors.length, 1);
  assert.match(errors[0], /"userNmae" is not returned by renderVals\(\)/);
});

test('the check catches a misspelled property on a loop variable', () => {
  const broken = templateSource().replace('{{ m.isUser }}', '{{ m.isUsr }}');
  assert.match(check('sidebar', broken).join('\n'), /"m\.isUsr" has no property "isUsr"/);
});

test('the check catches a list that does not exist', () => {
  const broken = templateSource().replace('list="{{ historyGroups }}"', 'list="{{ historyGroup }}"');
  assert.match(check('sidebar', broken).join('\n'), /historyGroup/);
});

test('the check catches a reference to a loop variable outside its loop', () => {
  const broken = templateSource().replace('{{ activeTitle }}', '{{ m.text }}');
  assert.match(check('sidebar', broken).join('\n'), /"m" is not returned by renderVals\(\)/);
});

test('the dc-runtime subset: no template expression uses anything but paths, literals, ! and equality', () => {
  const offenders = [...templateSource().matchAll(/\{\{\s*([\s\S]*?)\s*\}\}/g)]
    .map(match => match[1])
    .filter(expr => /[?&|+*/<>]|\(|=>/.test(expr.replace(/'[^']*'|"[^"]*"/g, '')));
  assert.deepEqual(offenders, [], 'the runtime resolves only property paths, literals, !, and ==/===/!=/!==');
});

test('the template is well formed: every tag that opens is closed in order', () => {
  const VOID = new Set(['input', 'link', 'meta', 'br', 'img', 'hr']);
  const template = templateSource().replace(/<!--[\s\S]*?-->/g, '').replace(/<style>[\s\S]*?<\/style>/g, '<style></style>');
  const stack = [];
  for (const match of template.matchAll(/<(\/?)([a-zA-Z][\w-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)(\/?)>/g)) {
    const [, closing, name, , selfClosing] = match;
    const line = template.slice(0, match.index).split('\n').length;
    if (VOID.has(name) || selfClosing) continue;
    if (!closing) { stack.push({ name, line }); continue; }
    const open = stack.pop();
    assert.ok(open && open.name === name, `line ${line}: </${name}> closes ${open ? `<${open.name}> from line ${open.line}` : 'nothing'}`);
  }
  assert.deepEqual(stack, [], 'nothing is left open');
});

test('the well-formedness check catches an unclosed tag', () => {
  // the same walk as above, on a template with one </div> missing
  const broken = '<div><span>x</span>';
  const stack = [];
  for (const match of broken.matchAll(/<(\/?)([a-zA-Z][\w-]*)[^>]*>/g)) {
    if (!match[1]) stack.push(match[2]); else stack.pop();
  }
  assert.deepEqual(stack, ['div']);
});
