package com.adityachandel.booklore.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class RequestUtilsTest {

    private HttpServletRequest mockRequest;
    private ServletRequestAttributes requestAttributes;

    @BeforeEach
    void setUp() {
        mockRequest = new MockHttpServletRequest();
        requestAttributes = new ServletRequestAttributes(mockRequest);
        RequestContextHolder.setRequestAttributes(requestAttributes);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void testGetCurrentRequest_success() {
        HttpServletRequest result = RequestUtils.getCurrentRequest();
        assertNotNull(result);
        assertEquals(mockRequest, result);
    }

    @Test
    void testGetCurrentRequest_noRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();

        assertThrows(IllegalStateException.class, RequestUtils::getCurrentRequest);
    }

    @Test
    void testGetCurrentRequest_nonServletAttributes() {
        RequestContextHolder.setRequestAttributes(null);

        assertThrows(IllegalStateException.class, RequestUtils::getCurrentRequest);
    }

    @Test
    void testGetCurrentRequest_multipleCalls() {
        HttpServletRequest result1 = RequestUtils.getCurrentRequest();
        HttpServletRequest result2 = RequestUtils.getCurrentRequest();

        assertEquals(result1, result2);
        assertEquals(mockRequest, result1);
    }
}
