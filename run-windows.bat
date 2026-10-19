@echo off
setlocal enabledelayedexpansion

:: Default parameters
set "MODE=local"
set "INPUT="
set "OUTPUT="
set "FORMAT="
set "FULL_ANALYSIS=false"
set "GRAPH_ONLY=false"

:: 1. Argument parsing
:parse_args
if "%~1"=="" goto check_args

if /I "%~1"=="--server" (
    set "MODE=server"
    shift
    goto parse_args
)

set "ARG=%~1"
if /I "!ARG:~0,9!"=="--format=" (
    set "FORMAT=!ARG:~9!"
    shift
    goto parse_args
)

if /I "%~1"=="--format" (
    if "%~2"=="" (echo Missing value for --format & exit /b 1)
    set "FORMAT=%~2"
    shift
    shift
    goto parse_args
)

if /I "%~1"=="--full-analysis" (
    set "FULL_ANALYSIS=true"
    shift
    goto parse_args
)

if /I "%~1"=="--graph-only" (
    set "GRAPH_ONLY=true"
    shift
    goto parse_args
)

if "!ARG:~0,1!"=="-" (echo Unknown option: %~1 & exit /b 1)

if "%INPUT%"=="" (
    set "INPUT=%~1"
    shift
    goto parse_args
)
if "%OUTPUT%"=="" (
    set "OUTPUT=%~1"
    shift
    goto parse_args
)
echo Too many arguments
exit /b 1

:: 2. Minimum arguments check
:check_args
if "%INPUT%"=="" (
    echo.
    echo Usage: run-windows.bat [--server] [--graph-only] [--format=jsonld^|ttl^|nquads^|rdf^|owl] [--full-analysis] "Text to analyze" [output_file]
    echo    --server           : Start/Use the local CoreNLP server ^(port 9000^)
    echo    --format=^<format^> : Output graph format: jsonld ^(default^), ttl, nquads, rdf, owl
    echo    --full-analysis    : Perform full analysis ^(default: false^)
    echo    --graph-only       : Write only the RDF graph, without analysis metadata
    echo.
    exit /b 1
)

if not exist "%~dp0kextractor.jar" (echo Missing kextractor.jar beside the launcher & exit /b 1)
set "OUTPUT_ARG="
if not "%OUTPUT%"=="" set OUTPUT_ARG="%OUTPUT%"

:: Build optional arguments string
set "EXTRA_ARGS="
if not "%FORMAT%"=="" set "EXTRA_ARGS=%EXTRA_ARGS% --format=%FORMAT%"
if "%FULL_ANALYSIS%"=="true" set "EXTRA_ARGS=%EXTRA_ARGS% --full-analysis"
if "%GRAPH_ONLY%"=="true" set "EXTRA_ARGS=%EXTRA_ARGS% --graph-only"

:: 3. Server Management
if not "%MODE%"=="server" goto execute
curl --fail --silent --connect-timeout 2 --max-time 3 "http://localhost:9000/ready" >nul 2>&1
if not errorlevel 1 goto execute
start /B "" java -Xmx8g -cp "%~dp0kextractor.jar" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 1500000 > corenlp_server.log 2>&1
set /a ATTEMPTS=0
:wait_loop
set /a ATTEMPTS+=1
if %ATTEMPTS% geq 60 (echo CoreNLP startup timed out; see corenlp_server.log & exit /b 1)
timeout /t 2 /nobreak >nul
curl --fail --silent --connect-timeout 2 --max-time 3 "http://localhost:9000/ready" >nul 2>&1
if errorlevel 1 goto wait_loop
:execute
if "%MODE%"=="server" set "EXTRA_ARGS=%EXTRA_ARGS% --server"
java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar "%~dp0kextractor.jar" %EXTRA_ARGS% "%INPUT%" %OUTPUT_ARG%
exit /b %ERRORLEVEL%
