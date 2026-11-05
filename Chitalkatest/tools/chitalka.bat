@echo off
chcp 65001 >nul
cd /d "%~dp0"
where py >nul 2>&1 && py -3 chitalka_maker.py && exit /b 0
where python >nul 2>&1 && python chitalka_maker.py && exit /b 0
echo Нужен Python 3. Зайдите на https://www.python.org/downloads/ и поставьте галочку "Add Python to PATH".
pause
