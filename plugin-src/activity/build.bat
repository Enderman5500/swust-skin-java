@echo off
setlocal
cd /d "%~dp0"
if not exist "..\..\target\swust-skin.jar" (
  echo 未找到 target\swust-skin.jar，请先构建宿主：mvn -DskipTests package
  exit /b 1
)
set "OUT=..\..\plugins\activity"
if not exist "%OUT%" mkdir "%OUT%"
if exist "%OUT%\cn" rmdir /s /q "%OUT%\cn"
javac --release 21 -encoding UTF-8 -cp "..\..\target\swust-skin.jar" -d "%OUT%" src\cn\edu\swust\mc\skin\plugins\activity\*.java
if errorlevel 1 exit /b 1
copy /y plugin.json "%OUT%\plugin.json" >nul
copy /y client.mjs "%OUT%\client.mjs" >nul
jar --create --file "activity.jar" -C "%OUT%" plugin.json -C "%OUT%" client.mjs -C "%OUT%" cn
echo 已构建插件：plugins\activity 与 plugin-src\activity\activity.jar
