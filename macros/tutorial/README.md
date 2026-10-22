# PAW tutorial macros

Runnable Java adaptations of every example topic in `paw-java/tut.pdf` (140 PDF pages). These reproduce analysis operations using j4np HIPO and Twig; they are not literal CERNLIB/KUIP compatibility scripts. The table describes differences explicitly.

From the workstation Executive, run:

```text
MACRO/EXEC macros/tutorial/00_run_all.kumac
MACRO/EXEC macros/tutorial/02_vectors.jmacro
MACRO/EXEC macros/tutorial/13_function_loop.jmacro 12
```

From the project directory:

```sh
./run.sh --empty --macro macros/tutorial/02_vectors.jmacro
java -Djava.awt.headless=true -jar target/paw-java-0.1.0.jar --empty --motif --macro macros/tutorial/26_histogram_2d_modes.jmacro --snapshot docs/tutorial.png
```

The `.jmacro` format is a Java method body with imports for `org.jlab.paw`, Twig data/math/widgets, Java util/nio/functions, and `java.lang.Math` methods. A `paw` MacroContext provides the shared workspace, parameters, global variables, plotting, files, and command execution. It requires a JDK 21+ and runs local Java with the same privileges as the workstation. Run macros you trust. Relative files and nested macros resolve beside the macro file. Output goes into `macros/tutorial/output`; existing output filenames are replaced on reruns. Temporary compiler files are removed. All examples are independent and preserve other workspace directories.

Java replaces Fortran/COMIS callbacks, KUIP flow control and SIGMA arrays. Existing `.kumac` support remains the documented subset in HELP. The synthetic 3354-row staff sample is seeded and contains **no real CERN personnel data**; the tutorial's external APTUPLE.DAT/pawtut.hbook files were not supplied. Nation and division strings use numeric codes, with FR=7, IT=8 and EP=5. Scalar bank columns can be typed; variable-length string/array CWN fields are not emulated. Profiles use the tutorial's mean/RMS convention. Live macros check Cancel between batches. `00_print_all.jmacro` exports one vector PDF per example and a 59-page landscape A4 `tutorial_all.pdf` containing captured pictures.

Each file starts with its source PDF viewer page and adaptation. Original highlighted values/formulas are retained where possible; spectra that depend on unavailable files are synthetic. Advanced representations are Twig Widget extensions backed by H1F/H2F/Graph3D. They do not reproduce HIGZ pixel geometry, Gouraud shading, all SURF variants, editable picture stores, native printer dispatch, or interactive mouse GCUT. These differences remain future compatibility work; no unsupported legacy command is silently accepted.

