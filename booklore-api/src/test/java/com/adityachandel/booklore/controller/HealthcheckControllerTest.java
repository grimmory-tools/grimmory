package com.adityachandel.booklore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.context.annotation.ComponentScan;

import org.springframework.context.annotation.FilterType;

import org.springframework.test.context.TestPropertySource; // Added this import

import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import org.springframework.web.context.WebApplicationContext;



import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



@WebMvcTest(

        controllers = HealthcheckController.class,

        useDefaultFilters = false, // Set to false to disable default component filters

        includeFilters = @ComponentScan.Filter( // Explicitly include HealthcheckController

                type = FilterType.ASSIGNABLE_TYPE,

                classes = HealthcheckController.class

        )

)

@TestPropertySource(properties = {"app.version=1.0.0"}) // Added this line

public class HealthcheckControllerTest {



    @Autowired

    private MockMvc mockMvc;



    @Autowired

    private ObjectMapper objectMapper;



    @Autowired

    private WebApplicationContext webApplicationContext;



    @BeforeEach

    public void setup() {

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

    }



    @Test

    public void getPing_ShouldReturnHealthcheckResponse() throws Exception {

        mockMvc.perform(get("/api/v1/healthcheck"))

                .andExpect(status().isOk())

                .andExpect(jsonPath("$.data.status").value("UP"))

                .andExpect(jsonPath("$.data.message").value("Application is running smoothly."))

                .andExpect(jsonPath("$.data.timestamp").exists())

                .andExpect(jsonPath("$.data.version").value("1.0.0"));

    }

}
