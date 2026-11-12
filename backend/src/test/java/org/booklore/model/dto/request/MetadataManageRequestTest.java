package org.booklore.model.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataManageRequestTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestsWithOnlyAValueListStayValid() {
        MergeMetadataRequest merge = mapper.readValue(
                "{\"metadataType\":\"tags\",\"targetValues\":[\"Zebra\"],\"valuesToMerge\":[\"Zebra\",\"Zebu\"]}", MergeMetadataRequest.class);
        DeleteMetadataRequest delete = mapper.readValue(
                "{\"metadataType\":\"tags\",\"valuesToDelete\":[\"Zebra\"]}", DeleteMetadataRequest.class);

        assertThat(validator.validate(merge)).isEmpty();
        assertThat(merge.isSelectAll()).isFalse();
        assertThat(merge.getValuesToMerge()).containsExactly("Zebra", "Zebu");
        assertThat(validator.validate(delete)).isEmpty();
        assertThat(delete.isSelectAll()).isFalse();
        assertThat(delete.getValuesToDelete()).containsExactly("Zebra");
    }

    @Test
    void selectAllRequestsNeedNoValueList() {
        DeleteMetadataRequest delete = mapper.readValue(
                "{\"metadataType\":\"tags\",\"selectAll\":true,\"search\":\"zeb\",\"excludedValues\":[\"Zebu\"]}", DeleteMetadataRequest.class);

        assertThat(validator.validate(delete)).isEmpty();
        assertThat(delete.isSelectAll()).isTrue();
        assertThat(delete.getSearch()).isEqualTo("zeb");
        assertThat(delete.getExcludedValues()).containsExactly("Zebu");
    }

    @Test
    void requestsWithNeitherAreRejected() {
        assertThat(validator.validate(mapper.readValue("{\"metadataType\":\"tags\"}", DeleteMetadataRequest.class))).hasSize(1);
        assertThat(validator.validate(mapper.readValue("{\"metadataType\":\"tags\",\"valuesToDelete\":[]}", DeleteMetadataRequest.class))).hasSize(1);
        assertThat(validator.validate(mapper.readValue("{\"metadataType\":\"tags\",\"targetValues\":[\"Zebra\"]}", MergeMetadataRequest.class))).hasSize(1);
    }
}
