# start_backend.ps1
# Startup script for Personal Expense Tracker Java Backend

$ErrorActionPreference = "Stop"

# 1. Check if server is already running on port 8080
$isPortOpen = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 8080)
    $client.Close()
    $isPortOpen = $true
} catch {
    $isPortOpen = $false
}

if ($isPortOpen) {
    Write-Host "[Start] Java backend is already running on http://localhost:8080."
    exit 0
}

# 2. Locate Java and Javac executables
$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    $javaExe = (Get-ChildItem -Path "$env:USERPROFILE\.antigravity-ide", "$env:USERPROFILE\.p2", "C:\Program Files\Java" -Filter "java.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
    if ($javaExe) {
        $javaCmd = $javaExe
    } else {
        Write-Error "[Start] Error: Could not locate java.exe. Please ensure Java is installed."
        exit 1
    }
} else {
    $javaCmd = $javaCmd.Source
}

$javacCmd = Get-Command javac -ErrorAction SilentlyContinue
if (-not $javacCmd) {
    $javacExe = (Get-ChildItem -Path "$env:USERPROFILE\.antigravity-ide", "$env:USERPROFILE\.p2", "C:\Program Files\Java" -Filter "javac.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
    if ($javacExe) {
        $javacCmd = $javacExe
    } else {
        Write-Error "[Start] Error: Could not locate javac.exe."
        exit 1
    }
} else {
    $javacCmd = $javacCmd.Source
}

# 3. Compile Java source files
Write-Host "[Start] Compiling Java backend..."
& $javacCmd -cp "lib/*;java" java/*.java -d java
if ($LASTEXITCODE -ne 0) {
    Write-Error "[Start] Java compilation failed!"
    exit 1
}

# 4. Start Java Main server
Write-Host "[Start] Starting Java server on port 8080..."
& $javaCmd -cp "lib/*;java" Main
