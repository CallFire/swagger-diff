package com.deepoove.swagger.diff.output;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.apache.commons.lang3.StringUtils.countMatches;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.replace;
import static org.apache.commons.lang3.StringUtils.replaceOnce;
import static org.apache.commons.lang3.StringUtils.substring;
import static org.apache.commons.lang3.StringUtils.substringBefore;
import static org.apache.commons.lang3.StringUtils.substringBetween;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import com.deepoove.swagger.diff.SwaggerDiff;
import com.deepoove.swagger.diff.model.ChangedEndpoint;
import com.deepoove.swagger.diff.model.ChangedOperation;
import com.deepoove.swagger.diff.model.ChangedParameter;
import com.deepoove.swagger.diff.model.ElProperty;
import com.deepoove.swagger.diff.model.Endpoint;
import com.google.common.base.CaseFormat;

import io.swagger.models.ArrayModel;
import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.Response;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;

public class MarkdownRender implements Render {
	private static final Boolean writeDiffs = BooleanUtils.toBoolean(System.getProperty("callfire.write.diffs", "false"));
	public static final String TOUR_STATIC_CONSTANT = "    public static final String TOUR_RESOURCE_PATH = AUTH_PATH_PREFIX + \"/tour\";";
	public static final String METHOD_STATIC_CONSTANT = "    public static final String METHOD_PATH_CONSTANT_CONTACT_VIEW = \"/constantcontact/view\";";
	public static final Set<String> INTEGER_PARAMS = new HashSet<>(asList("pageSize", "page"));

	final String H3 = "### ";
	final String H2 = "## ";
	final String BLOCKQUOTE = "> ";
	final String CODE = "`";
	final String PRE_CODE = "    ";
	final String PRE_LI = "    ";
	final String LI = "* ";
	final String HR = "---\n";

	public MarkdownRender() {}

	public String render(SwaggerDiff diff) {
		try {
			// for now we don't need to new endpoints
//		List<Endpoint> newEndpoints = diff.getNewEndpoints();
//		String ol_newEndpoint = ol_newEndpoint(newEndpoints);
			String ol_newEndpoint = "";

			List<Endpoint> missingEndpoints = diff.getMissingEndpoints();
			String ol_missingEndpoint = ol_missingEndpoint(missingEndpoints);

			List<ChangedEndpoint> changedEndpoints = diff.getChangedEndpoints();
			String ol_changed = ol_changed(changedEndpoints);

			return renderHtml(diff.getOldVersion(), diff.getNewVersion(), ol_newEndpoint, ol_missingEndpoint, ol_changed);
		}
		catch (Exception e) {
			throw  new RuntimeException(e);
		}
	}

	public String renderHtml(String oldVersion, String newVersion, String ol_new, String ol_miss,
							 String ol_changed) {
		StringBuffer sb = new StringBuffer();
		sb.append(H2).append("Version " + oldVersion + " to " + newVersion).append("\n").append(HR);
		sb.append(H3).append("What's New").append("\n").append(HR)
				.append(ol_new).append("\n").append(H3)
				.append("What's Deprecated").append("\n").append(HR)
				.append(ol_miss).append("\n").append(H3)
				.append("What's Changed").append("\n").append(HR)
				.append(ol_changed);
		return sb.toString();
	}

	private String ol_newEndpoint(List<Endpoint> endpoints) {
		if (null == endpoints) return "";
		StringBuffer sb = new StringBuffer();
		for (Endpoint endpoint : endpoints) {
			sb.append(li_newEndpoint(endpoint.getMethod().toString(),
					endpoint.getPathUrl(), endpoint.getSummary()));
		}
		return sb.toString();
	}

	private String li_newEndpoint(String method, String path, String desc) {
		StringBuffer sb = new StringBuffer();
		sb.append(LI).append(CODE).append(method).append(CODE)
				.append(" " + path).append(" " + desc + "\n");
		return sb.toString();
	}

