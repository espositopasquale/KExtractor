# Changelog

Notable changes to KExtractor are recorded here. Versions are listed in
reverse chronological order.

## [0.2.0] - 2026-11-03

### Added

- Semantic event frames built from dependency rules and VerbNet 3.4, with
  inherited frames, WordNet alignment, role consensus and dependency-rule
  diagnostics.
- Local graded word-sense disambiguation using ONNX Runtime, with occurrence-
  level WordNet senses and optional multilingual lexicalizations from OMW.
- Graph-only output and OWL RDF/XML serialization; retained JSON-LD, Turtle,
  N-Quads and RDF/XML formats.
- Provenance and source spans, plus explicit negation, modality and assertion
  status in graph output.
- A `--version` CLI option and consistent 0.2.0 executable metadata.
- A standard distribution with launchers, WordNet 3.0, VerbNet 3.4 XML,
  dependency metadata, notices and checksums.

### Changed

- Replaced the earlier OpenIE-based graph structure with document, chunk,
  semantic-frame, role-mention and entity nodes. OpenIE remains available as
  diagnostics with `--full-analysis`.
- Updated CLI output and cross-platform launchers, and expanded installation
  and usage documentation.

### Notes

- Java 21 or later is required. The compatible graded-wsd ONNX model and
  optional OMW database are external resources.
- The API and output vocabulary are not declared stable for a future 1.0.0.
  See the release notes for validation scope and known limitations.

## [0.1.0]

- Initial release of the Java command-line text-to-knowledge-graph tool.
- Added CoreNLP-based linguistic analysis, deterministic coreference handling,
  corpus input, local and server execution modes, and launch scripts for
  macOS/Linux and Windows.
- Added JSON-LD output by default, with Turtle, RDF/XML and N-Quads formats,
  plus optional full analysis.
