/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.factory.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonSyntaxException;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.model.factory.Factory;
import org.eclipse.che.api.core.model.project.ProjectConfig;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.api.factory.server.FactoryService.FactoryParametersResolverHolder;
import org.eclipse.che.api.factory.server.builder.FactoryBuilder;
import org.eclipse.che.api.factory.server.impl.SourceStorageParametersValidator;
import org.eclipse.che.api.factory.server.model.impl.AuthorImpl;
import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.ProjectConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.SourceStorageImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.shared.dto.EnvironmentDto;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.factory.server.DtoConverter.asDto;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class FactoryServiceTest {

    private static final String SERVICE_PATH            = "/factory";
    private static final String FACTORY_ID              = "correctFactoryId";
    private static final String USER_ID                 = "userId";
    private static final String USER_EMAIL              = "email";
    private static final String WORKSPACE_NAME          = "workspace";
    private static final String PROJECT_SOURCE_TYPE     = "git";
    private static final String PROJECT_SOURCE_LOCATION = "http://github.com/codenvy/platform-api.git";
    private static final String FACTORY_IMAGE_MIME_TYPE = "image/jpeg";
    private static final String IMAGE_NAME              = "image12";


    private static final DtoFactory DTO = DtoFactory.getInstance();

    private final String SERVICE_PATH_RESOLVER = SERVICE_PATH + "/resolver";

    @Mock
    private FactoryManager                  factoryManager;
    @Mock
    private FactoryCreateValidator          createValidator;
    @Mock
    private FactoryAcceptValidator          acceptValidator;
    @Mock
    private PreferenceManager               preferenceManager;
    @Mock
    private UserManager                     userManager;
    @Mock
    private FactoryEditValidator            editValidator;
    @Mock
    private WorkspaceManager                workspaceManager;
    @Mock
    private FactoryParametersResolverHolder factoryParametersResolverHolder;
    @Mock
    private Set<FactoryParametersResolver>  factoryParametersResolvers;
    @Mock
    private FactoryBuilder                  factoryBuilder;

    @InjectMocks
    private FactoryService factoryService;

    @SuppressWarnings("unused")
    private ApiExceptionMapper apiExceptionMapper;
    @SuppressWarnings("unused")
    private EnvironmentFilter  environmentFilter;

    private User user;

    @BeforeMethod
    public void setUp() throws Exception {
        final FactoryBuilder factoryBuilder = spy(new FactoryBuilder(new SourceStorageParametersValidator()));
        doNothing().when(factoryBuilder).checkValid(any(FactoryDto.class));
        when(factoryParametersResolverHolder.getFactoryParametersResolvers()).thenReturn(factoryParametersResolvers);
        user = new UserImpl(USER_ID, USER_EMAIL, ADMIN_USER_NAME);
        when(userManager.getById(anyString())).thenReturn(user);
        when(preferenceManager.find(USER_ID)).thenReturn(ImmutableMap.of("preference", "value"));
    }

    @Filter
    public static class EnvironmentFilter implements RequestFilter {
        public void doFilter(GenericContainerRequest request) {
            EnvironmentContext context = EnvironmentContext.getCurrent();
            context.setSubject(new SubjectImpl(ADMIN_USER_NAME, USER_ID, ADMIN_USER_PASSWORD, false));
        }
    }

    @Test
    public void shouldSaveFactoryWithImagesFromFormData() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        when(factoryBuilder.build(any(InputStream.class))).thenReturn(factoryDto);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", JsonHelper.toJson(factoryDto), APPLICATION_JSON)
                                         .multiPart("image", getImagePath().toFile(), FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(200)
                                         .when()
                                         .post(SERVICE_PATH);


        final FactoryDto result = getFromResponse(response, FactoryDto.class);
        final boolean found = result.getLinks()
                                    .stream()
                                    .anyMatch(link -> link.getRel().equals("image")
                                                      && link.getProduces().equals(FACTORY_IMAGE_MIME_TYPE)
                                                      && !link.getHref().isEmpty());
        factoryDto.withLinks(result.getLinks())
                  .getCreator()
                  .withCreated(result.getCreator().getCreated());
        assertEquals(result, factoryDto);
        assertTrue(found);
    }

    @Test
    public void shouldSaveFactoryFromFormDataWithoutImages() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
        when(factoryBuilder.build(any(InputStream.class))).thenReturn(factoryDto);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", JsonHelper.toJson(factoryDto), APPLICATION_JSON)
                                         .expect()
                                         .statusCode(200)
                                         .when()
                                         .post("/private" + SERVICE_PATH);
        final FactoryDto result = getFromResponse(response, FactoryDto.class);
        factoryDto.withLinks(result.getLinks())
                  .getCreator()
                  .withCreated(result.getCreator().getCreated());
        assertEquals(result, factoryDto);
    }

    @Test
    public void shouldSaveFactoryWithSetImageButWithOutImageContent() throws Exception {
        final Factory factory = createFactory();
        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryBuilder.build(any(InputStream.class))).thenReturn(factoryDto);

        given().auth()
               .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
               .multiPart("factory", DTO.toJson(factoryDto), APPLICATION_JSON)
               .multiPart("image", File.createTempFile("img", ".jpeg"), "image/jpeg")
               .expect()
               .statusCode(200)
               .when()
               .post("/private" + SERVICE_PATH);

        verify(factoryManager).saveFactory(eq(factoryDto), eq(Collections.<FactoryImage>emptySet()));
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenInvalidFactorySectionProvided() throws Exception {
        when(factoryBuilder.build(any(InputStream.class))).thenThrow(new JsonSyntaxException("Invalid json"));
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", "invalid content", FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(400)
                                         .when()
                                         .post("/private" + SERVICE_PATH);

        final ServiceError err = getFromResponse(response, ServiceError.class);
        assertEquals(err.getMessage(), "Invalid JSON value of the field 'factory' provided");
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenNoFactorySectionProvided() throws Exception {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("some data", "some content", FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(400)
                                         .when()
                                         .post("/private" + SERVICE_PATH);

        final ServiceError err = getFromResponse(response, ServiceError.class);
        assertEquals(err.getMessage(), "factory configuration required");
    }

    @Test
    public void shouldThrowServerExceptionWhenImpossibleToBuildFactoryFromProvidedData() throws Exception {
        final String errMessage = "eof";
        when(factoryBuilder.build(any(InputStream.class))).thenThrow(new IOException(errMessage));
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", "any content", FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(500)
                                         .when()
                                         .post("/private" + SERVICE_PATH);

        final ServiceError err = getFromResponse(response, ServiceError.class);
        assertEquals(err.getMessage(), errMessage);
    }

    @Test
    public void shouldSaveFactoryWithoutImages() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.saveFactory(any(FactoryDto.class))).thenReturn(factory);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(ContentType.JSON)
                                         .body(factoryDto)
                                         .expect()
                                         .statusCode(200)
                                         .post(SERVICE_PATH);

        assertEquals(getFromResponse(response, FactoryDto.class).withLinks(emptyList()), factoryDto);
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenFactoryConfigurationNotProvided() throws Exception {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(ContentType.JSON)
                                         .expect()
                                         .statusCode(400)
                                         .post(SERVICE_PATH);
        final String errMessage = getFromResponse(response, ServiceError.class).getMessage();
        assertEquals(errMessage, "Factory configuration required");
    }

    @Test
    public void shouldReturnFactoryByIdentifierWithoutValidation() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        when(factoryManager.getFactoryImages(FACTORY_ID)).thenReturn(emptySet());

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(getFromResponse(response, FactoryDto.class).withLinks(emptyList()), factoryDto);
    }

    @Test
    public void shouldReturnFactoryByIdentifierWithValidation() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        when(factoryManager.getFactoryImages(FACTORY_ID)).thenReturn(emptySet());
        doNothing().when(acceptValidator).validateOnAccept(any(FactoryDto.class));

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "?validate=true");

        assertEquals(getFromResponse(response, FactoryDto.class).withLinks(emptyList()), factoryDto);
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenFactoryIsNotExist() throws Exception {
        final String errMessage = format("Factory with id %s is not found", FACTORY_ID);
        doThrow(new NotFoundException(errMessage)).when(factoryManager)
                                                  .getById(anyString());

        final Response response = given().expect()
                                         .statusCode(404)
                                         .when()
                                         .get(SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(getFromResponse(response, ServiceError.class).getMessage(), errMessage);
    }


    @Test
    public void shouldBeAbleToUpdateFactory() throws Exception {
        final Factory existed = createFactory();
        final Factory update = createFactoryWithStorage("git", "http://github.com/codenvy/platform-api1.git");
        when(factoryManager.getById(FACTORY_ID)).thenReturn(existed);
        when(factoryManager.updateFactory(any())).thenReturn(update);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .body(JsonHelper.toJson(asDto(existed, user)))
                                         .when()
                                         .expect()
                                         .statusCode(200)
                                         .put("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        final FactoryDto result = getFromResponse(response, FactoryDto.class);
        verify(factoryManager, times(1)).updateFactory(any());
        assertEquals(result.withLinks(emptyList()), asDto(update, user));
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenUpdatingNonExistingFactory() throws Exception {
        final Factory factory = createFactoryWithStorage("git", "http://github.com/codenvy/platform-api.git");
        doThrow(new NotFoundException(format("Factory with id %s is not found.", FACTORY_ID))).when(factoryManager)
                                                                                              .getById(anyString());

        final Response response = given().auth().basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .body(JsonHelper.toJson(factory))
                                         .when()
                                         .put("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(response.getStatusCode(), 404);
        assertEquals(DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
                     format("Factory with id %s is not found.", FACTORY_ID));
    }

    @Test
    public void shouldNotBeAbleToUpdateANullFactory() throws Exception {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(APPLICATION_JSON)
                                         .when()
                                         .put("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(response.getStatusCode(), 400);
        assertEquals(DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class)
                        .getMessage(), "Factory configuration required");

    }

    // FactoryService#removeFactory(String id) tests:

    @Test
    public void shouldRemoveFactoryByGivenIdentifier() throws Exception {
        final Factory factory = createFactory();
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);

        given().auth()
               .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
               .param("id", FACTORY_ID)
               .expect()
               .statusCode(204)
               .when()
               .delete("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        verify(factoryManager).removeFactory(FACTORY_ID);
    }

    @Test
    public void shouldNotBeAbleToRemoveNotExistingFactory() throws Exception {
        doThrow(new NotFoundException("Not found")).when(factoryManager).removeFactory(anyString());

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .param("id", FACTORY_ID)
                                   .when()
                                   .delete("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(response.getStatusCode(), 404);
    }

    // FactoryService#getImage(String factoryId, String imageId) tests:

    @Test
    public void shouldReturnFactoryImageWithGivenName() throws Exception {
        final byte[] imageContent = Files.readAllBytes(getImagePath());
        final FactoryImage image = new FactoryImage(imageContent, FACTORY_IMAGE_MIME_TYPE, IMAGE_NAME);
        when(factoryManager.getFactoryImages(FACTORY_ID, IMAGE_NAME)).thenReturn(ImmutableSet.of(image));

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "/image?imgId=" + IMAGE_NAME);

        assertEquals(response.getContentType(), FACTORY_IMAGE_MIME_TYPE);
        assertEquals(response.getHeader("content-length"), String.valueOf(imageContent.length));
        assertEquals(response.asByteArray(), imageContent);
    }

    @Test
    public void shouldReturnFirstFoundFactoryImageWhenImageNameNotSpecified() throws Exception {
        final byte[] imageContent = Files.readAllBytes(getImagePath());
        final FactoryImage image = new FactoryImage(imageContent, FACTORY_IMAGE_MIME_TYPE, IMAGE_NAME);
        when(factoryManager.getFactoryImages(FACTORY_ID)).thenReturn(ImmutableSet.of(image));

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "/image");

        assertEquals(response.getContentType(), "image/jpeg");
        assertEquals(response.getHeader("content-length"), String.valueOf(imageContent.length));
        assertEquals(response.asByteArray(), imageContent);
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenFactoryImageWithGivenIdentifierIsNotExist() throws Exception {
        final String errMessage = "Image with name " + IMAGE_NAME + " is not found";
        when(factoryManager.getFactoryImages(FACTORY_ID, IMAGE_NAME)).thenThrow(new NotFoundException(errMessage));

        final Response response = given().expect()
                                         .statusCode(404)
                                         .when()
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "/image?imgId=" + IMAGE_NAME);

        assertEquals(getFromResponse(response, ServiceError.class).getMessage(), errMessage);
    }

    @Test
    public void shouldBeAbleToReturnUrlSnippet() throws Exception {
        final String result = "snippet";
        when(factoryManager.getFactorySnippet(anyString(), anyString(), any(UriInfo.class))).thenReturn(result);

        given().expect()
               .statusCode(200)
               .contentType(MediaType.TEXT_PLAIN)
               .body(equalTo(result))
               .when()
               .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=url");
    }

    private Factory createFactory() {
        return createFactoryWithStorage(PROJECT_SOURCE_TYPE, PROJECT_SOURCE_LOCATION);
    }

    private Factory createFactoryWithStorage(String type, String location) {
        return FactoryImpl.builder()
                          .setId(FACTORY_ID)
                          .setVersion("4.0")
                          .setWorkspace(createWorkspaceConfig(type, location))
                          .setCreator(new AuthorImpl(USER_ID, 12L))
                          .build();
    }

    private static WorkspaceConfig createWorkspaceConfig(String type, String location) {
        return WorkspaceConfigImpl.builder()
                                  .setName(WORKSPACE_NAME)
                                  .setEnvironments(singletonList(new EnvironmentImpl(createEnvDto())))
                                  .setProjects(createProjects(type, location))
                                  .build();
    }

    private static EnvironmentDto createEnvDto() {
        final MachineConfigImpl devMachine = MachineConfigImpl.builder()
                                                              .setDev(true)
                                                              .setName("dev-machine")
                                                              .setType("docker")
                                                              .setSource(new MachineSourceImpl("location").setLocation("recipe"))
                                                              .setServers(asList(new ServerConfImpl("wsagent",
                                                                                                    "8080",
                                                                                                    "https",
                                                                                                    "path1"),
                                                                                 new ServerConfImpl("ref2",
                                                                                                    "8081",
                                                                                                    "https",
                                                                                                    "path2")))
                                                              .setEnvVariables(singletonMap("key1", "value1"))
                                                              .build();
        return org.eclipse.che.api.workspace.server.DtoConverter.asDto(new EnvironmentImpl("dev-env", null, singletonList(devMachine)));
    }

    private static List<ProjectConfig> createProjects(String type, String location) {
        final ProjectConfigImpl projectConfig = new ProjectConfigImpl();
        projectConfig.setSource(new SourceStorageImpl(type, location, null));
        return ImmutableList.of(projectConfig);
    }

    private static <T> T getFromResponse(Response response, Class<T> clazz) throws Exception {
        return DTO.createDtoFromJson(response.getBody().asInputStream(), clazz);
    }

    private static Path getImagePath() throws Exception {
        final URL res = currentThread().getContextClassLoader().getResource("100x100_image.jpeg");
        assertNotNull(res);
        return Paths.get(res.toURI());
    }
}
