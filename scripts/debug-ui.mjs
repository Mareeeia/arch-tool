import { chromium } from "playwright";

const url = process.env.ATB_URL ?? "http://localhost:7071/";

const browser = await chromium.launch({ headless: true, channel: "chrome" });
const page = await browser.newPage();

const errors = [];
page.on("console", (msg) => {
  if (msg.type() === "error") errors.push(msg.text());
});
page.on("pageerror", (err) => errors.push(err.message));

await page.goto(url, { waitUntil: "networkidle", timeout: 120_000 });
await page.waitForTimeout(2000);

const depth = page.locator("#depth");
for (const d of [2, 3, 4, 5, 4, 3, 4, 2, 4]) {
  await depth.fill(String(d));
  await depth.dispatchEvent("input");
  await page.waitForTimeout(800);
}

await page.waitForTimeout(3000);
const canvas = await page.locator("#cy canvas").count();
const unique = [...new Set(errors)];

console.log(JSON.stringify({ url, canvas, errorCount: errors.length, uniqueErrors: unique }, null, 2));
if (unique.some((e) => e.includes("notify"))) process.exitCode = 1;

await browser.close();
