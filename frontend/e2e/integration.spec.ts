import { expect, test } from '@playwright/test'

test.skip(process.env.CIVICSIGNAL_INTEGRATION !== '1', 'alleen voor de gecontroleerde lokale integratiesmoke')
test('frontend gebruikt echte backend met gecontroleerde data en beheerlogin', async ({ page }) => {
  const adminPassword = process.env.CIVICSIGNAL_E2E_ADMIN_PASSWORD
  expect(adminPassword, 'CIVICSIGNAL_E2E_ADMIN_PASSWORD moet voor de smoke gezet zijn').toBeTruthy()
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.route('https://*.tile.openstreetmap.org/**', route => route.fulfill({ status: 204 }))
  await page.goto('/?municipality=CivicSmoke')
  await expect(page.getByText('Totaal meldingen')).toBeVisible()
  await expect(page.getByRole('article').filter({ hasText: 'Totaal meldingen' }).getByRole('strong')).toHaveText('20')
  await page.getByRole('button', { name: 'Meldingen', exact: true }).click()
  await expect(page.getByText('CIVIC-SMOKE-20')).toBeVisible()
  await page.getByRole('button', { name: 'Kaart', exact: true }).click()
  await expect(page.getByText(/18 meldingen/)).toBeVisible()
  await page.getByRole('button', { name: 'Beheer' }).click()
  await page.getByLabel('Wachtwoord').fill(adminPassword!)
  await page.getByRole('button', { name: 'Inloggen als beheerder' }).click()
  await expect(page.getByRole('button', { name: 'Uitloggen' })).toBeVisible()
  expect(errors).toEqual([])
})
