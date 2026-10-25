# Dot-sourced by scripts/setup-env.ps1: sections 7-8b (ARTEMIS and the local decision model).
# Uses the helpers and variables setup-env.ps1 defines; do not run it on its own.

# -------------------------------------------------------------
# 7. ARTEMIS (on-device testing via MCP)
# -------------------------------------------------------------
Write-Header "7. ARTEMIS Mobile Testing"
$artemisHome = if ($env:ARTEMIS_HOME) { $env:ARTEMIS_HOME } else { Join-Path (Split-Path $repoRoot -Parent) "artemis" }
# Not beside the repo: use the clone the ARTEMIS MCP server is already registered from.
if (-not $env:ARTEMIS_HOME -and -not (Test-Path (Join-Path $artemisHome "mcp_server")) -and (Get-Command claude -ErrorAction SilentlyContinue)) {
  $mcpPath = (& claude mcp get artemis 2>$null | Select-String -Pattern '^\s*PYTHONPATH=(.+)$' | Select-Object -First 1)
  if ($mcpPath) { $artemisHome = $mcpPath.Matches[0].Groups[1].Value.Trim() }
}
$artemisEnv = Join-Path $artemisHome ".env"
if (-not (Test-Path (Join-Path $artemisHome "mcp_server"))) {
  Write-Fail "ARTEMIS not found at $artemisHome (set ARTEMIS_HOME if it lives elsewhere)."
  if (Prompt-Fix "Clone google/artemis to $artemisHome now?") {
    git clone https://github.com/google/artemis.git $artemisHome
  }
  Write-Info "Then: cd $artemisHome; .\start.bat; uv run artemis mcp --install claude"
  $issuesFound++
} else {
  Write-Pass "ARTEMIS found at $artemisHome"

  if (Test-Path (Join-Path $artemisHome ".venv")) {
    Write-Pass "ARTEMIS virtualenv exists."
  } else {
    Write-Fail "ARTEMIS dependencies are not installed. Run: cd $artemisHome; .\start.bat"
    $issuesFound++
  }

  $hasKey = (Test-Path $artemisEnv) -and (Select-String -Path $artemisEnv -Pattern '^\s*(GEMINI_API_KEY|GOOGLE_API_KEY)\s*=\s*\S+' -Quiet)
  if ($hasKey) {
    Write-Pass "Gemini API key is set in $artemisEnv"
  } else {
    Write-Fail "No GEMINI_API_KEY in $artemisEnv"
    Write-Info "Get a key at https://aistudio.google.com/apikey and add: GEMINI_API_KEY=<key>"
    $issuesFound++
  }

  $claudeCmd = Get-Command claude -ErrorAction SilentlyContinue
  if ($claudeCmd) {
    & claude mcp get artemis *> $null
    if ($LASTEXITCODE -eq 0) {
      Write-Pass "ARTEMIS MCP server is registered with Claude Code."
    } else {
      Write-Fail "ARTEMIS MCP server is not registered with Claude Code."
      Write-Info "Run: cd $artemisHome; uv run artemis mcp --install claude   (then restart Claude Code)"
      $issuesFound++
    }
  } else {
    Write-Info "Claude Code CLI not found - register ARTEMIS with your agent: uv run artemis mcp --install <client>"
  }
  Write-Info "Final check from your agent: mobile_diagnose  (verdict must be 'ready' or 'degraded')"
}

# -------------------------------------------------------------
# 8. ARTEMIS model: Gemini (with local fallback) or local Qwen only
# -------------------------------------------------------------
Write-Header "8. ARTEMIS Model (Gemini / local Qwen)"
# Win32_VideoController.AdapterRAM caps at 4 GB, so only nvidia-smi gives real VRAM.
$vramGb = 0
$gpuName = "no NVIDIA GPU detected"
if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
  $gpus = & nvidia-smi --query-gpu=name,memory.total --format=csv,noheader,nounits 2>$null
  foreach ($g in $gpus) {
    $parts = $g -split ",\s*"
    $gb = [math]::Round([double]$parts[1] / 1024, 1)
    if ($gb -gt $vramGb) { $vramGb = $gb; $gpuName = $parts[0] }
  }
}
$ramGb = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)
Write-Info "Hardware: $gpuName ($vramGb GB VRAM), $ramGb GB RAM"

