# OctaPDF

![Your PDFs, in working order. The orange OctaPDF octopus floats between two sheets of paper.](images/octapdf-readme.svg)

OctaPDF is a self-hosted PDF reader and editor. Mark up a draft, turn a stack of scans into searchable documents,
or assemble a packet from several PDFs in your browser. Run it on your own machine or server.

[Everyday tools](#everyday-pdf-tools) · [Standout workflows](#standout-workflows) · [Run OctaPDF](#run-octapdf)

## Everyday PDF tools

- **Read and mark up.** Search a PDF, draw on pages, add text or images, and place a signature.
- **Organize pages visually.** Merge documents, reorder and rotate pages, remove extras, or extract the pages you need.
- **Convert and compress.** Create PDFs from images and Office files, export PDFs to images, or shrink a file before sharing it.
- **Prepare documents to share.** Add passwords, redact text, apply watermarks, or flatten forms and annotations.

## Standout workflows

- **Split a stack of scans in one pass.** Put printed divider sheets between documents before scanning.
  Auto Split detects the dividers and creates individual PDFs from the batch.
- **Make scans searchable.** Run OCR on scanned contracts or archived paperwork so you can find and copy text.
- **Compare revisions side by side.** See which text changed between two PDFs, such as updated terms in a revised agreement.
- **Repeat a workflow with Pipelines.** Save a sequence such as removing blank pages, running OCR, and compressing the result.
  Reuse it on future batches or call the REST API from a script.

## Run OctaPDF

From this checkout, start the app with the Gradle wrapper using JDK 21.

```sh
./gradlew bootRun
```

On Windows, run `gradlew.bat bootRun` from Command Prompt or `.\gradlew.bat bootRun` from PowerShell.
Open <http://localhost:8080> when the app starts. Some tools require locally installed utilities, such as Tesseract
for OCR, LibreOffice for Office conversions, and qpdf for compression. See the setup and configuration guides.
[Developer Guide](DeveloperGuide.md) · [Upstream documentation](https://docs.stirlingpdf.com)

OctaPDF is maintained by XolverLabs (DecoyINDIA) and based on Stirling-PDF v0.46.2. Stirling Tools publishes the
upstream Docker images and hosted services. [Maintainer](https://github.com/DecoyINDIA) ·
[Upstream release](https://github.com/Stirling-Tools/Stirling-PDF/releases/tag/v0.46.2) ·
[MIT license](LICENSE) · [OctaPDF logo guidelines](docs/OctaPDF-Logo-Guidelines.pdf)

## Supported Languages

Language coverage inherited from Stirling-PDF v0.46.2.

| Language                                     | Progress                               |
| -------------------------------------------- | -------------------------------------- |
| Arabic (العربية) (ar_AR)                        | ![83%](https://geps.dev/progress/83)   |
| Azerbaijani (Azərbaycan Dili) (az_AZ)        | ![82%](https://geps.dev/progress/82)   |
| Basque (Euskara) (eu_ES)                     | ![48%](https://geps.dev/progress/48)   |
| Bulgarian (Български) (bg_BG)                | ![92%](https://geps.dev/progress/92)   |
| Catalan (Català) (ca_CA)                     | ![89%](https://geps.dev/progress/89)   |
| Croatian (Hrvatski) (hr_HR)                  | ![81%](https://geps.dev/progress/81)   |
| Czech (Česky) (cs_CZ)                        | ![91%](https://geps.dev/progress/91)   |
| Danish (Dansk) (da_DK)                       | ![80%](https://geps.dev/progress/80)   |
| Dutch (Nederlands) (nl_NL)                   | ![79%](https://geps.dev/progress/79)   |
| English (English) (en_GB)                    | ![100%](https://geps.dev/progress/100) |
| English (US) (en_US)                         | ![100%](https://geps.dev/progress/100) |
| French (Français) (fr_FR)                    | ![92%](https://geps.dev/progress/92)   |
| German (Deutsch) (de_DE)                     | ![99%](https://geps.dev/progress/99)   |
| Greek (Ελληνικά) (el_GR)                     | ![91%](https://geps.dev/progress/91)   |
| Hindi (हिंदी) (hi_IN)                          | ![91%](https://geps.dev/progress/91)   |
| Hungarian (Magyar) (hu_HU)                   | ![99%](https://geps.dev/progress/99)   |
| Indonesian (Bahasa Indonesia) (id_ID)        | ![80%](https://geps.dev/progress/80)   |
| Irish (Gaeilge) (ga_IE)                      | ![91%](https://geps.dev/progress/91)   |
| Italian (Italiano) (it_IT)                   | ![99%](https://geps.dev/progress/99)   |
| Japanese (日本語) (ja_JP)                    | ![93%](https://geps.dev/progress/93)   |
| Korean (한국어) (ko_KR)                      | ![92%](https://geps.dev/progress/92)   |
| Norwegian (Norsk) (no_NB)                    | ![86%](https://geps.dev/progress/86)   |
| Persian (فارسی) (fa_IR)                      | ![87%](https://geps.dev/progress/87)   |
| Polish (Polski) (pl_PL)                      | ![95%](https://geps.dev/progress/95)   |
| Portuguese (Português) (pt_PT)               | ![91%](https://geps.dev/progress/91)   |
| Portuguese Brazilian (Português) (pt_BR)     | ![97%](https://geps.dev/progress/97)   |
| Romanian (Română) (ro_RO)                    | ![75%](https://geps.dev/progress/75)   |
| Russian (Русский) (ru_RU)                    | ![93%](https://geps.dev/progress/93)   |
| Serbian Latin alphabet (Srpski) (sr_LATN_RS) | ![60%](https://geps.dev/progress/60)   |
| Simplified Chinese (简体中文) (zh_CN)         | ![93%](https://geps.dev/progress/93)   |
| Slovakian (Slovensky) (sk_SK)                | ![69%](https://geps.dev/progress/69)   |
| Slovenian (Slovenščina) (sl_SI)              | ![94%](https://geps.dev/progress/94)   |
| Spanish (Español) (es_ES)                    | ![99%](https://geps.dev/progress/99)   |
| Swedish (Svenska) (sv_SE)                    | ![87%](https://geps.dev/progress/87)   |
| Thai (ไทย) (th_TH)                           | ![80%](https://geps.dev/progress/80)   |
| Tibetan (བོད་ཡིག་) (zh_BO)                     | ![88%](https://geps.dev/progress/88) |
| Traditional Chinese (繁體中文) (zh_TW)        | ![99%](https://geps.dev/progress/99)   |
| Turkish (Türkçe) (tr_TR)                     | ![97%](https://geps.dev/progress/97)   |
| Ukrainian (Українська) (uk_UA)               | ![96%](https://geps.dev/progress/96)   |
| Vietnamese (Tiếng Việt) (vi_VN)              | ![73%](https://geps.dev/progress/73)   |
| Malayalam (മലയാളം) (ml_ML)              | ![99%](https://geps.dev/progress/99)   |

## Stirling PDF Enterprise

Stirling PDF offers an Enterprise edition of its software. This is the same great software but with added features, support and comforts.
Check out our [Enterprise docs](https://docs.stirlingpdf.com/Pro)


## 🤝 Looking to contribute?

Join our community:
- [Contribution Guidelines](CONTRIBUTING.md)
- [Translation Guide (How to add custom languages)](HowToAddNewLanguage.md)
- [Issue Tracker](https://github.com/Stirling-Tools/Stirling-PDF/issues)
- [Discord Community](https://discord.gg/HYmhKj45pU)
- [Developer Guide](DeveloperGuide.md)
