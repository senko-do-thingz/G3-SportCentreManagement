param (
    [string]$OrphanEmail = "orphan1@example.com"
)

$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api/v1"
$passed = 0
$failed = 0
$skipped = 0

function Report-Pass($msg) {
    Write-Host "PASS: $msg" -ForegroundColor Green
    $script:passed++
}

function Report-Fail($msg) {
    Write-Host "FAIL: $msg" -ForegroundColor Red
    $script:failed++
}

function Report-Skip($msg) {
    Write-Host "SKIP: $msg" -ForegroundColor Yellow
    $script:skipped++
}

function Invoke-Api {
    param (
        [string]$Method,
        [string]$Uri,
        [hashtable]$Headers,
        [string]$Body
    )
    $req = @{ Method = $Method; Uri = $Uri; ErrorAction = "Stop" }
    if ($Headers) { $req.Headers = $Headers }
    if ($Body) { $req.Body = $Body; $req.ContentType = "application/json" }
    
    try {
        $res = Invoke-RestMethod @req
        return @{ status = 200; data = $res }
    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            return @{ status = $statusCode; data = $null }
        }
        return @{ status = 0; data = $null; error = $_.Exception.Message }
    }
}

function Login($email, $password) {
    $body = @{email=$email; password=$password} | ConvertTo-Json
    $res = Invoke-Api -Method Post -Uri "$base/auth/login" -Body $body
    if ($res.status -eq 200) {
        return $res.data.accessToken
    }
    return $null
}

Write-Host "--- SETUP ---"
$mgrToken = Login "manager@sportify.com" "password"
if (-not $mgrToken) {
    Write-Host "Failed to login as manager. Stopping." -ForegroundColor Red
    exit 1
}
$mgr = @{Authorization="Bearer $mgrToken"}

$s = Get-Date -Format "HHmmss"
$email1 = "member${s}a@example.com"
$reg1 = @{fullName="Member $s A";email=$email1;password="password123";phone="09$s$(Get-Random -Minimum 10 -Maximum 99)"} | ConvertTo-Json
$res = Invoke-Api -Method Post -Uri "$base/auth/register" -Body $reg1
$h1Token = Login $email1 'password123'
$h1 = @{Authorization="Bearer $h1Token"}

$found = Invoke-Api -Method Get -Uri "$base/members?query=$email1" -Headers $mgr
$code1 = $found.data.content[0].memberCode
$profileId = $found.data.content[0].id

$planRes = Invoke-Api -Method Get -Uri "$base/plans"
$multiSportPlan = $null
foreach ($p in $planRes.data) {
    if ($p.code -eq 'MULTI_SPORT') { $multiSportPlan = $p }
}
$planId = $multiSportPlan.id
$sports = @($multiSportPlan.eligibleSports[0].id, $multiSportPlan.eligibleSports[1].id)

Write-Host "`n--- STEP A: invalid sport id 99999 -> expect HTTP 400 ---"
$bodyA = @{planId=$planId; sportIds=@(99999)} | ConvertTo-Json
$resA = Invoke-Api -Method Post -Uri "$base/memberships" -Headers $h1 -Body $bodyA
if ($resA.status -eq 400) { Report-Pass "Invalid sport id gave HTTP 400" } else { Report-Fail "Expected 400, got $($resA.status)" }

Write-Host "`n--- STEP B: valid registration -> PENDING_PAYMENT, registrationType NEW, empty dates ---"
$bodyObj = @{planId=$planId; sportIds=$sports}
$bodyJson = $bodyObj | ConvertTo-Json
$resB = Invoke-Api -Method Post -Uri "$base/memberships" -Headers $h1 -Body $bodyJson
if ($resB.status -eq 200 -and $resB.data.status -eq "PENDING_PAYMENT" -and $resB.data.registrationType -eq "NEW" -and -not $resB.data.startDate -and -not $resB.data.endDate) {
    Report-Pass "Membership registered correctly"
} else {
    Report-Fail "Registration failed or returned wrong data. Status: $($resB.status)"
}
$membershipId = $resB.data.id

