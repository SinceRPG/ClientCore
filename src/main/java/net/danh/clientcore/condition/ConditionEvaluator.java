package net.danh.clientcore.condition;

import net.danh.clientcore.hook.HookRegistry;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ConditionEvaluator {
    private final HookRegistry hooks;

    public ConditionEvaluator(HookRegistry hooks) {
        this.hooks = hooks;
    }

    private static String strip(String input) {
        String trimmed = input.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static Double number(String input) {
        try {
            return Double.parseDouble(input.replace(",", "."));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean test(Player player, String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        ConditionLine line = ConditionLine.parse(expression);
        if (line == null) return false;
        String left = hooks.placeholders(player, line.placeholder());
        return compare(strip(left), line.operator(), strip(line.value()));
    }

    public boolean test(Player player, String legacyExpression, List<String> lines) {
        return evaluate(player, legacyExpression, lines).passed();
    }

    public Evaluation evaluate(Player player, String legacyExpression, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return new Evaluation(test(player, legacyExpression), Set.of());
        }
        Set<String> passedOptionalIds = new HashSet<>();
        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            ConditionLine line = ConditionLine.parse(raw);
            if (line == null) {
                return new Evaluation(false, Set.copyOf(passedOptionalIds));
            }
            String left = hooks.placeholders(player, line.placeholder());
            boolean passed = compare(strip(left), line.operator(), strip(line.value()));
            if (passed && line.optional() && !line.id().isBlank()) {
                passedOptionalIds.add(line.id());
            }
            if (!passed && !line.optional()) {
                return new Evaluation(false, Set.copyOf(passedOptionalIds));
            }
        }
        return new Evaluation(true, Set.copyOf(passedOptionalIds));
    }

    private boolean compare(String left, String op, String right) {
        Double leftNumber = number(left);
        Double rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) {
            return switch (op) {
                case "<=" -> leftNumber <= rightNumber;
                case ">=" -> leftNumber >= rightNumber;
                case "<" -> leftNumber < rightNumber;
                case ">" -> leftNumber > rightNumber;
                case "!=", "!" -> !leftNumber.equals(rightNumber);
                default -> leftNumber.equals(rightNumber);
            };
        }
        int compare = left.compareToIgnoreCase(right);
        return switch (op) {
            case "!=" -> compare != 0;
            case "=", "==" -> compare == 0;
            case "contains" -> left.toLowerCase().contains(right.toLowerCase());
            case "starts_with" -> left.toLowerCase().startsWith(right.toLowerCase());
            case "ends_with" -> left.toLowerCase().endsWith(right.toLowerCase());
            default -> false;
        };
    }

    public record Evaluation(boolean passed, Set<String> passedOptionalIds) {
    }

    private record ConditionLine(String id, String placeholder, String operator, String value, boolean optional) {
        static ConditionLine parse(String raw) {
            String[] split = raw.split(";", 5);
            if (split.length < 3) {
                return null;
            }
            if (split.length >= 5) {
                return new ConditionLine(split[0].trim(), split[1].trim(), normalize(split[2].trim()), split[3].trim(), split[4].trim().equalsIgnoreCase("optional"));
            }
            boolean optional = split.length >= 4 && split[3].trim().equalsIgnoreCase("optional");
            return new ConditionLine("", split[0].trim(), normalize(split[1].trim()), split[2].trim(), optional);
        }

        private static String normalize(String input) {
            return switch (input.toLowerCase()) {
                case "eq", "=", "==" -> "==";
                case "ne", "!=", "!" -> "!=";
                case "lte", "<=" -> "<=";
                case "gte", ">=" -> ">=";
                case "lt", "<" -> "<";
                case "gt", ">" -> ">";
                case "contains" -> "contains";
                case "starts", "starts_with" -> "starts_with";
                case "ends", "ends_with" -> "ends_with";
                default -> input;
            };
        }
    }
}
