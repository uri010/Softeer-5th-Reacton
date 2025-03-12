package com.softeer.reacton.domain.request.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum RequestType {
    SCREEN_ISSUE,
    HAVE_QUESTION,
    DIFFICULT,
    SOUND_ISSUE,
    TOO_FAST;

    public static List<String> getRequestTypes() {
        return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }
}