Write-Host "`n--- STEP C: second registration while PENDING -> expect HTTP 409 ---"
$resC = Invoke-Api -Method Post -Uri "$base/memberships" -Headers $h1 -Body $bodyJson
if ($resC.status -eq 409) { Report-Pass "Second registration gave HTTP 409" } else { Report-Fail "Expected 409, got $($resC.status)" }

Write-Host "`n--- STEP D: activate via dev endpoint, renew -> RENEWAL, activate renewal -> SCHEDULED ---"
$resD1 = Invoke-Api -Method Post -Uri "$base/dev/memberships/$membershipId/activate" -Headers $mgr
if ($resD1.status -eq 200) {
    # Check it is active and get dates
    $me = Invoke-Api -Method Get -Uri "$base/memberships/me" -Headers $h1
    $activeMem = $me.data | Where-Object { $_.id -eq $membershipId }
    if ($activeMem.status -eq "ACTIVE") {
        # Renew (post to /memberships again)
        $resD2 = Invoke-Api -Method Post -Uri "$base/memberships" -Headers $h1 -Body $bodyJson
        if ($resD2.status -eq 200 -and $resD2.data.registrationType -eq "RENEWAL") {
            $renewalId = $resD2.data.id
            $resD3 = Invoke-Api -Method Post -Uri "$base/dev/memberships/$renewalId/activate" -Headers $mgr
            if ($resD3.status -eq 200) {
                $me2 = Invoke-Api -Method Get -Uri "$base/memberships/me" -Headers $h1
                $schedMem = $me2.data | Where-Object { $_.id -eq $renewalId }
                if ($schedMem.status -eq "SCHEDULED") {
                    $activeEnd = [datetime]::Parse($activeMem.endDate)
                    $schedStart = [datetime]::Parse($schedMem.startDate)
                    $schedEnd = [datetime]::Parse($schedMem.endDate)
                    $activeStart = [datetime]::Parse($activeMem.startDate)
                    $duration1 = ($activeEnd - $activeStart).Days
                    $duration2 = ($schedEnd - $schedStart).Days
                    if ($schedStart.Date -eq $activeEnd.AddDays(1).Date -and $duration1 -eq $activeMem.durationDays -and $duration2 -eq $schedMem.durationDays) {
                        Report-Pass "Renewed and scheduled correctly with correct dates"
                    } else {
                        Report-Fail "Dates mismatch: activeStart=$activeStart, activeEnd=$activeEnd (dur=$duration1 vs $($activeMem.durationDays)), schedStart=$schedStart, schedEnd=$schedEnd (dur=$duration2 vs $($schedMem.durationDays))"
                    }
                } else { Report-Fail "Renewal not SCHEDULED after activation" }
            } else { Report-Fail "Failed to activate renewal" }
        } else { Report-Fail "Failed to renew or not RENEWAL type" }
    } else { Report-Fail "Initial membership not ACTIVE after activation" }
} else { Report-Fail "Failed to activate initial membership" }

Write-Host "`n--- STEP E: GET /api/v1/members without query -> HTTP 200 and totalElements >= 1 ---"
$resE = Invoke-Api -Method Get -Uri "$base/members" -Headers $mgr
if ($resE.status -eq 200 -and $resE.data.totalElements -ge 1) {
    Report-Pass "GET /members works without query"
} else {
    Report-Fail "GET /members failed. Status: $($resE.status), totalElements: $($resE.data.totalElements)"
}

Write-Host "`n--- STEP F: Check-in ALLOWED ---"
$ci = @{identifier=$code1; note="test F"} | ConvertTo-Json
$resF = Invoke-Api -Method Post -Uri "$base/check-ins" -Headers $mgr -Body $ci
if ($resF.status -eq 200 -and $resF.data.result -eq "ALLOWED") {
    Report-Pass "Check-in ALLOWED"
} else {
    Report-Fail "Check-in FAILED. Status: $($resF.status), Result: $($resF.data.result)"
}