	private String ol_missingEndpoint(List<Endpoint> endpoints) throws IOException {
		if (null == endpoints) return "";
		StringBuffer sb = new StringBuffer();
		for (Endpoint endpoint : endpoints) {
			sb.append(li_newEndpoint(endpoint.getMethod().toString(),
					endpoint.getPathUrl(), endpoint.getSummary()));
			writeDiffs(endpoint);
		}
		return sb.toString();
	}

	private void writeDiffs(Endpoint endpoint) throws IOException {
		if (!writeDiffs) {
			return;
		}
		String resourceName = substringBefore(endpoint.getOperation().getTags().get(0), " ");
		File resourceFile = new File(
				format("../Nova/nova-server/src/main/java/com/callfire/soa/nova/server/resource/auth/%sResource.java",
						resourceName));
		File resourceTestFile = new File(
				format("../Nova/nova-server/src/test/java/com/callfire/soa/nova/server/resource/auth/%sResourceTest.java",
						resourceName));
		File novaClientFile = new File("../Nova/nova-client/src/main/java/com/callfire/soa/nova/client/NovaClient.java");

		writeResourceFile(endpoint, resourceName, resourceFile, resourceTestFile, novaClientFile);
		writeResourceMethod(endpoint, resourceName, resourceFile, resourceTestFile, novaClientFile);

	}

