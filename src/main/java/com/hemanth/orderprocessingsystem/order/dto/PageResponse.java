package com.hemanth.orderprocessingsystem.order.dto;

import java.util.List;

/**
 * Stable API pagination response independent of Spring Data's internal JSON shape.
 *
 * @param content page content
 * @param page current zero-based page number
 * @param size requested page size
 * @param totalElements total matching records
 * @param totalPages total number of pages
 * @param first whether this is the first page
 * @param last whether this is the last page
 * @param <T> response item type
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
