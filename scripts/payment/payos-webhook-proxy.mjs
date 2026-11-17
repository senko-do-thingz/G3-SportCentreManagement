import http from 'node:http'

const host = '127.0.0.1'
const port = 8082
const callbackPath = '/api/v1/payments/payos/webhook'
const upstreamHost = '127.0.0.1'
const upstreamPort = 8080
const maxBodyBytes = 64 * 1024

const server = http.createServer(async (request, response) => {
  if (request.method !== 'POST' || request.url !== callbackPath) {
    response.writeHead(404, { 'content-type': 'application/json' })
    response.end('{"error":"Not found"}')
    return
  }

  if (!String(request.headers['content-type'] ?? '').toLowerCase().startsWith('application/json')) {
    response.writeHead(415, { 'content-type': 'application/json' })
    response.end('{"error":"JSON required"}')
    return
  }

  const chunks = []
  let bytes = 0
  try {
    for await (const chunk of request) {
      bytes += chunk.length
      if (bytes > maxBodyBytes) {
        response.writeHead(413, { 'content-type': 'application/json' })
        response.end('{"error":"Request too large"}')
        return
      }
      chunks.push(chunk)
    }
  } catch {
    response.writeHead(400, { 'content-type': 'application/json' })
    response.end('{"error":"Invalid request"}')
    return
  }

  const body = Buffer.concat(chunks)
  const upstream = http.request({
    host: upstreamHost,
    port: upstreamPort,
    path: callbackPath,
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      'content-length': body.length,
    },
  }, (upstreamResponse) => {
    response.writeHead(upstreamResponse.statusCode ?? 502, {
      'content-type': upstreamResponse.headers['content-type'] ?? 'application/json',
      'cache-control': 'no-store',
    })
    upstreamResponse.pipe(response)
  })

  upstream.on('error', () => {
    if (response.headersSent) return response.destroy()
    response.writeHead(502, { 'content-type': 'application/json' })
    response.end('{"error":"Backend unavailable"}')
  })
  upstream.end(body)
})

server.listen(port, host, () => {
  process.stdout.write(`PayOS webhook proxy listening on http://${host}:${port}${callbackPath}\n`)
})

function stop() {
  server.close(() => process.exit(0))
}
process.on('SIGINT', stop)
process.on('SIGTERM', stop)
