# KExtractor

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Maven](https://img.shields.io/badge/build-Maven-orange.svg)
![License: GPL v3](https://img.shields.io/badge/License-GPL_v3-green.svg)


KExtractor is a Java command-line application that transforms English text into semantic knowledge graphs. Stanford CoreNLP provides linguistic annotations, dependency rules and VerbNet assign event roles, and the ChangeIsKey/graded-wsd ONNX model associates word occurrences with WordNet senses. Selected senses can then retrieve multilingual lexicalizations from OMW.

---
## Design Philosophy

KExtractor preserves original text, occurrence offsets, resource identifiers and semantic evidence so extraction results can be inspected. The pipeline combines dependency rules with trained NLP models. A selected sense is a model decision, and its score is recorded separately from any claim of externally validated correctness.

---
## Core Capabilities
- **Advanced Natural Language Processing** - Implements a shift-reduce dependency parsing strategy to ensure high-fidelity syntactic analysis and relationship extraction.
- **Coreference Resolution** - Uses CoreNLP coreference chains to connect mentions while preserving the original text and evidence offsets in the final graph.
- **Semantic Web Integration** - Outputs linked data in JSON-LD (default), Turtle, RDF/XML, OWL RDF/XML with vocabulary declarations, and N-Quads formats. The graphs integrate the schema.org vocabulary alongside a custom project namespace (`kex`) for downstream graph database ingestion.
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
mvn -f kextractor/pom.xml clean package
```

Upon successful compilation, the following file will be generated:

```bash
kextractor/target/KExtractor-0.1.0.jar
```

Copy this JAR into the project root, alongside the launch scripts, and rename it to `kextractor.jar` - this is the filename the launch scripts (`run-mac-linux.sh` / `run-windows.bat`) expect to find in the project root.

```bash
cp kextractor/target/KExtractor-0.1.0.jar ./kextractor.jar
```

## Operational Syntax

KExtractor can be invoked either directly via `java -jar` or through the bundled launch scripts, which provide automatic CoreNLP server management.


### Direct Invocation
#### Direct String Analysis
```bash
java -Xmx8g -jar kextractor/target/KExtractor-0.1.0.jar \
  "Barack Obama was born in Honolulu. He studied at Harvard." \
  output.json
```
#### Document Ingestion
```bash
java -Xmx8g -jar kextractor/target/KExtractor-0.1.0.jar \
  document.txt \
  result_graph.json
```
> **Note:** If the output file parameter is omitted, the system defaults to `kextractor-output.json` for JSON-LD, or the corresponding extension for other formats, in the current working directory.
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
## Semantic graph for retrieval

The final `rdfKnowledgeGraph` now uses **semantic frames derived from dependency rules**, in both local and server modes. This changes the graph structure from direct OpenIE edges to document, chunk, frame, role mention and entity nodes. OpenIE triples remain available as diagnostics with `--full-analysis`.

For `Alice did not write Book.`, the frame has predicate `write`, agent `Alice`, theme `Book`, and `kex:negated: true`. It links through `prov:wasDerivedFrom` to the original sentence. Negative and modal frames must not be treated as positive facts.

| Node | Useful properties |
|---|---|
| `schema:DigitalDocument` | Original text, SHA-256 content hash, extraction method |
| `kex:Chunk` | Sentence text, document reference, sentence index, character offsets, mentioned entities, events |
| `kex:SemanticFrame` | Lemmatized predicate, agent/patient/theme/recipient/time/location/instrument/oblique, negation, modality, voice, retrieval text, source chunk |
| `kex:RoleMention` | Semantic role, entity reference, original surface text and character offsets |
| Entity | Label, NER type, source chunks |

Offsets are zero-based UTF-16 character positions with an exclusive end, matching Java and CoreNLP. They refer to the original input; WSD fields explicitly identify their sentence-relative coordinate system. Entities have occurrence-based IDs, or share an ID through an explicit CoreNLP coreference chain. Identical labels alone do not merge different entities. IDs are scoped to the content hash of the document; cross-document entity linking is not performed.

For a RAG index, embed each chunk's `schema:text` and store its `@id`, document ID and offsets as metadata. After vector retrieval, traverse `kex:events` and their role references to expand context, then cite the original chunk. `kex:retrievalText` provides the complete evidence sentence for event-level indexing. This project exports the graph; it does not create embeddings or a vector database.

The document method is recorded as `verb-centered-dependency-frames-v2`. Each frame records whether its roles come from VerbNet consensus or dependency rules. This is an English syntax-based SRL system, not a trained PropBank/FrameNet labeler. Verbal predicates and copular attributes are supported. Enhanced dependencies supply explicit controlled subjects and relative references. A coordination rule shares an established subject with a conjoined predicate that has no subject of its own; its evidence is recorded separately. Missing control arguments are not guessed. Chunks correspond to sentences rather than overlapping token windows.

All five serialization formats preserve these nodes and links. Explicit output filenames are used unchanged, so specify `output.ttl` when selecting Turtle:

```bash
./run-mac-linux.sh --format=ttl \
  "Alice did not write Book." output.ttl
```

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
### CLI validation and RDF semantics

Input is required. Use `--help` for syntax. Both `--format ttl` and `--format=ttl` are supported. Without an output path, the filename is `kextractor-output` with the selected format extension (`.json`, `.ttl`, `.nq`, `.rdf`, `.owl`). An explicit filename is used unchanged. Failures return a nonzero exit status. Text files are read as UTF-8.

Entity names use `schema:name`; relationships are represented by semantic frames and their `kex` roles. Both pipelines build the final graph from original-text annotations and coreference chains. `--full-analysis` additionally includes root-dependency summaries, resolved text and OpenIE triples as diagnostics. The former heuristic OpenIE-to-schema graph generator has been removed. Launcher readiness checks have bounded waits.

### VerbNet integration

The graph builder automatically loads the official XML lexicon from `verbnet/verbnet3.4` relative to the working directory. The external lexicon is not bundled in the JAR. Both local and server modes perform VerbNet matching in the KExtractor process.

Set a different location with `KEX_VERBNET_DIR` (also supported by the launch scripts), or the JVM property `-Dkextractor.verbnet.dir=/absolute/path/to/verbnet3.4`. Use `off` to disable it. An explicitly configured missing or invalid directory causes an error; an absent default directory uses dependency rules.

```bash
KEX_VERBNET_DIR="$PWD/verbnet/verbnet3.4" ./run-mac-linux.sh \
  "The news amused Alice." output.json
```

VerbNet XML classes and inherited frames are indexed by verb lemma. Matching uses nominal intransitive/transitive/ditransitive slots, prepositional complements, supported finite and infinitival complements, and explicitly represented lexical particles. Passive arguments are aligned to active frame slots; indirect and direct objects retain their distinct dative positions. Every required frame slot and every observed core argument must fit. Extra obliques can remain adjuncts and use dependency roles. The matcher retains candidates with the greatest observed argument coverage and preposition specificity. Unsupported frames fall back to dependency rules.

`kex:verbnetClass`, `kex:verbnetFrame`, `kex:verbnetSyntax`, and `kex:verbnetCandidate` preserve compatible classes and inherited frame IDs. IDs use the declaring class plus a one-based XML frame ordinal. Roles are assigned where retained frame candidates agree, with selected WordNet sense keys used for lexical alignment when mapped. The graph records unmatched argument tokens and the method on each role mention. Supported syntactic restrictions are checked; subject/object control requires explicit enhanced subject evidence. Selectional restrictions and semantic predicates are not evaluated, as recorded separately. A single frame match does not establish a unique lexical sense or calibrated confidence.

For example, matching `amuse` can assign `stimulus` to its subject and `experiencer` to its object, instead of the generic agent/theme fallback. Each role mention records `kex:roleAssignmentMethod`; frame metadata records matching status and all candidates. Existing evidence offsets, negation and modality are preserved.

Official data and documentation: [CU CLEAR VerbNet](https://github.com/cu-clear/verbnet/). Keep the downloaded resource and its accompanying notices separately from application source.

## Word sense disambiguation

The application uses a single local backend: [ChangeIsKey/graded-wsd](https://huggingface.co/ChangeIsKey/graded-wsd), executed through ONNX Runtime in Java. `WordNetRepository` enumerates candidate senses from the target lemma and part of speech. `GradedWsdModel` compares the original sentence, with the exact occurrence marked by `<t>` and `</t>`, against each candidate definition. The Java tokenizer implements the supplied RoBERTa byte-level BPE specification from `tokenizer.json`.

Place the compatible ONNX export and matching assets together:

```text
models/
├── model.onnx
├── tokenizer.json
└── config.json
```

The model directory is discovered from `models/graded-wsd/` or `models/` in the working directory and its ancestors. Set `KEX_WSD_MODEL_DIR` or `-Dkextractor.wsd.model.dir=/absolute/model/directory` to choose it explicitly. Missing or invalid resources and inference failures stop extraction with an error. See [installation and model configuration](INSTALL.md#word-sense-disambiguation-resources) for details.

The output is a **raw applicability regression score**, on a nominal annotation scale of 1–4; predictions can fall outside that range. It is not a probability. Scores are used directly without sigmoid, softmax or clipping. Selection requires a sufficient top score and a sufficient margin over the second candidate:

| JVM property | Default | Purpose |
|---|---:|---|
| `kextractor.wsd.threshold` | `2.5` | Minimum raw score for the first-ranked candidate |
| `kextractor.wsd.margin` | `0.25` | Minimum difference between the first two scores |
| `kextractor.wsd.threads` | `2` | ONNX intra-operation CPU threads |

These acceptance settings are heuristics. Ties and low scores or margins remain ambiguous. The model was trained on graded Oxford Dictionary applicability judgements; applying it to WordNet definitions is a transfer setting that needs domain evaluation.

For example, run from the repository root:

```bash
java -Xmx8g \
  -Dkextractor.wsd.threshold=2.5 \
  -Dkextractor.wsd.margin=0.25 \
  -jar kextractor/target/KExtractor-0.1.0.jar \
  "The bank approved a loan while a fisherman waited on the bank of the river." \
  output.json
```

Frames and role mentions expose `kex:wsdMethod: graded-wsd-onnx-v1`, target context and offsets, status, candidate synsets, sense keys, definitions and `kex:applicabilityScore`. Selection is attached to the occurrence, so repeated words can receive different senses. `resolved` means the acceptance rule selected a candidate; `kex:wsdValidated: false` records that it was not externally validated. Predicate sense keys filter VerbNet classes only when explicit member mappings are available; the graph retains syntax-based role evidence when lexical alignment is unavailable.

### WordNet and multilingual lexicalizations

A complete Princeton WordNet dictionary supplies English candidates and definitions. Maven bundles `kextractor/src/resources/dict` alongside standard `src/main/resources`. Configuration precedence is `-Dkextractor.wordnet.dir` or `KEX_WORDNET_DIR`, then a local `dict/`, then bundled resources. Packaged dictionary resources are extracted to temporary seekable files for JWI and removed on normal JVM exit. The actual dictionary version is read from the resource. Preserve its accompanying license.

OMW is an optional read-only SQLite resource for lexicalizations of selected senses. Configure `KEX_OMW_DB` or `-Dkextractor.omw.db`; otherwise `omw_multilingual.db` is discovered in the working directory and its ancestors. Set `-Dkextractor.omw.db=off` to omit OMW. The default target language is Italian (`-Dkextractor.omw.language=ita`). Offset-based OMW lookup requires compatible Princeton WordNet 3.0 data. OMW supplies lexicalizations of the selected concept, not a translation of the complete sentence.

### Assessing the model

The test tree contains model contract checks, graph regression checks, and separate assessment runners for WSD and complex-text graphs. `mvn -f kextractor/pom.xml test` runs the checks without loading ONNX; model accuracy is measured by the WSD assessment runner below. From the IDE, run `com.kextractor.nlp.disambiguation.GradedWsdAssessment` on the test classpath, with the repository root as working directory and `-Xmx512m`. Arguments are:

```text
kextractor/src/test/resources/wsd/graded-wsd-assessment.json reports/graded-wsd-assessment.json
```

Append `validate` as the third argument to check labels and UTF-16 spans without loading ONNX. The dataset includes 30 synthetic sentences and 32 target occurrences, including repeated words, distractors, passives and Unicode. Gold lemmas and POS isolate WSD from tagging errors. Twenty-seven occurrences have manually proposed WordNet 3.0 labels; five intentionally lack enough context and are measured separately.

The current diagnostic run ranked the labelled sense first in **26/27 cases (96.3%)**, with **23/24 correct accepted selections (95.8% precision)** and **24/27 coverage (88.9%)**. It also selected a sense for **3/5 deliberately underspecified occurrences**. These repeatedly inspected synthetic examples are development diagnostics, not an independent benchmark or a general accuracy claim. Review the labels and use untouched domain examples before calibrating thresholds.

The [report](reports/graded-wsd-assessment.json) includes raw candidate rankings, acceptance decisions, category/POS metrics, configuration and resource hashes. Use a different report filename for each experiment. See [terminal assessment commands](INSTALL.md#assess-the-disambiguation-model) and the [dataset guide](kextractor/src/test/resources/wsd/README.md).

## Complex sentences and OWL examples

The graph retains the structure between clauses. `kex:clausalComplement`, `kex:openComplement`, `kex:adverbialClause`, and `kex:coordinatedWith` connect predicate frames. Conditional clauses also use `kex:condition`; relative clauses point to their nominal antecedent through `kex:relativeAntecedent`, with the reverse `kex:qualifiedByFrame` link. Clausal arguments reference frames through `kex:eventArgument` rather than becoming noun entities.

Clauses attached to nouns through `acl` or `ccomp` use `kex:nominalComplementOf` and `kex:clausalContent`; a noun complement is not automatically reinterpreted as a relative clause. Basic clause structure and explicit argument enhancements are kept separate, following the [Universal Dependencies enhanced representation](https://universaldependencies.org/u/overview/enhanced-syntax.html).

Participial modifiers such as `signed report` or `damaged bridge` qualify their nominal head through `kex:modifiedEntity` and `kex:qualifiedByFrame`. Their voice remains unspecified when the parse supplies no voice evidence. They are not treated as independent active statements detached from the modified noun.

Enhanced dependencies explicitly supplied by CoreNLP can recover subjects in constructions such as `Alice promised to send…` and resolve a relative pronoun to its antecedent. A subject established this way can also be shared with a coordinated verb, as in `told the engineer to inspect and repair`. Each role mention records its dependency relation and whether its evidence is basic, enhanced, a shared coordination subject or nominal coordination. Coordinated nouns use a `kex:ArgumentGroup` with members and `and`/`or`, so an alternative such as `the doctor or the nurse` is preserved as a group. Subjects, objects and passive voice are handled per predicate.

Negation and modal auxiliaries remain attached to their own clause. Coordinated frames link to their `kex:coordinationScope`; the extractor does not automatically distribute ambiguous negation or modality over conjunctions. Quantifier scope, such as `no scientist`, is not fully analyzed. Frames have `kex:assertionStatus: unasserted-event-description`: extracting a conditional, reported or desired action does not establish that it occurred. Retrieval should include the parent frame, connectives and source sentence.

`--format=owl` exports RDF/XML containing an `owl:Ontology`, class declarations and separate object/data property declarations. The instances describe documents, discourse frames, mentions and participants. No domain/range constraints or world-event truth assertions are inferred. Exports can be opened in Protégé; serialization checks do not constitute OWL reasoner consistency checks.

The [complex-text corpus](kextractor/src/test/resources/graph/complex-texts.json) produces these examples:

| Example | Structures |
|---|---|
| [Passive transfer](reports/graph-assessment/passive-transfer-negation.owl) | Passive arguments, ditransitive transfer, temporal and concessive clauses, negation |
| [Control and coordination](reports/graph-assessment/control-and-coordination.owl) | Subject/object control, shared arguments, coordinated verbs |
| [Conditions and content](reports/graph-assessment/conditions-modality-and-content.owl) | Conditions, modal auxiliaries, reported complements and negation |
| [Relatives and alternatives](reports/graph-assessment/relative-clauses-and-alternatives.owl) | Relative antecedents, coordinated recipients and alternatives |

Regenerate them from the repository root:

```bash
mvn -f kextractor/pom.xml package
java -Xmx2g -cp "kextractor/target/test-classes:kextractor/target/KExtractor-0.1.0.jar" \
  com.kextractor.triples.semantic.GraphAssessment
```

In VS Code, select **Graph — complex OWL assessment**. The [summary](reports/graph-assessment/summary.json) records structural checks and export results. Each `.owl` has a corresponding source `.txt`, graph `.json`, and a real CoreNLP parse under `annotations/`. This runner uses a smaller CoreNLP configuration without coreference or OpenIE; the application still uses its configured full pipeline. See the [graph assessment guide](kextractor/src/test/resources/graph/README.md).
