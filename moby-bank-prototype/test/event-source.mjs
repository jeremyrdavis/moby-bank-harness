// A small EventSource for Node (which has none by default): reads a text/event-stream with fetch and dispatches
// { data } to the listeners added for each event name. Enough for the harness API's server-sent events.
export class NodeEventSource {
  constructor(url) {
    this.url = url;
    this.listeners = {};
    this.controller = new AbortController();
    this.run();
  }

  addEventListener(type, listener) {
    (this.listeners[type] ??= []).push(listener);
  }

  close() {
    this.controller.abort();
  }

  async run() {
    try {
      const response = await fetch(this.url, { headers: { Accept: 'text/event-stream' }, signal: this.controller.signal });
      const decoder = new TextDecoder();
      let buffer = '';
      for await (const chunk of response.body) {
        buffer += decoder.decode(chunk, { stream: true }).replace(/\r\n/g, '\n');
        let end;
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          this.dispatch(buffer.slice(0, end));
          buffer = buffer.slice(end + 2);
        }
      }
    } catch (e) {
      // closed by close(), or the server went away
    }
  }

  dispatch(block) {
    let name = 'message';
    const data = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) name = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).trim());
    }
    if (!data.length && name === 'message') return; // a comment such as ": keep-alive"
    (this.listeners[name] ?? []).forEach(listener => listener({ data: data.join('\n') }));
  }
}
