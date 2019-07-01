package com.deepoove.swagger.diff.cli;

import static com.google.common.collect.Iterables.isEmpty;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.deepoove.swagger.diff.SwaggerDiff;
import com.deepoove.swagger.diff.SwaggerDiffReader;
import com.deepoove.swagger.diff.model.ChangedEndpoint;
import com.deepoove.swagger.diff.model.ChangedOperation;
import com.deepoove.swagger.diff.model.ChangedParameter;
import com.deepoove.swagger.diff.model.SwaggerModel;
import com.deepoove.swagger.diff.output.HtmlRender;
import com.deepoove.swagger.diff.output.MarkdownRender;
import com.deepoove.swagger.diff.output.Render;

/**
 * $java -jar swagger-diff.jar -old http://www.petstore.com/swagger.json \n
 *  -new http://www.petstore.com/swagger_new.json \n
 *  -v 2.0 \n
 *  -output-mode markdown \n
 *  
 * @author Sayi
 * @version 
 */
public class CLI {

    private static Logger logger = LoggerFactory.getLogger(CLI.class);
    private static final String OUTPUT_MODE_MARKDOWN = "markdown";
    
    @Parameter(names = "-old", description = "old api-doc location:Json file path or Http url", required = true, order = 0)
    private String oldSpec;
    
    @Parameter(names = "-new", description = "new api-doc location:Json file path or Http url", required = true, order = 1)
    private String newSpec;
    
    @Parameter(names = "-v", description = "swagger version:1.0 or 2.0", validateWith=  RegexValidator.class, order = 2)
    @Regex("(2\\.0|1\\.0)")
    private String version = SwaggerDiff.SWAGGER_VERSION_V2;
    
    @Parameter(names = "-output-mode", description = "render mode: markdown or html", validateWith=  RegexValidator.class, order = 3)
    @Regex("(markdown|html)")
    private String outputMode = OUTPUT_MODE_MARKDOWN;
    
    @Parameter(names = "--help", help = true, order = 5)
    private boolean help;

    @Parameter(names = "--strict-mode", description = "Strict mode, exception is thrown if diff found")
    private boolean strictMode = false;

    @Parameter(names = "--ignore-file", description = "File with properties to ignore")
    private String ignoreFilePath;
    
    @Parameter(names = "--version", description = "swagger-diff tool version", help = true, order = 6)
    private boolean v;
    
    public static void main(String[] args) {
        CLI cli = new CLI();
        JCommander jCommander = JCommander.newBuilder()
            .addObject(cli)
            .build();
        jCommander.parse(args);
        cli.run(jCommander);
    }

    public void run(JCommander jCommander) {
        if (help){
            jCommander.setProgramName("java -jar swagger-diff.jar");
            jCommander.usage();
            return;
        }
        if (v){
            JCommander.getConsole().println("1.2.1");
            return;
        }
        
        SwaggerDiff diff = SwaggerDiff.SWAGGER_VERSION_V2.equals(version)
                ? SwaggerDiff.compareV2(oldSpec, newSpec) : SwaggerDiff.compareV1(oldSpec, newSpec);

        if (ignoreFilePath != null) {
            SwaggerModel ignore = new SwaggerDiffReader().read(new File(ignoreFilePath));
            processIgnore(diff, ignore);
        }
        
        String render = getRender(outputMode).render(diff);
        JCommander.getConsole().println(render);
        int changedEndpoints = diff.getChangedEndpoints().size();
        int missingEndpoints = diff.getMissingEndpoints().size();
        int changedParameters = countParameters(diff.getChangedEndpoints());
        int changedProperties = countProperties(diff.getChangedEndpoints());
        JCommander.getConsole().println("Changed endpoints: " + changedEndpoints + ",\n"
                + "missing endpoints: " + missingEndpoints + ",\n"
                + "changed parameters: " + changedParameters + ",\n"
                + "changed properties: " + changedProperties);
        if (strictMode && (!diff.getChangedEndpoints().isEmpty() || !diff.getMissingEndpoints().isEmpty())) {
            throw new IllegalStateException("Swagger differences were found, please refer to console logs and fix it.");
        }
    }

