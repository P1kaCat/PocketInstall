@echo off
rem PocketInstall WinPE console. No installer or diskpart is called.
wpeinit
if errorlevel 1 echo Network initialization reported an error. Check NIC drivers.
cls
echo.
echo PocketInstall boot successful ^(WinPE^)
echo.
echo This proof does not run installation, formatting, or boot repair commands.
echo Windows PE itself may enumerate attached storage.
echo.
echo The command prompt remains available. Windows Setup is not included.
echo To shut down: wpeutil shutdown
echo To reboot:   wpeutil reboot
