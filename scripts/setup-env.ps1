<#
.SYNOPSIS
  Interactive developer environment doctor and setup script on Windows.
.DESCRIPTION
  Automatically verifies prerequisites:
  - Git & Submodules
  - Git hooks (.githooks)
  - JDK 21 & JAVA_HOME
  - Android SDK, ANDROID_HOME & platform-tools (adb)
  - Connected Android devices/emulators
  - Python 3.10+ & uv
  - GitHub CLI login and repo access
  - ARTEMIS clone, API key and MCP registration
  - ARTEMIS model: Gemini (local Qwen fallback) or local Qwen only, sized to your GPU
  - ktfmt (fetched by scripts/ci-local.sh)
  - Links .agents/skills/ into .claude/skills/ so Claude Code finds the skills
  
  Offers interactive automated fixes and provides exact environment variable instructions.
#>

param(
  [switch]$NonInteractive,
  # Which model ARTEMIS uses: 'gemini' (local Qwen as quota fallback) or 'qwen' (local only).
  [ValidateSet('gemini', 'qwen')][string]$ArtemisModel,
  # Decision model for the agents: 'nimble', 'tev1' (4B or 0.8B by GPU), 'laya', 'jev' (hosted only)
  # or 'none'. Default: by hardware.
  [ValidateSet('nimble', 'tev1', 'laya', 'jev', 'none')][string]$DecisionModel
)

$ErrorActionPreference = "Continue"

function Write-Header ($text) {
  Write-Host ""
  Write-Host "==================================================================" -ForegroundColor Cyan
  Write-Host "  $text" -ForegroundColor White
  Write-Host "==================================================================" -ForegroundColor Cyan
}

function Write-Pass ($text) {
  Write-Host "  [OK] $text" -ForegroundColor Green
}

function Write-Warn ($text) {
  Write-Host "  [WARN] $text" -ForegroundColor Yellow
}

function Write-Fail ($text) {
  Write-Host "  [FAIL] $text" -ForegroundColor Red
}

function Write-Info ($text) {
  Write-Host "  [INFO] $text" -ForegroundColor DarkGray
}

function Prompt-Fix ($promptText) {
  if ($NonInteractive) { return $false }
  Write-Host ""
  $response = Read-Host "  --> $promptText (y/N)"
  return ($response -eq 'y' -or $response -eq 'Y')
}

$repoRoot = (Resolve-Path "$PSScriptRoot\..").Path
$kit = @{ APP_MODULE = ":app"; APP_VARIANT = "Debug"; NDK_VERSION = ""; GRADLE_DIR = "." }
$kitFile = Join-Path $repoRoot "agent-kit.env"
if (Test-Path $kitFile) { Get-Content $kitFile | ForEach-Object { if ($_ -match '^\s*([A-Z_]+)=(.*)$') { $kit[$Matches[1]] = $Matches[2].Trim('"') } } }
Set-Location $repoRoot

Write-Header "Android Agent Kit - Interactive Developer Environment Setup"
Write-Info "Repository root: $repoRoot"
Write-Info "Platform: Windows $([System.Environment]::OSVersion.Version)"

$issuesFound = 0

# -------------------------------------------------------------
# 1. Git & Submodules
# -------------------------------------------------------------
Write-Header "1. Git Configuration & Submodules"
$gitCmd = Get-Command git -ErrorAction SilentlyContinue
if (-not $gitCmd) {
  Write-Fail "Git is not installed or not in PATH."
  Write-Info "Install Git for Windows from: https://git-scm.com/download/win"
  $issuesFound++
} else {
  Write-Pass "Git is installed: $($gitCmd.Source)"

  # Long paths: some submodules exceed MAX_PATH on Windows.
  if ((git config core.longpaths) -ne "true") {
    Write-Warn "git core.longpaths is not enabled (submodule checkouts can fail with 'Filename too long')."
    if (Prompt-Fix "Enable git core.longpaths globally now?") {
      git config --global core.longpaths true
      Write-Pass "Enabled git core.longpaths."
    } else {
      Write-Info "Manual command: git config --global core.longpaths true"
      $issuesFound++
    }
  } else {
    Write-Pass "git core.longpaths is enabled."
  }

  if ((Test-Path (Join-Path $repoRoot ".gitmodules")) -and ((git submodule status) -match '^-')) {
    Write-Warn "git submodules are not initialized."
    if (Prompt-Fix "Initialize git submodules now?") { git submodule update --init --recursive }
    else { Write-Info "Manual command: git submodule update --init --recursive"; $issuesFound++ }
  }

  # Check git hooks path
  $hooksPath = git config core.hooksPath
  if ($hooksPath -ne ".githooks") {
    Write-Warn "Git hooks path is not set to .githooks (currently: '$hooksPath')"
    if (Prompt-Fix "Set git core.hooksPath to .githooks now?") {
      git config core.hooksPath .githooks
      Write-Pass "Configured git core.hooksPath to .githooks."
    } else {
      Write-Info "Manual command: git config core.hooksPath .githooks"
    }
  } else {
    Write-Pass "Git hooks path is configured (.githooks)."
  }
}

