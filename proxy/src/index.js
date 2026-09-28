// Mindful Unlocks proxy: holds the TypeSafe key, fixes the questions, returns two numbers.
// It never logs request bodies, so the player's reason isn't stored by us. TypeSafe may retain
// inputs per its privacy policy (no fixed period; zero retention is enterprise-only).

const TYPESAFE_URL = 'https://api.typesafe.ai/v1/systemone'
const MAX_BODY = 2048

const QUESTIONS = {
  purposeful: {
    type: 'noul',
    instructions:
      'Does `reason` describe a specific, bounded task to do in `app` — such as replying to a particular person, ' +
      'posting something specific, or looking up one thing — rather than open-ended browsing or passing time?',
    criteria: {
      true: 'A concrete task with a natural end point',
      false: 'Browsing, scrolling, boredom, habit, or no clear task',
    },
  },
  category: {
    type: 'choice',
    instructions: 'What is the main reason for opening `app`?',
    criteria: {
      message: 'Replying to or contacting a specific person or group',
      create: 'Posting or sharing something specific',
      lookup: 'Finding one specific piece of information',
      work: 'A job, school, or business task',
      boredom: 'Passing time or nothing better to do',
      habit: 'Opening it automatically, out of habit',
      unclear: 'Too vague or unrelated to tell',
    },
  },
}

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const text = (v) => (typeof v === 'string' ? v.trim() : '')

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url)
    if (request.method !== 'POST' || pathname !== '/judge') return json({ error: 'not found' }, 404)

    const installId = text(request.headers.get('X-Install-Id'))
    // Read at most a small body: a reason is ≤ 200 chars, so anything big is abuse.
    if (Number(request.headers.get('Content-Length') ?? 0) > MAX_BODY) return json({ error: 'too large' }, 413)
    const raw = await request.text()
    if (raw.length > MAX_BODY) return json({ error: 'too large' }, 413)
    let body = null
    try { body = JSON.parse(raw) } catch {}
    const app = text(body?.app)
    const reason = text(body?.reason)
    if (!installId || app.length < 1 || app.length > 60 || reason.length < 1 || reason.length > 200) {
      return json({ error: 'invalid input' }, 400)
    }

    const { success } = await env.LIMITER.limit({ key: installId })
    if (!success) return json({ error: 'rate limited' }, 429)

    let res
    try {
      res = await fetch(TYPESAFE_URL, {
        method: 'POST',
        headers: { Authorization: `Bearer ${env.TYPESAFE_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ state: { app, reason }, model: 'jev-latest', questions: QUESTIONS }),
      })
    } catch {
      return json({ error: 'upstream' }, 502)
    }
    if (!res.ok) return json({ error: 'upstream' }, 502)

    const data = await res.json().catch(() => null)
    const purposeful = data?.answers?.purposeful?.noul
    const category = data?.answers?.category?.choice
    const inContract =
      typeof purposeful === 'number' && purposeful >= 0 && purposeful <= 1 &&
      typeof category === 'string' && Object.hasOwn(QUESTIONS.category.criteria, category)
    if (!inContract) return json({ error: 'upstream' }, 502)
    return json({ purposeful, category })
  },
}
