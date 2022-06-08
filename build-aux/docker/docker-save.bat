@set quot="
@set mycwd=%~dp0
rem cmd /c "pushd %quot%%%mycwd%%%quot% && docker save -o msgconvert-java.tar msgconvert-java"
docker save -o "%~dp0msgconvert-java.tar" msgconvert-java
