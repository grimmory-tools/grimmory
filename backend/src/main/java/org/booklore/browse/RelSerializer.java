package org.booklore.browse;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.util.List;

// Serializes rel following the RWPM Link Object convention used by the OPDS 2.0
// examples: a bare string for a single relation, an array for several.
public class RelSerializer extends ValueSerializer<List<String>> {

    @Override
    public void serialize(List<String> rels, JsonGenerator gen, SerializationContext context) {
        if (rels.size() == 1) {
            gen.writeString(rels.getFirst());
            return;
        }
        gen.writeStartArray();
        for (String rel : rels) {
            gen.writeString(rel);
        }
        gen.writeEndArray();
    }
}
