@echo off
cd java
java phpsocks5.Deploy
if not exist deploy.tmp goto nojava
del deploy.tmp
goto deployend
:nojava
echo There's no Java installed detected.  Please install Java./û�м�⵽��װ��Java���밲װJava��
:deployend
pause