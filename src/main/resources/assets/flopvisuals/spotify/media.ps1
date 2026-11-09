# Flop Visuals: reads what's playing from Windows' media controls (the same info as the volume popup).
# Prints one JSON line per second. Exits when Minecraft closes.
param([int]$ParentId = 0)
$ErrorActionPreference = 'Stop'
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
    function Await($op, [Type]$type) {
        $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
        if (-not $task.Wait(4000)) { throw 'timeout' }
        $task.Result
    }
    $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
    $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
    $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
} catch {
    [Console]::Out.WriteLine((@{ error = "Windows media controls unavailable: $($_.Exception.Message)" } | ConvertTo-Json -Compress))
    exit 1
}

$browsers = 'chrome|msedge|firefox|brave|opera|vivaldi|arc'
$lastKey = ''
$artTries = 0

function Pick-Session {
    $sessions = @($mgr.GetSessions())
    # Prefer the Spotify app, then a browser (the Spotify web player), then whatever Windows says is current.
    $p = $sessions | Where-Object { $_.SourceAppUserModelId -match 'Spotify' } | Select-Object -First 1
    if (-not $p) {
        $cur = $mgr.GetCurrentSession()
        if ($cur -and $cur.SourceAppUserModelId -match $browsers) { $p = $cur }
    }
    if (-not $p) { $p = $sessions | Where-Object { $_.SourceAppUserModelId -match $browsers } | Select-Object -First 1 }
    return $p
}

# Commands from Minecraft arrive on standard input: toggle, next, previous.
$stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput())
$pendingCmd = $stdin.ReadLineAsync()
$nextReport = [DateTime]::MinValue

while ($true) {
    if ($ParentId -gt 0 -and -not (Get-Process -Id $ParentId -ErrorAction SilentlyContinue)) { exit 0 }
    if ($pendingCmd -and $pendingCmd.IsCompleted) {
        $cmd = $pendingCmd.Result
        if ($null -eq $cmd) { $pendingCmd = $null; continue }   # no more commands coming
        try {
            $s = Pick-Session
            if ($s) {
                switch ($cmd.Trim()) {
                    'toggle' { $null = Await ($s.TryTogglePlayPauseAsync()) ([bool]) }
                    'next' { $null = Await ($s.TrySkipNextAsync()) ([bool]) }
                    'previous' { $null = Await ($s.TrySkipPreviousAsync()) ([bool]) }
                }
            }
        } catch { }
        "{`"ack`":`"$($cmd.Trim())`"}" | ForEach-Object { [Console]::Out.WriteLine($_) }
        $pendingCmd = $stdin.ReadLineAsync()
        Start-Sleep -Milliseconds 150
        $nextReport = [DateTime]::MinValue   # report the new state right away
    }
    if ([DateTime]::UtcNow -lt $nextReport) { Start-Sleep -Milliseconds 100; continue }
    $nextReport = [DateTime]::UtcNow.AddMilliseconds(1000)
    $out = @{ playing = $false; title = ''; source = '' }
    try {
        $pick = Pick-Session
        if ($pick) {
            $props = Await ($pick.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $info = $pick.GetPlaybackInfo()
            $tl = $pick.GetTimelineProperties()
            $out.source = if ($pick.SourceAppUserModelId -match 'Spotify') { 'app' } else { 'browser' }
            $out.title = [string]$props.Title
            $out.artist = [string]$props.Artist
            if (-not $out.artist) { $out.artist = [string]$props.AlbumArtist }
            $out.album = [string]$props.AlbumTitle
            $out.playing = ($info.PlaybackStatus -eq [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionPlaybackStatus]::Playing)
            $out.positionMs = [long]$tl.Position.TotalMilliseconds
            $out.durationMs = [long]($tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds)
            $out.updatedMs = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
            $out.nowMs = [long][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
            $key = "$($out.title)|$($out.artist)|$($out.album)"
            # The cover can arrive a moment after the title changes, so send it for a few seconds after each change.
            if ($key -ne $lastKey) { $lastKey = $key; $artTries = 4 }
            if ($artTries -gt 0 -and $props.Thumbnail) {
                $artTries--
                try {
                    $stream = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $net = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($stream)
                    $ms = New-Object System.IO.MemoryStream
                    $net.CopyTo($ms)
                    $out.art = [Convert]::ToBase64String($ms.ToArray())
                    $net.Dispose()
                } catch { }
            }
        }
    } catch {
        $out.error = $_.Exception.Message
    }
    [Console]::Out.WriteLine(($out | ConvertTo-Json -Compress))
    [Console]::Out.Flush()
}
