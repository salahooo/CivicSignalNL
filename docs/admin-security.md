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

De React-login bewaart de Basic-credentials uitsluitend in geheugen; refresh en uitloggen wissen ze. Admincalls worden pas na verificatie gestart. Mislukte authenticatie is per remote address begrensd tot tien pogingen per minuut met maximaal 1.000 tijdelijke entries. Dit is single-instance bescherming; productie vereist daarnaast TLS, gateway-rate-limiting en secret management.
