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
goto parse_args

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

:: Build optional arguments string
set "EXTRA_ARGS="
if not "%FORMAT%"=="" set "EXTRA_ARGS=%EXTRA_ARGS% --format=%FORMAT%"
if "%FULL_ANALYSIS%"=="true" set "EXTRA_ARGS=%EXTRA_ARGS% --full-analysis"
if "%GRAPH_ONLY%"=="true" set "EXTRA_ARGS=%EXTRA_ARGS% --graph-only"

:: 3. Server Management 
if "%MODE%"=="server" (
    echo [Server Mode] Checking CoreNLP...
    :: Check if port 9000 is responding using curl (available in Windows 10+)
    curl -s "http://localhost:9000/" >nul 2>&1
    if errorlevel 1 (
        echo [!] Server not found. Starting CoreNLP in the background...

        start /B "" java -Xmx8g -cp "kextractor.jar" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 150000 > corenlp_server.log 2>&1
        echo Waiting for models to load into memory ^(may take up to 90 sec on first run^)...

        :: Wait loop until the server returns HTTP 200
        :wait_loop
        timeout /t 2 /nobreak >nul
        curl -s "http://localhost:9000/" >nul 2>&1
        if errorlevel 1 (
            <nul set /p="."
            goto wait_loop
        )
        echo.
        echo [OK] Server ready!
        echo.
    ) else (
        echo [OK] CoreNLP Server is already running on port 9000.
        echo.
    )
    :: Execute the jar passing the --server flag and extra arguments
    java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar kextractor.jar --server %EXTRA_ARGS% "%INPUT%" "%OUTPUT%"
) else (
    :: Classic Local Mode
    echo [Local Mode] Loading pipeline into RAM...
    echo.
    java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar kextractor.jar %EXTRA_ARGS% "%INPUT%" "%OUTPUT%"
)

endlocal