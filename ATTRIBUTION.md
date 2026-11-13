# Attribution

KExtractor 0.2.0 incorporates the software and linguistic resources below.
This summary identifies their contributions; retain the accompanying
`THIRD_PARTY_NOTICES.md`, `licenses/` directory and `DEPENDENCIES.json` when
redistributing the standard distribution. Those files contain available
license texts, copyright notices, dependency metadata and further attribution
details, including transitive dependencies.

## Application and runtime libraries

| Component | Contribution | Attribution |
|---|---|---|
| Stanford CoreNLP 4.5.10 | Tokenization, parsing, named entities, coreference and optional OpenIE diagnostics | Stanford NLP Group, Stanford University; composite distribution notice and license details are in `THIRD_PARTY_NOTICES.md`. |
| MIT Java Wordnet Interface (JWI) 2.2.3 | Reads WordNet dictionaries and sense data | Mark A. Finlayson; see the notice for the license declared by the exact published artifact. |
| Microsoft ONNX Runtime 1.20.0 | Runs the local word-sense model | Microsoft; MIT license. |
| Apache Jena 6.1.0 | RDF serialization and OWL vocabulary declarations | Apache Software Foundation; Apache-2.0. |
| Jackson 2.21.2 | JSON processing | FasterXML; Apache-2.0. |
| SLF4J 2.0.18 | Logging | QOS.ch; MIT license. |
| Xerial SQLite JDBC 3.46.1.0 | Access to optional OMW data | Xerial; Apache-2.0, with additional native-library notices. |
| Jakarta JSON Processing API 2.1.3 and Eclipse Parsson 1.1.9 | JSON-P API and implementation used by graph dependencies | Eclipse Foundation; see the included notices for license options and conditions. |

The distribution also contains transitive runtime dependencies. Their available
POMs, license declarations and original notices are inventoried in
`DEPENDENCIES.json` and preserved under `licenses/dependencies/`.

## Linguistic resources

- **Princeton WordNet 3.0** supplies English senses, definitions, sense keys
  and morphological data. Copyright 2006 by Princeton University. Its license
  header and license copy are included with the dictionary and under
  `licenses/WordNet.txt`.
- **VerbNet 3.4** supplies verb classes, syntactic frames, thematic roles and
  WordNet mappings. Copyright 2009 by the University of Colorado. Its license
  is included at `verbnet/LICENSE.txt` and `licenses/VerbNet.txt`.

## External and optional resources

- **ChangeIsKey/graded-wsd** provides the word-sense model used by the local
  ONNX inference path. The model weights are not included. The model card
  credits Pierluigi Cassotti and Nina Tahmasebi; see `THIRD_PARTY_NOTICES.md`
  for its research citation and the distribution's license-status caveat.
- **Open Multilingual Wordnet (OMW)** may be used for multilingual
  lexicalizations. Its database is optional and is not included. OMW
  incorporates wordnets with their own licenses and attribution requirements;
  consult the metadata for the exact database being used.

## Research citations

- Manning et al. (2014), *The Stanford CoreNLP Natural Language Processing
  Toolkit*, ACL System Demonstrations.
- Kipper, Korhonen, Ryant and Palmer (2008), *A Large-scale Classification of
  English Verbs*, Language Resources and Evaluation 42(1), 21-40.
- Cassotti and Tahmasebi (2025), *Sense-specific Historical Word Usage
  Generation*, Transactions of the Association for Computational Linguistics.

The project website has separate dependencies and font attributions documented
in the repository's `THIRD_PARTY_NOTICES.md`; it is not part of this binary
distribution. This summary is informational and does not replace the supplied
license texts or notices.