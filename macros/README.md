# PAW++ macro collection

The [full tutorial catalogue](tutorial/README.md) contains **59 runnable Java examples**, one for each example topic in the supplied `paw-java/tut.pdf`. It includes source PDF pages, run instructions, generated output locations, and explicit differences from legacy PAW.

```text
MACRO/EXEC macros/tutorial/00_run_all.kumac
MACRO/EXEC macros/tutorial/02_vectors.jmacro
MACRO/EXEC macros/tutorial/26_histogram_2d_modes.jmacro
MACRO/EXEC macros/tutorial/00_print_all.jmacro
```

`00_print_all.jmacro` creates a PDF for each example plus a 59-page A4 catalogue in `tutorial/output`. Java macros need a JDK 21+ and execute trusted local Java code. They use j4np HIPO for data and Twig for plotting.

The six introductory `.kumac` examples below use only the implemented PAW command subset and follow the original `paw.pdf` reference manual. All run independently; relative nested macro paths work regardless of the launch directory.

| Macro | Demonstrates |
| --- | --- |
| [00_run_all.kumac](00_run_all.kumac) | Run the six introductory examples |
| [01_vectors.kumac](01_vectors.kumac) | Vector creation, printing, slices, drawing, bias and scaling |
| [02_sigma_graphs.kumac](02_sigma_graphs.kumac) | SIGMA arrays and formula evaluation, curves and scatter plots |
| [03_histogram_filling.kumac](03_histogram_filling.kumac) | Value distributions, vector filling, scalar and weighted fills |
| [04_histogram_colors.kumac](04_histogram_colors.kumac) | Black/blue outlines, black fill, red border with green fill |
| [05_vector_histogram_operations.kumac](05_vector_histogram_operations.kumac) | Vector arithmetic, histogram content transfer, subtraction and rebinning |
| [06_fits_and_functions.kumac](06_fits_and_functions.kumac) | Gaussian/exponential Minuit fits and analytic functions |

Open an example through **File → Run Macro**, enter `MACRO/EXEC 'macros/04_histogram_colors.kumac'` in the Executive, or launch `./run.sh --empty --macro macros/04_histogram_colors.kumac` from the project directory.
