@echo off
setlocal
cd /d "%~dp0"
set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
  if exist "..\.tools\apache-maven-3.9.11\bin\mvn.cmd" (
    set "MVN=..\.tools\apache-maven-3.9.11\bin\mvn.cmd"
  ) else (
    echo 未找到 Maven。请安装 Maven 3.9+ 并加入 PATH。
    exit /b 1
  )
)
call "%MVN%" -DskipTests package
