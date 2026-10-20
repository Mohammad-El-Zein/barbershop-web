# Eigene Domains für Azure (Container Apps)

Diese Anleitung zeigt, wie Terraform die eigenen Domains kennt und verwaltet. Ein `terraform apply` entfernt die
Bindungen dann nicht mehr, und alle Adressen im System zeigen auf die Domains.

Beispiel dev:

| | Domain | Container App |
|---|---|---|
| Website | `barbershop.elzein.dev` | `ca-barbershop-dev-web` |
| API | `barbershop-api.elzein.dev` | `ca-barbershop-dev-api` |

Alle Befehle sind für **Windows cmd**: ein Befehl pro Zeile, ohne `^`. Platzhalter in `<spitzen Klammern>`
ersetzt du **ohne** die Klammern.

## Was Terraform macht

Die Domains stehen in `infra/azure/env/<umgebung>.tfvars`:

```hcl
web_domain = "barbershop.elzein.dev"
api_domain = "barbershop-api.elzein.dev"
cors_allow_default_web_url = true   # nur dev, Übergang
```

Leer (`""`) heißt: keine eigene Domain. Dann gelten die `*.azurecontainerapps.io`-Adressen wie bisher.

Ist eine Domain gesetzt, passiert Folgendes:

- **Bindung an die App:** `azurerm_container_app_custom_domain.frontend` bzw. `.backend` hängt den Hostnamen
  an die App.
- **Kostenloses Zertifikat:** `azurerm_container_app_environment_managed_certificate.frontend` bzw. `.backend`
  ist ein von Azure verwaltetes Zertifikat. Azure verlängert es selbst.
- **Backend-Einstellungen:**
  - `APP_FRONTEND_BASE_URL` (Links in den Mails) wird `https://<web_domain>`.
  - `APP_CORS_ALLOWED_ORIGINS` wird `https://<web_domain>`. Mit `cors_allow_default_web_url = true` kommt
    übergangsweise die alte Website-Adresse `https://ca-…-web.….azurecontainerapps.io` dazu. In prod ist das
    verboten.
- **Ausgaben:** `terraform output site_url`, `api_url` und `ci_variables` (`API_URL`, `NEXT_PUBLIC_API_URL`,
  `NEXT_PUBLIC_SITE_URL`) liefern die eigenen Domains.
- **Monitoring:** Der Verfügbarkeitstest prüft `https://<api_domain>/actuator/health/readiness`.
- **Bleibt gleich:** Serverseitige Anfragen des Frontends (`API_URL_INTERNAL`) gehen weiter an die interne
  Standard-Adresse.

**Ein Schritt geht nicht mit Terraform:** das Verknüpfen von Zertifikat und Hostname (SNI-Bindung). Azure stellt
das Zertifikat erst aus, wenn der Hostname schon an der App hängt. azurerm kann die Zertifikats-Felder einer
bestehenden Bindung aber nicht ändern, nur neu anlegen. Bei einer **neuen** Domain ist das deshalb ein einziger
`az`-Befehl (siehe unten). Danach sorgt `ignore_changes` dafür, dass Terraform die Bindung nicht anfasst.

Die Apps selbst brauchen kein `ignore_changes` für ihre Domains. In azurerm 4.x ist `ingress.custom_domain` nur
lesbar, und ein Update der App schickt die gebundenen Domains unverändert zurück (nachgeprüft im Quellcode von
azurerm 4.81.0).

## DNS (Cloudflare)

Pro Domain brauchst du zwei Einträge. Die Werte liefert Terraform: `terraform output frontend_default_fqdn`,
`backend_default_fqdn` und `custom_domain_verification_id`.

| Typ | Name | Inhalt |
|---|---|---|
| `CNAME` | `barbershop` | `<frontend_default_fqdn>` (z. B. `ca-barbershop-dev-web.<…>.azurecontainerapps.io`) |
| `TXT` | `asuid.barbershop` | `<custom_domain_verification_id>` |
| `CNAME` | `barbershop-api` | `<backend_default_fqdn>` |
| `TXT` | `asuid.barbershop-api` | `<custom_domain_verification_id>` |

Wichtig dabei:

- **Cloudflare-Proxy AUS** („DNS only“, graue Wolke). Mit Proxy sieht Azure den CNAME nicht. Dann kann es das
  Zertifikat weder ausstellen noch später verlängern.
- Die Einträge **bleiben stehen**. Azure prüft sie bei jeder Verlängerung des Zertifikats, etwa alle 6 Monate.

