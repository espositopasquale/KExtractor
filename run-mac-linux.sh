#!/bin/bash

# Default parameters
MODE="local"
INPUT=""
OUTPUT=""
FORMAT=""
FULL_ANALYSIS=false

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
      FORMAT="$2"
      shift 2
      ;;
    --full-analysis)
      FULL_ANALYSIS=true
      shift
      ;;
    *)

      if [ -z "$INPUT" ]; then
        INPUT="$1"
      elif [ -z "$OUTPUT" ]; then
        OUTPUT="$1"
      fi
      shift
      ;;
  esac
done

# 2. Minimum arguments check
if [ -z "$INPUT" ]; then
  echo -e "\nUsage: ./run-mac-linux.sh [--server] [--format=jsonld|ttl|nquads|rdf] \"Text to analyze\" [output_file.json]"
  echo -e "  --server           : Start/Use the local CoreNLP server (port 9000)"
  echo -e "  --format=<format>  : Output graph format: jsonld (default), ttl, nquads, rdf\n"
  echo -e "  --full-analysis    : Perform full analysis (default: false)\n"
  exit 1
fi


EXTRA_ARGS=()
if [ -n "$FORMAT" ]; then
  EXTRA_ARGS+=(--format="$FORMAT")
fi
if [ "$FULL_ANALYSIS" = true ]; then
  EXTRA_ARGS+=(--full-analysis)
fi

# 3. Server Management 
if [ "$MODE" == "server" ]; then
  echo -e "\033[36m[Server Mode]\033[0m Checking CoreNLP..."
  # Check if port 9000 is responding. If it fails, start the server.
  if ! curl -s "http://localhost:9000/" > /dev/null; then
    echo -e "\033[33mServer not found. Starting CoreNLP in the background...\033[0m"

    
    nohup java -Xmx8g -cp "kextractor.jar" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 1500000 > corenlp_server.log 2>&1 &
    # Wait loop until the server returns HTTP 200
    echo -n "Waiting for models to load into memory (may take up to 90 sec on first run)"
    until curl -s "http://localhost:9000/" > /dev/null; do
        sleep 2
        echo -n "."
    done
    echo -e "\n\033[32mServer ready!\033[0m\n"
  else
    echo -e "\033[32mCoreNLP Server is already running on port 9000.\033[0m\n"
  fi
  
  java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar kextractor.jar --server "${EXTRA_ARGS[@]}" "$INPUT" "$OUTPUT"
else
  # Classic Local Mode
  echo -e "\033[35m[Local Mode]\033[0m Loading pipeline into RAM...\n"
  java -Dorg.slf4j.simpleLogger.defaultLogLevel=off -Xmx8g -jar kextractor.jar "${EXTRA_ARGS[@]}" "$INPUT" "$OUTPUT"
fi