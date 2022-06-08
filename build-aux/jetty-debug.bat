call usejdk8 2>nul
set "myprojectdir=%~dp0.."
call mvnDebug -f %%myprojectdir%% jetty:run