## Bestehende Bindungen übernehmen (import)

Das brauchst du **einmal**, weil die Domains schon von Hand gebunden sind. Ohne Import würde Terraform versuchen,
alles **neu anzulegen**. Das schlägt fehl, weil es schon existiert.

### 1. Werte nachsehen

Diese Befehle lesen nur.

Zeigt deine Subscription-ID:

```
az account show --query id -o tsv
```

Zeigt die Namen der verwalteten Zertifikate. Azure hat sie beim Binden selbst vergeben, zum Beispiel
`mc-cae-barbersh-barbershop-elze-1234`:

```
az containerapp env certificate list -g rg-barbershop-dev -n cae-barbershop-dev --managed-certificates-only --query "[].{name:name, subject:properties.subjectName, state:properties.provisioningState}" -o table
```

Zeigt, dass die Website-Domain gebunden ist. Erwartet wird `SniEnabled`:

```
az containerapp hostname list -g rg-barbershop-dev -n ca-barbershop-dev-web -o table
```

Dasselbe für die API:

```
az containerapp hostname list -g rg-barbershop-dev -n ca-barbershop-dev-api -o table
```

### 2. Terraform vorbereiten

Wechselt in den Terraform-Ordner:

```
cd /d C:\Users\khale\Desktop\barbershop-web\infra\azure
```

Setzt die Subscription für alle folgenden Befehle in diesem Fenster. Die Secrets
(`TF_VAR_smtp_password`, `TF_VAR_admin_password` …) setzt du genauso wie bei deinem normalen `plan`:

```
set TF_VAR_subscription_id=<SUBSCRIPTION_ID>
```

### 3. Vier Importe

Die Reihenfolge ist egal. `<SUB>` ist die Subscription-ID, `<MC_WEB>` und `<MC_API>` sind die
Zertifikatsnamen aus Schritt 1.

Übernimmt die Bindung der Website-Domain:

```
terraform import -var-file=env/dev.tfvars -var-file=env/dev.local.tfvars "azurerm_container_app_custom_domain.frontend[0]" "/subscriptions/<SUB>/resourceGroups/rg-barbershop-dev/providers/Microsoft.App/containerApps/ca-barbershop-dev-web/customDomainName/barbershop.elzein.dev"
```

Übernimmt die Bindung der API-Domain:

```
terraform import -var-file=env/dev.tfvars -var-file=env/dev.local.tfvars "azurerm_container_app_custom_domain.backend[0]" "/subscriptions/<SUB>/resourceGroups/rg-barbershop-dev/providers/Microsoft.App/containerApps/ca-barbershop-dev-api/customDomainName/barbershop-api.elzein.dev"
```

Übernimmt das Zertifikat der Website:

```
terraform import -var-file=env/dev.tfvars -var-file=env/dev.local.tfvars "azurerm_container_app_environment_managed_certificate.frontend[0]" "/subscriptions/<SUB>/resourceGroups/rg-barbershop-dev/providers/Microsoft.App/managedEnvironments/cae-barbershop-dev/managedCertificates/<MC_WEB>"
```

Übernimmt das Zertifikat der API:

```
terraform import -var-file=env/dev.tfvars -var-file=env/dev.local.tfvars "azurerm_container_app_environment_managed_certificate.backend[0]" "/subscriptions/<SUB>/resourceGroups/rg-barbershop-dev/providers/Microsoft.App/managedEnvironments/cae-barbershop-dev/managedCertificates/<MC_API>"
```

Jeder Import endet mit `Import successful!`. Ein Import ändert nichts in Azure, nur den Terraform-State.

Ist ein Import schiefgegangen, zum Beispiel mit einem falschen Zertifikat, nimmt ihn
`terraform state rm "<Adresse>"` wieder zurück. Auch das ändert nichts in Azure.

### 4. Plan prüfen

Erstellt den Plan und speichert ihn:

```
terraform plan -var-file=env/dev.tfvars -var-file=env/dev.local.tfvars -out=domain.tfplan
```

**Erwartet:**

- Für die vier importierten Ressourcen: **keine Änderung**. Sie tauchen im Plan gar nicht auf.
- `azurerm_container_app.backend` wird **in-place** geändert (`~`), nämlich `APP_CORS_ALLOWED_ORIGINS` und
  `APP_FRONTEND_BASE_URL`.
- Eventuell wird der Verfügbarkeitstest in-place geändert (neue URL).
- Unten steht `0 to add, … to change, 0 to destroy`. Stehen dort noch andere Änderungen aus früheren Commits,
  sind das deine bekannten offenen Punkte.

