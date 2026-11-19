import http from 'node:http'

const host = '127.0.0.1'
const port = Number.parseInt(process.env.PAYOS_WEBHOOK_PROXY_PORT ?? '0', 10)
const callbackPath = '/api/v1/payments/payos/webhook'
const upstreamHost = '127.0.0.1'
const upstreamPort = 8080
const maxBodyBytes = 64 * 1024

const server = http.createServer(async (request, response) => {
  if (request.method !== 'POST' || request.url !== callbackPath) {
    process.stdout.write(`PayOS proxy rejected ${request.method} ${request.url} (expected POST ${callbackPath})\n`)
    response.writeHead(404, { 'content-type': 'application/json' })
    response.end('{"error":"Not found"}')
    return
  }

  process.stdout.write('PayOS webhook request reached the local proxy.\n')

  if (!String(request.headers['content-type'] ?? '').toLowerCase().startsWith('application/json')) {
    process.stdout.write('PayOS proxy rejected the webhook because its content type was not JSON.\n')
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
    process.stdout.write(`Backend returned HTTP ${upstreamResponse.statusCode ?? 'unknown'} for the PayOS webhook.\n`)
    response.writeHead(upstreamResponse.statusCode ?? 502, {
      'content-type': upstreamResponse.headers['content-type'] ?? 'application/json',
      'cache-control': 'no-store',
    })
    upstreamResponse.pipe(response)
  })

  upstream.on('error', () => {
    process.stdout.write('PayOS webhook proxy could not connect to the backend on port 8080.\n')
    if (response.headersSent) return response.destroy()
    response.writeHead(502, { 'content-type': 'application/json' })
    response.end('{"error":"Backend unavailable"}')
  })
  upstream.end(body)
})

server.listen(port, host, () => {
  const address = server.address()
  if (!address || typeof address === 'string') {
    process.stderr.write('PayOS webhook proxy could not determine its listening port.\n')
    process.exitCode = 1
    server.close()
    return
  }
  process.stdout.write(`PayOS webhook proxy listening on http://${host}:${address.port}${callbackPath}\n`)
})

function stop() {
  server.close(() => process.exit(0))
}
process.on('SIGINT', stop)
process.on('SIGTERM', stop)
