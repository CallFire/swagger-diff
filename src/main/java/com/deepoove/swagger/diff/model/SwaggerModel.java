package com.deepoove.swagger.diff.model;

import java.util.ArrayList;
import java.util.List;

import io.swagger.models.HttpMethod;

public class SwaggerModel {

    public List<Method> items = new ArrayList<>();

    public Method getByPath(String path) {
        return items.stream().filter(item -> item.path.equals(path)).findFirst().orElse(null);
    }

    public static class Method {
        public String path;
        public HttpMethod method;
        public String description;
        public List<String> parameters = new ArrayList<>();
        public List<String> response = new ArrayList<>();

        @Override
        public String toString() {
            return "Method{" + "path='" + path + '\'' + ", method='" + method + '\'' + ", parameters=" + parameters + ", response=" + response + '}';
        }
    }

    @Override
    public String toString() {
        return "SwaggerModel{" + "items=" + items + '}';
    }
}
