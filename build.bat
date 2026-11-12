@echo off
chcp 65001 >nul
java -version
mvn -B clean verify
if errorlevel 1 exit /b 1
echo Built: target\GawharaMissions-1.0.3.jar
