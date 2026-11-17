import http from 'node:http'

const backendPort = parsePort(process.env.VIETQR_BACKEND_PORT, 8080)
const proxyPort = parsePort(process.env.VIETQR_PROXY_PORT, 8081)
const maxBodyBytes = 64 * 1024
const callbackPaths = new Set([
  '/vqr/api/token_generate',
  '/vqr/bank/api/transaction-sync',
])

const server = http.createServer((request, response) => {
  const path = request.url ?? '/'
  response.setHeader('Cache-Control', 'no-store')

  if (request.method !== 'POST' || !callbackPaths.has(path)) {
    response.writeHead(404, { 'Content-Type': 'application/json' })
    response.end('{"error":"not_found"}')
    return
  }

  const chunks = []
  let bodyBytes = 0
  let oversized = false

  request.on('data', (chunk) => {
    bodyBytes += chunk.length
    if (bodyBytes > maxBodyBytes) {
      oversized = true
      chunks.length = 0
      return
    }
    if (!oversized) chunks.push(chunk)
  })

  request.on('end', () => {
    if (oversized) {
      response.writeHead(413, { 'Content-Type': 'application/json' })
      response.end('{"error":"request_too_large"}')
      return
    }

    const body = Buffer.concat(chunks)
    const headers = {
      'Content-Type': request.headers['content-type'] ?? 'application/json',
      'Content-Length': String(body.length),
      'Cache-Control': 'no-store',
    }
    const authorization = request.headers.authorization
    if (typeof authorization === 'string') headers.Authorization = authorization

    const upstream = http.request({
      hostname: '127.0.0.1',
      port: backendPort,
      method: 'POST',
      path,
      headers,
      timeout: 15_000,
    }, (backendResponse) => {
      response.writeHead(backendResponse.statusCode ?? 502, {
        'Content-Type': backendResponse.headers['content-type'] ?? 'application/json',
        'Cache-Control': 'no-store',
      })
      backendResponse.pipe(response)
      backendResponse.on('end', () => {
        process.stdout.write(`${request.method} ${path} -> ${backendResponse.statusCode ?? 502}\n`)
      })
    })

    upstream.on('timeout', () => upstream.destroy(new Error('Backend request timed out')))
    upstream.on('error', () => {
      if (!response.headersSent) {
        response.writeHead(502, { 'Content-Type': 'application/json' })
        response.end('{"error":"callback_backend_unavailable"}')
      }
    })
    upstream.end(body)
  })
})

server.listen(proxyPort, '127.0.0.1', () => {
  process.stdout.write(`VietQR callback proxy listening on 127.0.0.1:${proxyPort}\n`)
  process.stdout.write(`Allowed paths: ${[...callbackPaths].join(', ')}\n`)
})

server.on('error', (error) => {
  process.stderr.write(`VietQR callback proxy failed: ${error.message}\n`)
  process.exitCode = 1
})

function parsePort(value, fallback) {
  if (value == null || value === '') return fallback
  const port = Number(value)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`Invalid port value: ${value}`)
  }
  return port
}