**Abbrechen, nicht anwenden**, wenn du eines davon siehst:

- `destroy` oder `must be replaced` bei `azurerm_container_app_custom_domain` oder
  `azurerm_container_app_environment_managed_certificate`;
- `will be created` bei einer der beiden, weil dann der Import fehlt.

### 5. Anwenden

Wendet genau den geprüften Plan an:

```
terraform apply domain.tfplan
```

Das Backend startet dabei eine neue Revision mit den neuen Einstellungen. Ein neues Image ist dafür nicht nötig.

Prüft danach, ob die Bindungen noch da sind. Erwartet wird `SniEnabled` bei beiden Apps:

```
az containerapp hostname list -g rg-barbershop-dev -n ca-barbershop-dev-web -o table
```

## Frontend neu bauen (Pflicht nach jedem Domainwechsel)

`NEXT_PUBLIC_API_URL` und `NEXT_PUBLIC_SITE_URL` werden beim **Bauen** fest in das Frontend-Image geschrieben.
Das betrifft die API-Adresse im Browser, die Content-Security-Policy (`connect-src`), Canonical-Links und die
Sitemap. Das laufende Image kennt also noch die alten Adressen.

1. Aktualisiere im GitHub-Environment `dev` die Variablen `API_URL`, `NEXT_PUBLIC_API_URL` und
   `NEXT_PUBLIC_SITE_URL` auf die Werte aus `terraform output ci_variables`:
   `https://barbershop-api.elzein.dev` bzw. `https://barbershop.elzein.dev`.
   Der Health-Check der CD nimmt `API_URL` von dort.
2. Starte den Deploy auf eine dieser Arten:
   - Pushe einen neuen Commit auf `main`. Die CD baut das Frontend dann mit diesen Werten.
   - Oder starte von Hand: *Actions → CI → Run workflow → deploy: dev*.
   - Ohne CD baust du von Hand wie bisher (`docker build … --build-arg NEXT_PUBLIC_API_URL=https://barbershop-api.elzein.dev --build-arg NEXT_PUBLIC_SITE_URL=https://barbershop.elzein.dev …`) und rollst mit `az containerapp update -n ca-barbershop-dev-web --image …` aus.

## Cookies, CORS, CSP

- **Anmelde-Cookie** (`bs_refresh`): Es ist `HttpOnly`, `Secure`, `SameSite=Strict` und hat **kein**
  `Domain`-Attribut. Es gehört also nur zum API-Host. `barbershop.elzein.dev` und
  `barbershop-api.elzein.dev` sind **dieselbe Site** (`elzein.dev`). Das Cookie wird deshalb mitgeschickt, und
  du musst nichts ändern.
- **Alte Website-Adresse:** Sie ist nur ein Übergang. Von der alten Adresse aus (`azurecontainerapps.io`) ist die
  neue API eine **andere Site**. Das Strict-Cookie wird dort nicht gesendet, und man ist nach spätestens
  15 Minuten abgemeldet. Wenn alle die neue Adresse nutzen: `cors_allow_default_web_url = false`, dann
  `plan` und `apply`.
- **CSP:** Das Frontend erlaubt in `connect-src` genau `NEXT_PUBLIC_API_URL`. Das passt nach dem Neubau
  automatisch. Das Backend setzt keine CSP mit der API- oder Website-Adresse.
- **HSTS** gilt für `barbershop.elzein.dev` und seine Subdomains, nicht für `elzein.dev` selbst.
- **CAPTCHA (Turnstile):** Falls du es später aktivierst, trag `barbershop.elzein.dev` bei Cloudflare als
  erlaubten Hostnamen des Site-Keys ein.

## Neue Domain (zum Beispiel später für prod)

1. Lege die DNS-Einträge an (siehe oben).
2. Setze `web_domain` und `api_domain` in der tfvars, dann `plan` und `apply`. Terraform hängt die Hostnamen an
   und legt die Zertifikate an.
3. Verknüpfe das Zertifikat einmal mit dem Hostnamen. `<MC_ID>` ist die Ressourcen-ID des Zertifikats aus
   `az containerapp env certificate list -g <rg> -n <environment> --managed-certificates-only --query "[].id" -o tsv`:

   ```
   az containerapp hostname bind -g <rg> -n <app> --hostname <domain> --environment <environment> --certificate <MC_ID>
   ```

4. Baue das Frontend neu (siehe oben).
