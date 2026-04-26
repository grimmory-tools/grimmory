package org.booklore.config;

import org.booklore.context.KomgaCleanContext;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.BeanPropertyWriter;

import java.util.Collection;

/**
 * Custom BeanPropertyWriter that handles clean mode filtering.
 * When clean mode is enabled:
 * - Fields ending with "Lock" are excluded
 * - Null values are excluded
 * - Empty arrays/collections are excluded
 */
public class KomgaCleanBeanPropertyWriter extends BeanPropertyWriter {
    
    protected KomgaCleanBeanPropertyWriter(BeanPropertyWriter base) {
        super(base);
    }

    @Override
    public void serializeAsProperty(Object bean, JsonGenerator gen, SerializationContext prov) throws Exception {
        if (KomgaCleanContext.isCleanMode()) {
            String propertyName = getName();
            
            // Exclude properties ending with "Lock"
            if (propertyName.endsWith("Lock")) {
                return;
            }
            
            // Exclude null values
            Object value = get(bean);
            if (value == null) {
                return;
            }
            
            // Exclude empty collections/arrays
            if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
                return;
            }
        }
        
        // Default behavior
        super.serializeAsProperty(bean, gen, prov);
    }
}
