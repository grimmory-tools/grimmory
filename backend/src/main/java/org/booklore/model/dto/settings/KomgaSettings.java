package org.booklore.model.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KomgaSettings {
   private String rememberMeKey; 
   private Integer rememberMeDurationInSeconds;
}
