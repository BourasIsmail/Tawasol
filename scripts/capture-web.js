// Capture les pages web équivalentes aux écrans de la maquette : node capture-web.js <dossier> <identifiants.csv> [lang]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const out = process.argv[2];
const creds = Object.fromEntries(fs.readFileSync(process.argv[3], 'utf8').trim().split('\n').slice(1).map((l) => { const c = l.split(';'); return [c[0], c[1]]; }));
const lang = process.argv[4] || 'fr';
const B = process.env.BASE_URL || 'http://localhost:3000';
fs.mkdirSync(out, { recursive: true });
const PAGES = {
  'test.agent1': { 'agent__dash': '/dashboard', 'agent__fiche': '/fiche', 'agent__dem': '/demandes', 'agent__nconge': '/demandes/nouvelle?famille=conge', 'agent__fin': '/paie', 'agent__note': '/note', 'agent__rec': '/reclamations', 'agent__docs': '/documents', 'agent__notif': '/notifications', 'dep__a-list': '/deplacements', 'dep__a-new': '/deplacements/nouvelle' },
  'test.responsable': { 'responsable__dash': '/responsable/dashboard', 'responsable__queue': '/responsable/validation', 'responsable__perim': '/responsable/personnel', 'responsable__notation': '/responsable/notation', 'responsable__cal': '/responsable/calendrier', 'responsable__sup': '/responsable/suppleance' },
  'test.rh': { 'rh__dash': '/rh/dashboard', 'rh__trait': '/rh/demandes', 'rh__pers': '/rh/personnel', 'rh__imp': '/rh/imports', 'rh__lots': '/rh/lots', 'rh__pub': '/rh/publications', 'rh__camp': '/rh/notation', 'rh__carr': '/rh/avancement' },
};
(async () => {
  const b = await chromium.launch();
  for (const [u, pages] of Object.entries(PAGES)) {
    const ctx = await b.newContext({ viewport: { width: 1110, height: 760 } });
    const p = await ctx.newPage();
    await p.goto(`${B}/${lang}/login`);
    await p.fill('input[name=login]', u);
    const essai = 'Essai!2026-Mdp-' + u.replace('.', '');
    await p.fill('input[name=password]', creds[u]);
    await p.click('button[type=submit]');
    await p.waitForTimeout(1500);
    if (/change-password/.test(p.url())) {
      const f = await p.$$('input[type=password]');
      await f[0].fill(creds[u]); await f[1].fill(essai); if (f[2]) await f[2].fill(essai);
      await p.locator('button:not([disabled])').first().click();
      await p.waitForTimeout(1500);
    }
    if (/login/.test(p.url())) {
      await p.fill('input[name=login]', u); await p.fill('input[name=password]', essai); await p.click('button[type=submit]'); await p.waitForTimeout(1500);
    }
    for (const [name, route] of Object.entries(pages)) {
      try {
        await p.goto(`${B}/${lang}${route}`);
        await p.waitForTimeout(1800);
        await p.screenshot({ path: path.join(out, name + '.png') });
      } catch (e) { console.log('FAIL', name, String(e.message).split('\n')[0]); }
    }
    await ctx.close();
  }
  await b.close();
  console.log('ok');
})();
