@echo off

timeout 10

set updateDir=update\new\
set backupDir=update\old\

set dirs_to_copy=^
    conf^
    html^
    lib

set files_to_copy=^
    burst.cmd^
    burst.jar^
    burst.sh^
    genscoop.cl^
    init-mysql.sql^
    LICENSE.txt^
    README.md


REM Check update exists

IF NOT EXIST %updateDir% (
    echo Could not find update: %updateDir%
    exit /B
)

REM Clear previous backup
IF EXIST %backupDir% (
    rmdir %backupDir% /S /Q
)

REM Backup current install
(for %%f in (%dirs_to_copy%) do (
   xcopy %%f %backupDir%\%%f\ /e
))
(for %%f in (%files_to_copy%) do (
   xcopy %%f %backupDir%
))

REM Install new update
(for %%f in (%dirs_to_copy%) do (
   rmdir %%f\ /S /Q
   xcopy %updateDir%\%%f %%f\ /e /C /R /Y
))
(for %%f in (%files_to_copy%) do (
   del %%f
   xcopy %updateDir%%%f . /e /C /R /Y
))

REM Restore config
xcopy %backupDir%conf\brs.properties conf\
xcopy %backupDir%conf\logging.properties conf\

REM Restart node!
start javaw -jar burst.jar

exit /B