Write-Host "`n--- STEP G: Fresh member with no membership -> expect DENIED (No membership found) ---"
$email2 = "member${s}b@example.com"
$reg2 = @{fullName="Member $s B";email=$email2;password="password123";phone="08$s$(Get-Random -Minimum 10 -Maximum 99)"} | ConvertTo-Json
$res = Invoke-Api -Method Post -Uri "$base/auth/register" -Body $reg2
$h2Token = Login $email2 'password123'
$h2 = @{Authorization="Bearer $h2Token"}
$found2 = Invoke-Api -Method Get -Uri "$base/members?query=$email2" -Headers $mgr
$code2 = $found2.data.content[0].memberCode
$ci2 = @{identifier=$code2; note="test G"} | ConvertTo-Json

$resG = Invoke-Api -Method Post -Uri "$base/check-ins" -Headers $mgr -Body $ci2
if ($resG.status -eq 200 -and $resG.data.result -eq "DENIED" -and $resG.data.denialReason -match "No membership found") {
    Report-Pass "Check-in DENIED (No membership found)"
} else {
    Report-Fail "Check-in G failed. Status: $($resG.status)"
}

Write-Host "`n--- STEP H: Fresh member pending membership -> expect DENIED (PENDING_PAYMENT) ---"
$resH1 = Invoke-Api -Method Post -Uri "$base/memberships" -Headers $h2 -Body $bodyJson
$resH = Invoke-Api -Method Post -Uri "$base/check-ins" -Headers $mgr -Body $ci2
if ($resH.status -eq 200 -and $resH.data.result -eq "DENIED" -and $resH.data.denialReason -match "PENDING_PAYMENT") {
    Report-Pass "Check-in DENIED (PENDING_PAYMENT)"
} else {
    Report-Fail "Check-in H failed. Status: $($resH.status)"
}

Write-Host "`n--- STEP I: Empty identifier -> expect exactly HTTP 400 ---"
$ciI = @{identifier=""} | ConvertTo-Json
$resI = Invoke-Api -Method Post -Uri "$base/check-ins" -Headers $mgr -Body $ciI
if ($resI.status -eq 400) {
    Report-Pass "Empty identifier gave 400"
} else {
    Report-Fail "Expected 400, got $($resI.status)"
}

Write-Host "`n--- STEP J: Orphan account registers a plan -> check profile created ---"
$orphanToken = Login $OrphanEmail 'password'
if (-not $orphanToken) {
    if ($PSBoundParameters.ContainsKey('OrphanEmail')) {
        Report-Fail "Login for $OrphanEmail failed. Run scripts/dev/create-orphan-member.sql first."
    } else {
        Report-Skip "Login for $OrphanEmail failed. Skipping."
    }
} else {
    $foundO = Invoke-Api -Method Get -Uri "$base/members?query=$OrphanEmail" -Headers $mgr
    if ($foundO.status -eq 200 -and $foundO.data.content.Count -gt 0) {
        Report-Skip "Account $OrphanEmail already has a member profile. Skipping."
    } else {
        $hO = @{Authorization="Bearer $orphanToken"}
        $resJ1 = Invoke-Api -Method Post -Uri "$base/memberships" -Headers $hO -Body $bodyJson
        if ($resJ1.status -eq 200 -and $resJ1.data.status -eq "PENDING_PAYMENT") {
            $foundO2 = Invoke-Api -Method Get -Uri "$base/members?query=$OrphanEmail" -Headers $mgr
            if ($foundO2.status -eq 200 -and $foundO2.data.content.Count -gt 0) {
                Report-Pass "Orphan account now has a member profile"
            } else {
                Report-Fail "Orphan account still has no member profile"
            }
        } else {
            Report-Fail "Registration failed for orphan account. Status: $($resJ1.status)"
        }
    }
}

Write-Host "`n--- SUMMARY ---"
Write-Host "Passed: $passed" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor Red
Write-Host "Skipped: $skipped" -ForegroundColor Yellow

if ($failed -gt 0) {
    exit 1
}
exit 0
