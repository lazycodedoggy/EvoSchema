package io.github.evoschema.processor.dbscanner;

import java.lang.reflect.InvocationTargetException;
import org.apache.commons.lang3.StringUtils;

public final class ExecutionLogHelper
{
    private static final String PREFIX = "[EVOSCHEMA]";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final ThreadLocal<ExecutionContext> EXECUTION_CONTEXT = new ThreadLocal<>();

    private ExecutionLogHelper()
    {
    }

    public static String phaseBanner(String lifecycle, String componentName, String phase)
    {
        String message = String.format(
                "========== %s[%s][component=%s][phase=%s] ==========",
                PREFIX,
                lifecycle,
                valueOrUnknown(componentName),
                valueOrUnknown(phase)
        );
        return colorizeByLifecycleAndPhase(message, lifecycle, phase);
    }

    public static String methodEvent(String lifecycle, String phase, String componentName, String methodName, String dataSourceKey)
    {
        String message = String.format(
                "%s[%s][phase=%s][component=%s][method=%s][datasource=%s]",
                PREFIX,
                lifecycle,
                valueOrUnknown(phase),
                valueOrUnknown(componentName),
                valueOrUnknown(methodName),
                valueOrUnknown(dataSourceKey)
        );
        return colorizeByLifecycleAndPhase(message, lifecycle, phase);
    }

    public static String sqlEvent(
            String lifecycle,
            String phase,
            String componentName,
            String methodName,
            String dataSourceKey,
            String sql)
    {
        String message = String.format(
                "%s[%s][phase=%s][component=%s][method=%s][datasource=%s] sql=\"%s\"",
                PREFIX,
                lifecycle,
                valueOrUnknown(phase),
                valueOrUnknown(componentName),
                valueOrUnknown(methodName),
                valueOrUnknown(dataSourceKey),
                normalizeSql(sql)
        );
        return colorizeByLifecycleAndPhase(message, lifecycle, phase);
    }

    public static String failureEvent(
            String phase,
            String componentName,
            String methodName,
            String dataSourceKey,
            String sql,
            Throwable error)
    {
        String message;
        if (StringUtils.isBlank(sql)) {
            message = String.format(
                    "%s[FAIL][phase=%s][component=%s][method=%s][datasource=%s] reason=\"%s\"",
                    PREFIX,
                    valueOrUnknown(phase),
                    valueOrUnknown(componentName),
                    valueOrUnknown(methodName),
                    valueOrUnknown(dataSourceKey),
                    summarizeError(error)
            );
        } else {
            message = String.format(
                    "%s[FAIL][phase=%s][component=%s][method=%s][datasource=%s] sql=\"%s\" reason=\"%s\"",
                    PREFIX,
                    valueOrUnknown(phase),
                    valueOrUnknown(componentName),
                    valueOrUnknown(methodName),
                    valueOrUnknown(dataSourceKey),
                    normalizeSql(sql),
                    summarizeError(error)
            );
        }
        return colorizeRed(message);
    }

    public static String rollbackSkip(String componentName, String methodName, String dataSourceKey)
    {
        String message = String.format(
                "%s[ROLLBACK-SKIP][component=%s][method=%s][datasource=%s] compensation sql is blank",
                PREFIX,
                valueOrUnknown(componentName),
                valueOrUnknown(methodName),
                valueOrUnknown(dataSourceKey)
        );
        return colorizeYellow(message);
    }

    public static String templateBinding(String componentName, String methodName, String dataSourceKey, String policyName)
    {
        String message = String.format(
                "%s[TEMPLATE-BIND][component=%s][method=%s][datasource=%s][policy=%s]",
                PREFIX,
                valueOrUnknown(componentName),
                valueOrUnknown(methodName),
                valueOrUnknown(dataSourceKey),
                valueOrUnknown(policyName)
        );
        return colorizeCyan(message);
    }

