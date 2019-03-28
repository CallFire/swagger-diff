package com.deepoove.swagger;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.apache.commons.io.FileUtils.writeLines;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.deepoove.swagger.diff.SwaggerDiffReader;
import com.deepoove.swagger.diff.model.SwaggerModel;
import com.deepoove.swagger.diff.model.SwaggerModel.Method;

public class SwaggerDiffReaderTest {

    private SwaggerDiffReader swaggerDiffReader = new SwaggerDiffReader();

    @Test
    public void test() throws IOException {
        File file = File.createTempFile("temp", ".txt");
        file.deleteOnExit();
        List<String> lines = Arrays.asList(
                "`GET` /credit-card/view-list Credit Cards Collection",
                    "Return Type",
                    "",
                    "Insert name",
                    "Insert entities.city",
                    "Insert entities.id",
                "`POST` /billing/buy-credits buy credits with existing credit card",
                    "Parameters",
                    "",
                    "Modify creditCardId"
        );
        writeLines(file, lines, false);

        SwaggerModel swagger = swaggerDiffReader.read(file);
        assertEquals(2, swagger.items.size());
        Method creditCard = swagger.items.get(0);
        assertEquals("GET", creditCard.method);
        assertEquals("/credit-card/view-list", creditCard.path);
        assertEquals(0, creditCard.parameters.size());
        assertEquals(3, creditCard.response.size());
        assertTrue(creditCard.response.contains("name"));
        assertTrue(creditCard.response.contains("entities.city"));
        assertTrue(creditCard.response.contains("entities.id"));

        Method billing = swagger.items.get(1);
        assertEquals("POST", billing.method);
        assertEquals("/billing/buy-credits", billing.path);
        assertEquals(1, billing.parameters.size());
        assertTrue(billing.parameters.contains("creditCardId"));
    }
}
