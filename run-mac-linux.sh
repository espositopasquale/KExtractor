#!/bin/bash

set -u
SCRIPT_DIR="$(cd -- "$(dirname -- "$0")" && pwd)"

# Default parameters
MODE="local"
INPUT=""
OUTPUT=""
FORMAT=""
FULL_ANALYSIS=false
GRAPH_ONLY=false

# 1. Argument parsing 
while [[ $# -gt 0 ]]; do
  case $1 in
    --server)
      MODE="server"
      shift 
      ;;
    --format=*)
      FORMAT="${1#--format=}"
      shift 
      ;;
    --format)
      if [[ $# -lt 2 || "$2" == --* ]]; then echo "Missing value for --format" >&2; exit 1; fi
      FORMAT="$2"
      shift 2
      ;;
    --full-analysis)
      FULL_ANALYSIS=true
      shift
      ;;
    --graph-only)
      GRAPH_ONLY=true
      shift
      ;;
    --help|-h)
      echo "Usage: $0 [--server] [--format FORMAT] [--full-analysis] [--graph-only] INPUT [OUTPUT]"
      exit 0
      ;;
    --*) echo "Unknown option: $1" >&2; exit 1 ;;
    *)

      if [ -z "$INPUT" ]; then
        INPUT="$1"
      elif [ -z "$OUTPUT" ]; then
        OUTPUT="$1"
      else
        echo "Too many arguments" >&2; exit 1
      fi
      shift
      ;;
  esac
done

# 2. Minimum arguments check
if [ -z "$INPUT" ]; then
  echo -e "\nUsage: ./run-mac-linux.sh [--server] [--graph-only] [--format=jsonld|ttl|nquads|rdf|owl] \"Text to analyze\" [output_file]"
  echo -e "  --server           : Start/Use the local CoreNLP server (port 9000)"
  echo -e "  --format=<format>  : Output graph format: jsonld (default), ttl, nquads, rdf, owl\n"
  echo -e "  --full-analysis    : Perform full analysis (default: false)\n"
  echo -e "  --graph-only       : Write only the RDF graph, without analysis metadata\n"
  exit 1
fi


[[ -f "$SCRIPT_DIR/kextractor.jar" ]] || { echo "Missing kextractor.jar beside the launcher" >&2; exit 1; }
POSITIONAL=("$INPUT")
if [[ -n "$OUTPUT" ]]; then POSITIONAL+=("$OUTPUT"); fi
EXTRA_ARGS=()
if [ -n "$FORMAT" ]; then
  EXTRA_ARGS+=(--format="$FORMAT")
fi
if [ "$FULL_ANALYSIS" = true ]; then
  EXTRA_ARGS+=(--full-analysis)
fi
if [ "$GRAPH_ONLY" = true ]; then
  EXTRA_ARGS+=(--graph-only)
fi

# 3. Server Management 
if [ "$MODE" == "server" ]; then
  echo -e "\033[36m[Server Mode]\033[0m Checking CoreNLP..."
  # Check if port 9000 is responding. If it fails, start the server.
  if ! curl --fail --silent --connect-timeout 2 --max-time 3 "http://localhost:9000/ready" > /dev/null; then
    echo -e "\033[33mServer not found. Starting CoreNLP in the background...\033[0m"

    
    nohup java -Xmx8g -cp "$SCRIPT_DIR/kextractor.jar" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 1500000 > corenlp_server.log 2>&1 &
    # Wait loop until the server returns HTTP 200
    echo -n "Waiting for models to load into memory (may take up to 90 sec on first run)"
    SERVER_PID=$!
    DEADLINE=$((SECONDS + 180))
    until curl --fail --silent --connect-timeout 2 --max-time 3 "http://localhost:9000/ready" > /dev/null; do
        if ! kill -0 "$SERVER_PID" 2>/dev/null || (( SECONDS >= DEADLINE )); then
          echo "CoreNLP startup failed or timed out; see corenlp_server.log" >&2; exit 1
        fi
        sleep 2
        echo -n "."
    done
    echo -e "\n\033[32mServer ready!\033[0m\n"
  else
    echo -e "\033[32mCoreNLP Server is already running on port 9000.\033[0m\n"
  fi
  
  java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar "$SCRIPT_DIR/kextractor.jar" --server "${EXTRA_ARGS[@]}" "${POSITIONAL[@]}"
else
  # Classic Local Mode
  echo -e "\033[35m[Local Mode]\033[0m Loading pipeline into RAM...\n"
  java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar "$SCRIPT_DIR/kextractor.jar" "${EXTRA_ARGS[@]}" "${POSITIONAL[@]}"
fi