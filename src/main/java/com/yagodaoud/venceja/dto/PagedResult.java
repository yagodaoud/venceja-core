package com.yagodaoud.venceja.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PagedResult<T> {
    private List<T> content;
    private long totalElements;
    private int page;
    private int size;
}
