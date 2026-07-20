@echo off
setlocal
node "%~dp0silk-encoder.mjs" "%~1" "%~2"
exit /b %errorlevel%
