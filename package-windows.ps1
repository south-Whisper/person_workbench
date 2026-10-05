param(
  [string]$Version = "v1.0.4",
  [string]$OutputDirectory = (Join-Path $PSScriptRoot "packages")
)
$ErrorActionPreference = "Stop"
$required = @(".env.docker", "back/config/mail-secret.yml", "compose.yaml", "start-system.cmd", "START-HERE.txt")
foreach ($item in $required) {
  if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $item))) { throw "打包缺少文件：$item" }
}
$mailLine = Get-Content -LiteralPath (Join-Path $PSScriptRoot ".env.docker") | Where-Object { $_ -like "SETHUB_MAIL_PASSWORD=*" } | Select-Object -First 1
if (-not $mailLine -or $mailLine -eq "SETHUB_MAIL_PASSWORD=") { throw "发件邮箱授权码尚未配置" }
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$archive = Join-Path $OutputDirectory ("person_workbench-{0}-{1}.zip" -f $Version, $stamp)
& tar.exe -a -c -f $archive --exclude=.git --exclude=front/node_modules --exclude=front/dist --exclude=back/target --exclude=packages --exclude=.idea --exclude=test-output --exclude=启动系统.cmd .
if ($LASTEXITCODE -ne 0) { throw "压缩失败，退出代码：$LASTEXITCODE" }
Write-Host "安装包已生成：$archive"
Write-Host "这个安装包包含邮件和数据库配置，只能私下保存，不要上传网盘或公开仓库。"
