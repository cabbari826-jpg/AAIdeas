@echo off
chcp 65001 >nul
rem Make new crossword levels: make-levels.bat PACK COUNT   (for example: make-levels.bat az-starter 10)
rem Without arguments it asks. Needs Python 3 (python.org, tick "Add python.exe to PATH").
cd /d "%~dp0tools"
set PACK=%1
set COUNT=%2
if "%PACK%"=="" set /p PACK=Pack (az-starter, az-novruz, az-usaq, az-mini ...): 
if "%COUNT%"=="" set /p COUNT=How many new levels: 
set PY=py -3
%PY% --version >nul 2>&1 || set PY=python
set PYTHONUTF8=1
set PYTHONIOENCODING=utf-8
echo.
echo === 1. Checking the word lists ===
%PY% check_words.py || goto fail
echo.
echo === 2. Making %COUNT% levels for %PACK% ===
%PY% make_levels.py %PACK% %COUNT% %3
if errorlevel 2 echo (Fewer levels than asked: add words to content\words\%PACK%.txt)
if errorlevel 1 if not errorlevel 2 goto fail
echo.
echo === 3. Checking for repeats in all packs ===
%PY% report_dupes.py ..\duplicates-report.md || goto fail
%PY% review_sheet.py ..\clues-for-review.md
echo.
echo Done. New levels are in app\src\main\assets\. Clues to review: clues-for-review.md
pause
exit /b 0
:fail
echo.
echo Something went wrong, see the messages above. Nothing was published.
pause
exit /b 1