	private void writeResourceMethod(Endpoint endpoint, String resourceName, File resourceFile, File resourceTestFile,
									 File novaClientFile) throws IOException {
		File zendClientFile = new File("../Nova/nova-server/src/main/java/com/callfire/soa/nova/server/service/provider/zend/ZendClient.java");
		File teslaClientFile = new File("../Nova/nova-server/src/main/java/com/callfire/soa/nova/server/service/provider/tesla/TeslaClient.java");
		String resourceContent = readFileToString(resourceFile);
		int endOfClass = resourceContent.lastIndexOf("}");
		Response response200 = endpoint.getOperation().getResponses().get("200");
		String operationId = endpoint.getOperation().getOperationId();
		String methodConstantName = snakeCase(operationId);
		String returnType = endpoint.getMethod() == HttpMethod.DELETE ?
				"void" :
				modelType(getType(response200.getResponseSchema(),
						capitalize(operationId + "Response")));
		boolean isVoid = returnType.equals("void");
		List<Parameter> parameters = endpoint.getOperation().getParameters();
		String parametersString = parameters.stream()
				.filter(p -> !p.getName().contains("filters"))
				.map(p -> getParameterType(operationId, p) + " " + p.getName())
				.collect(joining(", "));
		String parametersCall = parameters.stream()
				.filter(p -> !p.getName().contains("filters"))
				.map(p -> p.getName())
				.collect(joining(", "));
		String annotatedParameterString = parameters.stream()
				.map(p -> "            " + getAnnotatedParameterString(operationId, p))
				.filter(s -> !s.contains("null"))
				.collect(joining(",\n"));
		boolean hasFilters = parameters.stream()
				.anyMatch(p -> p.getName().contains("filters"));
		if (hasFilters) {
			parametersCall += ", Filters.buildFrom(request)";
			annotatedParameterString += ",\n            HttpServletRequest request";
			parametersString += ", Filters filters";
		}
		if (isNotBlank(annotatedParameterString)) annotatedParameterString = "\n" + annotatedParameterString;
		String methodContent = format(readFileToString(new File("templates/method.template")),
				methodConstantName,
				endpoint.getMethod().name(),
				endpoint.getOperation().getSummary(), endpoint.getOperation().getSummary(),
				returnType, operationId,
				annotatedParameterString,
				isVoid ? "pa.getProvider()." + operationId + "(" + parametersCall + ");" : ("return pa.getProvider()." + operationId + "(" + parametersCall + ");")
		);

		if (hasFilters) {
			String parametersFilters = parameters.stream()
					.filter(p -> p.getName().contains("filters"))
					.map(p -> "            @ApiImplicitParam(name = \"" + p.getName() + "\", value = \"" + StringUtils.defaultIfBlank(
							p.getDescription(),
							p.getName()) + "\", dataType = \"" + ((QueryParameter) p).getType() + "\", paramType = \"query\"" + (p
							.getRequired() ? ", required = true" : "") + ")")
					.collect(joining(",\n"));
			String filters = "    @ApiImplicitParams({\n" + parametersFilters + "\n    })\n";
			methodContent = filters + methodContent;
		}
		appendAfterFoundToFile(novaClientFile,
				format("\n    public static final String METHOD_PATH_%s = \"%s\";", methodConstantName, StringUtils.removeStart(endpoint.getPathUrl(), resourcePath(endpoint))),
				METHOD_STATIC_CONSTANT);

		writeStringToFile(resourceFile,
				format("%s\n%s%s", substring(resourceContent, 0, endOfClass), methodContent,
						substring(resourceContent, endOfClass)));

		// Mock resource test methods
		String mockValues = parameters.stream()
				.filter(p -> !p.getName().contains("filters"))
				.map(p -> "        " + getParameterType(operationId, p) + " " + p.getName() + " = " + getMockValue(operationId,
						p) + ";\n")
				.collect(joining());
		String parametersMatch = parameters.stream()
				.filter(p -> !p.getName().contains("filters"))
				.map(p -> "eq(" + p.getName() + ")")
				.collect(joining(", "));
		if (hasFilters) {
			parametersMatch += ", any()";
			parametersCall = parameters.stream()
					.filter(p -> !p.getName().contains("filters"))
					.map(p -> p.getName())
					.collect(joining(", ")) + ", mock(HttpServletRequest.class)";
		}
		String testMethodContent = format(readFileToString(new File("templates/method-test.template")), capitalize(operationId),
				mockValues + "        resource." + operationId + "(" + parametersCall + ");\n" + "        verify(provider())." + operationId + "(" + parametersMatch + ");");
		writeBeforeLastBracet(resourceTestFile, testMethodContent, "}");

		// Provider method definitions
		File providerFile = new File("../Nova/nova-client/src/main/java/com/callfire/soa/nova/client/provider/Provider.java");
		String tag = "// " + endpoint.getOperation().getTags().get(0) + "\n";
		if (!readFileToString(providerFile).contains(tag)) {
			writeBeforeLastBracet(providerFile, "    " + tag, "}");
		}
		appendAfterFoundToFile(providerFile, "    " + returnType + " " + operationId + "(" + parametersString + ");\n", tag);

		// NovaClient RestApiDef definitions like static final RestApiDef<HostedWidgetViewResponse> HOSTED_WIDGET_VIEW = of("HostedWidgetHostedWidgetView", GET, HOSTEDWIDGET_RESOURCE_PATH, METHOD_HOSTED_WIDGET_VIEW, HostedWidgetViewResponse.class);
		if (!readFileToString(novaClientFile).contains(tag)) {
			writeBeforeLastBracet(novaClientFile, "    " + tag, "    // token state management");
		}
		String typeReference = returnType + ".class";
		if (returnType.contains("<")) {
			typeReference = snakeCase(substringBetween(returnType, "<", ">")) + "_" + snakeCase(
					substringBefore(returnType, "<")) + "_TYPE_REFERENCE";
			if (!readFileToString(novaClientFile).contains(typeReference)) {
				appendAfterFoundToFile(novaClientFile,
						"    public static final TypeReference<" + returnType + "> " + typeReference + " = new TypeReference<" + returnType + ">() {};\n",
						"public static final TypeReference<Map<String, String>> MAP_TYPE_REFERENCE = new TypeReference<Map<String, String>>() {};\n");
			}
		}
		appendAfterFoundToFile(novaClientFile, "    static final RestApiDef<" + capitalize(
				returnType) + "> " + methodConstantName + " = of(\"" + resourceName + capitalize(
				operationId) + "\", " + endpoint.getMethod()
				.name() + ", " + resourceName.toUpperCase() + "_RESOURCE_PATH, METHOD_PATH" + methodConstantName + (isVoid ?
				"" :
				", " + typeReference) + ");\n", tag);

		// NovaClient implementation methods:
		String additionalArgs = "";
		String paramsString = "";
		if (parametersString.contains("pageSize")) {
			paramsString = "        ParamMap params = PagingParams.builder().page(page).pageSize(pageSize)";
			if (parametersString.contains("filters")) {
				paramsString += ".filters(filters)";
			}
			if (parametersString.contains("orderBy")) {
				paramsString += ".orderBy(orderBy)";
			}
			paramsString += ".build().toParamMap();\n";
		}
		List<String> argParams = parameters.stream()
				.filter(p -> !INTEGER_PARAMS.contains(p.getName()))
				.filter(p -> !p.getName().equalsIgnoreCase("orderBy"))
				.filter(p -> !p.getName().contains("filter"))
				.filter(p -> !(p instanceof BodyParameter))
				.filter(p -> !(p instanceof PathParameter))
				.map(p -> p.getName())
				.collect(toList());
		if (!argParams.isEmpty()) {
			if (paramsString.isEmpty()) paramsString = "        ParamMap params = new ParamMap();\n";
			paramsString += argParams.stream()
					.map(pName -> "        params.putNotNull(\"" + pName + "\", " + pName + ");\n")
					.collect(Collectors.joining());
		}
		if (StringUtils.isNotBlank(paramsString)) {
			additionalArgs += ".params(params)";
		}
		String pathParams = parameters.stream()
				.filter(p -> p instanceof PathParameter)
				.map(p -> p.getName())
				.collect(Collectors.joining(", "));
		if (!pathParams.isEmpty())
			additionalArgs += ".args(new Object[] { " + pathParams + " })";
		String bodyParams = parameters.stream()
				.filter(p -> p instanceof BodyParameter)
				.map(p -> p.getName())
				.collect(Collectors.joining(", "));
		if (!bodyParams.isEmpty())
			additionalArgs += ".dto(" + bodyParams + ")";

		String clientMethodContent = "    @Override\n    public " + returnType + " " + operationId + "(" + parametersString + ") {\n" + paramsString + "        " + (
				isVoid ?
						"" :
						"return ") + "execute(builderWith(" + methodConstantName + ")" + additionalArgs + ".build());\n" + "    }\n";
		writeBeforeLastBracet(novaClientFile, clientMethodContent, "}");

		// NovaClientTest methods
		File novaClientTestFile = new File("../Nova/nova-client/src/test/java/com/callfire/soa/nova/client/NovaClientTest.java");
		int argsCount = countMatches(parametersMatch, ",");
		if (argsCount > 0 || isNotBlank(parametersMatch)) argsCount += 1;
		writeBeforeLastBracet(novaClientTestFile, String.format("    @Test\n    public void test" + capitalize(operationId) + "() {\n" + (
						isVoid ?
								"" :
								"        mockResponseObject(" + getMockType(returnType,
										null) + ");\n") + "        %snovaClient." + operationId + "(" + IntStream.range(0, argsCount)
						.mapToObj(i -> "null")
						.collect(Collectors.joining(", ")) + ")%s;\n" + "    }\n", isVoid ? "" : "assertNotNull(", isVoid ? "" : ")"),
				"}");

		// ZendClient RestApiDef definitions like static final RestApiDef<HostedWidgetViewResponse> HOSTED_WIDGET_VIEW = of("HostedWidgetHostedWidgetView", GET, HOSTEDWIDGET_RESOURCE_PATH, METHOD_HOSTED_WIDGET_VIEW, HostedWidgetViewResponse.class);
		if (!readFileToString(zendClientFile).contains(tag)) {
			writeBeforeLastBracet(zendClientFile, "    " + tag, "    protected ZendClient(AppType appType) {");
		}
		appendAfterFoundToFile(zendClientFile, "    static final RestApiDef<" + capitalize(
				returnType) + "> " + methodConstantName + " = of(\"Zend" + resourceName + capitalize(
				operationId) + "\", " + endpoint.getMethod()
				.name() + ", \"" + endpoint.getPathUrl() + "\", \"\"" + (isVoid ?
				"" :
				", " + typeReference) + ");\n", tag);

		// ZendClient implementation methods:
		writeBeforeLastBracet(zendClientFile, clientMethodContent, "}");

		// TeslaClient implementation methods:
		writeBeforeLastBracet(teslaClientFile, substringBefore(clientMethodContent,
				"{") + "{\n        throw new NotImplementedException();\n    }\n", "}");
	}

