# Automatischer Deploy nach Azure (Continuous Deployment)

Diese Anleitung richtet ein, dass GitHub die **Dev-Umgebung in Azure automatisch aktualisiert**, sobald ein Push
auf `main` die CI grün durchläuft. Alle Befehle sind für **Windows cmd** geschrieben: ein Befehl pro Zeile,
ohne `^`. Platzhalter in `<spitzen Klammern>` ersetzt du durch deine Werte, und zwar **ohne** die Klammern.

## Was passiert automatisch?

```
Push auf main ─► Workflow "CI" (Tests, Scans, Images, e2e) ─► grün? ─► Workflow "CD" ─► Environment "dev"
```

Der Deploy (`.github/workflows/deploy-azure.yml`) läuft so ab:

1. **Anmeldung bei Azure per OIDC.** GitHub bekommt für jeden Lauf ein kurzlebiges Token. Es wird kein Passwort
   und kein Client-Secret gespeichert.
2. **Images** mit dem Tag `sha-<7 Zeichen des Commits>`, zum Beispiel `sha-add0056`:
   - Das **Backend**-Image hat die CI schon gebaut und gescannt. Es wird nur von `ghcr.io` in deine ACR kopiert.
   - Das **Frontend** wird mit den `NEXT_PUBLIC_*`-Werten des Environments gebaut und in die ACR gepusht.
3. **Migration zuerst:** Der Job `job-barbershop-dev-migrate` bekommt das neue Backend-Image und wird gestartet.
   Ist er nicht `Succeeded`, **bricht der Deploy ab und es wird nichts ausgerollt**.
4. Danach wird das **Backend** (`ca-barbershop-dev-api`) aktualisiert, dann das **Frontend**
   (`ca-barbershop-dev-web`).
5. **Health-Check:** `<API_URL>/actuator/health/readiness`, bis zu 30 Versuche im Abstand von 10 Sekunden.
6. **Zusammenfassung** im Lauf: Commit, Image-Tag, Website-URL.

Es läuft immer nur **ein Deploy pro Umgebung** gleichzeitig. Ein neuer Deploy wartet, bis der laufende fertig
ist. Warten mehrere, führt GitHub nur den neuesten aus. Er enthält die älteren Commits ohnehin.

Terraform ignoriert Image-Änderungen absichtlich (`ignore_changes`). Für neue Versionen brauchst du
`terraform apply` also nicht.

Pull Requests und Forks lösen **nie** einen Deploy aus.

---

## Schritt 0: Werte sammeln

Du brauchst dafür nur lesende Befehle.

Zeigt Tenant-ID, Subscription-ID und das angemeldete Konto:

```
az account show --query "{tenant:tenantId, subscription:id, name:name, user:user.name}" -o table
```

Zeigt die Werte für die GitHub-Variablen. Diesen Befehl im Ordner `infra/azure` ausführen, `output` ändert
nichts:

```
terraform output ci_variables
```

Bei dir sieht das für dev so aus. Prüfe die Werte trotzdem immer mit dem Befehl oben.

| Variable | Wert (dev) |
|---|---|
| `AZURE_RESOURCE_GROUP` | `rg-barbershop-dev` |
| `ACR_NAME` | `acrbarbershopdevb1nz` (nur der Name, **ohne** `.azurecr.io`) |
| `BACKEND_APP_NAME` | `ca-barbershop-dev-api` |
| `FRONTEND_APP_NAME` | `ca-barbershop-dev-web` |
| `MIGRATE_JOB_NAME` | `job-barbershop-dev-migrate` |
| `API_URL` | `https://ca-barbershop-dev-api.proudflower-d87b9ca7.swedencentral.azurecontainerapps.io` |
| `NEXT_PUBLIC_API_URL` | gleicher Wert wie `API_URL` |
| `NEXT_PUBLIC_SITE_URL` | `https://ca-barbershop-dev-web.proudflower-d87b9ca7.swedencentral.azurecontainerapps.io` |
| `NEXT_PUBLIC_TENANT` | `the-barber-news` |
| `NEXT_PUBLIC_SHOW_DEMO_BANNER` | `true` |

---

## Schritt 1: Eine Identität für GitHub anlegen

GitHub braucht in Azure eine Identität, mit der es sich per OIDC anmeldet. Dafür gibt es zwei Wege. **Für
„Azure for Students“ empfehle ich Weg B.**

### Darf ich überhaupt App-Registrierungen anlegen?

„Azure for Students“ ist eine **Subscription**. App-Registrierungen gehören aber zum **Verzeichnis** (Microsoft
Entra ID / Tenant), und darüber entscheidet, wer das Verzeichnis verwaltet:

