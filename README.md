# KExtractor

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Maven](https://img.shields.io/badge/build-Maven-orange.svg)
![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-green.svg)


KExtractor is a Java-based Command Line Interface (CLI) engineered to transform unstructured English text into structured, Semantic Web–compliant Knowledge Graphs. It leverages the Stanford CoreNLP toolkit to orchestrate a comprehensive linguistic pipeline encompassing coreference resolution, Named Entity Recognition (NER), Open Information Extraction (OpenIE).

---
## Design Philosophy

KExtractor is rooted in a deterministic, linguistically grounded architecture rather than a probabilistic, black-box approach. By relying strictly on rule-based and statistically deterministic NLP components, the system prioritizes reproducibility and analytical rigor. The result is a transparent pipeline where a given input consistently yields the same output.

---
## Core Capabilities
- **Advanced Natural Language Processing** - Implements a shift-reduce dependency parsing strategy to ensure high-fidelity syntactic analysis and relationship extraction.
- **Coreference Resolution** - Deterministically resolves pronominal references prior to relationship extraction, reducing entity ambiguity in the final graph structure.
- **Semantic Web Integration** - Outputs linked data in JSON-LD (default), Turtle, RDF/XML, OWL-compatible RDF/XML, and N-Quads formats. The graphs integrate the schema.org vocabulary alongside a custom project namespace (`kex`) for downstream graph database ingestion and automated reasoning.
- **Multi-Format Graph Conversion** - Built-in utilities convert JSON-LD output into other W3C standards (Turtle, RDF/XML, N-Quads).
- **Corpus Processing** - Supports both direct string input and batch processing of flat text documents via a terminal interface.
- **Dual Execution Modes** - Runs processing directly in-process for quick tasks, or routes requests to a background CoreNLP server for large-scale processing without repeatedly loading linguistic resources.
- **Cross-Platform Launch Scripts** - Provides `run-mac-linux.sh` and `run-windows.bat` wrappers that automatically detect whether the CoreNLP server is running, start it if needed, and wait for initialization before processing input.
---
## System Requirements
| Requirement | Details |
|-------------|---------|
| Java Runtime Environment | JDK 21 or later |
| Build Automation | Apache Maven (required for compilation) |
| Memory Allocation | Minimum 4 GB RAM recommended |
| curl | Required by launch scripts to check server status |
### Recommended Heap Settings
| Scenario | Heap Size |
|----------|----------|
| Minimum reliable execution | `-Xmx6g` |
| Default / recommended | `-Xmx8g` |
| Large documents or batch processing | `-Xmx10g` – `-Xmx12g` |

---
## Deployment Instructions
### 1. Clone the Repository
```bash
git clone https://github.com/espositopasquale/KExtractor.git
cd KExtractor
```
### 2. Compile the Executable

Utilize Maven to compile the source code and generate the executable archive (Fat JAR) encompassing all dependencies:

```bash
mvn clean package
```

Upon successful compilation, the following file will be generated:

```bash
target/KExtractor-0.1.0.jar
```

Copy this JAR into the project root, alongside the launch scripts, and rename it to `kextractor.jar` - this is the filename the launch scripts (`run-mac-linux.sh` / `run-windows.bat`) expect to find in the project root.

```bash
cp target/KExtractor-0.1.0.jar ./kextractor.jar
```

## Operational Syntax

KExtractor can be invoked either directly via `java -jar` or through the bundled launch scripts, which provide automatic CoreNLP server management.


### Direct Invocation
#### Direct String Analysis
```bash
java -Xmx8g -jar target/KExtractor-0.1.0.jar \
  "Barack Obama was born in Honolulu. He studied at Harvard." \
  output.json
```
#### Document Ingestion
```bash
java -Xmx8g -jar target/KExtractor-0.1.0.jar \
  document.txt \
  result_graph.json
```
> **Note:** If the output file parameter is omitted, the system defaults to generating a `kextractor-output.json` file in the current working directory.
### Launch Scripts (Recommended)
The `run-mac-linux.sh` and `run-windows.bat` scripts wrap the same executable and support optional flags to control execution mode and output formatting.
#### CLI Flags
| Flag | Value | Description | Default |
|---|---|---|---|
| `--server` | None | Routes extraction to the local CoreNLP server on port 9000, starting it when needed. | Local pipeline |
| `--format=<format>` or `--format <format>` | `jsonld`, `ttl`, `nquads`, `rdf`, `owl` | Selects the graph serialization format. | `jsonld` |
| `--full-analysis` | None | Includes the optional detailed analysis data in the result. | Disabled |
| `--graph-only` | None | Writes only the graph serialization without extraction metadata. | Disabled |
> **Note:** When using `--format`, you can pass the argument either with an equals sign (e.g., `--format=ttl`) or separated by a space (e.g., `--format ttl`).
### macOS / Linux
#### Local Mode (Default)
Loads the full pipeline into RAM for the duration of the run. Best suited for short, one-off text processing.
```bash
./run-mac-linux.sh \
  "Linus Torvalds created the Linux kernel in 1991." \
  output.json
```
#### Format Conversion (Local)
Extract knowledge and output the result as a Turtle (ttl) file.
```bash
./run-mac-linux.sh --format=ttl \
  "Linus Torvalds created the Linux kernel in 1991." \
  output.json
```

To write only the JSON-LD graph without the surrounding extraction metadata:
```bash
./run-mac-linux.sh --graph-only \
  "Linus Torvalds created the Linux kernel in 1991." \
  graph.json
```
#### Local Server Mode (`--server`)
Best suited for large documents, batch processing, or repeated extraction tasks where avoiding repeated initialization overhead improves performance. If no local CoreNLP server is detected, the launch script automatically starts one in the background and waits for it to become ready before processing the input.
```bash
./run-mac-linux.sh --server --format=nquads \
  ./document.txt \
  graph-output.json
```
#### Full Analysis Mode
Run the local pipeline explicitly triggering the full analysis suite:
```bash
./run-mac-linux.sh --full-analysis \
  "Linus Torvalds created the Linux kernel in 1991." \
  output.json
```
### Windows
> **Note:** Assuming `run-windows.bat` mirrors the Linux script flags.
#### Local Mode
```batch
run-windows.bat ^
  "Linus Torvalds created the Linux kernel in 1991." ^
  output.json
```
#### Format Conversion (Local)
```batch
run-windows.bat --format ttl ^
  "Linus Torvalds created the Linux kernel in 1991." ^
  output.json
```
#### Server Mode
```batch
run-windows.bat --server --format rdf ^
  ./document.txt ^
  graph-output.json
```
## Serialized Output Example
**Input:**
```plaintext
Linus Torvalds created the Linux kernel in 1991.
```
**Default Output (JSON-LD):**
```json
{
   "rdfKnowledgeGraph" : {
    "@context" : {
      "schema" : "https://schema.org/",
      "kex" : "http://kextractor.com/entity/",
      "xsd" : "http://www.w3.org/2001/XMLSchema#"
    },
    "@graph" : [ {
      "@id" : "kex:linus_torvalds",
      "@type" : "schema:Person",
      "name" : "Linus Torvalds",
      "kex:createdLinuxKernelIn" : {
        "@value" : "1991",
        "@type" : "xsd:gYear"
      },
      "kex:created" : {
        "@id" : "kex:linux_kernel"
      }
    }, {
      "@id" : "kex:linux_kernel",
      "@type" : "schema:Thing",
      "name" : "Linux kernel"
    } ]
  }
}
```

If `--format=ttl` is used, an identical graph representation will be saved as `output.ttl` following W3C Turtle syntax.
If `--format=owl` is used, the graph is saved as OWL-compatible RDF/XML, suitable for use as an `.owl` file.
## Troubleshooting
### java.lang.OutOfMemoryError
This typically occurs when the JVM heap is too small for the combined memory footprint of the loaded pipeline models.
**Recommended Actions:**
- Ensure no other heavy applications are running.
- Verify that you are using `-Xmx8g` to allocate 8 GB of heap memory.
- For very large corpora, increase heap allocation:
```bash
-Xmx12g
```
or higher if system resources permit.
- If OOM errors persist even at `-Xmx12g`, consider reducing memory pressure by switching the `coref` annotator to the lighter rule-based `dcoref` variant, which can save 1–2 GB of resident memory at some cost to coreference accuracy.
### Port 9000 Conflict
Server mode requires port 9000. If another application is already using this port, the CoreNLP server will fail to start.
**Resolution:** Check for existing processes and free the port before running KExtractor in server mode.
### Terminating the Background Server
If the CoreNLP server hangs or you want to reclaim memory, terminate the Java process listening on port 9000.
#### Linux / macOS
**Find the process:**
```bash
lsof -i :9000
```
**Terminate it:**
```bash
kill -9 <PID>
```
#### Windows
**Find the process:**
```batch
netstat -ano | findstr :9000
```
**Terminate it:**
```batch
taskkill /PID <PID> /F
```
## Standards & Vocabularies

KExtractor generates Semantic Web–compliant linked data using a combination of a custom reference vocabulary (`kex`) and established open standards. This ensures interoperability with RDF stores, graph databases, and Linked Data applications.

### Schema.org

KExtractor uses the Schema.org vocabulary alongside its own `kex` namespace to describe entities and relationships. Schema.org is a reference vocabulary licensed under the Creative Commons Attribution-ShareAlike 3.0 License (CC BY-SA 3.0).

### W3C Semantic Web Standards
KExtractor produces **JSON-LD** by default and supports conversion to additional **W3C RDF serialization formats**, including:
- JSON-LD
- Turtle (TTL)
- RDF/XML
- OWL-compatible RDF/XML
- N-Quads
For more information on RDF and Semantic Web standards, see the World Wide Web Consortium (W3C): https://www.w3.org/RDF/
### Stanford CoreNLP
KExtractor leverages **Stanford CoreNLP** for natural language processing tasks, including tokenization, syntactic parsing, named entity recognition, coreference resolution, entity linking, coreference resolution, and Open Information Extraction.
Stanford CoreNLP is Copyright © The Board of Trustees of the Leland Stanford Junior University and is distributed under the **GNU General Public License v3.0 or later (GPLv3)**.
Source: https://github.com/stanfordnlp/CoreNLP