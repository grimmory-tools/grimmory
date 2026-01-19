package com.adityachandel.booklore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Jackson configuration for Komga API clean mode.
 */
@Configuration
public class JacksonConfig {

    public static final String KOMGA_CLEAN_OBJECT_MAPPER = "komgaCleanObjectMapper";

    @Bean(name = KOMGA_CLEAN_OBJECT_MAPPER)
    public ObjectMapper komgaCleanObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();

        // Register the custom serializer modifier on this dedicated mapper only
        mapper.setSerializerFactory(
            mapper.getSerializerFactory().withSerializerModifier(new BeanSerializerModifier() {
                @Override
                public List<BeanPropertyWriter> changeProperties(
                        com.fasterxml.jackson.databind.SerializationConfig config,
                        com.fasterxml.jackson.databind.BeanDescription beanDesc,
                        List<BeanPropertyWriter> beanProperties) {

                    return beanProperties.stream()
                        .map(KomgaCleanBeanPropertyWriter::new)
                        .collect(Collectors.toList());
                }
            })
        );

        return mapper;
    }
}
