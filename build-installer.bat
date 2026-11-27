@echo off
rem Builds GuessArena.jar and a Windows .exe installer.
rem Needs: JDK 17 or newer, and WiX Toolset 3.x (see README.md)
cd /d "%~dp0"
if exist out rmdir /s /q out
if exist dist rmdir /s /q dist
if exist installer rmdir /s /q installer
mkdir out dist installer

echo [1/3] Compiling...
javac --release 17 -encoding UTF-8 -d out src\guessarena\*.java || goto :fail
copy /y src\words.txt out\ >nul || goto :fail

echo [2/3] Creating JAR...
jar --create --file dist\GuessArena.jar --main-class guessarena.Main -C out . || goto :fail

echo [3/3] Creating installer...
jpackage --type exe --name GuessArena --app-version 1.0.0 --input dist --main-jar GuessArena.jar --main-class guessarena.Main --icon assets\icon.ico --dest installer --win-menu --win-shortcut --win-dir-chooser || goto :fail

echo.
echo Done. The installer is in the "installer" folder.
pause
exit /b 0

:fail
echo.
echo BUILD FAILED. Read the error above and check README.md.
pause
exit /b 1