- **Du hast dich mit einem privaten Konto angemeldet** und das Verzeichnis heißt zum Beispiel „Standardverzeichnis“
  oder „Default Directory“: Dann bist du dort meist selbst globaler Administrator und darfst
  App-Registrierungen anlegen.
- **Du hast dich mit dem Konto deiner Hochschule angemeldet**: Das Verzeichnis gehört der Hochschule. Dort ist
  „Benutzer können Anwendungen registrieren“ oft **abgeschaltet**. Dann schlägt `az ad app create` fehl, mit
  `Insufficient privileges to complete the operation` oder `Authorization_RequestDenied`.

So prüfst du das:

- **Im Portal:** *Microsoft Entra ID → Benutzer → Benutzereinstellungen → „Benutzer können Anwendungen
  registrieren“*.
- **Praktisch:** Probier Schritt A1. Kommt der Fehler oben, nimmst du Weg B.

**Weg B braucht keine Verzeichnisrechte.** Eine *User-Assigned Managed Identity* ist eine normale
Azure-Ressource in deiner Subscription. Als Besitzer deiner Students-Subscription darfst du sie anlegen und ihr
Rollen geben.

### Weg A: App-Registrierung (nur wenn du Verzeichnisrechte hast)

**A1.** Legt die App an und gibt die **App-ID** aus. Notiere sie, das ist `AZURE_CLIENT_ID`:

```
az ad app create --display-name barbershop-ci-dev --query appId -o tsv
```

**A2.** Legt den zugehörigen Service Principal an und gibt dessen **Objekt-ID** aus. Notiere sie, das ist
`<PRINCIPAL_ID>` für Schritt 2:

```
az ad sp create --id <APP_ID> --query id -o tsv
```

**A3.** Lege mit Notepad die Datei `C:\Temp\fc-dev.json` an (Ordner vorher anlegen), mit genau diesem Inhalt.
Groß- und Kleinschreibung zählt:

```json
{
  "name": "github-dev",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:Mohammad-El-Zein/barbershop-web:environment:dev",
  "audiences": ["api://AzureADTokenExchange"]
}
```

Erlaubt dann nur Läufen aus deinem Repo im GitHub-Environment `dev` die Anmeldung:

```
az ad app federated-credential create --id <APP_ID> --parameters C:\Temp\fc-dev.json
```

### Weg B: Managed Identity (empfohlen bei Azure for Students)

**B1.** Legt eine eigene Ressourcengruppe für die CI-Identität an. Sie liegt bewusst **nicht** in
`rg-barbershop-dev`, damit Terraform sie nie anfasst. Nimm dieselbe Region wie deine App, denn Students-
Subscriptions erlauben nur bestimmte Regionen:

```
az group create --name rg-barbershop-ci --location swedencentral
```

**B2.** Legt die Identität an und zeigt `clientId` und `principalId`. Notiere beide: `clientId` ist
`AZURE_CLIENT_ID`, `principalId` ist `<PRINCIPAL_ID>` für Schritt 2:

```
az identity create --resource-group rg-barbershop-ci --name id-barbershop-ci-dev --location swedencentral --query "{clientId:clientId, principalId:principalId}" -o table
```

**B3.** Erlaubt nur Läufen aus deinem Repo im GitHub-Environment `dev` die Anmeldung. Groß- und Kleinschreibung
zählt:

```
az identity federated-credential create --resource-group rg-barbershop-ci --identity-name id-barbershop-ci-dev --name github-dev --issuer https://token.actions.githubusercontent.com --subject repo:Mohammad-El-Zein/barbershop-web:environment:dev --audiences api://AzureADTokenExchange
```

---

## Schritt 2: Minimale Rechte vergeben

Die Identität braucht genau zwei Rollen:

- **AcrPush** auf die Registry, um Images hochzuladen.
- **Contributor** nur auf `rg-barbershop-dev`, für `az containerapp update` sowie `az containerapp job
  update/start`.

Außerhalb dieser Ressourcengruppe darf sie nichts. Secrets im Key Vault kann sie nicht lesen, weil der Key Vault
mit RBAC arbeitet und Contributor dort keine Lese-Rechte auf Secrets gibt.

Wähle **eine** der beiden Varianten. Nicht beide, sonst meldet Terraform später „RoleAssignmentExists“.

**Variante 1 (empfohlen, steht dann im Code):** Trag in `infra/azure/env/dev.local.tfvars` diese Zeile ein und
führe danach selbst `terraform plan` und `terraform apply` aus. Terraform legt dann genau diese beiden Rollen an
(`main.tf`, `ci_acr_push` und `ci_rg_contributor`):

```
ci_principal_id = "<PRINCIPAL_ID>"
```

**Variante 2 (direkt mit az).** Die Subscription-ID stammt aus Schritt 0.

Gibt der Identität AcrPush auf die Registry:

