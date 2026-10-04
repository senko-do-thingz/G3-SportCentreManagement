$ErrorActionPreference = "Stop"
$base = "http://localhost:8080/api/v1"

function Login($email, $password) {
    try {
        $body = @{email=$email; password=$password} | ConvertTo-Json
        $res = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType "application/json" -Body $body
        return $res.accessToken
    } catch {
        Write-Host "Login failed for $email" -ForegroundColor Red
        return $null
    }
}

$recToken = Login "manager@sportify.com" "password"
if (-not $recToken) {
    Write-Host "Failed to login as manager. Stopping." -ForegroundColor Red
    exit
}
$rec = @{Authorization="Bearer $recToken"}

Write-Host "--- STEP A: Create a new member ---"
$s = Get-Date -Format "HHmmss"
$email1 = "member${s}a@example.com"
$reg1 = @{fullName="Member $s A";email=$email1;password="password123";phone="09$s$(Get-Random -Minimum 10 -Maximum 99)"} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body $reg1 | Out-Null
Write-Host "PASS: Member $email1 registered."

$h1Token = Login $email1 'password123'
$h1 = @{Authorization="Bearer $h1Token"}

Write-Host "--- STEP B: Member searches themselves (should find their member profile) ---"
$found = Invoke-RestMethod "$base/members?query=$email1" -Headers $rec
if ($found.content.Count -eq 0) {
    Write-Host "FAIL: Member profile not found." -ForegroundColor Red
    exit
}
$code1 = $found.content[0].memberCode
$profileId = $found.content[0].id
Write-Host "PASS: Found member profile, code=$code1, id=$profileId"

Write-Host "--- STEP C: Register a membership ---"
$planRes = Invoke-RestMethod "$base/memberships/plans"
$multiSportPlan = $null
foreach ($p in $planRes) {
    if ($p.code -eq 'MULTI_SPORT') { $multiSportPlan = $p }
}
$planId = $multiSportPlan.id
$sports = @($multiSportPlan.eligibleSports[0].id, $multiSportPlan.eligibleSports[1].id)
$bodyObj = @{planId=$planId; sportIds=$sports}
$bodyJson = $bodyObj | ConvertTo-Json
$mRes = Invoke-RestMethod -Method Post -Uri "$base/memberships" -Headers $h1 -ContentType "application/json" -Body $bodyJson
if ($mRes.status -ne "PENDING_PAYMENT") {
    Write-Host "FAIL: Expected PENDING_PAYMENT, got $($mRes.status)" -ForegroundColor Red
} else {
    Write-Host "PASS: Membership created with status PENDING_PAYMENT"
}
$membershipId = $mRes.id

Write-Host "--- STEP D: Activate membership via dev endpoint ---"
try {
    Invoke-RestMethod -Method Post -Uri "$base/dev/memberships/$membershipId/activate" -Headers $rec | Out-Null
    Write-Host "PASS: Membership activated"
} catch {
    Write-Host "FAIL: Activation failed $_" -ForegroundColor Red
}

Write-Host "--- STEP E: Verify membership is ACTIVE ---"
$myMemberships = Invoke-RestMethod "$base/memberships" -Headers $h1
$activeCount = 0
foreach ($m in $myMemberships) {
    if ($m.id -eq $membershipId -and $m.status -eq "ACTIVE") {
        $activeCount++
    }
}
if ($activeCount -eq 1) {
    Write-Host "PASS: Membership verified as ACTIVE"
} else {
    Write-Host "FAIL: Membership is not ACTIVE" -ForegroundColor Red
}

Write-Host "--- STEP F: Check-in for the member -> expect ALLOWED ---"
$ci = @{identifier=$code1; note="test F"} | ConvertTo-Json
$ciRes = Invoke-RestMethod -Method Post -Uri "$base/check-ins" -Headers $rec -ContentType "application/json" -Body $ci
if ($ciRes.result -eq "ALLOWED") {
    Write-Host "PASS: Check-in ALLOWED"
} else {
    Write-Host "FAIL: Expected ALLOWED, got $($ciRes.result)" -ForegroundColor Red
}

Write-Host "--- STEP G: Fresh member with no membership -> expect DENIED ---"
$s2 = Get-Date -Format "HHmmss"
$email2 = "member${s2}b@example.com"
$reg2 = @{fullName="Member $s2 B";email=$email2;password="password123";phone="08$s2$(Get-Random -Minimum 10 -Maximum 99)"} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$base/auth/register" -ContentType "application/json" -Body $reg2 | Out-Null
$h2Token = Login $email2 'password123'
$h2 = @{Authorization="Bearer $h2Token"}

$found2 = Invoke-RestMethod "$base/members?query=$email2" -Headers $rec
$code2 = $found2.content[0].memberCode
$ci2 = @{identifier=$code2; note="test G"} | ConvertTo-Json
try {
    $ciRes2 = Invoke-RestMethod -Method Post -Uri "$base/check-ins" -Headers $rec -ContentType "application/json" -Body $ci2
    if ($ciRes2.result -eq "DENIED" -and $ciRes2.denialReason -match "No membership found") {
        Write-Host "PASS: Check-in DENIED (No membership found)"
    } else {
        Write-Host "FAIL: Expected DENIED (No membership found), got $($ciRes2.result) - $($ciRes2.denialReason)" -ForegroundColor Red
    }
} catch {
    Write-Host "FAIL: Check-in threw error $_" -ForegroundColor Red
}

Write-Host "--- STEP H: Fresh member pending membership -> expect DENIED ---"
Invoke-RestMethod -Method Post -Uri "$base/memberships" -Headers $h2 -ContentType "application/json" -Body $bodyJson | Out-Null
$ciRes3 = Invoke-RestMethod -Method Post -Uri "$base/check-ins" -Headers $rec -ContentType "application/json" -Body $ci2
if ($ciRes3.result -eq "DENIED" -and $ciRes3.denialReason -match "PENDING_PAYMENT") {
    Write-Host "PASS: Check-in DENIED (PENDING_PAYMENT)"
} else {
    Write-Host "FAIL: Expected DENIED (PENDING_PAYMENT), got $($ciRes3.result) - $($ciRes3.denialReason)" -ForegroundColor Red
}

Write-Host "--- STEP I: Empty identifier -> expect 400 ---"
try {
    Invoke-RestMethod -Method Post -Uri "$base/check-ins" -Headers $rec -ContentType "application/json" -Body (@{identifier=""} | ConvertTo-Json) | Out-Null
    Write-Host "FAIL: Expected 400 Bad Request" -ForegroundColor Red
} catch {
    Write-Host "PASS: Check-in failed with 400 (validation)"
}

Write-Host "--- STEP J: Orphan account registers a plan -> check profile created ---"
$h3Token = Login 'orphan1@example.com' 'password'
if (-not $h3Token) {
    Write-Host "FAIL: Login for orphan1@example.com failed. Ensure create-orphan-member.sql was run." -ForegroundColor Red
} else {
    $h3 = @{Authorization="Bearer $h3Token"}
    Invoke-RestMethod -Method Post -Uri "$base/memberships" -Headers $h3 -ContentType "application/json" -Body $bodyJson | Out-Null
    
    $found3 = Invoke-RestMethod "$base/members?query=orphan1@example.com" -Headers $rec
    if ($found3.content.Count -gt 0) {
        Write-Host "PASS: Orphan account now has a member profile (code: $($found3.content[0].memberCode))"
    } else {
        Write-Host "FAIL: Orphan account still has no member profile" -ForegroundColor Red
    }
}

Write-Host "Smoke test finished."