| Macro | PDF page | Tutorial topic | Adaptation / scope |
| --- | --- | --- | --- |
| [01_startup.jmacro](01_startup.jmacro) | 7 | Starting PAW Tutorial | Java macro initialization replaces PAWLOGON and ALLDEF; existing analysis objects are preserved. |
| [02_vectors.jmacro](02_vectors.jmacro) | 9 | Starting with vectors | Original tutorial vector values; Java arrays and GRAPH use Twig GraphErrors. |
| [03_matrix_vectors.jmacro](03_matrix_vectors.jmacro) | 11 | Some more vector commands | Original values; Java double[][] replaces multidimensional KUIP vectors. Output uses whitespace ASCII instead of Fortran formats. |
| [04_vector_draw_options.jmacro](04_vector_draw_options.jmacro) | 13 | VECTOR/DRAW options | Twig draw options replace PAW B/L/*/E; hatch codes and marker numbering use Twig conventions. |
| [05_vectors_histograms.jmacro](05_vectors_histograms.jmacro) | 15 | Vectors and Histograms | Original vector; distributions and bin-content insertion remain distinct operations. |
| [06_vector_operations.jmacro](06_vector_operations.jmacro) | 17 | Vector operations | Java syntax replaces KUIP/COMIS; Twig objects are plotted in the workstation. |
| [07_loop_vector_fit.jmacro](07_loop_vector_fit.jmacro) | 19 | Simple macro with loop and VECTOR fit | Java loop and Twig/Minuit polynomial fits replace DO and VECTOR/FIT. |
| [08_flow_control.jmacro](08_flow_control.jmacro) | 21 | Macros flow control | Java if/switch/while/do/for/break/continue/return and exceptions replace KUIP flow statements; no GOTO interpreter. |
| [09_more_fits.jmacro](09_more_fits.jmacro) | 23 | More on fits | Original noisy sine formula with seeded randomness; G, P3, P5 and a custom sine expression use Twig/Minuit. |
| [10_vector_read_match.jmacro](10_vector_read_match.jmacro) | 27 | VECTOR/READ with the MATCH option | Tutorial MATCH prefix and numeric columns; Java reads checked-in ASCII fixture. |
| [11_embedded_data.jmacro](11_embedded_data.jmacro) | 29 | Data embedded into a macro | Exact ten embedded rows; Java text block replaces APPLICATION DATA; temporary file is deleted. |
| [12_functions_1d.jmacro](12_functions_1d.jmacro) | 31 | Plot a few one-dimensional functions | Original expressions with the removable sinc singularity defined at x=0; Twig line styles and grid. |
| [13_function_loop.jmacro](13_function_loop.jmacro) | 33 | Plot a one-dimensional function and loop | Original loop formulas; optional integer argument defaults to 8; Java loop replaces KUIP substitution. |
| [14_parameters_return.jmacro](14_parameters_return.jmacro) | 35 | More on macro input parameters and return | Java parameters, lambdas and return replace EXEC/READ/$RETURN; command arguments replace interactive prompts for batch execution. |
| [15_global_variables.jmacro](15_global_variables.jmacro) | 36 | Global variables | Session-wide map replaces GLOBAL/CREATE/LIST/DELETE; nested helper sees the same globals. |
| [16_functions_2d.jmacro](16_functions_2d.jmacro) | 37 | Plot two-dimensional functions | Exact function formulas; Twig H2F plus added Twig widgets provide contour and surface representations. |
| [17_mandelbrot.jmacro](17_mandelbrot.jmacro) | 39 | The Mandelbrot distribution | Book MANDEL.F recurrence, range and 30-iteration limit translated into Java; colored H2 raster replaces BOX. |
| [18_functions_3d.jmacro](18_functions_3d.jmacro) | 41 | 3D functions drawing | Exact unit sphere and progressively restricted positive ranges; sampled 3D points replace the legacy implicit-surface renderer. |
| [19_histogram_creation.jmacro](19_histogram_creation.jmacro) | 43 | Histograms creation | Exact HTFUN1/HTFUN2 functions, seeded rejection sampling replaces HRNDM1; HIPO replaces HBOOK. |
| [20_histogram_file_read.jmacro](20_histogram_file_read.jmacro) | 47 | Read histograms from file and plot | Creates its own HIPO prerequisite, reads it back, lists objects and deletes an in-memory object. |
| [21_histogram_archiving.jmacro](21_histogram_archiving.jmacro) | 51 | Histogram archiving | HIPO object paths preserve the tutorial’s separate 1D/2D directories; RZ cycles and quotas are not emulated. |
| [22_multiple_fits.jmacro](22_multiple_fits.jmacro) | 53 | Multiple fits on histograms | Two range-limited Gaussian fits initialize a six-parameter combined Twig/Minuit fit. |
| [23_histogram_operations.jmacro](23_histogram_operations.jmacro) | 55 | Histogram operations | Scaled copies, arithmetic with error propagation, smoothing and HIPO output; the example avoids zero-denominator division. |
| [24_histogram_cosmetics.jmacro](24_histogram_cosmetics.jmacro) | 57 | Histogram operations: cosmetic modifications | Twig fonts, hatch, line width, grid and annotation replace hardware-font and shadow attributes. Angled text uses a custom Twig widget. |
| [25_histogram_attributes.jmacro](25_histogram_attributes.jmacro) | 59 | Histograms attributes | Data attributes round-trip in HIPO; log Y is explicitly restored as a view property rather than legacy IDOPT metadata. |
| [26_histogram_2d_modes.jmacro](26_histogram_2d_modes.jmacro) | 61 | Two-dimensional histograms representations | Four featured representations are Twig widgets over the same H2F; rendering differs from HIGZ. |
| [27_contour_levels.jmacro](27_contour_levels.jmacro) | 63 | Non equidistant contour plots | Exact contour levels; marching-squares lines replace HIGZ contour drawing. Annotation uses normalized coordinates. |
| [28_coordinate_systems.jmacro](28_coordinate_systems.jmacro) | 65 | Coordinate systems | Normalized polar/cylindrical/spherical/pseudorapidity mappings; geometric projections replace legacy LEGO coordinate conventions. |
| [29_logarithmic_lego.jmacro](29_logarithmic_lego.jmacro) | 67 | Logarithmic scales on lego plots | Positive axis endpoints and contents avoid undefined logs; independent X/Y/Z log transformations are displayed. |
| [30_histogram_subranges.jmacro](30_histogram_subranges.jmacro) | 69 | Subranges in histogram identifiers | Explicit 1-based Java crop calls replace PAW ID subrange syntax; the last crop approximates the example’s real-valued range using bins. |
| [31_stacked_lego.jmacro](31_stacked_lego.jmacro) | 71 | Stacked Lego plots | Submatrix heights are added bin by bin; cumulative LEGO and heatmap replace individually colored HIGZ stack layers. |
| [32_errors_1d.jmacro](32_errors_1d.jmacro) | 73 | Errors representation on 1D histograms | Custom rectangles and polygon envelopes supplement Twig EP bars; smoothed envelope uses Twig quadratic paths; PAW cap sizes differ. |
| [33_errors_2d_colors.jmacro](33_errors_2d_colors.jmacro) | 75 | Errors representations on 2D histograms | Exact tutorial functions; a Java error matrix supplements H2F, whose backend lacks independent bin errors; error magnitudes use absolute values. |
| [34_errors_2d_bars.jmacro](34_errors_2d_bars.jmacro) | 77 | Another way of drawing errors for 2D histograms | DERR.F is replaced with projected vertical z±sqrt(abs(z)) error bars over Twig H2F bins. |
| [35_iterative_background_fit.jmacro](35_iterative_background_fit.jmacro) | 81 | A more complex example | Synthetic spectrum replaces missing pawtut.hbook; sideband P3 and iterative two-Gaussian subtraction reproduce the analysis purpose. |
| [36_ntuple_rowwise.jmacro](36_ntuple_rowwise.jmacro) | 85 | Ntuple creation: row-wise | 3354 deterministic synthetic staff rows replace unavailable APTUPLE.DAT; all-double HIPO bank replaces RWN. |
| [37_ntuple_columnwise.jmacro](37_ntuple_columnwise.jmacro) | 87 | Ntuple creation: column-wise | HIPO typed columns replace CWN; numeric category codes replace character columns. HIPO is an event/bank format rather than HBOOK column-wise storage. |
| [38_ntuple_binning.jmacro](38_ntuple_binning.jmacro) | 91 | Automatic and user binning | Automatic finite ranges and sqrt(N) bins; explicit user booking and accumulated projection; Twig major ticks replace NDIV encoding. |
| [39_ntuple_selection_scan.jmacro](39_ntuple_selection_scan.jmacro) | 93 | Simple selection criteria on Ntuple | Original selection intent; numeric synthetic nation/division codes, Java expressions and transcript SCAN replace legacy aliases and character comparisons. |
| [40_spider_scan.jmacro](40_spider_scan.jmacro) | 97 | Option Spider in NTUPLE/SCAN | Normalized multidimensional event spiders plus population average and labelled axes; synthetic staff data. |
| [41_ntuple_masks_loops.jmacro](41_ntuple_masks_loops.jmacro) | 99 | Use of Ntuple masks and loops | Exact grade/step predicates; BitSet binary file replaces direct-access 32-bit-word PAW masks. |
| [42_ntuple_cuts.jmacro](42_ntuple_cuts.jmacro) | 103 | The use of Ntuple Cuts | Exact modulo flag cuts; persisted textual predicates and programmatic polygon cuts replace $nn and interactive mouse GCUT. Synthetic division labels use original names. |
| [43_alphanumeric_labels.jmacro](43_alphanumeric_labels.jmacro) | 105 | Alphanumeric labels | Twig custom tick labels replace HBOOK alphanumeric bins and SORT; category content moves with its label. |
| [44_profile_histograms.jmacro](44_profile_histograms.jmacro) | 107 | Ntuple and 2D histograms: profile histograms | Tutorial PROF convention is implemented explicitly: mean Y and population RMS per X bin. |
| [45_histogram_projections.jmacro](45_histogram_projections.jmacro) | 109 | Ntuple and 2D histograms: projections | H2F X/Y projections, fixed-bin slices and band projection; note Twig sliceX fixes X and returns Y bins. |
| [46_ntuple_to_vector.jmacro](46_ntuple_to_vector.jmacro) | 111 | Copy a Ntuple variable into a Vector | Copies every synthetic HIPO-style row age into a KUIP-compatible 1D vector; no COMIS callback required. |
| [47_file_merging.jmacro](47_file_merging.jmacro) | 113 | Merging of hbook files | HIPO ntuple rows concatenate; compatible histograms add with error propagation. Legacy HBOOK mixed-file merge is replaced by separate HIPO data/workspace operations. |
| [48_ntuple_chains.jmacro](48_ntuple_chains.jmacro) | 115 | Chain of Ntuples | Original five/two/ten chain expansion; files are read one at a time, with cancellation checks; no named KUIP chain interpreter. |
| [49_duplicate_rowwise.jmacro](49_duplicate_rowwise.jmacro) | 117 | RW-Ntuple duplicated with selection | Exact Children>=3 predicate; HIPO bank schema and selected rows replace NT/DUP and HFN. |
| [50_duplicate_columnwise_fr.jmacro](50_duplicate_columnwise_fr.jmacro) | 119 | CW-Ntuple duplicated with selection (1) | Nation FR is synthetic numeric code 7; typed HIPO bank replaces CWN and COMIS duplication. |
| [51_duplicate_columnwise_it.jmacro](51_duplicate_columnwise_it.jmacro) | 121 | CW-Ntuple duplicated with selection (2) | Nation IT is synthetic code 8; explicit row loop replaces the second COMIS method. |
| [52_sigma_1.jmacro](52_sigma_1.jmacro) | 123 | Examples of the SIGMA processor (1) | Exact SIGMA array formulas, with sinc(0)=1; Java doubles, Math and system APIs replace APPLICATION/$SIGMA/$RSIGMA and KUIP system-function syntax. |
| [53_sigma_2.jmacro](53_sigma_2.jmacro) | 127 | Examples of the SIGMA processor (2) | Original damped function, nine ellipses and spiral formulas use Java arrays and Math. |
| [54_graphical_histogram_add.jmacro](54_graphical_histogram_add.jmacro) | 129 | Graphical operations on histograms | Overlay keeps existing graphics; summed histogram implements the visible result of PAW + without mutating the source. |
| [55_live_dice.jmacro](55_live_dice.jmacro) | 131 | Updating plots in real time (1) | Seeded dice sampling, repeated graphics replacement and cancel checks; optional batches default 50. Headless runs skip display delays. |
| [56_live_gaussian.jmacro](56_live_gaussian.jmacro) | 133 | Updating plots in real time (2) | Seeded Gaussian batches; pathological zero denominators are excluded; output updates on the Swing event thread. |
| [57_picture_merge.jmacro](57_picture_merge.jmacro) | 135 | Merge pictures onto one plot | Captures a Twig picture and merges it as an inset; bitmap inset replaces editable HIGZ picture objects. |
| [58_postscript_output.jmacro](58_postscript_output.jmacro) | 137 | PostScript output | Twig renders PNG and vector PDF; PS/EPS preserve a raster of the canvas rather than HIGZ vector primitives. Page size follows canvas geometry. |
| [59_pawpp_panels.jmacro](59_pawpp_panels.jmacro) | 139 | PAW++ panels creation | Editable Swing command buttons replace KUIP PANEL; pixel bitmap ICON files and tutorial shell/printer commands are not reproduced. |

The interactive `00_run_all.kumac` sequence asks for a screen delay first: positive seconds advance automatically, negative seconds pause for Continue after each page, and zero advances immediately. Fractions are accepted; the default `-1` selects manual review. `MACRO/DELAY` and `MACRO/PAGE` are Java extensions; the manual dialog uses the same input mechanism as PAW/KUIP `READ` (PAW manual, printed p. 42). Enter any text and click Continue, or press Enter to retain the previous input; Cancel stops the sequence. Headless previews skip interaction and delays. Cancel operation interrupts automatic delays. `00_print_all.jmacro` exports unattended.
