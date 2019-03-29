package com.deepoove.swagger.diff;

import static java.lang.String.join;
import static org.apache.commons.io.FileUtils.readLines;
import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deepoove.swagger.diff.model.SwaggerModel;
import com.deepoove.swagger.diff.model.SwaggerModel.Method;
import com.google.common.collect.Sets;

import io.swagger.models.HttpMethod;

public class SwaggerDiffReader {

    private static Logger logger = LoggerFactory.getLogger(SwaggerDiff.class);
    private static final String COMMENT_PREFIX = "#";
    private static final String PARAMETERS = "Parameters";
    private static final String RESPONSE = "Return Type";
    private static final Set<String> NEW_LINE_START_MARKERS = Sets.newHashSet("`POST`", "`GET`", "`PUT`", "`DELETE`");

    public SwaggerModel read(File file) {
        List<String> lines = null;
        try {
            lines = readLines(file);
        }
        catch (IOException e) {
            logger.error("error occured", e);
            return new SwaggerModel();
        }

        boolean parametersBlock = false;
        boolean responseBlock = false;
        SwaggerModel swaggerModel = new SwaggerModel();

        Method method = null;
        for (String line : lines) {
            if (isBlank(line) || line.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            line = line.trim();

            if (NEW_LINE_START_MARKERS.stream().anyMatch(line::startsWith)) {
                method = new Method();
                swaggerModel.items.add(method);
                fillMethodAndPath(method, line);

                // parameters or response ended
                parametersBlock = false;
                responseBlock = false;
                continue;
            }

            if (PARAMETERS.equals(line)) {
                parametersBlock = true;
                responseBlock = false;
                continue;
            }
            if (RESPONSE.equals(line)) {
                responseBlock = true;
                parametersBlock = false;
                continue;
            }

            if (parametersBlock) {
                fillParameters(method, line);
            }
            if (responseBlock) {
                fillResponse(method, line);
            }
        }
        return swaggerModel;
    }

    private void fillMethodAndPath(Method method, String line) {
        String[] split = line.split(" ");
        method.method = HttpMethod.valueOf(split[0].replaceAll("`", ""));
        method.path = split[1];
        method.description = join(" ", Arrays.copyOfRange(split, 2, split.length));
    }

    private void fillParameters(Method method, String line) {
        String[] split = line.split(" ");
        method.parameters.add(split[1]);
    }

    private void fillResponse(Method method, String line) {
        String[] split = line.split(" ");
        method.response.add(split[1]);
    }
}
