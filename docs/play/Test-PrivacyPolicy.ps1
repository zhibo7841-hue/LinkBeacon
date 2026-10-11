param(
    [switch]$RequirePublishable
)

$ErrorActionPreference = 'Stop'
$privacyRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$privacySiteRoot = Join-Path $privacyRoot 'site'
$privacyExpectedFiles = @('privacy/index.html', 'privacy/policy.css', 'privacy/zh-CN/index.html')
$privacyActualFiles = @(Get-ChildItem -LiteralPath $privacySiteRoot -Recurse -File |
    ForEach-Object { [System.IO.Path]::GetRelativePath($privacySiteRoot, $_.FullName).Replace('\', '/') } |
    Sort-Object)
if (($privacyExpectedFiles -join '|') -ne ($privacyActualFiles -join '|')) {
    throw 'Public artifact must contain exactly the two policy pages and their stylesheet.'
}
if (@(Get-ChildItem -LiteralPath $privacySiteRoot -Recurse -Force |
    Where-Object { $_.Attributes -band [System.IO.FileAttributes]::ReparsePoint }).Count -gt 0) {
    throw 'Do not follow links into private files from the public site.'
}

$privacyDocuments = @()
$privacyStrictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)
foreach ($privacyRelativePath in @('privacy/index.html', 'privacy/zh-CN/index.html')) {
    $privacyFile = Join-Path $privacySiteRoot $privacyRelativePath
    $privacySource = $privacyStrictUtf8.GetString([System.IO.File]::ReadAllBytes($privacyFile))
    [xml]$privacyDocument = $privacySource
    $privacyDocuments += $privacyDocument
    if ($privacyDocument.html.head.meta[0].charset -ne 'UTF-8') { throw 'Missing UTF-8 metadata.' }
    if ($privacyDocument.SelectNodes('//meta[@name="viewport"]').Count -ne 1) { throw 'Missing responsive viewport.' }
    if ($privacyDocument.SelectNodes('//h1').Count -ne 1) { throw 'Expected one clear policy heading.' }
    if ($privacyDocument.SelectNodes('//article/section').Count -ne 16) { throw 'Expected all 16 policy chapters.' }
    if ($privacyDocument.SelectNodes('//script|//img|//iframe|//form|//audio|//video|//object').Count -ne 0) {
        throw 'Policy pages must not load trackers, scripts, embeds or submission forms.'
    }
    if ($privacySource.Contains('[PUBLIC_SUPPORT_EMAIL_TO_CONFIRM]')) { throw 'Unresolved support email.' }
    $privacyArticle = $privacyDocument.SelectSingleNode('//article')
    if ($privacyArticle.InnerText -match 'PUBLIC_SUPPORT_EMAIL_TO_CONFIRM|Pending Maintainer Confirmation|TODO|FIXME|PLACEHOLDER|测试占位') {
        throw 'Internal review placeholders must not appear in the policy article.'
    }
    $privacyMailLinks = @($privacyDocument.SelectNodes('//a[starts-with(@href,"mailto:")]'))
    if ($privacyMailLinks.Count -ne 3 -or @($privacyMailLinks |
        Where-Object { $_.href -ne 'mailto:lydd8086@gmail.com' }).Count -ne 0) {
        throw 'Unexpected or inconsistent public contact.'
    }
    foreach ($privacyLink in $privacyDocument.SelectNodes('//a[@href]|//link[@href]')) {
        $privacyHref = [string]$privacyLink.href
        if ($privacyHref -match '^mailto:') { continue }
        if ($privacyHref -match '^https:') {
            if ($privacyLink.Name -ne 'a' -or $privacyHref -ne 'https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement') {
                throw 'Unexpected third-party resource or external destination.'
            }
            continue
        }
        if ($privacyHref -match '^[a-zA-Z][a-zA-Z0-9+.-]*:|^//') { throw 'Unexpected URL scheme.' }
        $privacyDestination = [System.IO.Path]::GetFullPath((Join-Path (Split-Path $privacyFile) $privacyHref))
        if (-not $privacyDestination.StartsWith($privacySiteRoot + [System.IO.Path]::DirectorySeparatorChar)) {
            throw 'Link escapes public site.'
        }
        if (-not (Test-Path -LiteralPath $privacyDestination)) { throw 'Broken local link.' }
        if ((Get-Item -LiteralPath $privacyDestination).PSIsContainer -and
            -not (Test-Path -LiteralPath (Join-Path $privacyDestination 'index.html'))) {
            throw 'Language destination has no policy page.'
        }
    }
    if ($RequirePublishable -and $privacySource -match 'review-notice|\b(Draft|Pending|Preview)\b|尚未发布|等待维护者批准') {
        throw 'Publication blocked: review-only wording remains.'
    }
    if ($RequirePublishable -and $privacyDocument.SelectSingleNode('//time').datetime -ne '2026-10-11') {
        throw 'Publication date differs from the maintainer-approved date.'
    }
    [pscustomobject]@{
        File = $privacyRelativePath
        Language = $privacyDocument.html.lang
        Chapters = 16
        EffectiveDate = $privacyDocument.SelectSingleNode('//time').datetime
        SHA256 = (Get-FileHash -LiteralPath $privacyFile -Algorithm SHA256).Hash
    }
}
if ($privacyDocuments[0].html.lang -ne 'en' -or $privacyDocuments[1].html.lang -ne 'zh-CN') {
    throw 'Incorrect language attributes.'
}
if (($privacyDocuments[0].SelectNodes('//article/section').id -join '|') -ne
    ($privacyDocuments[1].SelectNodes('//article/section').id -join '|')) {
    throw 'Bilingual chapter structure mismatch.'
}
if ($privacyDocuments[0].SelectSingleNode('//time').datetime -ne $privacyDocuments[1].SelectSingleNode('//time').datetime) {
    throw 'Bilingual effective dates differ.'
}
if ($privacyDocuments[0].SelectSingleNode('//time').InnerText -ne 'October 11, 2026' -or
    $privacyDocuments[1].SelectSingleNode('//time').InnerText -ne '2026 年 10 月 11 日') {
    throw 'Unexpected effective-date text.'
}
$privacyCss = Get-Content -LiteralPath (Join-Path $privacySiteRoot 'privacy/policy.css') -Raw -Encoding utf8
if ($privacyCss -match '@import|url\(') { throw 'Unexpected remote stylesheet dependency.' }
if (-not $privacyCss.Contains('prefers-color-scheme: dark')) { throw 'Missing dark color support.' }
Write-Output 'PASS: isolated artifact, UTF-8, responsive metadata, chapter/date/contact parity, links, no article placeholders and no external scripts/assets.'
if (-not $RequirePublishable) { Write-Output 'Preview checks do not grant publication approval.' }
