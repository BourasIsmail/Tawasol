// Captures d'écran de contrôle du design (connexion FR/AR, tableau de bord) : node capture-design.js [login] [motdepasse]
const { chromium } = require('playwright');
const B = process.env.BASE_URL || 'http://localhost:3000';
(async () => {
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1440, height: 860 } });
  const p = await ctx.newPage();
  for (const loc of ['fr', 'ar']) {
    await p.goto(`${B}/${loc}/login`);
    await p.waitForSelector('main.auth');
    await p.screenshot({ path: `design-login-${loc}.png` });
  }
  if (process.argv[2]) {
    await p.goto(`${B}/fr/login`);
    await p.fill('input[name=login]', process.argv[2]);
    await p.fill('input[name=password]', process.argv[3]);
    await p.click('button[type=submit]');
    await p.waitForTimeout(2500);
    console.log(p.url());
  }
  await b.close();
})();
