# find-mc.ps1 - prints the PID of the running Minecraft (Java Edition) process.
# Usage:  powershell -ExecutionPolicy Bypass -File find-mc.ps1
#         powershell -ExecutionPolicy Bypass -File find-mc.ps1 -Quiet   (only the PID, for scripts)
param([switch]$Quiet)

$found = @()

# 1) java/javaw whose command line is a Minecraft client (works for every launcher, even minimised)
$procs = Get-CimInstance Win32_Process -Filter "Name='javaw.exe' OR Name='java.exe'" -ErrorAction SilentlyContinue
foreach ($p in $procs) {
    $cmd = [string]$p.CommandLine
    # Prism/MultiMC start java with their own EntryPoint and pass the game args over stdin,
    # so match the launcher markers and instance paths too, not just vanilla's main class.
    if ($cmd -match 'net\.minecraft\.client\.main\.Main|net\.fabricmc\.loader|KnotClient|--gameDir|org\.prismlauncher|org\.multimc|PrismLauncher|MultiMC|[\\/]instances[\\/]|minecraft') {
        $title = ''
        try { $title = (Get-Process -Id $p.ProcessId -ErrorAction Stop).MainWindowTitle } catch {}
        $ver = ''
        if ($cmd -match '--version\s+(\S+)') { $ver = $Matches[1] }
        $found += [pscustomobject]@{ PID = $p.ProcessId; Name = $p.Name; Version = $ver; Title = $title }
    }
}

# 2) fallback: any java window titled "Minecraft..."
if ($found.Count -eq 0) {
    Get-Process java, javaw -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like 'Minecraft*' } |
        ForEach-Object { $found += [pscustomobject]@{ PID = $_.Id; Name = $_.ProcessName; Version = ''; Title = $_.MainWindowTitle } }
}

# 3) last resort: a java process that has LWJGL (the game's native library) loaded
if ($found.Count -eq 0) {
    Get-Process java, javaw -ErrorAction SilentlyContinue | ForEach-Object {
        $hasLwjgl = $false
        try { $hasLwjgl = [bool]($_.Modules | Where-Object { $_.ModuleName -like 'lwjgl*' } | Select-Object -First 1) } catch {}
        if ($hasLwjgl) { $found += [pscustomobject]@{ PID = $_.Id; Name = $_.ProcessName; Version = ''; Title = $_.MainWindowTitle } }
    }
}

if ($found.Count -eq 0) {
    if (-not $Quiet) { Write-Host 'Minecraft is not running.' -ForegroundColor Red }
    exit 1
}

if ($Quiet) {
    $found | ForEach-Object { $_.PID }
} else {
    Write-Host "Found $($found.Count) Minecraft process(es):" -ForegroundColor Green
    $found | Format-Table -AutoSize
}
exit 0