# Instruct (non-thinking) variants: an agent loop needs short, predictable replies.
# num_ctx is capped so model + context fit in VRAM; Ollama's default (the model's full
# 256K window) spills to CPU and is ~10x slower.
$baseModel = $null; $tier = $null; $numCtx = 32768
if ($vramGb -ge 24) { $baseModel = "qwen3-vl:30b-a3b-instruct"; $tier = "30b" }
elseif ($vramGb -ge 10) { $baseModel = "qwen3-vl:8b-instruct"; $tier = "8b" }
elseif ($vramGb -ge 6) { $baseModel = "qwen3-vl:4b-instruct"; $tier = "4b"; $numCtx = 16384 }
$localModel = if ($tier) { "qwen3-vl-artemis:$tier" } else { $null }

$modelScript = Join-Path $repoRoot "scripts\artemis_model.py"
$haveArtemis = Test-Path (Join-Path $artemisHome "mcp_server")
$haveLocal = $false

if (-not $localModel) {
  Write-Info "Under 6 GB VRAM: a local vision model is too slow for ARTEMIS; ARTEMIS stays on Gemini."
  Write-Info "For quota relief, add a second cloud key (e.g. OPEN_ROUTER_API_KEY) instead."
} else {
  Write-Pass "Local model for your GPU: $baseModel -> $localModel (context $numCtx)"
  $ollamaCmd = Get-Command ollama -ErrorAction SilentlyContinue
  $tags = $null
  if ($ollamaCmd) {
    try { $tags = Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -TimeoutSec 3 -ErrorAction Stop } catch {}
  }
  if (-not $ollamaCmd) {
    Write-Info "Ollama not installed (optional). Get it from https://ollama.com/download/windows"
  } elseif (-not $tags) {
    Write-Warn "Ollama is installed but not running. Start it from the Start menu, then re-run."
  } elseif ($tags.models.name -contains $localModel) {
    Write-Pass "$localModel is ready in Ollama."
    $haveLocal = $true
  } elseif (Prompt-Fix "Download $baseModel (several GB) and create $localModel?") {
    & ollama pull $baseModel
    $modelfile = Join-Path $env:TEMP "artemis.Modelfile"
    "FROM $baseModel`nPARAMETER num_ctx $numCtx`nPARAMETER temperature 0" | Set-Content -Encoding ascii $modelfile
    & ollama create $localModel -f $modelfile
    $haveLocal = ($LASTEXITCODE -eq 0)
  } else {
    Write-Info "Later: ollama pull $baseModel, then re-run this script to create $localModel."
  }
}

if ($haveArtemis -and $haveLocal) {
  $mode = $ArtemisModel
  if (-not $mode -and -not $NonInteractive) {
    Write-Host ""
    Write-Host "  Which model should ARTEMIS drive the device with?" -ForegroundColor White
    Write-Host "    g) Gemini, with $localModel taking over when the quota runs out  (recommended)"
    Write-Host "    q) $localModel only - no Gemini calls, no quota, slower and less capable"
    Write-Host "    Enter) keep the current setting"
    $answer = Read-Host "  -->"
    if ($answer -eq 'g') { $mode = "gemini" } elseif ($answer -eq 'q') { $mode = "qwen" }
  }
  if ($mode) {
    & python $modelScript $mode --artemis-home $artemisHome --model $localModel
  } else {
    Write-Info ((& python $modelScript status --artemis-home $artemisHome) -join " ")
  }
  Write-Info "Switch any time: python scripts/artemis_model.py gemini|qwen|status"
}

