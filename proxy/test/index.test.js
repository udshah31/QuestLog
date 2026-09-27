import { afterEach, describe, expect, it, vi } from 'vitest'
import worker from '../src/index.js'

const env = { TYPESAFE_API_KEY: 'k', LIMITER: { limit: async () => ({ success: true }) } }

const judge = (body, headers = { 'X-Install-Id': 'abc' }) =>
  worker.fetch(new Request('https://x/judge', { method: 'POST', headers, body: JSON.stringify(body) }), env)

const typesafe = (answers, status = 200) =>
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ answers }), { status })))

afterEach(() => vi.unstubAllGlobals())

describe('POST /judge', () => {
  it('returns purposeful and category', async () => {
    typesafe({ purposeful: { type: 'noul', noul: 0.91 }, category: { type: 'choice', choice: 'message' } })
    const res = await judge({ app: 'Instagram', reason: 'reply to mum' })
    expect(res.status).toBe(200)
    expect(await res.json()).toEqual({ purposeful: 0.91, category: 'message' })
    const sent = JSON.parse(fetch.mock.calls[0][1].body)
    expect(sent.state).toEqual({ app: 'Instagram', reason: 'reply to mum' })
    expect(Object.keys(sent.questions).sort()).toEqual(['category', 'purposeful'])
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer k')
  })

  it('rejects bad input without calling TypeSafe', async () => {
    typesafe({})
    for (const [body, headers] of [
      [{ app: 'Instagram', reason: '  ' }, undefined],
      [{ app: 'Instagram', reason: 'x'.repeat(201) }, undefined],
      [{ app: '', reason: 'hi' }, undefined],
      [{ app: 'Instagram', reason: 'hi' }, {}],
    ]) expect((await judge(body, headers)).status).toBe(400)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('maps TypeSafe errors and malformed answers to 502', async () => {
    typesafe({}, 529)
    expect((await judge({ app: 'Instagram', reason: 'hi' })).status).toBe(502)
    typesafe({ purposeful: { noul: 'high' } })
    expect((await judge({ app: 'Instagram', reason: 'hi' })).status).toBe(502)
  })

  it('rate-limits per install id', async () => {
    typesafe({})
    const limited = { ...env, LIMITER: { limit: async () => ({ success: false }) } }
    const res = await worker.fetch(
      new Request('https://x/judge', { method: 'POST', headers: { 'X-Install-Id': 'abc' }, body: '{"app":"a","reason":"b"}' }),
      limited,
    )
    expect(res.status).toBe(429)
  })

  it('404s anything else', async () => {
    const res = await worker.fetch(new Request('https://x/', { method: 'GET' }), env)
    expect(res.status).toBe(404)
  })

  it('rejects oversized bodies before parsing them', async () => {
    typesafe({})
    const res = await worker.fetch(
      new Request('https://x/judge', { method: 'POST', headers: { 'X-Install-Id': 'abc' }, body: 'x'.repeat(3000) }),
      env,
    )
    expect(res.status).toBe(413)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('502s on a category or score outside the contract', async () => {
    typesafe({ purposeful: { noul: 0.9 }, category: { choice: 'banana' } })
    expect((await judge({ app: 'Instagram', reason: 'hi' })).status).toBe(502)
    typesafe({ purposeful: { noul: 1.5 }, category: { choice: 'message' } })
    expect((await judge({ app: 'Instagram', reason: 'hi' })).status).toBe(502)
  })
})
