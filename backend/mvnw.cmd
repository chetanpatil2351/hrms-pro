@echo off
setlocal

set "MAVEN_PROJECTBASEDIR=%~dp0"

pushd "%MAVEN_PROJECTBASEDIR%"

if not exist ".mvn\wrapper\maven-wrapper.jar" (
    echo ERROR: Maven Wrapper JAR is missing.
    popd
    exit /b 1
)

java -Dmaven.multiModuleProjectDirectory=. -classpath ".mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*

set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
