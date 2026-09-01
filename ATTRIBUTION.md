# ATTRIBUTION.md

## Third-Party Software and Data Attribution

This project, **KExtractor**, incorporates third-party software to enable
natural language processing and semantic knowledge graph construction. This
document lists these dependencies and their licensing terms.

---

## Stanford CoreNLP

KExtractor is built on top of the [Stanford CoreNLP](https://stanfordnlp.github.io/CoreNLP/)
toolkit, developed by the Stanford NLP Group at Stanford University.

- **Project:** Stanford CoreNLP
- **Institution:** Stanford University, Stanford NLP Group
- **Homepage:** https://stanfordnlp.github.io/CoreNLP/
- **Source:** https://github.com/stanfordnlp/CoreNLP
- **License:** GNU General Public License (GPL), v3 or later<sup>[1]</sup>

CoreNLP provides the foundational NLP capabilities used in KExtractor, including:

- Tokenization and sentence splitting
- Part-of-speech tagging
- Named entity recognition (NER)
- Dependency parsing
- Coreference resolution
- Open Information Extraction (OpenIE)

### License notes

> [1] Per the Stanford NLP Group, the underlying Stanford NLP code is
> licensed GPL v2+, but because CoreNLP bundles several Apache-licensed
> third-party libraries, the composite distribution is correctly licensed
> as GPL v3+. The full GPL permits free use, but **does not permit
> incorporation into proprietary software distributed to others** without a
> separate commercial license from Stanford. Organizations requiring a
> commercial license should contact the Stanford NLP Group directly
> (java-nlp-support@lists.stanford.edu).

This attribution is provided for informational purposes and does not
constitute legal advice. Consult the official CoreNLP repository and your
own legal counsel to confirm license obligations applicable to your use
and distribution of KExtractor.

### Citation

If publishing research or documentation that relies on CoreNLP's output,
consider citing:

> Manning, C. D., Surdeanu, M., Bauer, J., Finkel, J., Bethard, S. J., &
> McClosky, D. (2014). *The Stanford CoreNLP Natural Language Processing
> Toolkit.* In Proceedings of 52nd Annual Meeting of the Association for
> Computational Linguistics: System Demonstrations, pp. 55–60.

---

## Summary of External Dependencies

| Component            | Purpose                                   | License        |
|-----------------------|--------------------------------------------|-----------------|
| Stanford CoreNLP       | NLP pipeline (tokenization, NER, parsing, coreference, OpenIE) | GPL v3+ |

These components enable the transformation of unstructured text into
structured, machine-readable knowledge graphs.

---

## Questions

For questions about this attribution or KExtractor's own licensing terms,
please open an issue in the project repository.