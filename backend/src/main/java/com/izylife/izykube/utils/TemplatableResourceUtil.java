package com.izylife.izykube.utils;

import com.izylife.izykube.enums.TemplatableResourceKind;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class TemplatableResourceUtil {
    private static final Set<String> TEMPLATABLE_KINDS =
            Arrays.stream(TemplatableResourceKind.values())
                    .map(TemplatableResourceKind::getKind)
                    .collect(Collectors.toSet());

    public static boolean isTemplatable(String kind) {
        return TEMPLATABLE_KINDS.contains(kind);
    }
}
