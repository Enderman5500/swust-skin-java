@echo off
setlocal
cd /d "%~dp0"
if not exist "target\swust-skin.jar" (
  echo 未找到 target\swust-skin.jar，正在构建...
  call build.bat
  if errorlevel 1 exit /b 1
)
java --enable-native-access=ALL-UNNAMED -jar "target\swust-skin.jar"
