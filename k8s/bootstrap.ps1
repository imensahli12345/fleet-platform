# Rebuilds the whole fleet platform on an empty cluster, with one command:
#   powershell -ExecutionPolicy Bypass -File k8s\bootstrap.ps1
#
# 1. creates the namespace
# 2. creates the 3 Secrets with NEW random values (Secrets are never stored in Git)
#    - skipped if a Secret already exists, so running the script twice is safe
# 3. applies every manifest in k8s/fleet (Kubernetes handles the start order)
# 4. waits until every pod is ready
#
# Requirements: a running cluster (kubectl get nodes works) and D:\ai-service\.env

$ErrorActionPreference = "Stop"
$ns = "fleet"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$aiEnvFile = "D:\ai-service\.env"

function New-RandomHex([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
}

# Runs kubectl and stops the script if it fails (PowerShell doesn't stop on its own
# when an external program fails).
function Invoke-Kubectl {
    kubectl @args
    if ($LASTEXITCODE -ne 0) { throw "kubectl $args failed (exit code $LASTEXITCODE)" }
}

function Test-Secret([string]$name) {
    # --ignore-not-found: prints nothing (instead of an error) when the Secret doesn't exist
    $found = kubectl get secret $name -n $ns --ignore-not-found -o name
    return [bool]$found
}

Write-Host "== 1. Namespace"
Invoke-Kubectl apply -f "$root\fleet\00-namespace.yaml"

Write-Host "== 2. Secrets"
if (-not (Test-Secret "postgres-secret")) {
    # The value goes in a variable first: "--x=(function)" would be split into 2 arguments.
    $dbPassword = New-RandomHex 16
    Invoke-Kubectl create secret generic postgres-secret -n $ns `
        --from-literal=POSTGRES_USER=postgres `
        --from-literal=POSTGRES_PASSWORD=$dbPassword
} else { Write-Host "postgres-secret already exists, kept" }

if (-not (Test-Secret "jwt-secret")) {
    $jwtSecret = New-RandomHex 32
    Invoke-Kubectl create secret generic jwt-secret -n $ns --from-literal=JWT_SECRET=$jwtSecret
} else { Write-Host "jwt-secret already exists, kept" }

if (-not (Test-Secret "ai-service-secret")) {
    if (-not (Test-Path $aiEnvFile)) { throw "Missing $aiEnvFile (ai-service API keys)" }
    Invoke-Kubectl create secret generic ai-service-secret -n $ns --from-env-file=$aiEnvFile
    # .env has AI_SERVICE_API_KEY="" (quotes kept literally by kubectl): replace with a real key.
    # shipment-service reads the same Secret, so both sides always match.
    $patch = '{"stringData":{"AI_SERVICE_API_KEY":"' + (New-RandomHex 16) + '"}}'
    $patchFile = New-TemporaryFile
    Set-Content -Path $patchFile -Value $patch -Encoding ascii
    Invoke-Kubectl patch secret ai-service-secret -n $ns --patch-file $patchFile
    Remove-Item $patchFile
} else { Write-Host "ai-service-secret already exists, kept" }

Write-Host "== 3. Apply all manifests"
# No trailing "\": before a closing quote it would escape the quote.
Invoke-Kubectl apply -f "$root\fleet"

Write-Host "== 4. Waiting for pods (Spring services need 1-3 minutes)"
foreach ($db in "postgres", "mongodb", "kafka") {
    Invoke-Kubectl rollout status statefulset/$db -n $ns --timeout=600s
}
Invoke-Kubectl wait deployment --all -n $ns --for=condition=Available --timeout=900s
kubectl get pods -n $ns -o wide
