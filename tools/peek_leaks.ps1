$f = "C:\Users\fakemoon\Documents\Default Project\utility-mod\tools\exact_leaks.txt"
$rows = Get-Content $f | ForEach-Object {
  $p = $_ -split "`t"
  [pscustomobject]@{ Tag=$p[0]; Sites=[int]$p[1]; S=$p[2]; Src=$p[3] }
}
"total rows: $($rows.Count)"
"distinct strings: $(($rows | ForEach-Object S | Sort-Object -Unique).Count)"
""
"=== by tag ==="
$rows | Group-Object Tag | Sort-Object Count -Descending | Select-Object Name,Count | Format-Table -AutoSize
"=== longest 60 (descriptions) ==="
$rows | Sort-Object { $_.S.Length } -Descending | Select-Object -First 60 | ForEach-Object { "$($_.Sites)x  $($_.S)  @ $($_.Src)" }