# -------------------------------------------------------------
# 2. Java / JDK 21
# -------------------------------------------------------------
Write-Header "2. Java Development Kit (JDK 21)"
$javaHome = $env:JAVA_HOME
if ([string]::IsNullOrWhiteSpace($javaHome)) {
  $machineJavaHome = [System.Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
  $userJavaHome = [System.Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
  $javaHome = if ($machineJavaHome) { $machineJavaHome } else { $userJavaHome }
}

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if ($javaCmd) {
  $javaVerOutput = & java -version 2>&1 | Out-String
  Write-Pass "Java executable found: $($javaCmd.Source)"
  Write-Info ($javaVerOutput.Trim() -split "`n")[0]
  if ([string]::IsNullOrWhiteSpace($javaHome)) {
    $parentDir = Split-Path (Split-Path $javaCmd.Source -Parent) -Parent
    if (Test-Path "$parentDir\release") {
      $javaHome = $parentDir
    }
  }
} else {
  Write-Fail "Java executable not found in PATH."
  $issuesFound++
}

if (-not [string]::IsNullOrWhiteSpace($javaHome) -and (Test-Path $javaHome)) {
  Write-Pass "JAVA_HOME is set: $javaHome"
} else {
  Write-Warn "JAVA_HOME is not set or directory does not exist."
  Write-Info "To set JAVA_HOME permanently on Windows PowerShell, run:"
  Write-Host '    [System.Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Path\To\jdk-21", "User")' -ForegroundColor Yellow
  Write-Host '    [System.Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:JAVA_HOME\bin", "User")' -ForegroundColor Yellow
  $issuesFound++
}

# -------------------------------------------------------------
# 3. Android SDK (and NDK if agent-kit.env sets one)
# -------------------------------------------------------------
Write-Header "3. Android SDK (and NDK if agent-kit.env sets one)"
$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidHome)) {
  $androidHome = $env:ANDROID_SDK_ROOT
}
if ([string]::IsNullOrWhiteSpace($androidHome) -and (Test-Path "$repoRoot\$($kit.GRADLE_DIR)\local.properties")) {
  $localPropLine = Get-Content "$repoRoot\$($kit.GRADLE_DIR)\local.properties" | Where-Object { $_ -match "^sdk\.dir=(.*)$" }
  if ($localPropLine) {
    $rawPath = ($localPropLine -replace "^sdk\.dir=", "").Trim()
    $cleanPath = $rawPath.Replace("\:", ":").Replace("\\", "\")
    if (Test-Path $cleanPath) {
      $androidHome = $cleanPath
    }
  }
}
if ([string]::IsNullOrWhiteSpace($androidHome)) {
  $defaultSdk = "$env:LOCALAPPDATA\Android\Sdk"
  if (Test-Path $defaultSdk) {
    $androidHome = $defaultSdk
  }
}

if (-not [string]::IsNullOrWhiteSpace($androidHome) -and (Test-Path $androidHome)) {
  Write-Pass "Android SDK located at: $androidHome"
  
  # Check platform-tools (adb)
  $adbExe = Join-Path $androidHome "platform-tools\adb.exe"
  $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
  if ($adbCmd) {
    Write-Pass "adb is available in PATH: $($adbCmd.Source)"
  } elseif (Test-Path $adbExe) {
    Write-Warn "adb.exe exists at $adbExe, but platform-tools is not in PATH."
    Write-Info "Add to PATH with:"
    Write-Host "    [System.Environment]::SetEnvironmentVariable('Path', `$env:Path + ';$androidHome\platform-tools', 'User')" -ForegroundColor Yellow
  } else {
    Write-Fail "adb.exe not found in platform-tools. Install Android SDK Platform-Tools via Android Studio SDK Manager."
    $issuesFound++
  }

  # Check required NDK version
  $requiredNdk = $kit.NDK_VERSION
  $ndkDir = Join-Path $androidHome "ndk\$requiredNdk"
  if (-not $requiredNdk) {
    Write-Info "No NDK_VERSION in agent-kit.env; skipping the NDK check."
  } elseif (Test-Path $ndkDir) {
    Write-Pass "Required Android NDK ($requiredNdk) found at $ndkDir"
  } else {
    Write-Warn "Android NDK $requiredNdk not found at $ndkDir."
    Write-Info "Install via Android Studio: Settings -> SDK Manager -> SDK Tools -> NDK (Side by side) -> Show Package Details -> $requiredNdk"
    Write-Info "Or CLI: & `"$androidHome\cmdline-tools\latest\bin\sdkmanager.bat`" `"ndk;$requiredNdk`""
  }
} else {
  Write-Fail "ANDROID_HOME / Android SDK was not found."
  Write-Info "Install Android Studio and set ANDROID_HOME:"
  Write-Host '    [System.Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")' -ForegroundColor Yellow
  $issuesFound++
}

# -------------------------------------------------------------
# 4. Connected Android Devices & Emulators
# -------------------------------------------------------------
Write-Header "4. Connected Android Devices / Emulators"
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if ($adbCmd) {
  $deviceLines = & adb devices -l | Where-Object { $_ -match "^\S+\s+(device|unauthorized|offline)" }
  if ($deviceLines) {
    Write-Pass "Detected active Android device(s):"
    foreach ($d in $deviceLines) {
      Write-Host "    $d" -ForegroundColor Green
    }
  } else {
    Write-Warn "No Android devices or emulators currently connected."
    Write-Info "Connect a physical phone via USB/Wi-Fi (with USB Debugging enabled) or start an Android Virtual Device (AVD)."
  }
}

# -------------------------------------------------------------
# 5. Python & uv
# -------------------------------------------------------------
Write-Header "5. Python 3.10+ and uv Package Manager"
$pythonCmd = Get-Command python -ErrorAction SilentlyContinue
if ($pythonCmd) {
  $pyVer = & python --version 2>&1
  Write-Pass "Python is installed: $pyVer ($($pythonCmd.Source))"
} else {
  Write-Warn "Python was not found in PATH."
  Write-Info "Install Python 3.11+ from https://www.python.org/downloads/ or via winget: winget install Python.Python.3.12"
}

$uvCmd = Get-Command uv -ErrorAction SilentlyContinue
if ($uvCmd) {
  Write-Pass "uv package manager is installed: $($uvCmd.Source)"
} else {
  Write-Warn "uv is not installed. uv is used for Artemis mobile testing automation."
  if (Prompt-Fix "Install uv now via official PowerShell installer?") {
    powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"
  } else {
    Write-Info "To install uv manually: powershell -ExecutionPolicy ByPass -c `"irm https://astral.sh/uv/install.ps1 | iex`""
  }
}

# -------------------------------------------------------------
# 6. GitHub CLI (issue -> PR workflow)
# -------------------------------------------------------------
Write-Header "6. GitHub CLI"
$ghCmd = Get-Command gh -ErrorAction SilentlyContinue
if (-not $ghCmd) {
  Write-Fail "gh is not installed. The bug -> PR workflow uses it to read issues and open PRs."
  Write-Info "Install: winget install --id GitHub.cli   then: gh auth login"
  $issuesFound++
} else {
  Write-Pass "gh is installed: $($ghCmd.Source)"
  # 'gh auth status' also fails on a stale inactive account; test the active one instead.
  $ghUser = (& gh api user -q .login 2>$null | Out-String).Trim()
  if ($LASTEXITCODE -ne 0 -or -not $ghUser) {
    Write-Fail "gh is not logged in. Run: gh auth login"
    $issuesFound++
  } else {
    $perm = (& gh repo view --json viewerPermission -q .viewerPermission 2>$null | Out-String).Trim()
    if ($perm -in @("ADMIN", "MAINTAIN", "WRITE")) {
      Write-Pass "gh is logged in as $ghUser with $perm access to this repo."
    } else {
      Write-Warn "gh is logged in but has '$perm' access here - you cannot push fix branches."
      Write-Info "Ask a maintainer for write access to the repository."
    }
  }
}

. "$PSScriptRoot\setup-env-models.ps1"

# -------------------------------------------------------------
# 9. Kotlin Formatting (ktfmt)
# -------------------------------------------------------------
Write-Header "9. Kotlin Formatting (ktfmt)"
Write-Pass "Nothing to install: 'bash scripts/ci-local.sh --fix' downloads the ktfmt version CI uses."
if (Get-Command ktfmt -ErrorAction SilentlyContinue) {
  Write-Warn "A ktfmt is on your PATH - don't use it for this repo; its version may disagree with CI."
}

# -------------------------------------------------------------
# 10. Agent Skills for Claude Code
# -------------------------------------------------------------
# Skills live in .agents/skills/ (Gemini CLI reads it). Claude Code only reads .claude/skills/,
# so link each skill there. Junctions need no admin rights or Developer Mode.
Write-Header "10. Agent Skills for Claude Code"
$claudeSkills = Join-Path $repoRoot ".claude\skills"
New-Item -ItemType Directory -Force -Path $claudeSkills | Out-Null
$linked = 0
foreach ($skill in Get-ChildItem -Directory (Join-Path $repoRoot ".agents\skills")) {
  $link = Join-Path $claudeSkills $skill.Name
  if (Test-Path $link) { continue }
  New-Item -ItemType Junction -Path $link -Target $skill.FullName | Out-Null
  $linked++
}
Write-Pass "Claude Code sees every skill in .agents/skills/ ($linked new link(s) in .claude/skills/)."

# -------------------------------------------------------------
# Summary & Environment Variables Guide
# -------------------------------------------------------------
Write-Header "Environment Variables Reference Guide"
Write-Host "  To permanently set environment variables on Windows (run in Administrator PowerShell):" -ForegroundColor Cyan
Write-Host "    [System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-21', 'User')"
Write-Host "    [System.Environment]::SetEnvironmentVariable('ANDROID_HOME', '$env:LOCALAPPDATA\Android\Sdk', 'User')"
Write-Host '    [System.Environment]::SetEnvironmentVariable(''Path'', $env:Path + ";$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools", ''User'')'
Write-Host ""
Write-Host "  Alternatively, use GUI: press Win+R -> sysdm.cpl -> Advanced -> Environment Variables" -ForegroundColor DarkGray
Write-Host ""

Write-Header "Setup Doctor Summary"
if ($issuesFound -eq 0) {
  Write-Host "  ALL ESSENTIAL PREREQUISITES ARE MET! You are ready to build." -ForegroundColor Green
  Write-Host "  Next steps:" -ForegroundColor White
  Write-Host "    1. Build app:    cd $($kit.GRADLE_DIR); .\gradlew.bat :$($kit.APP_MODULE.TrimStart(':')):assemble$($kit.APP_VARIANT) --no-daemon --console=plain" -ForegroundColor Cyan
  Write-Host "    2. Run tests:    cd $($kit.GRADLE_DIR); .\gradlew.bat :$($kit.APP_MODULE.TrimStart(':')):test$($kit.APP_VARIANT)UnitTest --no-daemon --console=plain" -ForegroundColor Cyan
  Write-Host "    3. Format code:  bash scripts/ci-local.sh --fix" -ForegroundColor Cyan
} else {
  Write-Host "  Found $issuesFound issue(s) that need attention. Please review the warnings above." -ForegroundColor Yellow
}
Write-Host ""
