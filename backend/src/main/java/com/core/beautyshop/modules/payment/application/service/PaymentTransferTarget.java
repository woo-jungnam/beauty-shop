package com.core.beautyshop.modules.payment.application.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.regex.Pattern;

public final class PaymentTransferTarget {
    private static final Pattern CODE = Pattern.compile("\\b(?:ORD-?([A-Z0-9]{32}|[A-Z0-9]{8})|SPADEP-?([A-F0-9]{32}))\\b");
    private PaymentTransferTarget() {}

    public static String parse(String content) {
        var matcher = CODE.matcher(content == null ? "" : content.toUpperCase(Locale.ROOT));
        var targets = new LinkedHashSet<String>();
        while (matcher.find()) targets.add(matcher.group(1) == null ? "SPADEP-" + matcher.group(2) : "ORD-" + matcher.group(1));
        return targets.size() > 1 ? "AMBIGUOUS" : targets.stream().findFirst().orElse("UNKNOWN");
    }
}
