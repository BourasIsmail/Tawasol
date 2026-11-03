// Capture chaque écran de la maquette HTML (cadre web, français) : node capture-maquettes.js <dossier de sortie> [lang]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const out = process.argv[2];
const lang = process.argv[3] || 'fr';
fs.mkdirSync(out, { recursive: true });
const SCREENS = [
  'agent:dash', 'agent:fiche', 'agent:dem', 'agent:nconge', 'agent:suivi', 'agent:fin', 'agent:note', 'agent:rec', 'agent:docs', 'agent:notif',
  'responsable:dash', 'responsable:queue', 'responsable:detail', 'responsable:perim', 'responsable:notation', 'responsable:notasaisie', 'responsable:cal', 'responsable:sup',
  'rh:dash', 'rh:trait', 'rh:pers', 'rh:fiche', 'rh:imp', 'rh:lots', 'rh:mis', 'rh:carr', 'rh:camp', 'rh:pub', 'rh:pdf', 'rh:rap',
  'admin:comptes', 'admin:perim', 'admin:ref', 'admin:param', 'admin:audit',
  'trans:login', 'trans:forgot', 'trans:first', 'trans:states',
  'dep:a-new', 'dep:a-list', 'dep:a-suivi', 'dep:r-valider', 'dep:c-file', 'dep:c-detail', 'dep:c-bareme', 'dep:p-file', 'dep:s-analytics', 'dep:ad-roles',
];
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1500, height: 1000 } });
  await p.goto('file://' + path.resolve(__dirname, '../docs/maquettes/tawassol-maquettes.html'));
  await p.evaluate((l) => { document.querySelector(`[data-lang="${l}"]`).click(); }, lang);
  for (const s of SCREENS) {
    try {
      await p.evaluate((x) => go(x), s);
      await p.waitForTimeout(150);
      const el = await p.$('#stage');
      await el.screenshot({ path: path.join(out, s.replace(':', '__') + '.png') });
    } catch (e) { console.log('FAIL', s, String(e.message).split('\n')[0]); }
  }
  await b.close();
  console.log('ok', SCREENS.length);
})();
