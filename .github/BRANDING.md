# KExtractor on GitHub

The repository uses the website's connected K symbol, outlined Poppins wordmark
and palette: `#4300FF`, `#0065F8`, `#00CAFF`, `#00FFDE`.

## Assets

| Asset | Purpose |
|---|---|
| [logo-on-light.svg](assets/logo-on-light.svg) | README logo on a light surface |
| [logo-on-dark.svg](assets/logo-on-dark.svg) | README logo on a dark surface |
| [social-preview.png](assets/social-preview.png) | Repository link preview; 1280 × 640, solid background |
| [social-preview.svg](assets/social-preview.svg) | Editable vector source of the preview |
| [avatar.png](assets/avatar.png) | Square 1024 × 1024 icon for a project profile or organization |

The SVG lettering is converted to paths and needs no installed fonts. The logo
variants and avatar are copied from the existing website brand kit. The social
preview uses the same mark, typography and colors, with an illustrative
semantic graph. Its text is part of the image, not live extraction output.

## Repository settings

The README references these files directly and will display the branding when
the changes are pushed to GitHub. Uploading a social preview is a separate
repository setting: open **Settings → General → Social preview → Edit → Upload
an image** and select `assets/social-preview.png` from this directory.
[GitHub's instructions](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/customizing-your-repositorys-social-media-preview)
specify PNG/JPG/GIF under 1 MB and recommend 1280 × 640 for best display.

Suggested repository description:

> Linguistics-powered knowledge extraction: turn English text into semantic
> graphs with CoreNLP, VerbNet and contextual WordNet disambiguation.

Suggested topics:

```text
natural-language-processing  information-extraction  knowledge-graph
computational-linguistics  semantic-role-labeling  linked-data
word-sense-disambiguation  neurosymbolic-ai  java  verbnet  wordnet  rdf
```

`avatar.png` can be used if the project has its own organization or profile.
The repository's social preview is configured independently of the owner's
account avatar. No account or remote repository settings are changed by the
asset generator.

## Regenerate

With the website's existing dependencies installed, run from the repository root:

```bash
node kextractor-web/kextractor/scripts/generate-github-brand.cjs
```

The generator uses the local Poppins font files and the image/font libraries
already supplied by the website dependencies. It makes no network calls.
