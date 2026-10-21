@echo off
set SCRIPT="%TEMP%\create_sofi_shortcut.vbs"
set SOFI_DIR=%~dp0
echo Set oWS = WScript.CreateObject("WScript.Shell") > %SCRIPT%
echo sLinkFile = oWS.SpecialFolders("Desktop") ^& "\Sofi AI.lnk" >> %SCRIPT%
echo Set oLink = oWS.CreateShortcut(sLinkFile) >> %SCRIPT%
echo oLink.TargetPath = "wscript.exe" >> %SCRIPT%
echo oLink.Arguments = """" ^& "%SOFI_DIR%Start_Sofi_Silent.vbs" ^& """" >> %SCRIPT%
echo oLink.WorkingDirectory = "%SOFI_DIR%" >> %SCRIPT%
echo oLink.Description = "Sofi AI Digital Human Companion" >> %SCRIPT%
echo oLink.Save >> %SCRIPT%
cscript //nologo %SCRIPT%
del %SCRIPT%
echo Shortcut created on your Desktop: Sofi AI.lnk!
