package com.truckcar.notifications.template;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves templates with placeholders in format {{variableName}}.
 */
@Component
public class TemplateResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");

    public String resolve(String templateText, Map<String, Object> variables) {
        if (templateText == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return templateText;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateText);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = variables.get(variableName);
            String replacement = (value != null) ? Matcher.quoteReplacement(value.toString()) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
}