```
az role assignment create --assignee-object-id <PRINCIPAL_ID> --assignee-principal-type ServicePrincipal --role AcrPush --scope /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/rg-barbershop-dev/providers/Microsoft.ContainerRegistry/registries/acrbarbershopdevb1nz
```

Gibt der Identität Contributor nur auf die Dev-Ressourcengruppe:

```
az role assignment create --assignee-object-id <PRINCIPAL_ID> --assignee-principal-type ServicePrincipal --role Contributor --scope /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/rg-barbershop-dev
```

`ServicePrincipal` stimmt für beide Wege, auch für die Managed Identity. Für Rollenzuweisungen brauchst du selbst
die Rolle *Owner* (oder *User Access Administrator*) auf der Subscription. In einer Students-Subscription hast du
sie normalerweise.

**Noch engere Rolle (optional, nicht getestet):** Statt Contributor gehen *Container Apps Contributor* auf
`rg-barbershop-dev` plus *Managed Identity Operator* auf die Identität `id-barbershop-dev-apps`. Die Apps nutzen
diese Identität, und Azure prüft beim Update, ob du sie zuweisen darfst. Fehlt etwas, kommt `AuthorizationFailed`
oder `LinkedAuthorizationFailed`. Für den Anfang reicht Contributor auf die eine Ressourcengruppe.

Rollen brauchen bis zu 5–10 Minuten, bis sie wirken.

---

## Schritt 3: GitHub-Environment „dev“ anlegen

1. Öffne auf GitHub dein Repo, dann **Settings → Environments → New environment**.
2. Name: `dev` (genau so, klein geschrieben), dann **Configure environment**.
3. **Deployment branches and tags:** *Selected branches and tags → Add rule →* `main`. Der Workflow „CD“ läuft
   immer auf `main`.
4. Unter **Environment variables → Add environment variable** trägst du die Variablen unten ein. Es sind normale
   **Variablen**, keine Secrets. Keiner dieser Werte ist geheim.

| Name | Wert |
|---|---|
| `AZURE_CLIENT_ID` | App-ID (Weg A) oder `clientId` (Weg B) |
| `AZURE_TENANT_ID` | `tenant` aus Schritt 0 |
| `AZURE_SUBSCRIPTION_ID` | `subscription` aus Schritt 0 |
| `AZURE_RESOURCE_GROUP` | aus `ci_variables` (Tabelle in Schritt 0) |
| `ACR_NAME` | aus `ci_variables` |
| `BACKEND_APP_NAME` | aus `ci_variables` |
| `FRONTEND_APP_NAME` | aus `ci_variables` |
| `MIGRATE_JOB_NAME` | aus `ci_variables` |
| `API_URL` | aus `ci_variables` (ohne `/` am Ende) |
| `NEXT_PUBLIC_API_URL` | aus `ci_variables` |
| `NEXT_PUBLIC_SITE_URL` | aus `ci_variables` |
| `NEXT_PUBLIC_TENANT` | aus `ci_variables` |
| `NEXT_PUBLIC_SHOW_DEMO_BANNER` | aus `ci_variables` (`true`) |

Diese Variablen sind optional. Lass sie weg, wenn du sie nicht brauchst:

- `NEXT_PUBLIC_TURNSTILE_SITE_KEY`: nur wenn in dev ein `captcha_secret` gesetzt ist. Sonst kann man nicht buchen.
- `NEXT_PUBLIC_GA4_ID`, `NEXT_PUBLIC_GOOGLE_ADS_ID`, `NEXT_PUBLIC_GOOGLE_ADS_CONVERSION_LABEL`,
  `NEXT_PUBLIC_META_PIXEL_ID`: Tracking.

Fehlt eine Pflicht-Variable, bricht der Deploy gleich im ersten Schritt ab. Die Meldung nennt dann die fehlenden
Namen.

---

## Schritt 4: Erster Lauf und Prüfung

Der Workflow „CD“ läuft erst, wenn die Datei `.github/workflows/cd.yml` auf `main` bei GitHub liegt, also nach
deinem nächsten Push.

1. Pushe auf `main`.
2. Unter **Actions** läuft zuerst **CI**. Ist sie grün, startet **CD** mit dem Job *dev / Deploy · dev*.
3. Öffne den Lauf. Unten steht die **Zusammenfassung** mit Commit, Image-Tag und URL.

Ohne neuen Commit kannst du einen Deploy auch von Hand starten: *Actions → CI → Run workflow → Branch `main`,
deploy: `dev`*. Dabei läuft die ganze CI noch einmal mit. Genauso machst du einen **Rollback**: Run workflow auf
einem älteren Commit oder Tag.

Diese Befehle prüfen in Azure nur, sie ändern nichts.

Zeigt das Image, das das Backend gerade nutzt:

```
az containerapp show -g rg-barbershop-dev -n ca-barbershop-dev-api --query "properties.template.containers[0].image" -o tsv
```

