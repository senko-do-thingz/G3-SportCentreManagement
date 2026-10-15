$modules = @("identity", "catalog", "booking", "payment", "training", "ai_recommendation", "support", "notification")
$sub_packages = @("entity", "repository", "mapper", "dto", "service", "service/impl", "controller")

$base_src = "src/main/java/com/sportify"
$base_test = "src/test/java/com/sportify"

# Core module
$core_packages = @("config", "security", "exception", "audit", "common")
foreach ($pkg in $core_packages) {
    New-Item -ItemType Directory -Force -Path "$base_src/core/$pkg" | Out-Null
    New-Item -ItemType Directory -Force -Path "$base_test/core/$pkg" | Out-Null
}

# Feature modules
foreach ($mod in $modules) {
    foreach ($pkg in $sub_packages) {
        New-Item -ItemType Directory -Force -Path "$base_src/$mod/$pkg" | Out-Null
        New-Item -ItemType Directory -Force -Path "$base_test/$mod/$pkg" | Out-Null
    }
}

# Resources
New-Item -ItemType Directory -Force -Path "src/main/resources/db/migration" | Out-Null
New-Item -ItemType Directory -Force -Path "logs" | Out-Null

# Create Audit Log
$auditLogContent = @"
# AI Audit Log

## Phase: Project Initialization & Scaffolding
- **Date:** $(Get-Date -Format 'yyyy-MM-dd')
- **Key Decisions:** Chốt kiến trúc Module-Driven, Java 17, Spring Boot 3, MS SQL Server, Flyway, MapStruct cho DTO mapping, và quy ước Interface-Implementation cho Service layer.
- **Next Steps:** Implement Identity module.
"@

Set-Content -Path "logs/AI_Audit_Log.md" -Value $auditLogContent -Encoding UTF8
Write-Host "Scaffolding completed successfully."
