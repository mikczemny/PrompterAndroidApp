param(
    [string]$OutputDirectory = (Join-Path $env:TEMP "prompter-model-checksums")
)

$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$models = @(
    "vosk-model-small-en-us-0.15.zip",
    "vosk-model-small-es-0.42.zip",
    "vosk-model-small-cn-0.22.zip",
    "vosk-model-small-pl-0.22.zip",
    "vosk-model-small-fr-0.22.zip",
    "vosk-model-small-de-0.15.zip",
    "vosk-model-small-it-0.22.zip",
    "vosk-model-small-pt-0.3.zip",
    "vosk-model-small-ru-0.22.zip",
    "vosk-model-small-hi-0.22.zip",
    "vosk-model-small-ja-0.22.zip"
)

foreach ($model in $models) {
    $destination = Join-Path $OutputDirectory $model
    if (-not (Test-Path -LiteralPath $destination)) {
        curl.exe --fail --location --retry 3 --output $destination `
            "https://alphacephei.com/vosk/models/$model"
    }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant()
    "$model=$hash"
}