    public static String templateFallback(
            String componentName,
            String methodName,
            String dataSourceKey,
            String templateBeanName,
            String dataSourceBeanName)
    {
        return String.format(
                "%s[TEMPLATE-FALLBACK][component=%s][method=%s][datasource=%s] jdbcTemplate=%s -> dataSource=%s",
                PREFIX,
                valueOrUnknown(componentName),
                valueOrUnknown(methodName),
                valueOrUnknown(dataSourceKey),
                valueOrUnknown(templateBeanName),
                valueOrUnknown(dataSourceBeanName)
        );
    }

    public static Throwable unwrap(Throwable error)
    {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    public static String summarizeError(Throwable error)
    {
        Throwable rootCause = unwrap(error);
        String message = StringUtils.defaultIfBlank(rootCause.getMessage(), "<no message>");
        return rootCause.getClass().getSimpleName() + ": " + message;
    }

    public static void markContext(String phase, String methodName, String dataSourceKey)
    {
        EXECUTION_CONTEXT.set(new ExecutionContext(phase, methodName, dataSourceKey));
    }

    public static void clearContext()
    {
        EXECUTION_CONTEXT.remove();
    }

    public static String currentPhaseOrUnknown()
    {
        ExecutionContext context = EXECUTION_CONTEXT.get();
        if (context == null) {
            return "<unknown>";
        }
        return valueOrUnknown(context.phase);
    }

    public static String currentMethodOrUnknown()
    {
        ExecutionContext context = EXECUTION_CONTEXT.get();
        if (context == null) {
            return "<unknown>";
        }
        return valueOrUnknown(context.methodName);
    }

    public static String currentDataSourceOrUnknown()
    {
        ExecutionContext context = EXECUTION_CONTEXT.get();
        if (context == null) {
            return "<unknown>";
        }
        return valueOrUnknown(context.dataSourceKey);
    }

    public static void overrideCurrentDataSource(String dataSourceKey)
    {
        ExecutionContext context = EXECUTION_CONTEXT.get();
        if (context == null) {
            return;
        }
        EXECUTION_CONTEXT.set(new ExecutionContext(context.phase, context.methodName, dataSourceKey));
    }

    private static String normalizeSql(String sql)
    {
        String normalized = StringUtils.normalizeSpace(StringUtils.defaultString(sql));
        return StringUtils.defaultIfBlank(normalized, "<empty>");
    }

    private static String valueOrUnknown(String value)
    {
        return StringUtils.defaultIfBlank(value, "<unknown>");
    }

    private static String colorizeByLifecycleAndPhase(String message, String lifecycle, String phase)
    {
        if ("FAIL".equalsIgnoreCase(lifecycle)) {
            return colorizeRed(message);
        }
        if (isRollbackPhase(phase)) {
            return colorizeYellow(message);
        }
        if ("PREPARE".equalsIgnoreCase(lifecycle)) {
            return colorizeCyan(message);
        }
        if ("READY".equalsIgnoreCase(lifecycle)) {
            return colorizeCyan(message);
        }
        if ("START".equalsIgnoreCase(lifecycle)) {
            return colorizeCyan(message);
        }
        if ("SUCCESS".equalsIgnoreCase(lifecycle)) {
            return colorizeGreen(message);
        }
        return message;
    }

    private static boolean isRollbackPhase(String phase)
    {
        return "PRE-DDL-ROLLBACK".equalsIgnoreCase(StringUtils.defaultString(phase));
    }

    private static String colorizeGreen(String message)
    {
        return ANSI_GREEN + message + ANSI_RESET;
    }

    private static String colorizeRed(String message)
    {
        return ANSI_RED + message + ANSI_RESET;
    }

    private static String colorizeYellow(String message)
    {
        return ANSI_YELLOW + message + ANSI_RESET;
    }

    private static String colorizeCyan(String message)
    {
        return ANSI_CYAN + message + ANSI_RESET;
    }

    private static final class ExecutionContext
    {
        private final String phase;
        private final String methodName;
        private final String dataSourceKey;

        private ExecutionContext(String phase, String methodName, String dataSourceKey)
        {
            this.phase = phase;
            this.methodName = methodName;
            this.dataSourceKey = dataSourceKey;
        }
    }
}
