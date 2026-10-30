const http = require('http');

http.createServer((req, res) => {
  if (req.method !== 'POST' || req.url !== '/chat') {
    res.writeHead(404);
    return res.end();
  }

  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    let message = '';
    try {
      message = JSON.parse(body).message || '';
    } catch (e) {}

    let answer = "hello! i'm a demo shop bot. ask me about an order.";
    let toolCalls = [];

    const m = message.match(/order\s*#?(\d+)/i);
    if (m) {
      answer = `order ${m[1]} has shipped and arrives in 2 days.`;
      toolCalls = [{ name: 'getOrderStatus', arguments: { orderId: m[1] } }];
    }

    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ answer, toolCalls }));
  });
}).listen(9091, () => console.log('Fake agent on http://localhost:9091/chat'));
