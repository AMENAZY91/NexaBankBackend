package com.nexabank.api.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta paginada genérica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private java.util.List<T> data;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    /**
     * Cursor opaco para la siguiente página (paginación por cursor de Firestore).
     * {@code null} cuando no hay más resultados o cuando se usó paginación page/size clásica.
     */
    private String nextCursor;

    public static <T> PageResponse<T> of(java.util.List<T> data, int page, int size) {
        int total = data.size();
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        java.util.List<T> paged = data.subList(fromIndex, toIndex);
        return PageResponse.<T>builder()
                .data(paged)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages((int) Math.ceil((double) total / size))
                .build();
    }
}
