// Vérifie en navigateur les comptes de test créés par la reprise : node check-real-data.js <fichier IDENTIFIANTS-COMPTES-TEST.csv>
const { chromium } = require('playwright');
const fs = require('fs');
const B = process.env.BASE_URL || 'http://localhost:3000';
const creds = Object.fromEntries(
  fs.readFileSync(process.argv[2], 'utf8').trim().split('\n').slice(1).map((l) => { const c = l.split(';'); return [c[0], c[1]]; }),
);
(async () => {
  const b = await chromium.launch();
  async function login(u) {
    const ctx = await b.newContext({ viewport: { width: 1280, height: 900 } });
    const p = await ctx.newPage();
    await p.goto(B + '/fr/login');
    await p.fill('input[name=login]', u);
    await p.fill('input[name=password]', creds[u]);
    await p.click('button[type=submit]');
    await p.waitForURL(/change-password/);
    const f = await p.$$('input[type=password]');
    const n = 'Essai!2026-Mdp-' + u.replace('.', '');
    await f[0].fill(creds[u]);
    await f[1].fill(n);
    if (f[2]) await f[2].fill(n);
    await p.locator('button:not([disabled])').first().click();
    await p.waitForURL(/\/(login|dashboard|rh|responsable)/);
    if (/login/.test(p.url())) {
      await p.fill('input[name=login]', u);
      await p.fill('input[name=password]', n);
      await p.click('button[type=submit]');
      await p.waitForURL(/\/(dashboard|rh|responsable)/);
    }
    return { ctx, p };
  }
  const show = async (p, label, path) => {
    await p.goto(B + path);
    await p.waitForTimeout(1800);
    console.log('##', label, (await p.textContent('main')).replace(/\s+/g, ' ').slice(0, 280));
  };
  const a = await login('test.agent1');
  for (const [l, u] of [['paie', '/fr/paie'], ['note', '/fr/note'], ['demandes', '/fr/demandes'], ['carriere', '/fr/carriere'], ['deplacements', '/fr/deplacements']]) await show(a.p, l, u);
  await a.p.screenshot({ path: 'real-agent.png' });
  const r = await login('test.responsable');
  await show(r.p, 'responsable', '/fr/responsable/dashboard');
  await b.close();
})();