	private String snakeCase(String operationId) {
		return CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE,
				operationId).toUpperCase();
	}

	private void writeBeforeLastBracet(File file, String content, String stringToFind) throws IOException {
		String contentOfFile = readFileToString(file);
		int endOfClass = contentOfFile.lastIndexOf(stringToFind);
		writeStringToFile(file, format("%s\n%s%s", substring(contentOfFile, 0, endOfClass), content, substring(contentOfFile, endOfClass)));
	}

	private String getMockValue(String operationId, Parameter p) {
		return getMockType(getParameterType(operationId, p), p.getName());
	}

	private String getMockType(String type, String pName) {
		if (type.equalsIgnoreCase("String")) {
			return "\"test\" + currentTimeMillis()";
		}
		if (type.equalsIgnoreCase("integer") || type.equalsIgnoreCase("long")) {
			if (INTEGER_PARAMS.contains(pName)) return "123";
			return "currentTimeMillis()";
		}
		if (type.equalsIgnoreCase("boolean")) {
			return "true";
		}
		return "mock(" + StringUtils.defaultIfBlank(substringBetween(type, "<"), type) + ".class)";
	}

	private String getAnnotatedParameterString(String operationId, Parameter p) {
		if (p.getName().contains("filters")) return null;
		String type = getParameterType(operationId, p);
		String name = p.getName();
		if (p instanceof QueryParameter)
			return "@ApiParam(value = \"" + p.getDescription() + "\") @RequestParam(value = \"" + name + "\"" + (!p.getRequired() ?
					", required = false" :
					"") + ") " + type + " " + name;
		if (p instanceof PathParameter)
			return "@ApiParam(value = \"" + p.getDescription() + "\", required = true) @PathVariable(\"" + name + "\") " + type + " " + name;
		if (p instanceof BodyParameter)
			return "@ApiParam(name = \"" + name + "\") @RequestBody " +  type + " " + name;
		throw new NotImplementedException("oops");
	}

	private String getParameterType(String operationId, Parameter p) {
		if (INTEGER_PARAMS.contains(p.getName())) return "Integer";
		if (p instanceof QueryParameter) return modelType(((QueryParameter) p).getType());
		if (p instanceof PathParameter) return modelType(((PathParameter) p).getType());
		if (p instanceof BodyParameter) return modelType(getType(((BodyParameter) p).getSchema(), capitalize(operationId) + capitalize(p.getName())));
		throw new NotImplementedException("oops");
	}

	private String getType(Model model, String modelName) {
		if (model == null) return "void";
		if (model instanceof ModelImpl) {
			String responseType = StringUtils.capitalize(((ModelImpl) model).getType());
			if (responseType.equals("Object")) {
				// write object name
				if (model.getProperties().containsKey("_messages")) {
					return "void";
				}
				if (model.getProperties().containsKey("_embedded")) {
					// page?
					String childModelType = writeModel(modelName,
							((ObjectProperty)((ArrayProperty) ((ObjectProperty) model.getProperties().get("_embedded")).getProperties()
									.get("entities")).getItems()).getProperties());
					return (model.getProperties().containsKey("pageSize") ?
							"PageableViewList" :
							"ViewList") + "<" + childModelType + ">";
				}
				return writeModel(modelName, model.getProperties());
			}
			return modelType(responseType);
		}
		Property items = ((ArrayModel) model).getItems();
		return "List<" + (items instanceof ObjectProperty ?
				writeModel(modelName, ((ObjectProperty) items).getProperties()) :
				getPropertyType(items)) + ">";
	}

	private String writeModel(String modelName, Map<String, Property> properties) {
		try {
			File modelFile = new File(
					"../Nova/nova-client/src/main/java/com/callfire/soa/nova/client/model/" + modelName + ".java");
			if (modelFile.exists()) {
				return modelName;
			}
			modelFile.createNewFile();
			// write resource file
			String modelContent = format(readFileToString(new File("templates/model.template")), modelName, properties.entrySet()
					.stream()
					.map(e -> format("    private %s %s;", capitalize(getPropertyType(e.getValue()).equals("object") ?
							writeModel(modelName + capitalize(e.getKey()), ((ObjectProperty) e.getValue()).getProperties()) :
							getPropertyType(e.getValue())), e.getKey()))
					.collect(joining("\n")));
			writeStringToFile(modelFile, replace(modelContent, "Integer", "Long"));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return modelName;
	}

	private String getPropertyType(Property value) {
		if (value.getType().equals("array")) return "List<" + capitalize(((ArrayProperty) value).getItems().getType()) + ">";
		return value.getType();
	}

	private String modelType(String value) {
		return value.toLowerCase().equals("void") ? "void" : capitalize(value.toLowerCase().equals("integer") ? "Long" : value);
	}

	private void writeResourceFile(Endpoint endpoint, String resourceName, File resourceFile, File resourceTestFile, File novaClientFile) throws IOException {
		if (resourceFile.exists()) {
			return;
		}
		// write resource file
		writeStringToFile(resourceFile,
				format(readFileToString(new File("templates/resource.template")), resourceName,
						resourceName.toUpperCase()));
		// write constatnt in NovaClient file
		appendAfterFoundToFile(novaClientFile,
				format("\n    public static final String %s_RESOURCE_PATH = AUTH_PATH_PREFIX + \"%s\";",
						resourceName.toUpperCase(), resourcePath(endpoint)), TOUR_STATIC_CONSTANT);
		// write test resource file
		writeStringToFile(resourceTestFile,
				format(readFileToString(new File("templates/resource-test.template")), resourceName,
						resourceName.toUpperCase()));
	}

	private void appendAfterFoundToFile(File novaClientFile, String constant, String contentToFind) throws IOException {
		writeStringToFile(novaClientFile, replaceOnce(readFileToString(novaClientFile),
				contentToFind, contentToFind + constant));
	}

	private String resourcePath(Endpoint endpoint) {
		return substring(endpoint.getPathUrl(), 0,
				StringUtils.indexOf(endpoint.getPathUrl(), "/", 3));
	}

	private String ol_changed(List<ChangedEndpoint> changedEndpoints) {
		if (null == changedEndpoints) return "";
		StringBuffer sb = new StringBuffer();
		for (ChangedEndpoint changedEndpoint : changedEndpoints) {
			String pathUrl = changedEndpoint.getPathUrl();
			Map<HttpMethod, ChangedOperation> changedOperations = changedEndpoint
					.getChangedOperations();
			for (Entry<HttpMethod, ChangedOperation> entry : changedOperations
					.entrySet()) {
				String method = entry.getKey().toString();
				ChangedOperation changedOperation = entry.getValue();
				String desc = changedOperation.getSummary();

				StringBuffer ul_detail = new StringBuffer();
				if (changedOperation.isDiffParam()) {
					ul_detail.append(PRE_LI).append("Parameters")
							.append(ul_param(changedOperation));
				}
				if (changedOperation.isDiffProp()) {
					ul_detail.append(PRE_LI).append("Return Type")
							.append(ul_response(changedOperation));
				}
				sb.append(CODE).append(method).append(CODE)
						.append(" " + pathUrl).append(" " + desc + "  \n")
						.append(ul_detail);
			}
		}
		return sb.toString();
	}

	private String ul_response(ChangedOperation changedOperation) {
		List<ElProperty> addProps = changedOperation.getAddProps();
		List<ElProperty> delProps = changedOperation.getMissingProps();
		List<ElProperty> changedProps = changedOperation.getChangedProps();
		StringBuffer sb = new StringBuffer("\n\n");

		String prefix = PRE_LI + PRE_CODE;
		for (ElProperty prop : addProps) {
			sb.append(PRE_LI).append(PRE_CODE).append(li_addProp(prop) + "\n");
		}
		for (ElProperty prop : delProps) {
			sb.append(prefix).append(li_missingProp(prop) + "\n");
		}
		for (ElProperty prop : changedProps) {
			sb.append(prefix).append(li_changedProp(prop) + "\n");
		}
		return sb.toString();
	}

	private String li_missingProp(ElProperty prop) {
		Property property = prop.getProperty();
		StringBuffer sb = new StringBuffer("");
		sb.append("Delete ").append(prop.getEl())
				.append(null == property.getDescription() ? ""
						: (" //" + property.getDescription()));
		return sb.toString();
	}

	private String li_addProp(ElProperty prop) {
		Property property = prop.getProperty();
		StringBuffer sb = new StringBuffer("");
		sb.append("Insert ").append(prop.getEl())
				.append(null == property.getDescription() ? ""
						: (" //" + property.getDescription()));
		return sb.toString();
	}

	private String li_changedProp(ElProperty prop) {
		Property property = prop.getProperty();
		String prefix = "Modify ";
		String desc = " //" + property.getDescription();
		String postfix = (null == property.getDescription() ? "" : desc);

		StringBuffer sb = new StringBuffer("");
		sb.append(prefix).append(prop.getEl())
				.append(postfix);
		return sb.toString();
	}

	private String ul_param(ChangedOperation changedOperation) {
		List<Parameter> addParameters = changedOperation.getAddParameters();
		List<Parameter> delParameters = changedOperation.getMissingParameters();
		List<ChangedParameter> changedParameters = changedOperation
				.getChangedParameter();
		StringBuffer sb = new StringBuffer("\n\n");
		for (Parameter param : addParameters) {
			sb.append(PRE_LI).append(PRE_CODE)
					.append(li_addParam(param) + "\n");
		}
		for (ChangedParameter param : changedParameters) {
			List<ElProperty> increased = param.getIncreased();
			for (ElProperty prop : increased) {
				sb.append(PRE_LI).append(PRE_CODE)
						.append(li_addProp(prop) + "\n");
			}
		}
		for (ChangedParameter param : changedParameters) {
			boolean changeRequired = param.isChangeRequired();
			boolean changeDescription = param.isChangeDescription();
			if (changeRequired || changeDescription) sb.append(PRE_LI)
					.append(PRE_CODE).append(li_changedParam(param) + "\n");
		}
		for (ChangedParameter param : changedParameters) {
			List<ElProperty> missing = param.getMissing();
			List<ElProperty> changed = param.getChanged();
			for (ElProperty prop : missing) {
				sb.append(PRE_LI).append(PRE_CODE)
						.append(li_missingProp(prop) + "\n");
			}
			for (ElProperty prop : changed) {
				sb.append(PRE_LI).append(PRE_CODE)
						.append(li_changedProp(prop) + "\n");
			}
		}
		for (Parameter param : delParameters) {
			sb.append(PRE_LI).append(PRE_CODE)
					.append(li_missingParam(param) + "\n");
		}
		return sb.toString();
	}

	private String li_addParam(Parameter param) {
		StringBuffer sb = new StringBuffer("");
		sb.append("Add ").append(param.getName())
				.append(null == param.getDescription() ? ""
						: (" //" + param.getDescription()));
		return sb.toString();
	}

	private String li_missingParam(Parameter param) {
		StringBuffer sb = new StringBuffer("");
		sb.append("Delete ").append(param.getName())
				.append(null == param.getDescription() ? ""
						: (" //" + param.getDescription()));
		return sb.toString();
	}

	private String li_changedParam(ChangedParameter changeParam) {
		boolean changeRequired = changeParam.isChangeRequired();
		boolean changeDescription = changeParam.isChangeDescription();
		Parameter rightParam = changeParam.getRightParameter();
		Parameter leftParam = changeParam.getLeftParameter();
		StringBuffer sb = new StringBuffer("");
		sb.append(rightParam.getName());
		if (changeRequired) {
			sb.append(" change into " + (rightParam.getRequired() ? "required" : "not required"));
		}
		if (changeDescription) {
			sb.append(" Notes ").append(leftParam.getDescription()).append(" change into ")
					.append(rightParam.getDescription());
		}
		return sb.toString();
	}

}
