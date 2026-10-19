# Installing KExtractor

Run the commands below from the repository root.

## Prerequisites

| Requirement | Details |
|---|---|
| Java | JDK 21 or later |
| Build | Apache Maven |
| Memory | `-Xmx8g` for the full pipeline; allow additional memory for ONNX native allocations |
| Launch scripts | `curl` for CoreNLP server readiness checks |
| Linguistic resources | Complete WordNet dictionary and a compatible graded-wsd ONNX export |

## Clone the repository

```bash
git clone https://github.com/espositopasquale/KExtractor.git
cd KExtractor
```

## Word sense disambiguation resources

Model weights, downloaded WordNet/VerbNet data, local databases and generated
`reports/` are intentionally excluded from Git. After cloning, supply these
resources locally using the paths or environment variables below. The small
assessment datasets under `kextractor/src/test/resources/` remain versioned.

KExtractor runs [ChangeIsKey/graded-wsd](https://huggingface.co/ChangeIsKey/graded-wsd) locally through ONNX Runtime 1.20.0. Place the compatible ONNX export and its matching tokenizer/configuration together:

```text
models/
├── model.onnx
├── tokenizer.json
└── config.json
```

The current installation already provides these files. Preserve the model revision, license and accompanying tokenizer files with your deployment. Application startup does not download model weights. Python and a separate inference service are not required; RoBERTa byte-level BPE tokenization runs in Java using the supplied `tokenizer.json`.

The ONNX graph must accept rank-two INT64 `input_ids` and `attention_mask`, optionally `token_type_ids`, and produce FLOAT32 `logits` of shape `[batch, 1]`. Its configuration must identify RoBERTa regression and its tokenizer must include the trained `<t>` and `</t>` target markers. The application validates this contract rather than accepting arbitrary ONNX encoders.

Model discovery searches `models/graded-wsd/` and `models/` in the working directory and its ancestors. To select a directory explicitly:

```bash
export KEX_WSD_MODEL_DIR="$PWD/models"
```

The equivalent JVM option is `-Dkextractor.wsd.model.dir=/absolute/path/to/models`.

### WordNet

A complete Princeton WordNet dictionary is required to enumerate candidates and retrieve English definitions. A local copy placed in `kextractor/src/resources/dict` is bundled by Maven; no extra configuration is needed when it is present. This directory is ignored by Git and must be populated separately after cloning. It contains the `data.*`, `index.*`, exception and sense-index files. Preserve the WordNet license with these resources.

To use another dictionary, set `KEX_WORDNET_DIR` or `-Dkextractor.wordnet.dir=/absolute/path/to/dict`. These take precedence over a local `dict/` and bundled resources. Dictionary resources packaged in the executable JAR are extracted to temporary seekable files for JWI. Missing dictionaries or invalid configured paths produce an error.

### Optional multilingual lexicalizations

The read-only `omw_multilingual.db` database is discovered in the working directory and its ancestors. Override its location with `KEX_OMW_DB` or `-Dkextractor.omw.db=/absolute/path/to/database.db`. Set `-Dkextractor.omw.db=off` to run without multilingual lookup. Italian is the default output language; use `-Dkextractor.omw.language=fra`, for example, for French.

OMW lookups use the selected synset offset and POS and require compatible Princeton WordNet 3.0 data. OMW supplies multilingual lexicalizations after English disambiguation; it does not replace the English dictionary.

## Build the executable

```bash
mvn -f kextractor/pom.xml clean package
```

The executable is `kextractor/target/KExtractor-0.1.0.jar`. Model files remain external to the JAR. To use the launch scripts, copy the executable beside them:

```bash
cp kextractor/target/KExtractor-0.1.0.jar ./kextractor.jar
```

Rebuild and recopy the JAR after source changes.

## Run the application

```bash
java -Xmx8g -jar kextractor/target/KExtractor-0.1.0.jar \
  "The river overflowed its muddy bank." output.json
```

Or use the launcher, which can also manage the CoreNLP server:

```bash
./run-mac-linux.sh --server \
  "The bank approved the loan." output.json
```

For an IDE launch, use `com.kextractor.app.App` as the main class, repository root as the working directory, `-Xmx8g` as VM options and the quoted text plus output path as program arguments. Direct main-class invocation does not start the CoreNLP server automatically.

### Acceptance settings

```bash
java -Xmx8g \
  -Dkextractor.wsd.model.dir="$PWD/models" \
  -Dkextractor.wsd.threshold=2.5 \
  -Dkextractor.wsd.margin=0.25 \
  -Dkextractor.wsd.threads=2 \
  -jar kextractor/target/KExtractor-0.1.0.jar \
  "The bank approved the loan." output.json
```

`threshold` is the minimum raw applicability score and `margin` is the minimum difference from the second candidate. These defaults are provisional heuristics, not calibrated correctness guarantees. `threads` controls ONNX intra-operation CPU threads and must be positive; its default is two.

The model produces raw regression scores on a nominal 1–4 applicability annotation scale. Predictions can fall outside that range. The backend does not apply sigmoid, softmax or clipping. Low scores, ties and small margins remain ambiguous; model initialization and inference failures stop extraction. The model was trained on Oxford Dictionary applicability judgements, so scoring WordNet definitions is a transfer setting to assess on your domain.

## Assess the disambiguation model

The test tree contains model contract checks and one occurrence-level assessment runner. Run the contract checks with:

```bash
mvn -f kextractor/pom.xml test
```

These checks cover model input/decision contracts, including tokenizer reference parity. They do not load ONNX or measure sense-selection accuracy.

To run inference over the assessment dataset, first compile the test runner and produce the dependency classpath:

```bash
mvn -f kextractor/pom.xml test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/classpath.txt
```

Maven writes the classpath to `kextractor/target/classpath.txt`. In Bash on Linux/macOS:

```bash
WSD_ASSESSMENT_CP="kextractor/target/test-classes:kextractor/target/classes:$(cat kextractor/target/classpath.txt)"

java -Xmx512m -cp "$WSD_ASSESSMENT_CP" \
  com.kextractor.nlp.disambiguation.GradedWsdAssessment \
  kextractor/src/test/resources/wsd/graded-wsd-assessment.json \
  reports/graded-wsd-assessment.json
```

Use `-Dkextractor.wsd.model.dir=...`, `-Dkextractor.wsd.threshold=...` and `-Dkextractor.wsd.margin=...` before `-cp` when comparing configurations. Choose a different report filename for each experiment.

To validate WordNet labels and UTF-16 occurrence spans without loading ONNX, append `validate`:

```bash
java -Xmx512m -cp "$WSD_ASSESSMENT_CP" \
  com.kextractor.nlp.disambiguation.GradedWsdAssessment \
  kextractor/src/test/resources/wsd/graded-wsd-assessment.json \
  reports/graded-wsd-assessment.json validate
```

You can also run this main class directly from the IDE on its test classpath with the same working directory and arguments. On Windows, Java classpath entries use `;` rather than `:`.

The included VS Code configurations are **WSD — assessment**, **WSD — validate dataset**, and **KExtractor — graded WSD**. The assessment was run with a 512 MB Java heap; allow additional RAM for the roughly 1.4 GB model and ONNX native allocations. The assessment does not load CoreNLP.

The dataset has 30 synthetic sentences and 32 occurrence targets: 27 manually labelled targets and five deliberately underspecified diagnostics. It uses gold lemmas and POS to isolate disambiguation from tagging errors. These repeatedly inspected cases are development material; they do not establish accuracy on independent documents. Review the proposed labels and add untouched domain data before tuning acceptance settings.

The report separates top-ranked accuracy, accepted precision, coverage and underspecified selections, with category/POS breakdowns and resource hashes. See the [assessment dataset guide](kextractor/src/test/resources/wsd/README.md) for the schema, and the [README](README.md) for graph features, launch flags and the current diagnostic result.

## Build OWL graphs from complex sentences

The graph builder matches VerbNet frames to observed arguments and exports links between complements, relative clauses, conditions and coordinated predicates. VerbNet is discovered in `verbnet/verbnet3.4` in the working directory and its ancestors. Override it with `KEX_VERBNET_DIR` or `-Dkextractor.verbnet.dir=/absolute/path/to/verbnet3.4`. Use `off` to use dependency rules alone. The local and server pipelines explicitly request maximal enhanced dependencies.

To generate the four complex-text diagnostic graphs:

```bash
mvn -f kextractor/pom.xml package
java -Xmx2g -cp "kextractor/target/test-classes:kextractor/target/KExtractor-0.1.0.jar" \
  com.kextractor.triples.semantic.GraphAssessment
```

The default output is `reports/graph-assessment/`: each source has `.txt`, `.json` and `.owl` files, with parser evidence in `annotations/` and checks in `summary.json`. Optional runner arguments are `DATASET OUTPUT_DIRECTORY`. It reuses one WSD model session and uses CoreNLP without coreference/OpenIE. The included VS Code configuration is **Graph — complex OWL assessment**. Allow additional RAM for the model's native allocations.

For a text file through the normal application pipeline:

```bash
java -Xmx8g -jar kextractor/target/KExtractor-0.1.0.jar \
  --format owl reports/graph-assessment/passive-transfer-negation.txt graph.owl
```

OWL exports contain an ontology declaration, class declarations, and separate object/data properties. You can open them in Protégé. Frame roles describe source text; their presence does not establish that a conditional, negated or reported event occurred. The diagnostic runner checks RDF/XML roundtrips, source spans, references and selected structural expectations, without running an OWL reasoner. See the [graph assessment guide](kextractor/src/test/resources/graph/README.md) for parser limits and the [generated examples](README.md#complex-sentences-and-owl-examples).
