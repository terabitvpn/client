; Terabit VPN — Windows installer (Inno Setup 7).
; A plain EXE installer on purpose: no MSI / Windows Installer service, no admin rights,
; works on trimmed Windows builds too. Build: tools/windows/build-installer.ps1
#ifndef AppVersion
  #define AppVersion "0.1.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\..\desktopApp\build\compose\binaries\main\app\Terabit"
#endif

[Setup]
AppId={{D8E7FE77-382B-4346-9630-25FBE06EE67F}
AppName=Terabit VPN
AppVersion={#AppVersion}
AppVerName=Terabit VPN {#AppVersion}
AppPublisher=Terabit
AppPublisherURL=https://terabitvpn.ru
AppSupportURL=https://t.me/terabitvpnbot
AppUpdatesURL=https://terabitvpn.ru
DefaultDirName={localappdata}\Programs\Terabit
DefaultGroupName=Terabit VPN
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir=..\..\build\release
OutputBaseFilename=Terabit-Windows
SetupIconFile=..\..\desktopApp\icons\terabit.ico
UninstallDisplayIcon={app}\Terabit.exe
UninstallDisplayName=Terabit VPN
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=yes
RestartApplications=no
LicenseFile=..\..\LICENSE

[Languages]
Name: "ru"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "en"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "autostart"; Description: "Запускать Terabit вместе с Windows"; GroupDescription: "Дополнительно:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Terabit VPN"; Filename: "{app}\Terabit.exe"
Name: "{group}\Удалить Terabit VPN"; Filename: "{uninstallexe}"
Name: "{userdesktop}\Terabit VPN"; Filename: "{app}\Terabit.exe"; Tasks: desktopicon

[Registry]
; terabit:// links from the site and subscription pages open the app
Root: HKCU; Subkey: "Software\Classes\terabit"; ValueType: string; ValueName: ""; ValueData: "URL:Terabit VPN"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\terabit"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKCU; Subkey: "Software\Classes\terabit\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\Terabit.exe"",0"
Root: HKCU; Subkey: "Software\Classes\terabit\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\Terabit.exe"" ""%1"""
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "Terabit"; ValueData: """{app}\Terabit.exe"" --autostart"; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{app}\Terabit.exe"; Description: "{cm:LaunchProgram,Terabit VPN}"; Flags: nowait postinstall skipifsilent
; self-update runs the installer silently — bring Terabit back afterwards
Filename: "{app}\Terabit.exe"; Flags: nowait skipifnotsilent

[UninstallRun]
; Close Terabit gracefully first: it disconnects and restores the system proxy on exit.
Filename: "{sys}\taskkill.exe"; Parameters: "/IM Terabit.exe"; Flags: runhidden waituntilterminated; RunOnceId: "CloseTerabit"
; Then stop only OUR xray (other VPN clients run xray.exe too — never touch theirs).
Filename: "powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -Command ""Start-Sleep 2; Get-Process Terabit,xray -ErrorAction SilentlyContinue | Where-Object {{ $_.Path -like '{app}*' }} | Stop-Process -Force"""; Flags: runhidden waituntilterminated; RunOnceId: "StopOurXray"

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
