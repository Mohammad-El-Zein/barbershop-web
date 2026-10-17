<#
  Helpers for the PowerShell demo / test commands (Windows PowerShell 5.1 and PowerShell 7).
  Dot-source it:   . .\infra\demo-helpers.ps1 ; Initialize-Demo
#>

$script:TENANT  = "the-barber-news"
$script:HAIRCUT = "7b3f1c2e-0000-4000-8000-000000000101"
$script:BEARD   = "7b3f1c2e-0000-4000-8000-000000000102"
$script:PREMIUM = "7b3f1c2e-0000-4000-8000-000000000103"
$script:LUCA    = "7b3f1c2e-0000-4000-8000-000000000201"
$script:MO      = "7b3f1c2e-0000-4000-8000-000000000202"

function Get-EnvValue([string]$Key) {
    $file = Join-Path $PSScriptRoot ".env"
    if (-not (Test-Path $file)) { return $null }
    $line = Get-Content $file | Where-Object { $_ -match "^$Key=" } | Select-Object -Last 1
    if ($line) { return $line.Substring($Key.Length + 1).Trim() }
    return $null
}

function Initialize-Demo([string]$Api = "", [string]$Mailpit = "") {
    $port = Get-EnvValue "BACKEND_HOST_PORT"; if (-not $port) { $port = "8080" }
    $mport = Get-EnvValue "MAILPIT_UI_PORT"; if (-not $mport) { $mport = "8025" }
    $script:API = if ($Api) { $Api } else { "http://localhost:$port" }
    $script:MAILPIT = if ($Mailpit) { $Mailpit } else { "http://localhost:$mport" }
    $script:ADMIN_EMAIL = Get-EnvValue "APP_ADMIN_EMAIL"
    $script:ADMIN_PASSWORD = Get-EnvValue "APP_ADMIN_PASSWORD"
    Set-Variable -Scope Global -Name API -Value $script:API
    Set-Variable -Scope Global -Name MAILPIT -Value $script:MAILPIT
    Set-Variable -Scope Global -Name TENANT -Value $script:TENANT
    Set-Variable -Scope Global -Name HAIRCUT -Value $script:HAIRCUT
    Set-Variable -Scope Global -Name BEARD -Value $script:BEARD
    Set-Variable -Scope Global -Name PREMIUM -Value $script:PREMIUM
    Set-Variable -Scope Global -Name LUCA -Value $script:LUCA
    Set-Variable -Scope Global -Name MO -Value $script:MO
    Set-Variable -Scope Global -Name ADMIN_EMAIL -Value $script:ADMIN_EMAIL
    Set-Variable -Scope Global -Name ADMIN_PASSWORD -Value $script:ADMIN_PASSWORD
    "API=$script:API  Mailpit=$script:MAILPIT  admin=$script:ADMIN_EMAIL"
}

function Step([string]$Title) { Write-Host ""; Write-Host "> $Title" -ForegroundColor Yellow }

function Show($Object) { $Object | ConvertTo-Json -Depth 8 }

<#
  Calls the API and returns [Status, Body, Headers] - also for 4xx/5xx (no exception).
  Bodies are read as UTF-8 (Windows PowerShell 5.1 would otherwise garble umlauts).
#>
function Api([string]$Method, [string]$Path, $Body = $null, [string]$Token = "", [string]$Lang = "de",
             [hashtable]$Headers = @{}, $Session = $null, [switch]$NoRetry) {
    $r = Invoke-Api $Method $Path $Body $Token $Lang $Headers $Session
    if ($r.Status -eq 429 -and -not $NoRetry) {
        # the per-IP rate limiter answered; wait as told and try once more
        $wait = [int]("$($r.Headers['Retry-After'])" -replace '\D', '')
        if ($wait -le 0) { $wait = 5 }
        Write-Host "    (rate limit: HTTP 429 on $Path - waiting $wait s as Retry-After says)" -ForegroundColor DarkGray
        Start-Sleep -Seconds ($wait + 1)
        $r = Invoke-Api $Method $Path $Body $Token $Lang $Headers $Session
    }
    return $r
}

function Invoke-Api([string]$Method, [string]$Path, $Body, [string]$Token, [string]$Lang, [hashtable]$Headers,
                    $Session) {
    $h = @{ "Accept-Language" = $Lang }
    foreach ($k in $Headers.Keys) { $h[$k] = $Headers[$k] }
    if ($Token) { $h["Authorization"] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$script:API$Path"; Headers = $h; UseBasicParsing = $true }
    if ($Session) { $params["WebSession"] = $Session }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress))
    }
    try {
        $r = Invoke-WebRequest @params
        $text = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        $status = [int]$r.StatusCode
        $respHeaders = $r.Headers
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { throw }
        $status = [int]$resp.StatusCode
        $respHeaders = $resp.Headers
        if ($PSVersionTable.PSVersion.Major -ge 7) {
            $text = $_.ErrorDetails.Message
        } else {
            $stream = $resp.GetResponseStream(); $stream.Position = 0
            $text = (New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)).ReadToEnd()
        }
    }
    $parsed = $null
    if ($text) { try { $parsed = $text | ConvertFrom-Json } catch { $parsed = $text } }
    [pscustomobject]@{ Status = $status; Body = $parsed; Headers = $respHeaders }
}

function Login-Admin {
    $r = Api POST "/api/auth/$script:TENANT/login" @{ email = $script:ADMIN_EMAIL; password = $script:ADMIN_PASSWORD }
    if ($r.Status -ne 200) { throw "admin login failed: HTTP $($r.Status) $($r.Body.detail)" }
    return $r.Body.accessToken
}

# -- Mailpit ----------------------------------------------------------------

function Get-Mails {
    $raw = Invoke-WebRequest "$script:MAILPIT/api/v1/messages?limit=200" -UseBasicParsing
    $list = [Text.Encoding]::UTF8.GetString($raw.RawContentStream.ToArray()) | ConvertFrom-Json
    $list.messages | ForEach-Object {
        [pscustomobject]@{ ID = $_.ID; To = ($_.To | ForEach-Object Address) -join ","; Subject = $_.Subject; Created = $_.Created }
    }
}

<# Waits for the newest mail to $To whose subject contains $SubjectPart; returns the full message (Text, HTML, Attachments). #>
function Wait-Mail([string]$To, [string]$SubjectPart, [int]$Seconds = 15) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $hit = Get-Mails | Where-Object { $_.To -match [regex]::Escape($To) -and $_.Subject -like "*$SubjectPart*" } |
            Sort-Object Created -Descending | Select-Object -First 1
        if ($hit) {
            $raw = Invoke-WebRequest "$script:MAILPIT/api/v1/message/$($hit.ID)" -UseBasicParsing
            return [Text.Encoding]::UTF8.GetString($raw.RawContentStream.ToArray()) | ConvertFrom-Json
        }
        Start-Sleep -Milliseconds 400
    }
    throw "no mail to $To with subject '*$SubjectPart*'"
}

<# Extracts the 43-character token after e.g. "/pay/" from a mail's text. #>
function Get-LinkToken($Mail, [string]$PathPart) {
    $m = [regex]::Match($Mail.Text, [regex]::Escape($PathPart) + "([A-Za-z0-9_-]{43})")
    if (-not $m.Success) { throw "no '$PathPart' link in mail '$($Mail.Subject)'" }
    return $m.Groups[1].Value
}
