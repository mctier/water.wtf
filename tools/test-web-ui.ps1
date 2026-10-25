$ErrorActionPreference='Stop'
$root=Split-Path -Parent (Split-Path -Parent $PSCommandPath)
Set-Location $root
$jdk='C:\Program Files\Java\jdk-21.0.12\bin'
$gson=Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.code.gson\gson\2.13.2" -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
$out=Join-Path $root 'artifacts/web-ui-20261017/test-classes'
New-Item -ItemType Directory -Force $out | Out-Null
& "$jdk\javac.exe" -encoding UTF-8 -cp $gson -d $out src/client/java/com/utilitymod/web/WebState.java src/client/java/com/utilitymod/web/LocalWebServer.java tools/tests/WebUiTest.java
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& "$jdk\java.exe" -cp "$out;$gson;$root/src/main/resources" WebUiTest @args
exit $LASTEXITCODE
