package net.danh.clientcore.util;

import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class Formula {
    private Formula() {
    }

    public static double evaluate(String expression, Map<String, Double> variables, Random random, double fallback) {
        if (expression == null || expression.isBlank()) {
            return fallback;
        }
        try {
            return new Parser(expression, variables, random).parse();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static final class Parser {
        private final String input;
        private final Map<String, Double> variables;
        private final Random random;
        private int pos;

        private Parser(String input, Map<String, Double> variables, Random random) {
            this.input = input;
            this.variables = variables;
            this.random = random;
        }

        private double parse() {
            double value = expression();
            skipWhitespace();
            if (pos != input.length()) {
                throw new IllegalArgumentException("Unexpected token");
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += term();
                } else if (match('-')) {
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= factor();
                } else if (match('/')) {
                    value /= factor();
                } else {
                    return value;
                }
            }
        }

        private double factor() {
            skipWhitespace();
            if (match('+')) {
                return factor();
            }
            if (match('-')) {
                return -factor();
            }
            if (match('(')) {
                double value = expression();
                expect(')');
                return value;
            }
            if (peekDigit()) {
                return number();
            }
            String name = identifier();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Expected factor");
            }
            skipWhitespace();
            if (match('(')) {
                double first = expression();
                skipWhitespace();
                if (match(',')) {
                    double second = expression();
                    expect(')');
                    if ("random".equals(name)) {
                        double min = Math.min(first, second);
                        double max = Math.max(first, second);
                        return min + (random.nextDouble() * (max - min));
                    }
                    if ("min".equals(name)) {
                        return Math.min(first, second);
                    }
                    if ("max".equals(name)) {
                        return Math.max(first, second);
                    }
                }
                expect(')');
                if ("floor".equals(name)) {
                    return Math.floor(first);
                }
                if ("ceil".equals(name)) {
                    return Math.ceil(first);
                }
                if ("round".equals(name)) {
                    return Math.round(first);
                }
                throw new IllegalArgumentException("Unknown function");
            }
            return variables.getOrDefault(name, 0.0D);
        }

        private boolean match(char expected) {
            if (pos < input.length() && input.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!match(expected)) {
                throw new IllegalArgumentException("Expected " + expected);
            }
        }

        private void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        private boolean peekDigit() {
            return pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.');
        }

        private double number() {
            int start = pos;
            while (pos < input.length() && (Character.isDigit(input.charAt(pos)) || input.charAt(pos) == '.')) {
                pos++;
            }
            return Double.parseDouble(input.substring(start, pos));
        }

        private String identifier() {
            int start = pos;
            while (pos < input.length()) {
                char ch = input.charAt(pos);
                if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '-') {
                    break;
                }
                pos++;
            }
            return input.substring(start, pos).replace('-', '_').toLowerCase(Locale.ROOT);
        }
    }
}
