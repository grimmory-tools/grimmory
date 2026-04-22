package org.booklore.model.dto.komga;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KomgaUserDto {
    private String id;
    private String email;
    
    @Builder.Default
    private List<String> roles = new ArrayList<>();
    
    @Builder.Default
    private Boolean sharedAllLibraries = true;
    
    @Builder.Default
    private List<String> sharedLibrariesIds = new ArrayList<>();
    
    @Builder.Default
    private List<String> labelsAllow = new ArrayList<>();
    
    @Builder.Default
    private List<String> labelsExclude = new ArrayList<>();
}
