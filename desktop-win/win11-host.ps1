param([string]$RootDir = $PSScriptRoot)

$listener = New-Object System.Net.HttpListener
$port = 48120
while ($port -lt 48150) {
  try {
    $listener.Prefixes.Clear()
    $listener.Prefixes.Add("http://127.0.0.1:$port/")
    $listener.Start()
    break
  } catch {
    $port++
  }
}

$url = "http://127.0.0.1:$port/index.html"
Write-Host "JARVIS 3D Gesture Control running at $url"
Write-Host "Close this window to stop the local Windows 11 server."

$edgePaths = @(
  "$env:ProgramFiles(x86)\Microsoft\Edge\Application\msedge.exe",
  "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe"
)
$browser = $edgePaths | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($browser) {
  Start-Process $browser -ArgumentList "--app=$url --window-size=1440,900"
} else {
  Start-Process $url
}

$mimeTypes = @{
  ".html" = "text/html; charset=utf-8"
  ".js"   = "application/javascript; charset=utf-8"
  ".png"  = "image/png"
  ".ico"  = "image/x-icon"
  ".json" = "application/json"
}

try {
  while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $reqPath = $ctx.Request.Url.AbsolutePath.TrimStart('/')
    if ([string]::IsNullOrWhiteSpace($reqPath)) { $reqPath = "index.html" }
    $safeName = [System.IO.Path]::GetFileName($reqPath)
    $filePath = Join-Path $RootDir $safeName

    if (Test-Path $filePath -PathType Leaf) {
      $ext = [System.IO.Path]::GetExtension($filePath).ToLower()
      $ctx.Response.ContentType = if ($mimeTypes.ContainsKey($ext)) { $mimeTypes[$ext] } else { "application/octet-stream" }
      $bytes = [System.IO.File]::ReadAllBytes($filePath)
      $ctx.Response.ContentLength64 = $bytes.Length
      $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    } else {
      $ctx.Response.StatusCode = 404
    }
    $ctx.Response.Close()
  }
} finally {
  $listener.Stop()
}