# -------------------------------------------------------------
# 8b. Local decision model for the agent hooks (Nimble, Tev1 or Laya)
# -------------------------------------------------------------
# Optional. The hooks (scripts/local-model.sh) and the local-model skill ask a System One model quick yes/no
# and pick-one questions instead of reading long text. All run on Ollama 0.35+ except Laya.
# Measured on an RTX 5080 (60 tone samples, 10 log questions):
#   Nimble 9B   ~9 GB VRAM, ~8K-token window, tone 55/60, logs 10/10
#   Tev1 4B     ~4.7 GB,    ~2K-token window, tone 55/60, logs 10/10
#   Tev1 0.8B   ~0.9 GB,    ~2K-token window, tone 46/60, logs 10/10
#   Laya (pip)  ~1.3 GB,    ~512 tokens,      tone 46/60
# Jev (TypeSafe, hosted, paid per token) reads ~28K tokens; it is only used for text too long for
# the local model, never by the hooks. All of it is optional.
Write-Header "8b. Decision Model for Agents (Nimble / Tev1 / Laya local, Jev hosted)"
$tevModel = if ($vramGb -ge 6) { "tev1:4b" } else { "tev1:0.8b" }
$recommended = if ($vramGb -ge 12) { "nimble" } elseif ($vramGb -ge 2 -or $ramGb -ge 8) { "tev1" } else { "jev" }
$recText = @{ nimble = "Nimble + Jev"; tev1 = "$tevModel + Jev"; jev = "Jev only" }[$recommended]
Write-Info "Recommended for this PC ($vramGb GB VRAM, $ramGb GB RAM): $recText (Jev optional)."
$decision = $DecisionModel
if (-not $decision -and -not $NonInteractive) {
  Write-Host ""
  Write-Host "  Which decision model should the agents use?" -ForegroundColor White
  Write-Host "    n) Nimble - local, free, needs ~9 GB VRAM, reads ~6K tokens$(if ($recommended -eq 'nimble') { '  (recommended)' })"
  Write-Host "    t) $tevModel - local, free, $(if ($tevModel -eq 'tev1:4b') { '~5 GB VRAM' } else { '~1 GB' }), reads ~1.5K tokens$(if ($recommended -eq 'tev1') { '  (recommended)' })"
  Write-Host "    l) Laya   - local, free, pip server, reads ~400 tokens"
  Write-Host "    j) Jev only - hosted, paid per token, no local model$(if ($recommended -eq 'jev') { '  (recommended)' })"
  Write-Host "    s) skip - no decision model; hooks stay off"
  Write-Host "    Enter) $recText"
  $answer = Read-Host "  -->"
  $decision = switch ($answer) { 'n' { "nimble" } 't' { "tev1" } 'l' { "laya" } 'j' { "jev" } 's' { "none" } default { $recommended } }
}
if (-not $decision) { $decision = $recommended }
if ($decision -eq "none") { $decision = $null }

$ollamaModel = $null
# The hooks read these per-user variables; clear what the other choices set (and the
# NIMBLE_* names they had before the local-model rename).
foreach ($v in "LOCAL_MODEL_URL", "LOCAL_MODEL_NAME", "LOCAL_MODEL_MAX_BYTES", "LOCAL_MODEL_LOCAL",
    "NIMBLE_URL", "NIMBLE_MODEL", "NIMBLE_MAX_BYTES", "NIMBLE_LOCAL") {
  if ($decision) { [System.Environment]::SetEnvironmentVariable($v, $null, "User") }
}

