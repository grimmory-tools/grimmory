package org.booklore.app.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppDashboardResponse {
    /**
     * Map of scroller ID (from UserSetting dashboard config) to its list of books.
     */
    private Map<String, List<AppBookSummary>> scrollers;
}
