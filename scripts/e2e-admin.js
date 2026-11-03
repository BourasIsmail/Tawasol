// E2E de l'espace admin : connexion, changement de mot de passe obligatoire, comptes, rôles, audit, accès refusé aux autres profils.
const { chromium } = require('playwright');
const B = 'http://localhost:3000';
(async () => {
  const b = await chromium.launch();
  const errs = [];
  const step = async (n, fn) => { try { await fn(); console.log('OK  ', n); } catch (e) { console.log('FAIL', n, String(e.message).split('\n')[0]); } };
  const ctx = await b.newContext({ viewport: { width: 1280, height: 850 } });
  const p = await ctx.newPage();
  p.on('pageerror', (e) => errs.push('PAGEERR ' + e.message));
  p.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text()); });
  const NEW = 'Nouveau!2026-Mot-de-passe-Admin';

  await step('connexion admin puis mot de passe à changer', async () => {
    await p.goto(B + '/fr/login');
    await p.fill('input[name=login]', 'admin');
    await p.fill('input[name=password]', 'Admin!2026-Tawassol-Init');
    await p.click('button[type=submit]');
    await p.waitForURL(/change-password/);
    const f = await p.$$('input[type=password]');
    await f[0].fill('Admin!2026-Tawassol-Init');
    await f[1].fill(NEW);
    if (f[2]) await f[2].fill(NEW);
    await p.locator('button:not([disabled])').first().click();
    await p.waitForURL(/\/(login|admin|dashboard)/);
  });
  await step('reconnexion avec le nouveau mot de passe', async () => {
    if (/login/.test(p.url())) {
      await p.fill('input[name=login]', 'admin');
      await p.fill('input[name=password]', NEW);
      await p.click('button[type=submit]');
    }
    await p.waitForURL(/\/fr\/admin\/comptes/);
  });
  await step('menu admin seulement, pas de menu agent', async () => {
    await p.waitForSelector('text=Comptes et rôles');
    if (await p.locator('nav >> text=Mes demandes').count()) throw new Error('menu agent visible');
  });
  await step('liste des comptes et rôles', async () => {
    await p.waitForSelector('table.tbl');
    await p.waitForSelector('text=Permissions du rôle');
    const rows = await p.locator('table.tbl tbody tr').count();
    if (rows < 5) throw new Error('lignes ' + rows);
  });
  await step("changer le rôle d'un compte", async () => {
    await p.fill('.fld input', '20063');
    await p.waitForTimeout(800);
    const sel = p.locator('table.tbl tbody tr select').first();
    const opts = await sel.locator('option').allTextContents();
    await sel.selectOption({ label: opts.find((o) => /paie/i.test(o)) });
    await p.waitForTimeout(800);
  });
  await step('désactiver un compte (confirmation)', async () => {
    p.once('dialog', (d) => d.accept());
    await p.click('table.tbl tbody tr >> button:has-text("Désactiver")');
    await p.waitForSelector('table.tbl >> text=Désactivé');
  });
  await step("journal d'audit", async () => {
    await p.goto(B + '/fr/admin/audit');
    await p.waitForSelector('text=ADMIN_COMPTE_MODIFIE');
  });
  await step("l'admin ne peut pas ouvrir les pages des agents", async () => {
    await p.goto(B + '/fr/demandes');
    await p.waitForURL(/\/admin\/comptes/);
  });
  await step('le compte désactivé ne peut plus se connecter', async () => {
    const c2 = await b.newContext();
    const q = await c2.newPage();
    await q.goto(B + '/fr/login');
    await q.fill('input[name=login]', '20063');
    await q.fill('input[name=password]', 'Demo!2026-Tawassol');
    await q.click('button[type=submit]');
    await q.waitForSelector('[role=alert]');
  });
  await step("un agent ne voit pas l'espace admin", async () => {
    const c3 = await b.newContext();
    const a = await c3.newPage();
    await a.goto(B + '/fr/login');
    await a.fill('input[name=login]', '48217');
    await a.fill('input[name=password]', 'Demo!2026-Tawassol');
    await a.click('button[type=submit]');
    await a.waitForURL(/dashboard/);
    await a.goto(B + '/fr/admin/comptes');
    await a.waitForSelector('[role=alert]');
    const r = await c3.request.get(B + '/api/bff/admin/utilisateurs');
    if (r.status() !== 403) throw new Error('API ' + r.status());
  });
  await p.goto(B + '/fr/admin/comptes');
  await p.waitForTimeout(1500);
  await p.screenshot({ path: 'admin-comptes.png' });
  console.log('ERREURS NAVIGATEUR:', JSON.stringify(errs.filter((e) => !/favicon|fonts|40[1-4]/.test(e))));
  await b.close();
})();