if (-not $decision) {
  Write-Info "No decision model. The hooks stay off; nothing breaks. Re-run to choose one."
} elseif ($decision -eq "jev") {
  [System.Environment]::SetEnvironmentVariable("LOCAL_MODEL_LOCAL", "0", "User")
  Write-Pass "Jev only: lm-ask goes to Jev; the hooks stay off (they never pay for Jev)."
} elseif ($decision -eq "nimble" -or $decision -eq "tev1") {
  if ($decision -eq "nimble") {
    $ollamaModel = "nimble"
    if ($vramGb -lt 12) { Write-Warn "Nimble needs ~9 GB VRAM while loaded; this PC has $vramGb GB. Expect CPU spill and slow answers." }
    Write-Pass "Nimble ($vramGb GB VRAM; it needs ~9 GB while loaded)."
    if ($localModel -and $vramGb -lt 18) {
      Write-Info "Nimble and $localModel do not both fit in VRAM; Ollama swaps them, so the first hook after an ARTEMIS run is slower."
    }
  } else {
    # Tev1's window is ~2K tokens: send the last 3.6 KB (dense Gradle logs run ~2 bytes a token).
    $ollamaModel = $tevModel
    [System.Environment]::SetEnvironmentVariable("LOCAL_MODEL_NAME", $tevModel, "User")
    [System.Environment]::SetEnvironmentVariable("LOCAL_MODEL_MAX_BYTES", "3600", "User")
    Write-Pass "$tevModel ($vramGb GB VRAM, $ramGb GB RAM). Hooks use it (LOCAL_MODEL_NAME, LOCAL_MODEL_MAX_BYTES set; restart your agent)."
    if ($vramGb -lt 2) { Write-Info "No usable GPU: $tevModel runs on the CPU. Untested here; expect slower answers." }
    Write-Info "Longer text goes to Jev when a key is set; otherwise lm-ask reads only the tail."
  }
  $ollamaCmd = Get-Command ollama -ErrorAction SilentlyContinue
  $ollamaVer = if ($ollamaCmd) { ((& ollama --version 2>$null) -join " ") -replace '.*?(\d+\.\d+\.\d+).*', '$1' } else { $null }
  $tags = $null
  if ($ollamaCmd) {
    try { $tags = Invoke-RestMethod -Uri "http://localhost:11434/api/tags" -TimeoutSec 3 -ErrorAction Stop } catch {}
  }
  if (-not $ollamaCmd) {
    Write-Info "Ollama not installed (optional). Get 0.35.0 or later from https://ollama.com/download/windows"
  } elseif ($ollamaVer -and [version]$ollamaVer -lt [version]"0.35.0") {
    Write-Warn "Ollama $ollamaVer is too old for the /v1/systemone endpoint. Update to 0.35.0 or later."
  } elseif (-not $tags) {
    Write-Warn "Ollama is installed but not running. Start it from the Start menu, then re-run."
  } elseif ($tags.models.name -contains $ollamaModel -or $tags.models.name -contains "${ollamaModel}:latest") {
    Write-Pass "$ollamaModel is ready in Ollama. The hooks use it at http://127.0.0.1:11434."
  } elseif (Prompt-Fix "Download $ollamaModel into Ollama ($(@{ 'nimble' = '~9.5 GB'; 'tev1:4b' = '~4.5 GB'; 'tev1:0.8b' = '~0.8 GB' }[$ollamaModel]))?") {
    & ollama pull $ollamaModel
  } else {
    Write-Info "Later: ollama pull $ollamaModel"
  }
} else {
  $layaEnv = Join-Path $env:USERPROFILE "laya-env"
  $device = if ($vramGb -ge 4) { "cuda" } else { "cpu" }
  Write-Pass "Laya fits this PC ($gpuName, $vramGb GB VRAM, $ramGb GB RAM; runs on $device)."
  if ($device -eq "cpu") { Write-Info "No CUDA GPU: Laya on the CPU is untested here and slower." }
  if (Test-Path (Join-Path $layaEnv "Scripts\laya-serve.exe")) {
    Write-Pass "Laya is installed in $layaEnv."
  } elseif (Prompt-Fix "Create $layaEnv and install Laya with PyTorch (~3 GB download)?") {
    & python -m venv $layaEnv
    $py = Join-Path $layaEnv "Scripts\python.exe"
    & $py -m pip install --upgrade pip
    if ($device -eq "cuda") {
      & $py -m pip install torch --index-url https://download.pytorch.org/whl/cu128
    } else {
      & $py -m pip install torch --index-url https://download.pytorch.org/whl/cpu
    }
    & $py -m pip install "laya[serve]"   # [serve] brings fastapi + uvicorn for laya-serve
  } else {
    Write-Info "Later: re-run this script and answer yes to install Laya."
  }
  $start = Join-Path $layaEnv "start-laya.ps1"
  if ((Test-Path (Join-Path $layaEnv "Scripts\laya-serve.exe")) -and -not (Test-Path $start)) {
    @"
# Starts the Laya System One server on 127.0.0.1:8000 (loopback only). -Background logs to laya-env\logs.
param([switch]`$Background)
`$envDir = Join-Path `$env:USERPROFILE 'laya-env'
`$env:LAYA_HOST = '127.0.0.1'; `$env:LAYA_PORT = '8000'; `$env:LAYA_MODELS = 'english'
`$env:LAYA_DEVICE = '$device'; `$env:LAYA_PRELOAD = '1'; `$env:HF_HUB_DISABLE_SYMLINKS_WARNING = '1'
`$exe = Join-Path `$envDir 'Scripts\laya-serve.exe'
if (`$Background) {
  `$logDir = Join-Path `$envDir 'logs'; New-Item -ItemType Directory -Force -Path `$logDir | Out-Null
  Start-Process -FilePath `$exe -WindowStyle Hidden -RedirectStandardOutput (Join-Path `$logDir 'out.log') -RedirectStandardError (Join-Path `$logDir 'err.log')
} else { & `$exe }
"@ | Set-Content -Encoding utf8 $start
    Write-Pass "Wrote $start"
  }
  # Point the hooks at Laya: same /v1/systemone API, but a 512-token window.
  [System.Environment]::SetEnvironmentVariable("LOCAL_MODEL_URL", "http://127.0.0.1:8000", "User")
  [System.Environment]::SetEnvironmentVariable("LOCAL_MODEL_NAME", "laya", "User")
  [System.Environment]::SetEnvironmentVariable("LOCAL_MODEL_MAX_BYTES", "1800", "User")
  Write-Info "Hooks now use Laya (LOCAL_MODEL_URL, LOCAL_MODEL_NAME, LOCAL_MODEL_MAX_BYTES set for your user; restart your agent)."
  Write-Info "Start it: powershell -ExecutionPolicy Bypass -File `"$start`" -Background"
}

# Jev (TypeSafe, hosted): optional, paid per input token. lm-ask sends text there only when it
# is too long for the local model (or there is none); the hooks never do. The key stays in a
# per-user file, never in the repo.
if ($decision) {
  $jevKeyFile = Join-Path $env:USERPROFILE ".config\typesafe\api_key"
  if ($env:JEV_API_KEY -or (Test-Path $jevKeyFile)) {
    Write-Pass "Jev: a TypeSafe API key is set. Spend is logged in ~\.config\typesafe\usage.log."
  } elseif ($NonInteractive) {
    Write-Info "Jev (optional): no TypeSafe API key. Add one later in $jevKeyFile."
  } else {
    $secure = Read-Host "  --> Paste a TypeSafe API key for Jev (Enter to skip; add it later)" -AsSecureString
    $key = [System.Net.NetworkCredential]::new("", $secure).Password.Trim()
    if ($key) {
      New-Item -ItemType Directory -Force -Path (Split-Path $jevKeyFile) | Out-Null
      Set-Content -Path $jevKeyFile -Value $key -NoNewline -Encoding ascii
      & icacls $jevKeyFile /inheritance:r /grant:r "$($env:USERNAME):(R,W)" *> $null
      Write-Pass "Saved the key to $jevKeyFile (readable by you only)."
    } else {
      Write-Info "Jev skipped. Later: put the key in $jevKeyFile, or set JEV_API_KEY."
    }
  }
  if (Get-Command claude -ErrorAction SilentlyContinue) {
    if (-not (& claude plugin list 2>$null | Select-String -Quiet "typesafe")) {
      if (Prompt-Fix "Install the TypeSafe skill for Claude Code (how to build with Jev)?") {
        & claude plugin marketplace add typesafe-ai/skills
        & claude plugin install typesafe@typesafe-ai
      }
    } else { Write-Pass "TypeSafe skill is installed for Claude Code." }
  }
  Write-Info "Gemini/Antigravity: npx skills add typesafe-ai/skills --skill typesafe-ai (pick your agent)."
}

# jgl = jg (jevgrep) with the local model judging: finds code by meaning, nothing leaves the PC.
if ($decision -and $decision -ne "jev") {
  $haveJg = [bool](Get-Command jg -ErrorAction SilentlyContinue)
  $haveRg = [bool](Get-Command rg -ErrorAction SilentlyContinue)
  if ($haveJg -and $haveRg) {
    Write-Pass "jg and ripgrep are installed; ~/.local-model/jgl searches code by meaning with $decision."
  } elseif (Prompt-Fix "Install jg (npm) and ripgrep (winget) for search by meaning?") {
    if (-not $haveJg) { & npm install -g @remotehost/jg }
    if (-not $haveRg) { & winget install --id BurntSushi.ripgrep.MSVC -e --silent --accept-package-agreements --accept-source-agreements }
    Write-Info "Open a new terminal so PATH picks up rg."
  } else {
    Write-Info "Later: npm install -g @remotehost/jg; winget install BurntSushi.ripgrep.MSVC"
  }
}

# Global skills (local-model, code-graph, headless-emulator) work in every project, so they live in the
# agent-workflow repo (https://github.com/raghavsatyadev/agent-workflow), not here. Its install.sh links
# them into Claude Code, Gemini/Antigravity and Codex skill folders. Installed only when asked.
$missingSkills = @("local-model", "code-graph", "headless-emulator") |
  Where-Object { -not (Test-Path (Join-Path $env:USERPROFILE ".claude\skills\$_\SKILL.md")) }
$awInstall = Join-Path (Split-Path $repoRoot) "agent-workflow\install.sh"
$awHint = "git clone --recurse-submodules https://github.com/raghavsatyadev/agent-workflow.git, then bash agent-workflow/install.sh (Git Bash)"
# Git Bash, not the WSL bash.exe in System32 or WindowsApps, which comes first on some PATHs.
$bash = Get-Command bash -All -ErrorAction SilentlyContinue |
  Where-Object { $_.Source -notmatch '\\(System32|WindowsApps)\\' } | Select-Object -First 1 -ExpandProperty Source
if (-not $bash) {
  $roots = @("$env:ProgramFiles\Git")
  $git = Get-Command git -ErrorAction SilentlyContinue
  if ($git) { $d = Split-Path $git.Source; $roots += (Split-Path $d), (Split-Path (Split-Path $d)) }
  $bash = $roots | ForEach-Object { Join-Path $_ "bin\bash.exe" } | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $missingSkills) {
  Write-Pass "Global skills are installed. Re-run agent-workflow/install.sh after a pull."
} elseif (-not (Test-Path $awInstall)) {
  Write-Warn "Missing global skills: $($missingSkills -join ', '). Install: $awHint"
} elseif (-not $bash) {
  Write-Warn "bash (Git Bash) not found; install Git for Windows, then run: bash $awInstall"
} elseif (Prompt-Fix "Install the global skills ($($missingSkills -join ', ')) from agent-workflow for every project?") {
  & $bash $awInstall
  Write-Pass "Restart your agent. Check: echo hi | bash ~/.local-model/lm-ask yesno `"Is this a greeting?`""
} else {
  Write-Warn "Skipped. Missing global skills: $($missingSkills -join ', '). Later: bash $awInstall"
}
if ($decision) {
  if ($ollamaModel) { Write-Info "Free the GPU after use: bash ~/.local-model/lm-off $ollamaModel (Claude Code does it on session end)." }
}

