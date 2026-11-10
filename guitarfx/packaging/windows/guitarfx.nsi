; GuitarFX's Windows installer (spec 027, T059, research R9, FR-026), made with NSIS (makensis; its stub is zlib-licensed, nothing of NSIS
; is linked into GuitarFX). It places:
;   C:\Program Files\GuitarFX\guitarfx.exe and uninstall.exe, a Start menu entry, the entry of Settings > Apps
;   C:\Program Files\Common Files\VST3\GuitarFX.vst3 (the VST3 bundle layout) and C:\Program Files\Common Files\CLAP\GuitarFX.clap
; The settings folder (%APPDATA%\GuitarFX) is never written by the installer, so an install over an older version keeps it; the
; uninstaller removes it only when the guitarist says so (a silent uninstall, /S, keeps it).
; Usage: makensis -DVERSION=X.Y.Z -DLABEL=<tag> -DSOURCE=<folder with guitarfx.exe and guitarfx_plugin.dll> -DOUTFILE=<setup.exe> guitarfx.nsi

Unicode true
!include "MUI2.nsh"
!include "x64.nsh"

!ifndef VERSION | LABEL | SOURCE | OUTFILE
	!error "give -DVERSION, -DLABEL, -DSOURCE and -DOUTFILE"
!endif

!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\GuitarFX"

Name "GuitarFX"
OutFile "${OUTFILE}"
InstallDir "$PROGRAMFILES64\GuitarFX"
RequestExecutionLevel admin
SetCompressor /SOLID lzma
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "GuitarFX"
VIAddVersionKey "CompanyName" "TuxGuitarAdvanced"
VIAddVersionKey "FileDescription" "GuitarFX installer"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "ProductVersion" "${LABEL}"
VIAddVersionKey "LegalCopyright" "LGPL-2.1-or-later"

!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "French"
!insertmacro MUI_LANGUAGE "Spanish"

LangString RemoveSettings ${LANG_ENGLISH} "Also remove your GuitarFX sounds and settings?"
LangString RemoveSettings ${LANG_FRENCH} "Supprimer aussi vos sons et réglages GuitarFX ?"
LangString RemoveSettings ${LANG_SPANISH} "¿Borrar también sus sonidos y ajustes de GuitarFX?"
LangString Needs64 ${LANG_ENGLISH} "GuitarFX needs a 64-bit Windows."
LangString Needs64 ${LANG_FRENCH} "GuitarFX demande un Windows 64 bits."
LangString Needs64 ${LANG_SPANISH} "GuitarFX necesita un Windows de 64 bits."

Function .onInit
	${IfNot} ${RunningX64}
		MessageBox MB_OK|MB_ICONSTOP "$(Needs64)"
		Abort
	${EndIf}
	SetRegView 64
FunctionEnd

Function un.onInit
	SetRegView 64
FunctionEnd

Section "GuitarFX"
	SetShellVarContext all
	; over an older version: its files go, the settings stay
	RMDir /r "$COMMONFILES64\VST3\GuitarFX.vst3"
	Delete "$COMMONFILES64\CLAP\GuitarFX.clap"
	SetOutPath "$INSTDIR"
	File "/oname=guitarfx.exe" "${SOURCE}\guitarfx.exe"
	SetOutPath "$COMMONFILES64\VST3\GuitarFX.vst3\Contents\x86_64-win"
	File "/oname=GuitarFX.vst3" "${SOURCE}\guitarfx_plugin.dll"
	SetOutPath "$COMMONFILES64\CLAP"
	File "/oname=GuitarFX.clap" "${SOURCE}\guitarfx_plugin.dll"
	CreateShortCut "$SMPROGRAMS\GuitarFX.lnk" "$INSTDIR\guitarfx.exe"
	WriteUninstaller "$INSTDIR\uninstall.exe"
	WriteRegStr HKLM "${UNINSTALL_KEY}" "DisplayName" "GuitarFX"
	WriteRegStr HKLM "${UNINSTALL_KEY}" "DisplayVersion" "${LABEL}"
	WriteRegStr HKLM "${UNINSTALL_KEY}" "Publisher" "TuxGuitarAdvanced"
	WriteRegStr HKLM "${UNINSTALL_KEY}" "DisplayIcon" "$INSTDIR\guitarfx.exe"
	WriteRegStr HKLM "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
	WriteRegStr HKLM "${UNINSTALL_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteRegStr HKLM "${UNINSTALL_KEY}" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
	WriteRegDWORD HKLM "${UNINSTALL_KEY}" "NoModify" 1
	WriteRegDWORD HKLM "${UNINSTALL_KEY}" "NoRepair" 1
SectionEnd

Section "Uninstall"
	SetShellVarContext all
	Delete "$INSTDIR\guitarfx.exe"
	Delete "$INSTDIR\uninstall.exe"
	RMDir "$INSTDIR"
	RMDir /r "$COMMONFILES64\VST3\GuitarFX.vst3"
	Delete "$COMMONFILES64\CLAP\GuitarFX.clap"
	Delete "$SMPROGRAMS\GuitarFX.lnk"
	DeleteRegKey HKLM "${UNINSTALL_KEY}"
	; the settings are the guitarist's: in their own %APPDATA%, removed only when they say so
	SetShellVarContext current
	IfSilent done
	IfFileExists "$APPDATA\GuitarFX\*.*" 0 done
	MessageBox MB_YESNO|MB_DEFBUTTON2 "$(RemoveSettings)" IDNO done
	RMDir /r "$APPDATA\GuitarFX"
	done:
SectionEnd
