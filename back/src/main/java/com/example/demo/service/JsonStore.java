package com.example.demo.service;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import java.util.*;

@Component
public class JsonStore {
    private final ObjectMapper mapper = new ObjectMapper();
    public String write(Object value) { return mapper.writeValueAsString(value); }
    public Map<String, Object> read(String value) { return mapper.readValue(value, new TypeReference<LinkedHashMap<String,Object>>() {}); }
    public Map<String,Object> copy(Map<String,Object> value) { return read(write(value)); }
}
