const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({ args: ['--no-sandbox', '--disable-setuid-sandbox'] });
  const page = await browser.newPage();
  
  // Screenshot localhost:5173
  try {
    await page.goto('http://localhost:5173', { waitUntil: 'networkidle2' });
    await page.screenshot({ path: '/home/marcos/.gemini/antigravity/brain/1ef79305-3dc9-4a0c-831f-c58e75a56507/artifacts/admin_ui.png' });
    console.log('Admin UI screenshot saved.');
  } catch (e) {
    console.error('Error getting admin UI:', e.message);
  }

  // Screenshot prototype HTML
  try {
    await page.goto('file:///home/marcos/Applications/marketplace-ceara/Onda%20Marketplace%20P0.html', { waitUntil: 'networkidle0' });
    await page.screenshot({ path: '/home/marcos/.gemini/antigravity/brain/1ef79305-3dc9-4a0c-831f-c58e75a56507/artifacts/prototype_ui.png' });
    console.log('Prototype UI screenshot saved.');
  } catch (e) {
    console.error('Error getting prototype UI:', e.message);
  }

  await browser.close();
})();
