param(
    [string]$Password = "LoadTest@123",
    [string]$DatabaseUrl = "jdbc:postgresql://127.0.0.1:5432/issue_tracker",
    [string]$DatabaseUser = "issue_tracker",
    [string]$DatabasePassword = "issue_tracker"
)

$ErrorActionPreference = "Stop"
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Resolve-Path (Join-Path $scriptDirectory "..\..")
$sqlPath = Join-Path $scriptDirectory "generate-load-test-data.sql"
$runnerPath = Join-Path $scriptDirectory "JdbcSqlRunner.java"
$driverRoots = @(
    "D:\repository\org\postgresql\postgresql",
    (Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql")
)
$driverPath = $driverRoots |
    Where-Object { Test-Path $_ } |
    ForEach-Object {
        Get-ChildItem -Path $_ -Recurse -Filter "postgresql-*.jar" |
            Sort-Object FullName |
            Select-Object -Last 1
    } |
    Select-Object -First 1 -ExpandProperty FullName

Write-Host "Generating 2,050 users and 1,000,000 tickets. This can take several minutes."

if (-not $driverPath) {
    throw "PostgreSQL JDBC driver was not found. Run the backend Maven build first."
}

& java `
    --class-path $driverPath `
    $runnerPath `
    $DatabaseUrl `
    $DatabaseUser `
    $DatabasePassword `
    $Password `
    $sqlPath
if ($LASTEXITCODE -ne 0) {
    throw "Load-test data generation failed with exit code $LASTEXITCODE."
}

Write-Host "Load-test data generation completed."
