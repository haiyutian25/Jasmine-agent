# One-off: collapse the repetitive error conversion into `AgentFailure::from`.
# Pattern (any indentation): .map_err(|error| AgentFailure::Failed {\n detail: error.detail(),\n })
$file = 'D:\ima\minimal-hello\rust\ffi\src\lib.rs'
$text = [System.IO.File]::ReadAllText($file)
$pattern = '\.map_err\(\|error\| AgentFailure::Failed \{\s*\r?\n\s*detail: error\.detail\(\),\s*\r?\n\s*\}\)'
$matches = [regex]::Matches($text, $pattern)
Write-Output ("matched: " + $matches.Count)
$new = [regex]::Replace($text, $pattern, '.map_err(AgentFailure::from)')
[System.IO.File]::WriteAllText($file, $new, (New-Object System.Text.UTF8Encoding($false)))
$remaining = [regex]::Matches($new, 'AgentFailure::Failed')
Write-Output ("remaining AgentFailure::Failed occurrences: " + $remaining.Count)
