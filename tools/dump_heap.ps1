# dump_heap.ps1 — GC.heap_dump the first javaw (the Minecraft client) to %TEMP%\umheap.bin
$javaw = Get-Process javaw -ErrorAction Stop | Select-Object -First 1
$jcmd = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot\bin\jcmd.exe"
if (-not (Test-Path $jcmd)) {
    $jcmd = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Recurse -Filter jcmd.exe -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
}
if (-not $jcmd) { Write-Host "jcmd not found"; exit 1 }
$out = Join-Path $env:TEMP "umheap.bin"
if (Test-Path $out) { Remove-Item $out -Force }
Write-Host "dumping javaw pid $($javaw.Id) -> $out"
& $jcmd $javaw.Id GC.heap_dump $out
if (Test-Path $out) {
    $f = Get-Item $out
    Write-Host "done: $out ($([math]::Round($f.Length/1MB,1)) MB)"
} else {
    Write-Host "dump failed - run from an elevated prompt if jcmd refused"
}
