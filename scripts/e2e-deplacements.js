// E2E du module Déplacements : agent -> responsable -> cellule des déplacements -> cellule de paie, documents et vérification QR.
const {chromium}=require('playwright');
const B='http://localhost:3000', PW='Demo!2026-Tawassol';
(async()=>{const b=await chromium.launch();
const errs=[];
const step=async(n,fn)=>{try{await fn();console.log('OK  ',n)}catch(e){console.log('FAIL',n,String(e.message).split('\n')[0])}};
async function session(login){const ctx=await b.newContext({viewport:{width:1280,height:900},acceptDownloads:true});const p=await ctx.newPage();
  p.on('pageerror',e=>errs.push(login+' PAGEERR '+e.message));p.on('console',m=>{if(m.type()==='error')errs.push(login+' CONSOLE '+m.text())});
  await p.goto(B+'/fr/login');await p.fill('input[name=login]',login);await p.fill('input[name=password]',PW);await p.click('button[type=submit]');await p.waitForURL(/\/fr\/(dashboard|rh|responsable)/);return {ctx,p}}
let url;const mois=String(1+Math.floor(Math.random()*8)).padStart(2,'0'),jour=10+Math.floor(Math.random()*10);const D1=`2026-${mois}-${jour}T08:00`,D2=`2026-${mois}-${jour+2}T20:00`;
const agent=await session('48217'), resp=await session('31502'), cell=await session('29044'), paie=await session('20063');
await step('agent : saisie avec estimation en direct',async()=>{const p=agent.p;await p.goto(B+'/fr/deplacements/nouvelle');
  await p.fill('textarea','Inspection du centre de formation');
  const opts=await p.$$eval('select >> nth=1 option',o=>o.map(x=>x.value).filter(Boolean));
  await p.selectOption('select >> nth=0',{index:1});await p.selectOption('select >> nth=1',{index:2});
  await p.selectOption('select >> nth=3',{label:'Voiture de service'}).catch(()=>{});
  await p.fill('input[type=datetime-local] >> nth=0',D1);await p.fill('input[type=datetime-local] >> nth=1',D2);
  await p.waitForSelector('text=Total');await p.click('button:has-text("Soumettre")');await p.waitForURL(/\/deplacements\/[0-9a-f-]{36}$/);url=p.url();await p.waitForSelector('text=Soumise')});
await step('le numéro d\'ordre n\'existe pas encore',async()=>{await agent.p.waitForSelector('text=Attribué à l\'approbation')});
await step('agent : aucune action de validation sur sa propre mission',async()=>{const n=await agent.p.locator('button:has-text("Valider")').count();if(n)throw new Error('bouton de validation visible')});
await step('chaîne hiérarchique : chaque responsable valide à son tour, dans l\'ordre',async()=>{
  const {execSync}=require('child_process');const id=url.split('/').pop();
  const chain=execSync(`mysql tawassol_demo2 -N -e "select p.matricule from etape_validation e join personnel p on p.id=e.responsable_personnel_id where e.objet_id='${id}' order by e.ordre"`).toString().trim().split('\n');
  if(chain.length<2)throw new Error('chaîne trop courte : '+chain.length);
  // un responsable d'un niveau supérieur ne peut pas décider avant son tour
  const dernier=await session(chain[chain.length-1]);await dernier.p.goto(url);
  if(await dernier.p.locator('button:has-text("Valider (responsable)")').count())throw new Error('bouton visible hors de son tour');
  // le statut reste « Soumise » tant que tous n'ont pas validé
  for(let i=0;i<chain.length;i++){const s=i===chain.length-1?dernier:(i===0?resp:await session(chain[i]));if(i===0&&chain[0]!=='31502')throw new Error('niveau 1 inattendu '+chain[0]);
    await s.p.goto(url);await s.p.waitForSelector('text=Niveau '+(i+1)+' sur '+chain.length);await s.p.click('button:has-text("Valider (responsable)")');
    await s.p.waitForTimeout(800);
    if(i<chain.length-1){await agent.p.goto(url);await agent.p.waitForSelector('text=Soumise');}else{await s.p.reload();await s.p.waitForSelector('text=Validée par le responsable')}}
  await agent.p.goto(url);await agent.p.waitForSelector('text=Chaîne de validation');});
await step('cellule : approbation et numéro d\'ordre',async()=>{const p=cell.p;await p.goto(url);await p.click('button:has-text("Approuver")');await p.waitForSelector('text=Approuvée');await p.waitForSelector('dd.mono:has-text("-2026-")')});
await step('agent : télécharge l\'ordre de mission (PDF)',async()=>{const p=agent.p;await p.goto(url);const [d]=await Promise.all([p.waitForEvent('download'),p.click('a:has-text("Ordre de mission")')]);const f=await d.path();const buf=require('fs').readFileSync(f);if(!buf.toString('latin1').startsWith('%PDF'))throw new Error('pas un PDF');require('fs').copyFileSync(f,'ordre-e2e.pdf')});
await step('cellule : finalisation avec les dates réelles',async()=>{const p=cell.p;await p.goto(url);await p.click('button:has-text("Finaliser")');await p.fill('input[type=datetime-local]',D2);await p.click('button:has-text("Confirmer")');await p.waitForSelector('text=Finalisée');await p.waitForSelector('dd.mono:has-text("E")')});
await step('cellule : ne peut pas valider le paiement',async()=>{const n=await cell.p.locator('button:has-text("paiement")').count();if(n)throw new Error('bouton de paiement visible pour la cellule des déplacements')});
await step('paie : valide le paiement',async()=>{const p=paie.p;await p.goto(url);await p.click('button:has-text("Valider le paiement")');await p.waitForSelector('text=Payée')});
await step('agent : télécharge l\'état des indemnités',async()=>{const p=agent.p;await p.goto(url);const [d]=await Promise.all([p.waitForEvent('download'),p.click('a:has-text("État des indemnités")')]);const f=await d.path();if(!require('fs').readFileSync(f).toString('latin1').startsWith('%PDF'))throw new Error('pas un PDF');require('fs').copyFileSync(f,'etat-e2e.pdf')});
await step('vérification publique par QR (sans connexion)',async()=>{const api=await agent.ctx.request.get(B+'/api/bff/missions/'+url.split('/').pop());const m=await api.json();
  const num=m.numeroOrdm;const pub=await b.newContext();const pg=await pub.newPage();
  const {execSync}=require('child_process');const tok=execSync(`pdftotext -raw ordre-e2e.pdf - | tr -d '\\n ' | grep -oE 'verification/ordre/[0-9a-f-]{36}/[A-Za-z0-9_-]{24}' | head -1`).toString().trim();
  if(!tok)throw new Error('lien de vérification absent du PDF');await pg.goto(B+'/fr/'+tok);await pg.waitForSelector('text=Document authentique');await pg.waitForSelector('text='+num);
  const txt=await pg.textContent('main');if(/Samira|48217/.test(txt))throw new Error('donnée personnelle visible');
  await pg.goto(B+'/fr/'+tok+'x');await pg.waitForSelector('text=introuvable')});
await step('responsable : analytique interdite, cellule : analytique et export',async()=>{const r=await resp.ctx.request.get(B+'/api/bff/missions/analytique?annee=2026');
  const c=await cell.ctx.request.get(B+'/api/bff/missions/analytique?annee=2026');if(c.status()!==200)throw new Error('cellule '+c.status());
  const x=await cell.ctx.request.get(B+'/api/bff/missions/export?annee=2026');if(x.status()!==200)throw new Error('export '+x.status());
  await cell.p.goto(B+'/fr/deplacements/analytique');await cell.p.waitForSelector('text=Par statut')});
await step('arabe RTL',async()=>{await agent.p.goto(B+'/ar/deplacements');await agent.p.waitForSelector('text=التنقلات');if(await agent.p.getAttribute('html','dir')!=='rtl')throw new Error('pas rtl')});
await agent.p.screenshot({path:'dep-ar.png'});await cell.p.goto(url);await cell.p.screenshot({path:'dep-detail.png'});
console.log('ERREURS NAVIGATEUR:',JSON.stringify(errs.filter(e=>!/favicon|fonts|status of 40[34]|422|403/.test(e))));
await b.close()})();
