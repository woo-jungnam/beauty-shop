package com.core.beautyshop.shared.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@JsonIgnoreProperties(value = {"pageable", "sort"}, ignoreUnknown = true)
public class CacheablePage<T> extends PageImpl<T> {

    @JsonCreator
    public CacheablePage(
            @JsonProperty("content") List<T> content,
            @JsonProperty("number") int number,
            @JsonProperty("size") int size,
            @JsonProperty("totalElements") long totalElements
    ) {
        super(content, PageRequest.of(number, Math.max(size, 1)), totalElements);
    }

    public static <T> CacheablePage<T> from(Page<T> page) {
        return new CacheablePage<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
