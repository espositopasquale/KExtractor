# Third-Party Notices

KExtractor uses the software, linguistic data and model resources listed below.
Versions in the Java sections correspond to the 0.2.0 distribution. Each
component retains its own copyright, license and attribution requirements.
This summary accompanies the original license texts and notices supplied with
the components.

## Java application and bundled libraries

### Stanford CoreNLP 4.5.10

- Copyright © The Board of Trustees of the Leland Stanford Junior University
- License: GNU General Public License v3.0 or later (GPL-3.0-or-later), as
  described for the composite CoreNLP distribution
- Use: linguistic annotation, dependency parsing, named entities, coreference
  and optional OpenIE diagnostics; the distribution also includes CoreNLP models
- [Website and licensing](https://stanfordnlp.github.io/CoreNLP/#license)
- [Source](https://github.com/stanfordnlp/CoreNLP)
- Research citation: [Manning et al. (2014)](https://aclanthology.org/P14-5010/)

### Other directly declared runtime libraries

| Component | Version | Use | License reference | Source |
|---|---|---|---|---|
| Microsoft ONNX Runtime | 1.20.0 | Local graded-wsd inference | [MIT](https://github.com/microsoft/onnxruntime/blob/v1.20.0/LICENSE) | [microsoft/onnxruntime](https://github.com/microsoft/onnxruntime) |
| Apache Jena | 6.1.0 | RDF graph serialization and OWL vocabulary declarations | [Apache-2.0](https://github.com/apache/jena/blob/main/LICENSE) | [apache/jena](https://github.com/apache/jena) |
| Jackson Databind and Core | 2.21.2 | JSON annotation and graph processing | [Apache-2.0](https://github.com/FasterXML/jackson-databind/blob/2.21/LICENSE) | [FasterXML](https://github.com/FasterXML) |
| SLF4J API and Simple | 2.0.18 | Application logging | [MIT](https://www.slf4j.org/license.html) | [qos-ch/slf4j](https://github.com/qos-ch/slf4j) |
| Xerial SQLite JDBC | 3.46.1.0 | Access to the optional OMW database | [Apache-2.0](https://github.com/xerial/sqlite-jdbc/blob/3.46.1.0/LICENSE), with accompanying native-library and third-party notices | [xerial/sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) |
| Jakarta JSON Processing API | 2.1.3 | JSON-P API used by graph dependencies | EPL-2.0, with GPL-2.0 plus Classpath Exception available under the supplied secondary-license conditions | [jakartaee/jsonp-api](https://github.com/jakartaee/jsonp-api) |
| Eclipse Parsson | 1.1.9 | JSON-P implementation | EPL-2.0, with GPL-2.0 plus Classpath Exception available under the supplied secondary-license conditions | [eclipse-ee4j/parsson](https://github.com/eclipse-ee4j/parsson) |

The Jakarta JSON-P and Parsson license texts and secondary-license conditions
are preserved from their JARs in the release's `licenses/dependencies/` directory.
The SLF4J JARs retain the QOS.ch copyright notices. Apache and other upstream
copyright notices remain in the corresponding dependency notice files.

### MIT Java Wordnet Interface (JWI) 2.2.3

- Author: Mark A. Finlayson
- Use: reading WordNet dictionaries, candidate senses and morphological forms
- [Project](https://projects.csail.mit.edu/jwi/)
- [Source archive for 2.2.3](https://projects.csail.mit.edu/jwi/download.php?f=edu.mit.jwi_2.2.3_src.zip)
- The published Maven POM for `edu.mit:jwi:2.2.3` declares
  [Creative Commons Attribution-NonCommercial 3.0 Unported](https://creativecommons.org/licenses/by-nc/3.0/legalcode).
  That POM is retained in the release dependency metadata.
- The [current upstream license page](https://projects.csail.mit.edu/jwi/license.html)
  describes CC BY 4.0 for the newer upstream distribution. Its applicability to
  the exact 2.2.3 artifact needs confirmation before public distribution; this
  notice retains the distinction between the two versions' license information.

### Transitive dependencies and test tools

The executable also includes transitive dependencies of the libraries above,
including Jackson Annotations 2.21 and additional Apache, Eclipse, Google and
other components. The generated 0.2.0 release contains:

- `DEPENDENCIES.json`: coordinates and SHA-256 hashes of the 71 shaded runtime
  artifacts, available POM license declarations and paths to extracted notices.
- `licenses/dependencies/`: original POMs and available license, notice,
  copyright and third-party notice files from those artifacts.

Some POMs inherit license declarations from a parent; an empty declaration list
in the inventory does not mean that a component has no license. Preserve the
original notices together with this summary when redistributing the release.

Tests use [JUnit Jupiter](https://github.com/junit-team/junit5), version 5.11.0,
under the Eclipse Public License 2.0. JUnit is a test dependency and is not
included in the executable runtime dependency set.

## Bundled linguistic resources

### Princeton WordNet 3.0

- WordNet 3.0 Copyright 2006 by Princeton University. All rights reserved.
- Use: English lexical senses, definitions, sense keys and morphological data
- License: [Princeton WordNet license](https://wordnet.princeton.edu/license-and-commercial-use)
- [Project](https://wordnet.princeton.edu/)
- [Downloads](https://wordnet.princeton.edu/download/current-version)
- [Citation guidance](https://wordnet.princeton.edu/citing-wordnet)

The standard 0.2.0 JAR includes the local WordNet dictionary. Its original
license header is retained in the data files and copied to `licenses/WordNet.txt`
in the generated distribution. WordNet is a registered name of Princeton
University; its license includes conditions concerning use of the university's
name in advertising and publicity.

### VerbNet 3.4

- VerbNet 3.0 (or 3.X) Copyright 2009 by University of Colorado. All rights reserved.
- Use: verb-class membership, inherited syntactic frames, thematic roles and
  explicit mappings to WordNet sense keys
- License: [VerbNet 3.X license](https://verbs.colorado.edu/~mpalmer/projects/verbnet/license.3.0.txt)
- [Project](https://verbs.colorado.edu/verbnet/)
- [XML data and source repository](https://github.com/cu-clear/verbnet/)
- Research citation: Karin Kipper, Anna Korhonen, Neville Ryant and Martha Palmer
  (2008), *A Large-scale Classification of English Verbs*, Language Resources
  and Evaluation 42(1), 21–40

The standard release includes the XML lexicon in `verbnet/verbnet3.4/`; its
license is preserved in `verbnet/LICENSE.txt` and `licenses/VerbNet.txt`.
KExtractor reads these files directly in Java. The upstream Python API is not
part of the binary distribution. The VerbNet license includes conditions on
copyright notices, citation and use of the University of Colorado name.

## External model and optional multilingual data

### ChangeIsKey/graded-wsd

- Provider: the Change is Key project; model card credits Pierluigi Cassotti and
  Nina Tahmasebi
- Use: occurrence-level word-sense applicability scoring through a locally
  supplied ONNX export of the RoBERTa-based model
- [Model card and files](https://huggingface.co/ChangeIsKey/graded-wsd)
- Research citation: Cassotti and Tahmasebi (2025),
  [*Sense-specific Historical Word Usage Generation*](https://doi.org/10.1162/tacl_a_00761)

The standard release does not include these model weights. The upstream model
card inspected on 2026-11-03 does not state an explicit model license. Confirm
the terms for the exact weights, tokenizer and export before redistribution;
the ONNX Runtime software license does not establish the license of model data.
Reference local-export hashes are recorded in `EXTERNAL_RESOURCES.json`.

### Open Multilingual Wordnet (OMW)

- Credits: Francis Bond, collaborators and the authors of each contributing wordnet
- Use: optional multilingual lexicalizations of selected WordNet senses from a
  compatible local `omw_multilingual.db`
- [Project](https://omwn.org/)
- [Data repository and per-wordnet license metadata](https://github.com/omwn/omw-data/blob/main/index.toml)
- Research citations: Bond and Paik (2012), *A Survey of Wordnets and their Licenses*;
  Bond and Foster (2013), [*Linking and Extending an Open Multilingual Wordnet*](https://aclanthology.org/P13-1133/)

The standard release does not include the OMW database. Contributing wordnets
have their own licenses and attribution requirements. When distributing a
database or lexical data derived from it, retain the notices and citations of
the actual source wordnets alongside the OMW project attribution.

## Website software and fonts

The repository's Next.js website is maintained separately from the Java CLI and
is not included in its standard binary archive. Its principal components are:

| Component | License | Source |
|---|---|---|
| Next.js | MIT | [vercel/next.js](https://github.com/vercel/next.js) |
| React and React DOM | MIT | [facebook/react](https://github.com/facebook/react) |
| Tailwind CSS | MIT | [tailwindlabs/tailwindcss](https://github.com/tailwindlabs/tailwindcss) |
| TypeScript | Apache-2.0 | [microsoft/TypeScript](https://github.com/microsoft/TypeScript) |
| Poppins | SIL Open Font License 1.1; Copyright 2020 The Poppins Project Authors | [itfoundry/Poppins](https://github.com/itfoundry/Poppins) |
| Geist and retained Geist Mono font assets | SIL Open Font License 1.1; The Geist Project Authors | [vercel/geist-font](https://github.com/vercel/geist-font) |

The website's `package.json` and lockfile identify its dependency versions.
Font license copies are kept in `app/fonts/OFL.txt` and
`app/fonts/Poppins/OFL.txt` within the website project. The generated brand
wordmarks also use Poppins outlines; retain its font license with those assets
when the applicable distribution terms require it.
