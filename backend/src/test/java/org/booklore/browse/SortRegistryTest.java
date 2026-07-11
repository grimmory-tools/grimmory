package org.booklore.browse;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import org.booklore.exception.APIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SortRegistryTest {

    @Mock
    private Root<Object> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;

    @Test
    void registersAndExposesKeysInOrder() {
        SortRegistry<Object> registry = new SortRegistry<>()
                .register("id", ascOn("id"))
                .register("title", ascOn("title"));
        assertTrue(registry.has("id"));
        assertTrue(registry.has("title"));
        assertEquals(List.of("id", "title"), List.copyOf(registry.keys()));
    }

    @Test
    void dispatchesEachTermToItsBuilderInOrder() {
        Order idOrder = mock(Order.class);
        Order titleOrder = mock(Order.class);
        SortRegistry<Object> registry = new SortRegistry<>()
                .register("title", ctx -> List.of(titleOrder))
                .register("id", ctx -> List.of(idOrder));

        List<Order> orders = registry.toOrders(
                List.of(new SortTerm("title", false), new SortTerm("id", false)), root, query, cb, 7L);

        assertEquals(List.of(titleOrder, idOrder), orders);
    }

    @Test
    void passesDirectionAndUserIdToBuilder() {
        boolean[] sawDescending = {false};
        Long[] sawUser = {null};
        SortRegistry<Object> registry = new SortRegistry<>().register("title", ctx -> {
            sawDescending[0] = ctx.descending();
            sawUser[0] = ctx.userId();
            return List.of(mock(Order.class));
        });

        registry.toOrders(List.of(new SortTerm("title", true)), root, query, cb, 42L);

        assertTrue(sawDescending[0]);
        assertEquals(42L, sawUser[0]);
    }

    @Test
    void unknownKeyThrows() {
        SortRegistry<Object> registry = new SortRegistry<>().register("id", ascOn("id"));
        assertThrows(APIException.class,
                () -> registry.toOrders(List.of(new SortTerm("bogus", false)), root, query, cb, null));
    }

    private SortOrderBuilder<Object> ascOn(String attribute) {
        return ctx -> List.of(mock(Order.class));
    }
}
