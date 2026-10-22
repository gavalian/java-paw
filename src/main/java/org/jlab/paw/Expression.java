package org.jlab.paw;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Small, deterministic expression language; never executes Java or shell code. */
public final class Expression {
    private Expression() { }
    public static ToDoubleFunction<double[]> compile(String source, List<String> columns) {
        Parser parser = new Parser(source, columns);
        ToDoubleFunction<double[]> result = parser.or();
        parser.space();
        if (parser.position != source.length()) throw parser.error("Unexpected token");
        return result;
    }
    public static boolean truth(double value) { return Double.isFinite(value) && value != 0; }
    private static final class Parser {
        final String source;
        final List<String> columns;
        int position;
        Parser(String source, List<String> columns) { this.source = source; this.columns = columns; }
        IllegalArgumentException error(String text) { return new IllegalArgumentException(text + " at position " + (position + 1) + " in " + source); }
        void space() { while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++; }
        boolean take(String token) { space(); if (!source.startsWith(token, position)) return false; position += token.length(); return true; }
        ToDoubleFunction<double[]> or() {
            var left = and();
            while (take("||")) { var a = left; var b = and(); left = row -> truth(a.applyAsDouble(row)) || truth(b.applyAsDouble(row)) ? 1 : 0; }
            return left;
        }
        ToDoubleFunction<double[]> and() {
            var left = comparison();
            while (take("&&")) { var a = left; var b = comparison(); left = row -> truth(a.applyAsDouble(row)) && truth(b.applyAsDouble(row)) ? 1 : 0; }
            return left;
        }
        ToDoubleFunction<double[]> comparison() {
            var a = sum();
            for (String op : new String[]{"<=", ">=", "==", "!=", "<", ">"}) {
                if (take(op)) { var b = sum(); return row -> {
                    double x = a.applyAsDouble(row), y = b.applyAsDouble(row);
                    if (!Double.isFinite(x) || !Double.isFinite(y)) return 0;
                    return switch (op) { case "<=" -> x <= y ? 1 : 0; case ">=" -> x >= y ? 1 : 0;
                        case "==" -> x == y ? 1 : 0; case "!=" -> x != y ? 1 : 0; case "<" -> x < y ? 1 : 0; default -> x > y ? 1 : 0; };
                }; }
            }
            return a;
        }
        ToDoubleFunction<double[]> sum() {
            var left = product();
            while (true) {
                if (take("+")) { var a = left; var b = product(); left = row -> a.applyAsDouble(row) + b.applyAsDouble(row); }
                else if (take("-")) { var a = left; var b = product(); left = row -> a.applyAsDouble(row) - b.applyAsDouble(row); }
                else return left;
            }
        }
        ToDoubleFunction<double[]> product() {
            var left = unary();
            while (true) {
                if (take("*")) { var a = left; var b = unary(); left = row -> a.applyAsDouble(row) * b.applyAsDouble(row); }
                else if (take("/")) { var a = left; var b = unary(); left = row -> a.applyAsDouble(row) / b.applyAsDouble(row); }
                else if (take("%")) { var a = left; var b = unary(); left = row -> a.applyAsDouble(row) % b.applyAsDouble(row); }
                else return left;
            }
        }
        ToDoubleFunction<double[]> unary() {
            if (take("!")) { var a = unary(); return row -> truth(a.applyAsDouble(row)) ? 0 : 1; }
            if (take("-")) { var a = unary(); return row -> -a.applyAsDouble(row); }
            if (take("+")) return unary();
            var a = atom();
            if (take("^")) { var b = unary(); return row -> Math.pow(a.applyAsDouble(row), b.applyAsDouble(row)); }
            return a;
        }
        ToDoubleFunction<double[]> atom() {
            if (take("(")) { var value = or(); if (!take(")")) throw error("Expected )"); return value; }
            space(); int start = position;
            if (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                while (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) position++;
                if (position < source.length() && (source.charAt(position) == 'e' || source.charAt(position) == 'E')) {
                    position++; if (position < source.length() && (source.charAt(position) == '+' || source.charAt(position) == '-')) position++;
                    while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
                }
                double value;
                try { value = Double.parseDouble(source.substring(start, position)); } catch (NumberFormatException ex) { throw error("Invalid number"); }
                return row -> value;
            }
            while (position < source.length() && (Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_')) position++;
            if (start == position) throw error("Expected number, column, or function");
            String name = source.substring(start, position);
            if (take("(")) {
                var a = or();
                if (take(",")) {
                    var b = or(); if (!take(")")) throw error("Expected )");
                    return switch (name) { case "atan2" -> row -> Math.atan2(a.applyAsDouble(row),b.applyAsDouble(row));
                        case "sign" -> row -> Math.copySign(Math.abs(a.applyAsDouble(row)),b.applyAsDouble(row));
                        case "pow" -> row -> Math.pow(a.applyAsDouble(row), b.applyAsDouble(row));
                        case "mod" -> row -> a.applyAsDouble(row) % b.applyAsDouble(row);
                        case "min" -> row -> Math.min(a.applyAsDouble(row), b.applyAsDouble(row));
                        case "max" -> row -> Math.max(a.applyAsDouble(row), b.applyAsDouble(row)); default -> throw error("Unknown two-argument function " + name); };
                }
                if (!take(")")) throw error("Expected )");
                return switch (name) { case "sqrt" -> row -> Math.sqrt(a.applyAsDouble(row)); case "abs" -> row -> Math.abs(a.applyAsDouble(row));
                    case "sin" -> row -> Math.sin(a.applyAsDouble(row)); case "cos" -> row -> Math.cos(a.applyAsDouble(row));
                    case "cosh" -> row -> Math.cosh(a.applyAsDouble(row));
                    case "sinh" -> row -> Math.sinh(a.applyAsDouble(row));
                    case "floor" -> row -> Math.floor(a.applyAsDouble(row));
                    case "int" -> row -> { double v=a.applyAsDouble(row); return v<0?Math.ceil(v):Math.floor(v); };
                    case "asin" -> row -> Math.asin(a.applyAsDouble(row)); case "acos" -> row -> Math.acos(a.applyAsDouble(row)); case "atan" -> row -> Math.atan(a.applyAsDouble(row));
                    case "tanh" -> row -> Math.tanh(a.applyAsDouble(row)); case "tan" -> row -> Math.tan(a.applyAsDouble(row)); case "exp" -> row -> Math.exp(a.applyAsDouble(row));
                    case "log" -> row -> Math.log(a.applyAsDouble(row)); case "log10" -> row -> Math.log10(a.applyAsDouble(row));
                    default -> throw error("Unknown function " + name); };
            }
            if (name.equals("pi")) return row -> Math.PI;
            if (name.equals("true")) return row -> 1;
            if (name.equals("false")) return row -> 0;
            int column = columns.indexOf(name);
            if (column < 0) throw error("Unknown column " + name);
            return row -> row[column];
        }
    }
}