    private void processIgnore(SwaggerDiff diff, SwaggerModel ignore) {
        for (SwaggerModel.Method toIgnore : ignore.items) {
            ChangedEndpoint endpoint = diff.getChangedEndpoints().stream().filter(e -> e.getPathUrl().equals(toIgnore.path)).findFirst().orElse(null);
            if (endpoint == null) {
                logger.error("to ignore endpoint wasn't found: " + toIgnore);
                continue;
            }

            ChangedOperation operation = endpoint.getChangedOperations().get(toIgnore.method);
            if (operation == null) {
                logger.error("to ignore operation wasn't found: " + toIgnore);
                continue;
            }

            for (ChangedParameter param : operation.getChangedParameter()) {
                param.getIncreased().removeIf(prop -> toIgnore.parameters.remove(prop.getEl()));
                param.getChanged().removeIf(prop -> toIgnore.parameters.remove(prop.getEl()));
                param.getMissing().removeIf(prop -> toIgnore.parameters.remove(prop.getEl()));
            }
            operation.getChangedParameter().removeIf(param -> isEmpty(param.getMissing())
                    && isEmpty(param.getIncreased())
                    && isEmpty(param.getChanged())
                    && isEmpty(param.getChanged())
                    && !param.isChangeRequired());
            operation.getMissingParameters().removeIf(parameter -> toIgnore.parameters.remove(parameter.getName()));
            operation.getAddParameters().removeIf(parameter -> toIgnore.parameters.remove(parameter.getName()));

            operation.getAddProps().removeIf(prop -> toIgnore.response.remove(prop.getEl()));
            operation.getMissingProps().removeIf(prop -> toIgnore.response.remove(prop.getEl()));
            operation.getChangedProps().removeIf(prop -> toIgnore.response.remove(prop.getEl()));

            if (!toIgnore.parameters.isEmpty()) {
                logger.error("not all params were ignored: " + toIgnore);
            }
            if (!toIgnore.response.isEmpty()) {
                logger.error("not all properties were ignored: " + toIgnore);
            }

            if (isEmpty(operation.getAddParameters())
                    && isEmpty(operation.getMissingParameters())
                    && isEmpty(operation.getChangedParameter())
                    && isEmpty(operation.getAddProps())
                    && isEmpty(operation.getMissingProps())
                    && isEmpty(operation.getChangedProps())) {
                endpoint.getChangedOperations().remove(toIgnore.method);
            }
        }
        // clean empty changepoints
        diff.getChangedEndpoints().removeIf(endpoint -> endpoint.getChangedOperations().isEmpty() && endpoint.getMissingOperations().isEmpty() && endpoint.getNewOperations().isEmpty());
    }

    private int countParameters(List<ChangedEndpoint> changedEndpoints) {
        int count = 0;
        for (ChangedEndpoint endpoint : changedEndpoints) {
            for (ChangedOperation operation : endpoint.getChangedOperations().values()) {
                count += operation.getAddParameters().size() + operation.getMissingParameters().size();
                for (ChangedParameter parameter : operation.getChangedParameter()) {
                    count += parameter.getIncreased().size() + parameter.getMissing().size() + parameter.getChanged().size();
                }
            }
        }
        return count;
    }

    private int countProperties(List<ChangedEndpoint> changedEndpoints) {
        int count = 0;
        for (ChangedEndpoint endpoint : changedEndpoints) {
            for (ChangedOperation operation : endpoint.getChangedOperations().values()) {
                count += operation.getAddProps().size() + operation.getChangedProps().size() + operation.getMissingProps().size();
            }
        }
        return count;
    }

    private Render getRender(String outputMode) {
        if (OUTPUT_MODE_MARKDOWN.equals(outputMode)) return new MarkdownRender();
        return new HtmlRender("Changelog", "http://deepoove.com/swagger-diff/stylesheets/demo.css");
    }

    public String getOldSpec() {
        return oldSpec;
    }

    public String getNewSpec() {
        return newSpec;
    }

    public String getVersion() {
        return version;
    }

    public String getOutputMode() {
        return outputMode;
    }


}
