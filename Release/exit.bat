@echo off
for /f %%a in (pid.txt) do taskkill /F /PID %%a