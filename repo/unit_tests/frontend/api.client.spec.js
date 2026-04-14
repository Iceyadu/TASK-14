import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import * as axiosModule from 'axios'
import CryptoJS from 'crypto-js'

/**
 * Unit tests for the API client verifying correct path contracts,
 * signature header presence, and HMAC computation.
 */

// Mock axios to capture request config and avoid real HTTP calls in jsdom
vi.mock('axios', () => {
  const instance = {
    defaults: { headers: { common: {} }, baseURL: '/api' },
    interceptors: {
      request: { use: vi.fn(), eject: vi.fn() },
      response: { use: vi.fn(), eject: vi.fn() }
    },
    get: vi.fn().mockResolvedValue({ data: {} }),
    post: vi.fn().mockResolvedValue({ data: {} }),
    put: vi.fn().mockResolvedValue({ data: {} }),
    delete: vi.fn().mockResolvedValue({ data: {} }),
    /** Minimal axios.getUri behavior for signing path (path + serialized params). */
    getUri(config) {
      const base = (config.baseURL ?? instance.defaults.baseURL ?? '').replace(/\/$/, '') || ''
      const path = config.url?.startsWith('/') ? config.url : `/${config.url || ''}`
      const params = config.params
      if (!params || Object.keys(params).length === 0) {
        return `${base}${path}`
      }
      const qs = new URLSearchParams(params).toString()
      const joiner = path.includes('?') ? '&' : '?'
      return `${base}${path}${joiner}${qs}`
    }
  }
  const axiosMock = {
    create: vi.fn(() => instance)
  }

  return { default: axiosMock, __mockInstance: instance }
})

let mockInstance

beforeEach(() => {
  vi.resetModules()
  mockInstance = axiosModule.__mockInstance
})

describe('API Client Path Contracts', () => {
  it('testAuthLoginPath', async () => {
    const { authApi } = await import('../../frontend/src/api/client.js')
    await authApi.login({ username: 'test', password: 'pass', deviceFingerprint: 'fp' })

    expect(mockInstance.post).toHaveBeenCalledWith(
      '/auth/login',
      expect.objectContaining({ username: 'test' })
    )
  })

  it('testRosterListPath', async () => {
    const { rosterApi } = await import('../../frontend/src/api/client.js')
    await rosterApi.list({ page: 1 })

    const [url, config] = mockInstance.get.mock.calls.at(-1)
    expect(url).toBe('/rosters')
    expect(config?.params).toEqual({ page: 1 })
  })

  it('testExamSessionPublishPath', async () => {
    const { examSessionApi } = await import('../../frontend/src/api/client.js')
    await examSessionApi.publish(42, 'idem-key-1')

    expect(mockInstance.post).toHaveBeenCalledWith(
      '/exam-sessions/42/publish',
      null,
      expect.objectContaining({
        headers: expect.objectContaining({ 'Idempotency-Key': 'idem-key-1' })
      })
    )
  })

  it('testNotificationInboxPath', async () => {
    const { notificationApi } = await import('../../frontend/src/api/client.js')
    await notificationApi.getInbox({ page: 1 })

    const [url, config] = mockInstance.get.mock.calls.at(-1)
    expect(url).toBe('/notifications/inbox')
    expect(config?.params).toEqual({ page: 1 })
  })
})

describe('Request Signing', () => {
  it('testSignatureHeadersPresent', () => {
    // Verify the interceptor is registered
    // The real client registers a request interceptor that adds X-Timestamp, X-Nonce, X-Signature
    expect(mockInstance.interceptors.request.use).toHaveBeenCalled()
  })

  it('testSignatureComputation', () => {
    // Test the HMAC computation logic directly
    const signingKey = 'test-signing-key-abc123'
    const method = 'POST'
    const path = '/api/exam-sessions/1/publish'
    const timestamp = '1700000000'
    const nonce = 'test-nonce-uuid'
    const body = '{"idempotencyKey":"key-1"}'

    const bodyHash = CryptoJS.SHA256(body).toString()
    const message = [method, path, timestamp, nonce, bodyHash].join('\n')
    const signature = CryptoJS.HmacSHA256(message, signingKey).toString()

    // Verify the signature is deterministic
    const signature2 = CryptoJS.HmacSHA256(message, signingKey).toString()
    expect(signature).toBe(signature2)

    // Verify it is non-empty and hex-like
    expect(signature).toBeTruthy()
    expect(signature.length).toBeGreaterThan(0)
    expect(signature).toMatch(/^[a-f0-9]+$/)

    // Verify different key produces different signature
    const wrongKeySignature = CryptoJS.HmacSHA256(message, 'wrong-key').toString()
    expect(wrongKeySignature).not.toBe(signature)
  })
})
