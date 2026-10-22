# PAW++ Java

A Java 21+ desktop analysis workstation built around the **j4np-data HIPO** and **j4np-ui Twig** modules. The interface follows chapter 9 of [`docs/paw.pdf`](docs/paw.pdf): Executive Window, Main Browser, Graphics Window, Histogram Style Panel, Ntuple Viewer, Scanner, and Cut Editor.

The default theme uses Java's Motif look and feel with the manual's cream panels, gold menus, indigo title bars, red browser title bar, beveled controls, and histogram/ntuple icons. **Executive Window → Options → Interface → Modern** switches to FlatLaf without clearing the analysis. Theme choice is remembered; `--motif` and `--modern` override it at startup.

This is a working first release, not a complete CERNLIB reimplementation or a pixel-identical native X11 application. All application panels use separate, independently movable and resizable Swing `JFrame` windows, including viewers, scanners, cut editors, browser clones, help and command panels. Native OS title bars and window controls remain available; classic Motif colors and controls are applied inside each frame. Screen geometry, fonts, and Twig's plot rendering differ from the original. See the coverage table below before using existing PAW macros.

## Download and run a release

Download `java-paw-v<VERSION>-build.<NUMBER>.tar.gz` from the
[Releases page](https://github.com/gavalian/java-paw/releases). The archive contains
`java-paw.jar` (the shaded application with its runtime dependencies), `macros/`
including all 59 tutorials under `macros/tutorial/` and their data/helpers,
`examples/`, documentation, and build information. No Maven installation or access
to the private j4np repository is required. Java 21 or newer is required; use a
full **JDK 21+** to execute the Java tutorial macros.

Extract the archive and run from the extracted directory so example paths resolve.
Replace the sample archive and directory names below with your downloaded release:

```sh
tar -xzf java-paw-v0.1.0-build.123.tar.gz
cd java-paw-v0.1.0-build.123
java -jar java-paw.jar --motif
```

Run an example directly:

```sh
java -jar java-paw.jar --motif --empty --macro macros/tutorial/26_histogram_2d_modes.jmacro
```

Or enter `MACRO/EXEC 'macros/tutorial/00_run_all.kumac'` in the Executive to explore
all tutorials. Generated files go under the example output directories. The
standalone release JAR remains available for users who already have the examples;
the `.tar.gz` bundle provides everything together. `SHA256SUMS` covers both assets.

## Build and launch

Run from this directory:

```sh
./build.sh --with-j4np
./run.sh --motif
```

`--with-j4np` downloads [gavalian/j4np](https://github.com/gavalian/j4np) into the ignored `.deps/j4np` directory, checks out the tested revision pinned in `scripts/j4np.ref`, and compiles and installs `j4np-ui`, `j4np-data`, and their required modules into the local Maven cache before building this project. Subsequent workstation-only builds can use `./build.sh`. Maven and JDK 21 or newer are required. The first build may download Maven dependencies. The module version defaults to `1.2.1`. Use `J4NP_VERSION=...` to change it for both builds. To build a different upstream revision, set `J4NP_REF` to its commit SHA. To use an existing local checkout without changing its revision, run `J4NP_DIR=/absolute/path/to/j4np ./build.sh --with-j4np`. Additional Maven goals and options follow `--with-j4np`, for example `./build.sh --with-j4np clean verify`.

Maven packages the application as an **uber JAR** by default, using the Shade plugin. With the j4np modules installed, build it directly with:

```sh
mvn clean package
```

The distributable is `target/paw-java-0.1.0.jar`. It contains the application and its runtime dependencies, including j4np/Twig and FlatLaf, merges service registrations, and sets the executable main class. Copy this one JAR to another computer with Java 21 or newer; no Maven installation or dependency JARs are needed to run it. `original-paw-java-0.1.0.jar` is Maven's unbundled intermediate and should not be distributed. Tutorial macros and user data remain separate files; executing `.jmacro` files requires a JDK 21+.

Launch the bundled JAR with:

```sh
java -jar target/paw-java-0.1.0.jar --modern
./run.sh --empty
```

The Executive, Main Browser, Graphics, and Histogram Style windows open separately and share one analysis session. Move, resize or minimize each window normally. Closing the Browser, Graphics or Style window hides it; reopen it from **Executive → View**, or choose **Restore Window Layout** to arrange and reveal all four. A new plot also reopens hidden graphics. Closing the Executive or choosing **File → Quit** closes the session and all its windows. Auxiliary viewers, scanners, cut editors, help and command panels close individually.

Startup normally loads three reproducible sample histograms: Gaussian `10`, exponential `20`, and correlated 2-D `30`. `--empty` starts with empty memory. Double-click an object to plot it; right-click for fits, smoothing, rebinning, copying, reset, or deletion.

With multiple graphics regions, each fresh plot uses the active region and advances to the next, wrapping back to the first after the last region. `H/PLOT id S` overlays the most recently plotted region without advancing; `S` is case-insensitive. **Superimpose** uses the same behavior. `CD region` or the active-zone selector explicitly chooses the destination for the next plot or overlay. The selector shows the next fresh-plot destination. Java macro plots and custom plots follow the same progression; annotations and axis changes remain attached to the most recent plot. A single-region canvas retains its usual replacement/overlay behavior.

## HIPO analysis

1. In the Executive or Browser **File** menu choose **Open HIPO Data**. The browser lists the file's bank schemas under **Ntuples**.
2. Double-click a bank to open its Ntuple Viewer. Column names are read from the HIPO dictionary. Numerical bank columns are evaluated as doubles; very large 64-bit integers may lose precision beyond 2^53.
3. Set X to a column or expression such as `sqrt(px^2+py^2)`. Leave Y blank for 1-D; specify Y for 2-D.
4. Choose bin counts and explicit axis ranges. Set a cut such as `pid==11 && abs(vz)<5`, or use the Cut Editor's comparison rows and negation buttons.
5. Choose **Plot** to project selected rows into a Twig histogram in the current memory directory. **Scan** opens a sortable table containing up to the first 1000 accepted rows. Double-clicking a variable immediately projects it using the viewer's current bins and range.

First row is **zero-based**. First/count refer to consecutive rows of the selected bank across events, not event numbers. Count bounds the number of input bank rows examined before cuts. Data is streamed rather than loaded in full. Projections, scans, commands, and storage operations run in a background worker; a second operation waits until the first has completed. Cancel requests interruption; streaming operations and macros check interruption. A fit or file write may finish before it can stop.

Expressions support numerical columns, `pi`, parentheses, `+ - * / % ^`, comparisons `< <= == != >= >`, Boolean `&& || !`, and `sqrt abs sin cos tan sinh cosh exp log log10 floor int pow mod min max`. Exponentiation is right-associative. `log` is natural logarithm. Comparisons involving nonfinite values evaluate false; projection skips nonfinite coordinates. The expression language never executes Java or shell commands. Cuts are numerical expressions, not the full KUIP/Fortran language.

## Executive and macros

Type `HELP` for the complete implemented command reference. Enter executes the Input Pad; Shift+Enter inserts a line break. Up/Down recall submitted commands. The input **H** toggle holds editable commands until released; the transcript **H** toggle holds its scroll position. Save Transcript and Search are in the Executive menus.

Interactive commands with missing required arguments open a **Command arguments** panel. Supplied arguments are already filled in, and numeric booking fields offer editable defaults. For example, `HI/BOOK` asks for the histogram ID, title, number of bins, and X limits; `HI/BOOK 40 'My spectrum'` retains the ID and title. **OK** submits, **Reset** restores the original fields, and **Cancel**, Escape, or closing the panel cancels the pending command. Complete commands execute immediately. Histogram creation from the File menu uses the same panel. `HI/` is accepted alongside `H/`, `HISTO/`, and `HISTOGRAM/`. Macro files and headless previews retain strict argument validation without opening these panels.

Examples:

```text
H/BOOK 40 'My spectrum' 100 -5 5
HISTOGRAM/FILL 40 0.25
H/PLOT 40
DEMO
ZONE 2 2
CD 1
H/PLOT 10
H/FIT 10 G
CD 2
H/PLOT 20
CD 3
H/PLOT 30
HISTOGRAM/REBIN 10 coarse 2
HISTOGRAM/SHOW coarse
WORKSPACE/SAVE 'analysis.hipo'
```

`CD` selects a graphics region (1-based); `CDIR` changes a memory directory under `//PAWC`. Identifiers without a full path resolve in that directory. The browser exposes populated subdirectories and their parent. Quotes preserve spaces in titles and filenames.

**Main Browser → Macro** displays folders and `.kumac` / `.jmacro` files, starting in the directory from which the application was launched. Double-click a folder to browse, **.. (Up)** to go to its parent, or a macro to execute it. The current folder is retained when switching browser categories. `run.sh` preserves your launch directory.

**File → Run Macro** or `MACRO/EXEC 'examples/demo.kumac'` executes command macros, one command per line. Blank lines and lines starting with `#` or `*` are ignored; `|` introduces an inline comment. Execution stops at the first error and reports the filename and line. Nested macros resolve relative to their parent file and are limited to eight levels. `.kumac` implements the command subset in HELP, rather than the full KUIP language.

The [macro catalogue](macros/README.md) includes six introductory PAW command examples and **59 Java tutorial examples** corresponding to every topic in the supplied `tut.pdf`. The [tutorial coverage table](macros/tutorial/README.md) links each file to its source PDF page and explains adaptations. Run the complete sequence with `MACRO/EXEC 'macros/tutorial/00_run_all.kumac'`. Both `00_run_all.kumac` launchers first ask for a delay in seconds: positive numbers automatically advance after that delay (fractions such as `0.5` work), negative numbers show **Continue** after each example, and `0` advances immediately. The default is `-1` for manual review. Invalid input is requested again. The Java commands `MACRO/DELAY name` and `MACRO/PAGE seconds 'prompt'` implement this choice. Manual mode uses the same input window as the documented `READ name [prompt]` statement. The input window waits for **Continue** (or Enter); **Cancel**, Escape, or closing that window stops the sequence. Any input is accepted. Previously entered input is offered as the default, and an empty reply retains it. `READ` values may be referenced as `[name]` in subsequent command arguments. The Executive **Cancel operation** button interrupts automatic delays. Headless/snapshot runs retain defaults and skip interaction and delays; `00_print_all.jmacro` remains an unattended export.

Each example owns a separate memory directory; generated data/plots go to `macros/tutorial/output`.

`.jmacro` files are ordinary Java method bodies compiled locally by a **JDK 21+**. They receive a `paw` MacroContext for workspace access, plotting, arrays, parameters, globals, files and command execution. Java replaces tutorial Fortran/COMIS callbacks, KUIP flow control, and complex SIGMA calculations. These trusted local programs have the same process privileges as the application. They are distinct from the restricted numerical expression parser used by the Ntuple Viewer. `./run.sh --empty --macro macros/tutorial/02_vectors.jmacro` runs an example at startup.

The command language also supports 1-D vectors, slices, vector arithmetic, histogram filling/content transfer, graphs, a scalar/array SIGMA subset, and PAW basic color encodings via `SET HCOL/PCOL/HWID/PWID`. Earlier plots retain their colors when a later zone changes style.

Rebin factors must divide the bin count. Histogram arithmetic checks compatible binning and propagates independent bin errors; division by zero fails without installing a partially computed output. Smoothing computes a three-bin weighted average. Arithmetic and rebinning use Twig's float histogram storage, so small floating-point differences are expected. Gaussian, exponential, constant, and linear fits use Twig's Minuit fitter; fit parameters and chi-square appear in the transcript. Fitter diagnostics also appear in the launch terminal. The inherited fitter does not expose a complete convergence status through this UI.

## Ntuple commands

All twenty commands in the PAW Ntuple reference and the four MASK commands are available through `NT/HELP` and `MASK/HELP`. They keep PAW numeric IDs and syntax while using HIPO banks and Java `.jmacro` row callbacks. Creation, ASCII input, weighted projection, scan/dump, cuts, persistent masks, chains, merge/recovery, principal components and bounded caching share the same browser/viewer registry. Generate sample data with `NT/RANDOM 'a/I,b/F,c' 10000 'random.hipo'` (default float type, optional ID and seed). See the [Ntuple reference and storage adaptations](docs/ntuples.md), or run `MACRO/EXEC 'macros/ntuple_demo.jmacro'` for a four-region example.

## Storage and plot output

**Save Workspace** writes histograms, graphs and registered Ntuples into an actual HIPO file using `j4np.hipo5.io.HipoWriter`. Each dataset record contains its `//PAWC/...` path and Twig's JSON representation in HIPO nodes. Group `32000` is reserved: item `1` identifies `PAW-JAVA-WORKSPACE:1`; item `2` holds the object path; item `3` holds dataset JSON. The file can be read with ordinary j4np HIPO tools.

**Open Workspace** validates and reads that format, then replaces memory. Failed validation preserves the existing objects. Workspace and graphics writes use a temporary file and atomic replacement when supported. The file chooser asks before replacing an existing file; an explicit `WORKSPACE/SAVE` command replaces the specified file.

Event data files and analysis snapshots both use `.hipo`, but have different contents: use **Open HIPO Data** for bank schemas and **Open Workspace** for histogram snapshots. Snapshots contain histogram/graph data, attributes, paths, and native HIPO Ntuples with IDs, schemas, titles and dataset identities; they do not include open source files, viewer cuts, macros, transcript, fit functions, or window positions. Save cuts and transcript separately. Serialization follows Twig's numeric precision and statistics conventions.

Graphics **Export** writes PNG or vector PDF using Twig's own renderer, plus raster PostScript/EPS. Tutorial PRINTALL creates individual vector PDFs and a multipage A4 catalogue of captured pictures. A single graphics canvas supports up to 16 regions. The Style Panel controls titles, line/fill colors, line width, linear/log Y, axis ranges, drawing mode, statistics, and overlays. Twig's native graphics context menu remains available for its additional operations.

## Manual coverage

| Manual area | This release |
| --- | --- |
| Executive Window (§9.1) | Input and transcript pads, holds, command history, search, transcript saving, command reference |
| Main Browser (§9.2) | Memory objects/directories, colored icons, double-click actions, histogram context menus, HIPO banks, browser snapshot clone |
| Graphics (§9.3) | Twig H1F/H2F/functions, overlays, regions, PNG/PDF export, native Twig context menu |
| Histogram Style (§9.4) | Core attributes, titles, axis ranges, log Y, draw modes, statistics, fits and rebinning |
| Ntuple commands (§6.5) | All twenty reference commands plus four MASK commands, with HIPO storage and Java callbacks; see [Ntuple reference](docs/ntuples.md) |
| Ntuple Viewer (§9.5) | Shared HIPO registry, PAW IDs, weighted X/Y expressions, cuts, one-based row windows, projections and scans |
| Cut Editor / Scanner (§9.6) | Boolean expressions, comparison-row builder, negation, cut-file saving/loading, sortable scan table |
| Command panels (§9.7) | Built-in launcher and Java macro-created editable command panels; bitmap ICON syntax differs |
| Legacy PAW analysis | Supported histogram operations and fit models documented by `HELP` |

Tutorial Java macros add profiles (mean/RMS), HIPO ntuple creation/selection/merging/chains, masks, labels, custom fits, 3-D point clouds, surface/lego/contour widgets, coordinate transforms, error envelopes, live updates and picture insets. These use the same j4np/Twig backend; consult the catalogue for differences in each example. The tutorial's unavailable external staff/spectrum files are replaced by reproducible synthetic fixtures.

Compatibility still differs from CERN PAW: HBOOK/RZ/ZEBRA import, full KUIP spelling/abbreviations and macro syntax, Fortran/COMIS execution, multidimensional KUIP vectors, native bitmap ICON panels, hardware-font codes, all HIGZ surface/shading variants, editable HIGZ pictures, native printer dispatch and LaTeX export are not implemented. Java equivalents or documented approximations cover the tutorial topics; this is not full legacy binary or script compatibility.

## Validation and preview

```sh
mvn test
java -Djava.awt.headless=true -jar target/paw-java-0.1.0.jar --motif --snapshot docs/motif.png
java -Djava.awt.headless=true -jar target/paw-java-0.1.0.jar --modern --snapshot docs/modern.png
```

Tests exercise actual HIPO event generation and streaming, cuts and expressions, memory snapshots including 2-D histograms, replacement and invalid-file handling, histogram error propagation, commands/macros, a numerical Gaussian fit, and PNG/PDF output. Snapshot mode composites the same window contents offscreen for visual inspection; it does not open OS windows. Interactive mode uses independent frames, rather than an enclosing desktop.

## Source organization

- `Workspace`: analysis state, HIPO persistence, streaming rows, projections, histogram operations.
- `Expression`: column expressions and cut parsing.
- `Commands`, `Vectors`, `NtupleCommands`: PAW-style commands, vector/Ntuple operations and command macros.
- `NtupleStore`, `NtupleMask`, `NtupleCallback`, `HipoRecovery`: native bank persistence, persistent selections, compiled row callbacks and physical file recovery.
- `GraphicalCutPrompt`, `NtupleCloud`: interactive mouse cuts and three/four-variable rendering.
- `JavaMacros`, `MacroContext`, `TutorialTable`: Java tutorial macros and typed HIPO ntuples.
- `TutorialGraphics`, `PlotText`, `PictureInset`: Twig widget extensions for tutorial representations.
- `Workstation`: shared analysis session, browsers, graphics, style controls, ntuple/cut/scanner windows and worker coordination.
- `WorkstationWindow`: independent Swing frames, per-window menus, theme updates and window lifecycle; headless preview shells share the same content.
- `Theme`: Motif and FlatLaf styling.
- `GraphicsExport`: checked plot export with atomic replacement.
- `Main`: startup options and offscreen previews.

The upstream source modules `j4np-data` and `j4np-ui` remain the backend and plotting implementations; PAW Java adds the workstation and command layer around them.

## Command help browser

`HELP` opens a searchable command tree. `HELP HISTOGRAM`, `HISTOGRAM/HELP`, and `HI/HELP` open histogram help; `HELP VECTOR`, `VECTOR/HELP`, and `V/HELP` open vector help. The same forms work for Function, Graphics, Workspace, Macro, and KUIP/general commands, including nested paths such as `HISTOGRAM/CREATE/HELP`. Bare group names also open their help browser. `HELP HI/BOOK` selects that command directly. `CHELP` accepts the same command/group paths; `USAGE command` writes a syntax reminder to the transcript.

Select a command to see its syntax, description, and parameter defaults. **Use command** places it in the Input Pad for editing, and **Arguments...** opens its parameter form. The Executive and Main Browser **Commands** menus expose all groups. Parameter-panel **Help** opens the selected command's documentation. Ntuple help describes the existing Ntuple Viewer workflow and identifies unsupported legacy command-line operations. Noninteractive command views print the same scoped help as text.

## CI and automatic releases

[Build, test and release](.github/workflows/ci-release.yml) runs on pull requests into `main`, pushes to `main` (including merged pull requests), and manual dispatch. It uses Java 21 to download and build the pinned j4np modules from source, runs the application tests, builds the shaded JAR, and verifies it by rendering a headless snapshot.

After a successful build on `main`, the workflow creates a GitHub release tagged `v<project-version>-build.<workflow-run-number>` at the exact built commit. Each release includes the runnable shaded JAR, `SHA256SUMS`, and `build-info.txt` recording both source revisions. Pull requests only build and test. Manual runs on other branches do not publish. Re-running an already published run leaves its release intact.

The workflow uses the built-in `GITHUB_TOKEN` with `contents: write` only in the publishing job. **j4np currently requires authenticated access:** add an Actions repository secret named `J4NP_READ_TOKEN` with read-only Contents access to `gavalian/j4np` (a fine-grained token must be granted access to that repository). The built-in token cannot read a different private repository. If j4np becomes public, this secret is optional. Fork pull requests do not receive this secret and cannot build the private dependency. For local authenticated downloads, use `J4NP_REPOSITORY=git@github.com:gavalian/j4np.git ./build.sh --with-j4np`. GitHub Actions must be enabled for the repository and allowed by organization policy to publish releases. Commit and push this workflow to GitHub to activate it. To update j4np, change its commit in `scripts/j4np.ref`, update its version there and in `pom.xml`/`build.sh` if needed, then run the complete build before merging.

The repository contains application sources, documentation, manuals and macros. Downloaded j4np sources, Maven output, and generated analysis files are ignored. The original distribution directory is preserved as a migration backup.

Releases also attach `java-paw-v<VERSION>-build.<NUMBER>.tar.gz`, containing the
shaded JAR as `java-paw.jar`, macros (including `macros/tutorial`), examples, and
documentation under one directory. Packaging excludes generated `output` folders.
CI extracts this archive and runs both `examples/demo.kumac` and the complete
59-topic tutorial with the packaged JAR before publishing. Checksums cover the
standalone JAR and archive.
