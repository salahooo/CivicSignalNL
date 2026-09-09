# Beheer-API-beveiliging

`/api/v1/admin/**` en Actuator-metrics gebruiken stateless HTTP Basic met rol `ADMIN`. Status, health, info, rapportpublicatie en zoeken blijven publiek. Zonder `CIVICSIGNAL_ADMIN_PASSWORD` blijft beheer fail-closed; credentials komen nooit in Git of de frontendbundle.

Gebruik buiten localhost altijd HTTPS: Basic-auth verstuurt credentials per Authorization-header. CSRF staat uit omdat deze API geen sessies of cookies gebruikt. Configureer lokaal de gebruiker via `CIVICSIGNAL_ADMIN_USERNAME` en wachtwoord via `CIVICSIGNAL_ADMIN_PASSWORD`.

```powershell
$env:CIVICSIGNAL_ADMIN_USERNAME="admin"
$securePassword = Read-Host "Admin password" -AsSecureString
$credential = [PSCredential]::new($env:CIVICSIGNAL_ADMIN_USERNAME, $securePassword)
$env:CIVICSIGNAL_ADMIN_PASSWORD = $credential.GetNetworkCredential().Password
Invoke-RestMethod http://localhost:8080/api/v1/admin/dead-letters -Authentication Basic -Credential $credential
```
