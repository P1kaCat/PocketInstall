@echo off
rem PocketInstall WinPE proof only. No installer or diskpart is called.
wpeinit
cls
echo.
echo PocketInstall boot successful ^(WinPE^)
echo.
echo This proof does not run installation, formatting, or boot repair commands.
echo Use a VM without disks, or disconnect physical disks before this test.
echo Windows PE itself may enumerate attached storage.
echo.
echo Press any key to shut down.
pause >nul
wpeutil shutdown
