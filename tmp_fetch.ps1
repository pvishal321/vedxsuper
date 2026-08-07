$urls = @('https://smartapi.angelone.in/docs/WebSocket2','https://smartapi.angelone.in/docs/MarketData','https://smartapi.angelone.in/docs/WebSocketOrderStatus')
foreach ($u in $urls) {
  try {
    $r = Invoke-WebRequest -Uri $u -UseBasicParsing
    Write-Output "URL $u"
    Write-Output "Status $($r.StatusCode)"
    $text = $r.Content
    $text = $text -replace '<[^>]+>', ' '
    $text = $text -replace '\s+', ' '
    if ($text.Length -gt 2000) { $text = $text.Substring(0,2000) }
    Write-Output $text
    Write-Output '---'
  }
  catch {
    Write-Output "ERR $u $($_.Exception.Message)"
  }
}
