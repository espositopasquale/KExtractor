# Graded WSD assessment

`GradedWsdAssessment` evaluates the real `ChangeIsKey/graded-wsd` ONNX backend with English WordNet candidates. It does not train the model or build a graph-vector cache. Model load or inference errors fail the assessment instead of silently selecting another backend.

Run `com.kextractor.nlp.disambiguation.GradedWsdAssessment.main` on the **test classpath** from the IDE. Set the working directory to the repository root and VM options to `-Xmx512m`. ONNX also allocates native memory outside this heap. Default arguments are:

```text
kextractor/src/test/resources/wsd/graded-wsd-assessment.json reports/graded-wsd-assessment.json
```

To validate the dataset without loading ONNX, append `validate`:

```text
kextractor/src/test/resources/wsd/graded-wsd-assessment.json reports/graded-wsd-assessment.json validate
```

The validation command does not create a report. It checks case IDs, occurrence spans, WordNet lemmas/POS and all gold sense keys. The lemma and Penn POS in the dataset are supplied to the model deliberately; this assessment isolates sense disambiguation from POS tagging and lemmatization errors.

The included dataset contains 30 synthetic sentences and 32 target occurrences, including repeated words, distractors, verbs, passives and an emoji before a target to exercise UTF-16 offsets. Twenty-seven occurrences have manually proposed WordNet 3.0 gold labels. Five deliberately lack enough context and are measured separately. These cases were used repeatedly during earlier development and are **development diagnostics, not an independent accuracy benchmark**. The labels need independent review. Add separately annotated domain examples before making deployment decisions.

Each case contains `id`, `category`, `text`, and a `targets` array. A target specifies `surface`, `lemma`, Penn `pos`, `start`, `end`, `goldSenseKeys`, and `expected`. Offsets use **Java UTF-16 code units**, with an inclusive start and exclusive end, and must exactly select the surface in the original text. For a labelled target, `expected` is `select`; multiple acceptable sense keys are allowed. For deliberately insufficient context, use an empty gold array and `expected: "abstain"`. A selection on such an item is a diagnostic, not automatically a factual error.

The JSON report records:

- `summary.topAccuracy`: correct first-ranked senses divided by labelled targets.
- `summary.acceptedAccuracy`: correct accepted senses divided by accepted labelled targets; `null` when none are accepted.
- `summary.coverage`: accepted labelled targets divided by labelled targets.
- `summary.underspecifiedAccepted`: selections on deliberately insufficient contexts, excluded from labelled accuracy.
- `byCategory`, `byPos`, and per-occurrence rankings with raw scores, gold ranks and graph evidence.
- Dataset/model/tokenizer hashes, WordNet version, backend settings and execution times.

Raw graded model scores and their margins are ranking signals, **not calibrated probabilities of correctness**. Use a different report filename for each configuration. Keep a separate evaluation split untouched while choosing thresholds or model settings.

`mvn -f kextractor/pom.xml test` runs `GradedWsdAssessmentTest` without constructing an ONNX session. These checks cover occurrence offsets, target preservation, acceptance rules and Java token IDs against `tokenizer-reference.json`. The reference IDs were generated independently with Hugging Face `tokenizers 0.22.2` and checked against `transformers 4.57.6` (`RobertaTokenizerFast`). The fixture records the tokenizer hash; after changing tokenizer assets, regenerate the reference with the original Hugging Face implementation. Python is not required to run the Java checks or assessment.