Zeigt die letzten Ausführungen des Migrationsjobs:

```
az containerapp job execution list -g rg-barbershop-dev -n job-barbershop-dev-migrate -o table
```

Zeigt die Logs einer Migration. Den Namen der Ausführung zeigt der Befehl oben, er steht auch im GitHub-Log:

```
az containerapp job logs show -g rg-barbershop-dev -n job-barbershop-dev-migrate --execution <EXECUTION_NAME> --container migrate
```

### Typische Fehler

| Meldung im Log | Ursache | Lösung |
|---|---|---|
| `variables missing in the GitHub environment 'dev'` | Variable fehlt oder ist falsch geschrieben | Schritt 3 prüfen; Namen exakt wie in der Tabelle |
| `AADSTS70021: No matching federated identity record found` | `subject` passt nicht | In Schritt A3 bzw. B3 genau `repo:Mohammad-El-Zein/barbershop-web:environment:dev`; Environment heißt `dev` |
| `AADSTS700016` oder „application … not found“ | falsche Client-ID oder Tenant-ID | `AZURE_CLIENT_ID` und `AZURE_TENANT_ID` prüfen; bei Weg B die `clientId`, nicht die `principalId` |
| `No subscriptions found` | Rollen fehlen noch | Schritt 2; 5–10 Minuten warten, dann den Lauf neu starten (*Re-run jobs*) |
| `unauthorized` / `denied` beim Push in die ACR | AcrPush fehlt | Schritt 2 |
| `AuthorizationFailed … Microsoft.App/…` | Contributor auf `rg-barbershop-dev` fehlt | Schritt 2 |
| `ghcr.io/… : not found` oder `denied` | Die CI hat das Backend-Image für diesen Commit nicht gepusht, oder das Repo darf das Paket nicht lesen | Prüfen, ob der Job *Image · backend* in der CI grün war; sonst auf GitHub *Packages → backend → Package settings → Manage Actions access*: Repo mit *Read* hinzufügen |
| `migration job Failed — nothing was rolled out` | Eine Flyway-Migration ist fehlgeschlagen | Logs mit dem Befehl oben ansehen. Die alte Version läuft weiter |
| `backend not ready after 30 attempts` | Das neue Backend startet nicht | In Azure Log Stream bzw. Revisionen ansehen; Rollback siehe `docs/DEPLOYMENT.md` → *Releases and rollback* |
| `Branch "…" is not allowed to deploy to dev` | Regel aus Schritt 3.3 | `main` als erlaubten Branch eintragen |
| CD startet gar nicht | `cd.yml` noch nicht auf `main`, CI nicht grün, oder CI lief für einen Pull Request | Erst pushen; nur grüne Push-Läufe auf `main` deployen |
| `RequestDisallowedByPolicy` (Students) | Region nicht erlaubt | Dieselbe Region wie die App nehmen (`swedencentral`) |
| `Insufficient privileges` bei `az ad app create` | keine Verzeichnisrechte | Weg B |

---

## Später: prod (vorbereitet, noch nicht aktiv)

`cd.yml` hat schon einen Job **prod** für Release-Tags `v*` (zum Beispiel `v1.0.0`). Er läuft erst, wenn du ihn
einschaltest, und braucht dann trotzdem deine **manuelle Freigabe**. So schaltest du ihn ein:

1. Lege für prod eine eigene Identität an, wie in Schritt 1, aber mit `prod` statt `dev` im Namen und im
   `subject`: `repo:Mohammad-El-Zein/barbershop-web:environment:prod`. Gib ihr die Rollen aus Schritt 2 auf die
   **prod**-Ressourcen.
2. Lege das GitHub-Environment `prod` an:
   - **Required reviewers:** dich selbst eintragen. Das ist die manuelle Freigabe.
   - **Deployment branches and tags:** `main` (für `cd.yml`) und das Tag-Muster `v*` (für den manuellen Deploy
     über CI).
   - Variablen wie in Schritt 3, mit den Werten aus `terraform output ci_variables` der prod-Umgebung.
3. Unter **Settings → Secrets and variables → Actions → Variables** (Repository-Ebene) die Variable
   `PROD_DEPLOY_ENABLED` mit dem Wert `true` anlegen.

Danach startet jedes `git tag v1.2.3` mit Push nach grüner CI einen prod-Deploy. Er wartet auf deine Freigabe
unter *Actions → CD → Review deployments*.

## GitLab

`.gitlab-ci.yml` nutzt dasselbe Skript (`infra/azure/scripts/rollout.sh`), deployt aber weiterhin **nur von Hand**
(`deploy:dev`, `deploy:prod`). Die OIDC-Einrichtung für GitLab steht in `docs/DEPLOYMENT.md` → *CI/CD setup*.
