# Graph assessment

`complex-texts.json` contains four original English paragraphs with passives,
transfer and ditransitive verbs, negation, control, coordination, conditional and
relative clauses, reported content, and modal verbs. The expectations check
specific structures; this small diagnostic corpus does not measure general
extraction accuracy.

The finite form `before the pump restarts` was initially tagged by CoreNLP as a
nominal phrase (`restarts/NNS`), so no restart frame could be constructed from
that parse. The diagnostic text uses `before the pump can restart` to make the
verb explicit while retaining the expected restart-frame check. This is a
selected structural test corpus, not an unbiased parser benchmark; the graph
builder does not silently replace the parser's POS labels.

The object relative uses `a sample which the laboratory had tested`: the variant
with `that` was labeled a nominal `ccomp` by the parser. The builder preserves
nominal clause attachments through `kex:nominalComplementOf`/`kex:clausalContent`
and does not reinterpret all noun complements as relative clauses. The selected
example retains the explicit relative-antecedent check.

Run `com.kextractor.triples.semantic.GraphAssessment` on the test classpath,
or use the graph assessment launch configuration. Optional arguments are the
dataset path and the output directory. Defaults resolve from either the repository
or the Maven module directory.

The runner uses actual CoreNLP analysis (`tokenize,ssplit,pos,lemma,ner,depparse`,
enhanced dependencies enabled), VerbNet and one shared graded-WSD ONNX session.
It writes `SOURCE.txt`, `SOURCE.json` (JSON-LD), `SOURCE.owl` (OWL RDF/XML), and
`annotations/SOURCE.json` (the parser evidence) under
`reports/graph-assessment`, with aggregate results in `summary.json`.

Every OWL graph is parsed again with Jena. The runner verifies ontology and
property declarations, frame and role source spans, internal references,
frame descriptions, and the declared linguistic expectations. Failed checks
remain in the report and cause a nonzero exit status after all artifacts have
been written. These checks do not run an OWL reasoner or certify all OWL 2 DL
constraints. Role assertions describe a frame and preserve its local negation,
modality and embedding; they do not establish that the described event occurred.
