package com.newswiki.dto;

public record WikiJobResult(
        int inputCount,
        int succeeded,
        int failed,
        String detail
) {
}
