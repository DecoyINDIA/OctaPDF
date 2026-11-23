# OctaPDF

<p align="center">
  <img src="images/octapdf-readme.svg" alt="OctaPDF — Powerful PDF tools. Beautifully simple. The orange OctaPDF octopus in a kinetic document workspace." width="820">
</p>

<p align="center">
  <strong>Powerful PDF tools. Beautifully simple.</strong><br>
  A calm, private, local-first document workspace for editing, organizing, converting, and securing PDFs.
</p>

<p align="center">
  <a href="#quickstart">Quickstart</a> &middot;
  <a href="#guided-workflows">Guided Workflows</a> &middot;
  <a href="#the-experience">The Experience</a> &middot;
  <a href="#deployment">Deployment</a> &middot;
  <a href="docs/OctaPDF-Logo-Guidelines.pdf">Brand Guidelines</a> &middot;
  <a href="LICENSE">License (MIT)</a>
</p>

---

## Why OctaPDF?

OctaPDF is engineered for people who work with documents every day and value privacy, precision, and craft. 

Instead of routing sensitive agreements, medical forms, or financial drafts through third-party cloud converters, OctaPDF runs entirely on your local machine or private server. Your files never leave your control.

- **100% Local-First & Private:** All document parsing, rendering, OCR, and transformations execute locally. Zero external network telemetry.
- **Universal Intake Deck:** Drop a PDF onto the workspace to immediately view, reorder, annotate, redact, or shrink it—no hunting through menus.
- **Unified Workflows:** Group complex multi-step operations (e.g. merge $\rightarrow$ remove blank pages $\rightarrow$ OCR $\rightarrow$ compress) into fluid, single-pass routines.
- **Command Palette (`⌘K`):** Jump to any of the 50+ specialized PDF operations in seconds with keyboard navigation.
- **The Paper Desk Aesthetic:** A refined interface built on warm eggshell paper tones, rich espresso and bone-white ink, and deliberate, quiet interaction details.

---

## Guided Workflows

OctaPDF organizes over 50 operations into four human-centered document jobs:

### 1. Assemble & Clean
- **Multi-Tool Workspace:** Visually drag, reorder, rotate, duplicate, and delete pages using interactive thumbnails.
- **Merge & Split:** Combine disparate PDFs into unified packets, or split by chapters, fixed page counts, or file size.
- **Auto-Split Scans:** Insert printed barcode or blank divider sheets into a physical scan stack; OctaPDF detects the dividers and creates separated documents automatically.

### 2. Sign & Annotate
- **Digital Signatures:** Draw authentic signatures with smooth pressure simulation, type styled cursive approvals, or upload signature assets.
- **Draft Markups & Highlights:** Annotate text, add comments, insert callouts, and apply date or approval stamps.
- **Watermarking & Flattening:** Apply custom diagonal watermarks and flatten interactive form fields or annotations before sharing.

### 3. Redact & Secure
- **True Redaction:** Permanently black out sensitive text, account numbers, and figures, sanitizing the underlying PDF content streams.
- **Metadata Scrubbing:** Strip author identities, edit histories, printer metadata, and software fingerprints in one click.
- **Encryption & Permissions:** Lock PDFs with AES passwords and restrict printing, modification, or content copying.
- **Archival PDF/A:** Convert modern PDFs into standardized PDF/A documents for long-term legal and institutional preservation.

### 4. Compress & OCR
- **Smart Compression:** Reduce file weights for email delivery using optimized `qpdf` stream compaction without sacrificing legibility.
- **Searchable OCR:** Turn scanned contracts, receipts, and image archives into fully searchable, selectable text layers powered by Tesseract.
- **Two-Way Conversion:** Transform images, Markdown, and Office files into clean PDFs, or export pages as high-resolution PNGs and JPEGs.
- **Automated Pipelines:** Chain repetitive sequences into reusable workflows and trigger them via batch uploads or the local REST API.

---

## Quickstart

### Prerequisites
- **Java JDK 21** or later
- Optional local utilities for advanced features:
  - `qpdf` (for PDF compression and linearization)
  - `tesseract-ocr` (for OCR text recognition)
  - `libreoffice` (for Office-to-PDF conversions)

### Run from Source

Clone the repository and launch with the Gradle wrapper:

```sh
# Clone repository
git clone https://github.com/DecoyINDIA/OctaPDF.git
cd OctaPDF

# Run on macOS / Linux
./gradlew bootRun

# Run on Windows (PowerShell)
.\gradlew.bat bootRun
```

Once started, open **<http://localhost:8080>** in your browser.

---

## Deployment

### Docker (Recommended for Production)

Run OctaPDF in an isolated container with all underlying OCR and conversion engines pre-configured:

```sh
# Start with production compose configuration
docker compose -f docker-compose.prod.yml up -d
```

For hardened environments, OctaPDF supports:
- Read-only root filesystem (`read_only: true`)
- Non-root user execution
- Internal bridge networking with zero external egress

---

## The Design System

OctaPDF is designed around **"The Paper Desk"** aesthetic:
- **Light Mode:** Warm eggshell canvas (`#FAF7F2`), pure paper surfaces (`#FFFFFF`), subtle hairlines (`#E8E2D8`), and deep espresso ink (`#241E1B`).
- **Dark Mode:** Warm obsidian canvas (`#161412`), elevated charcoal tiles (`#1F1C19`), warm rims (`#36312B`), and bone-white ink (`#F5EFE6`).
- **Brand Accent:** Radiant Terracotta Octa (`#E8501C` / `#FF6B35`), used sparingly as a functional signal color for primary actions and active focus rings.

For full logo usage, geometry, and spacing rules, see [OctaPDF Logo Guidelines](docs/OctaPDF-Logo-Guidelines.pdf).

---

## Community & Contributing

We welcome contributions, bug reports, and UX feedback:
- **Contributing Guide:** [CONTRIBUTING.md](CONTRIBUTING.md)
- **Developer Guide:** [DeveloperGuide.md](DeveloperGuide.md)
- **Translations:** [HowToAddNewLanguage.md](HowToAddNewLanguage.md)
- **Repository & Issues:** [github.com/DecoyINDIA/OctaPDF](https://github.com/DecoyINDIA/OctaPDF)

---

## Attribution & License

- **Maintainer:** OctaPDF is maintained by [XolverLabs (DecoyINDIA)](https://github.com/DecoyINDIA) & Contributors.
- **Open-Source Lineage:** OctaPDF is forked from and based on [Stirling-PDF](https://github.com/Stirling-Tools/Stirling-PDF) v0.46.2 by Anthony Stirling & Stirling Tools.
- **License:** Licensed under the [MIT License](LICENSE). Third-party dependencies and their respective licenses are cataloged in `3rdPartyLicenses.json`.
