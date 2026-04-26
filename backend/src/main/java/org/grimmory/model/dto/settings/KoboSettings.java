package org.grimmory.model.dto.settings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class KoboSettings {
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private boolean convertToKepub = true;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int conversionLimitInMb = 100;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private boolean convertCbxToEpub = false;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int conversionLimitInMbForCbx = 100;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private boolean forceEnableHyphenation = false;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private int conversionImageCompressionPercentage = 85;
    @Builder.Default @JsonSetter(nulls = Nulls.SKIP)
    private boolean forwardToKoboStore = true;
}
