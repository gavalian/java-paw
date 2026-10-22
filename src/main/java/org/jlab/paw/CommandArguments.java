package org.jlab.paw;

import java.util.List;
import twig.data.H2F;

/** Parameter descriptions for interactive commands; execution remains in Commands. */
public final class CommandArguments {
    private CommandArguments() { }
    public record Spec(String command, List<String> labels, List<String> defaults, int required) { }
    private static Spec fields(String command, int required, String labels, String defaults) {
        return new Spec(command, List.of(labels.split(";", -1)), List.of(defaults.split(";", -1)), required);
    }
    static Spec spec(String command, Workspace workspace, List<String> args) {
        return switch (command) {
            case "HISTOGRAM/CREATE/1D" -> fields(command, 5, "Histogram ID;Title;Number of bins;X minimum;X maximum", ";;100;0;1");
            case "HISTOGRAM/CREATE/2D" -> fields(command, 8, "Histogram ID;Title;X bins;X minimum;X maximum;Y bins;Y minimum;Y maximum", ";;100;0;1;100;0;1");
            case "HISTOGRAM/PLOT" -> fields(command, 1, "Histogram ID;Overlay option (S)", ";");
            case "HISTOGRAM/RESET", "HISTOGRAM/DELETE", "HISTOGRAM/SHOW" -> fields(command, 1, "Histogram ID", "");
            case "HISTOGRAM/COPY", "HISTOGRAM/SMOOTH" -> fields(command, 2, "Source histogram;Output histogram ID", ";");
            case "HISTOGRAM/REBIN" -> fields(command, 3, "Source histogram;Output histogram ID;Merge factor", ";;2");
            case "HISTOGRAM/FIT" -> fields(command, 2, "Histogram ID;Fit model (G, E, P0, P1)", ";G");
            case "HISTOGRAM/ADD", "HISTOGRAM/SUB", "HISTOGRAM/MUL", "HISTOGRAM/DIV" -> fields(command, 3, "First histogram;Second histogram;Output histogram ID", ";;");
            case "HISTOGRAM/FILL" -> fill(command, workspace, args);
            case "HISTOGRAM/PUT_VECTOR/CONTENT", "HISTOGRAM/GET_VECTOR/CONTENT" -> fields(command, 2, "Histogram ID;Vector name", ";");
            case "FUNCTION/PLOT" -> fields(command, 3, "Function expression;X minimum;X maximum", ";0;1");
            case "ZONE" -> fields(command, 2, "Columns;Rows", "1;1");
            case "CD" -> fields(command, 1, "Active zone (1-based)", "1");
            case "SET" -> fields(command, 2, "Attribute (HCOL, PCOL, HWID, PWID);Value", ";1");
            case "WORKSPACE/SAVE", "WORKSPACE/OPEN", "GRAPHICS/EXPORT", "MACRO/EXEC" -> fields(command, 1, "File path", "");
            case "READ" -> fields(command, 1, "Variable name;Prompt", ";");
            case "MACRO/DELAY" -> fields(command, 1, "Delay variable", "screen_delay");
            case "MACRO/PAGE" -> fields(command, 2, "Delay in seconds;Prompt", "-1;Continue to the next screen");
            case "VECTOR/CREATE" -> fields(command, 1, "Vector declaration (name(size));Type (R or I)", ";R");
            case "VECTOR/INPUT" -> fields(command, 2, "Vector name;Values (space-separated)", ";");
            case "VECTOR/PRINT", "VECTOR/DELETE" -> fields(command, 1, "Vector name", "");
            case "VECTOR/COPY" -> fields(command, 2, "Source vector;Output vector", ";");
            case "VECTOR/DRAW", "VECTOR/PLOT" -> fields(command, 1, "Vector name;Histogram ID;Overlay option (S)", ";;");
            case "VECTOR/HFILL" -> fields(command, 2, "Vector name;Histogram ID", ";");
            case "VBIAS", "VSCALE" -> fields(command, 3, "Source vector;Scalar;Output vector", ";1;");
            case "VADD", "VMULTI", "VSUBTR", "VDIVID" -> fields(command, 3, "First vector;Second vector;Output vector", ";;");
            case "GRAPH" -> fields(command, 3, "Number of points;X vector;Y vector;Drawing option (L, P, LP)", ";;;L");
            case "SIGMA" -> fields(command, 1, "Assignment expression", "");
            default -> NtupleCommands.spec(command);
        };
    }
    private static Spec fill(String command, Workspace workspace, List<String> args) {
        // Resolve the histogram first so the next panel asks for the correct dimensions.
        if (args.size() == 1) return fields(command, 1, "Histogram ID", "");
        boolean two = false;
        if (args.size() > 1) try { two = workspace.get(args.get(1)) instanceof H2F; } catch (IllegalArgumentException ignored) { }
        return two ? fields(command, 3, "Histogram ID;X value;Y value;Weight", ";0;0;1")
                : fields(command, 2, "Histogram ID;X value;Weight", ";0;");
    }
}
