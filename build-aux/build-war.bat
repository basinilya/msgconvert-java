call usejdk8 2>nul
set "myprojectdir=%~dp0.."
call mvn -f %%myprojectdir%% package
