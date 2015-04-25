package com.alexecollins.docker.orchestration;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.alexecollins.docker.orchestration.model.BuildFlag;
import com.alexecollins.docker.orchestration.model.Conf;
import com.alexecollins.docker.orchestration.model.HealthChecks;
import com.alexecollins.docker.orchestration.model.Id;
import com.alexecollins.docker.orchestration.model.Link;
import com.alexecollins.docker.orchestration.util.Logs;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.DockerException;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.PushEventStreamItem;
import com.github.dockerjava.jaxrs.BuildImageCmdExec;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.client.ClientResponse;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.hasItem;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DockerOrchestratorTest {

    private static final String IMAGE_NAME = "theImage";
    private static final String IMAGE_ID = "imageId";

    private static final String CONTAINER_NAME = "theContainer";
    private static final String CONTAINER_ID = "containerId";

    private static final String TAG_NAME = "test-tag";

    @Mock
    private DockerClient dockerMock;
    @Mock
    private Repo repoMock;
    @Mock
    private File fileMock;
    @Mock
    private File srcFileMock;
    @Mock
    private Id idMock;
    @Mock
    private FileOrchestrator fileOrchestratorMock;
    @Mock
    private ClientResponse clientResponseMock;
    @Mock
    private Conf confMock;
    @Mock
    private CreateContainerResponse createContainerResponse;
    @Mock
    private ContainerConfig containerConfigMock;
    @Mock
    private Container containerMock;
    @Mock
    private InspectContainerResponse containerInspectResponseMock;
    @Mock
    private BuildImageCmd buildImageCmdMock;
    @Mock
    private CreateContainerCmd createContainerCmdMock;
    @Mock
    private StartContainerCmd startContainerCmdMock;
    @Mock
    private InspectContainerCmd inspectContainerCmdMock;
    @Mock
    private ListContainersCmd listContainersCmdMockOnlyRunning;
    @Mock
    private RemoveContainerCmd removeContainerCmdMock;
    @Mock
    private StopContainerCmd stopContainerCmdMock;
    @Mock
    private TagImageCmd tagImageCmdMock;
    @Mock
    private PushImageCmd pushImageCmd;
    @Mock
    private DockerfileValidator dockerfileValidator;
    @Mock
    private DefinitionFilter definitionFilter;
    private DockerOrchestrator testObj;

    @SuppressWarnings("unchecked")
    private final Appender<ILoggingEvent> appender = mock(Appender.class);
    private final ArgumentCaptor<ILoggingEvent> captor = ArgumentCaptor.forClass(ILoggingEvent.class);

    private final static Logger LOGGER = (Logger) LoggerFactory.getLogger(DockerOrchestrator.class);

    @Before
    public void setup() throws DockerException, IOException {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        LOGGER.setLevel(Level.INFO);
        LOGGER.addAppender(appender);

        testObj = new DockerOrchestrator(
                dockerMock,
                repoMock,
                fileOrchestratorMock,
                EnumSet.noneOf(BuildFlag.class),
                LOGGER,
                dockerfileValidator,
                definitionFilter
        );

        when(repoMock.src(idMock)).thenReturn(srcFileMock);
        when(repoMock.conf(idMock)).thenReturn(confMock);
        when(repoMock.tag(idMock)).thenReturn(IMAGE_NAME);
        when(repoMock.findImageId(idMock)).thenReturn(IMAGE_ID);
        when(repoMock.containerName(idMock)).thenReturn(CONTAINER_NAME);

        when(confMock.getLinks()).thenReturn(new ArrayList<Link>());
        when(confMock.getHealthChecks()).thenReturn(new HealthChecks());
        when(confMock.getTags()).thenReturn(Arrays.asList(IMAGE_NAME + ":" + TAG_NAME));

        when(repoMock.findImageId(idMock)).thenReturn(IMAGE_ID);
        when(repoMock.findContainer(idMock)).thenReturn(containerMock);
        when(containerMock.getId()).thenReturn(CONTAINER_ID);

        when(fileOrchestratorMock.prepare(idMock, srcFileMock, confMock)).thenReturn(fileMock);

        when(repoMock.ids(false)).thenReturn(Arrays.asList(idMock));
        when(repoMock.ids(true)).thenReturn(Arrays.asList(idMock));
        when(repoMock.tag(any(Id.class))).thenReturn(IMAGE_NAME + ":" + TAG_NAME);

        when(dockerMock.buildImageCmd(eq(fileMock))).thenReturn(buildImageCmdMock);
        when(buildImageCmdMock.withRemove(false)).thenReturn(buildImageCmdMock);
        when(buildImageCmdMock.withTag(any(String.class))).thenReturn(buildImageCmdMock);
        when(buildImageCmdMock.exec()).thenReturn(new BuildImageCmdExec.ResponseImpl(IOUtils.toInputStream("Successfully built")));

        when(dockerMock.createContainerCmd(IMAGE_ID)).thenReturn(createContainerCmdMock);
        when(createContainerCmdMock.exec()).thenReturn(createContainerResponse);
        when(createContainerCmdMock.withName(eq(CONTAINER_NAME))).thenReturn(createContainerCmdMock);

        when(createContainerResponse.getId()).thenReturn(CONTAINER_ID);

        when(dockerMock.startContainerCmd(CONTAINER_ID)).thenReturn(startContainerCmdMock);
        when(dockerMock.stopContainerCmd(CONTAINER_ID)).thenReturn(stopContainerCmdMock);
        when(dockerMock.removeContainerCmd(CONTAINER_ID)).thenReturn(removeContainerCmdMock);

        when(dockerMock.listContainersCmd()).thenReturn(listContainersCmdMockOnlyRunning);
        when(listContainersCmdMockOnlyRunning.withShowAll(false)).thenReturn(listContainersCmdMockOnlyRunning);
        when(listContainersCmdMockOnlyRunning.exec()).thenReturn(Collections.<Container>emptyList());

        when(stopContainerCmdMock.withTimeout(anyInt())).thenReturn(stopContainerCmdMock);

        when(dockerMock.inspectContainerCmd(CONTAINER_ID)).thenReturn(inspectContainerCmdMock);
        when(inspectContainerCmdMock.exec()).thenReturn(containerInspectResponseMock);
        when(containerInspectResponseMock.getImageId()).thenReturn(IMAGE_ID);

        when(dockerMock.tagImageCmd(anyString(), anyString(), anyString())).thenReturn(tagImageCmdMock);
        when(tagImageCmdMock.withForce()).thenReturn(tagImageCmdMock);

        when(dockerMock.pushImageCmd(anyString())).thenReturn(pushImageCmd);
        when(pushImageCmd.withAuthConfig(any(AuthConfig.class))).thenReturn(pushImageCmd);
        when(pushImageCmd.exec()).thenReturn(new PushImageCmd.Response() {
            private final InputStream proxy = IOUtils.toInputStream("{\"status\":\"The push refers to...\"}");

            @Override
            public int read() throws IOException {
                return proxy.read();
            }

            @Override
            public Iterable<PushEventStreamItem> getItems() throws IOException {
                return null;
            }
        });

        when(definitionFilter.test(any(Id.class), any(Conf.class))).thenReturn(true);
    }


    @Test
    public void createAndStartNewContainer() throws DockerException, IOException {
        when(repoMock.imageExists(idMock)).thenReturn(false);
        when(repoMock.findContainer(idMock)).thenReturn(null);

        testObj.start();

        verify(createContainerCmdMock).exec();
        verify(startContainerCmdMock).exec();
    }

    @Test
    public void logsDockerOutputWhenStartContainerFails() throws DockerException, IOException {
        Container myContainer = mock(Container.class);
        when(myContainer.getId()).thenReturn("Not Exist");

        when(repoMock.imageExists(idMock)).thenReturn(true);
        when(repoMock.findContainer(idMock)).thenReturn(myContainer);

        when(confMock.isLogOnFailure()).thenReturn(true);

        when(dockerMock.inspectContainerCmd(anyString())).thenThrow(new DockerException("something wrong", 404));

        LogContainerCmd logContainerCmd = mock(LogContainerCmd.class);
        when(dockerMock.logContainerCmd(anyString())).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdErr()).thenReturn(logContainerCmd);
        when(logContainerCmd.withStdOut()).thenReturn(logContainerCmd);

        byte[] bytes = new byte[]{Logs.BytePrefix.StdOut.getHeaderByte(), 0, 0, 0, 0, 0, 0, 0, 'b', 'l', 'a', 'h', ' ', 'b', 'l', 'a', 'h', '\n',
                Logs.BytePrefix.StdErr.getHeaderByte(), 0, 0, 0, 0, 0, 0, 0, 'b', 'l', 'a', 'h', '2', ' ', 'b', 'l', 'a', 'h', '2'};
        InputStream stream = new ByteArrayInputStream(bytes);
        when(logContainerCmd.exec()).thenReturn(stream);

        try {
            testObj.start();
        } catch (OrchestrationException e) {
            verify(appender, atLeastOnce()).doAppend(captor.capture());
            List<ILoggingEvent> logging = captor.getAllValues();
            assertThat(logging, CoreMatchers.hasItem(loggedMessage("STDOUT: blah blah")));
            assertThat(logging, CoreMatchers.hasItem(loggedMessage("STDERR: blah2 blah2")));
        } catch (Exception e) {
            fail("Expected OrchestrationException");
        }
    }

    @Test
    public void startExistingContainerAsImageIdsMatch() throws DockerException, IOException {
        when(repoMock.imageExists(idMock)).thenReturn(true);
        when(listContainersCmdMockOnlyRunning.exec()).thenReturn(Collections.<Container>emptyList());

        testObj.start();

        verify(createContainerCmdMock, times(0)).exec();
        verify(startContainerCmdMock).exec();
    }

    @Test
    public void containerIsAlreadyRunning() throws DockerException, IOException {
        when(listContainersCmdMockOnlyRunning.exec()).thenReturn(Arrays.asList(containerMock));

        testObj.start();

        verify(createContainerCmdMock, times(0)).exec();
        verify(startContainerCmdMock, times(0)).exec();
    }

    @Test
    public void removeExistingContainerThenCreateAndStartNewOneAsImageIdsDoNotMatch() throws DockerException, IOException {
        when(containerInspectResponseMock.getImageId()).thenReturn("A Different Image Id");

        testObj.start();

        verify(removeContainerCmdMock).exec();
        verify(createContainerCmdMock).exec();
        verify(startContainerCmdMock).exec();
    }

    @Test
    public void stopARunningContainer() {
        when(repoMock.findContainers(idMock, false)).thenReturn(Arrays.asList(containerMock));
        when(stopContainerCmdMock.withTimeout(1)).thenReturn(stopContainerCmdMock);

        testObj.stop();

        verify(stopContainerCmdMock).exec();
    }

    @Test
    public void logsLoadedPlugin() throws Exception {
        verify(appender, atLeastOnce()).doAppend(captor.capture());
        List<ILoggingEvent> logging = captor.getAllValues();
        assertThat(logging, hasItem(loggedMessage("Loaded " + TestPlugin.class + " plugin")));
    }

    @Test
    public void pluginStarted() throws Exception {
        TestPlugin testObjPlugin = testObj.getPlugin(TestPlugin.class);

        assertNull(testObjPlugin.lastStarted());

        testObj.start();

        assertEquals("idMock", testObjPlugin.lastStarted().toString());
    }

    @Test
    public void pluginStopped() throws Exception {
        when(repoMock.findContainers(idMock, false)).thenReturn(Arrays.asList(containerMock));
        TestPlugin testObjPlugin = testObj.getPlugin(TestPlugin.class);

        assertNull(testObjPlugin.lastStopped());

        testObj.stop();

        assertEquals("idMock", testObjPlugin.lastStopped().toString());
    }

    @Test
    public void buildImage() {
        testObj.build(idMock);

        verify(dockerMock).tagImageCmd(IMAGE_ID, IMAGE_NAME, TAG_NAME);
    }

    @Test
    public void buildImageWithRegistryAndPort() {
        String repositoryWithRegistryAndPort = "my.registry.com:5000/mynamespace/myrepository";

        when(confMock.getTags()).thenReturn(Arrays.asList(repositoryWithRegistryAndPort + ":" + TAG_NAME));
        when(tagImageCmdMock.withForce()).thenReturn(tagImageCmdMock);

        testObj.build(idMock);

        verify(dockerMock).tagImageCmd(IMAGE_ID, repositoryWithRegistryAndPort, TAG_NAME);
    }

    @Test
    public void pushImage() {
        testObj.push();

        verify(dockerMock).pushImageCmd(IMAGE_NAME);
    }

    @Test
    public void pushImageWithRegistryAndPort() {
        String repositoryWithRegistryAndPort = "my.registry.com:5000/mynamespace/myrepository";

        when(repoMock.tag(idMock)).thenReturn(repositoryWithRegistryAndPort + ":" + TAG_NAME);

        testObj.push();

        verify(dockerMock).pushImageCmd(repositoryWithRegistryAndPort);
    }

    @Test
    public void validationDelegatesToDockerfileValidator() throws Exception {

        testObj.validate();
        verify(dockerfileValidator).validate(srcFileMock);

    }

    @Test
    public void filteredDefinitionsAreNotInvoked() throws Exception {
        when(definitionFilter.test(any(Id.class), any(Conf.class))).thenReturn(false);

        testObj.validate();
        testObj.clean();
        testObj.build();
        testObj.start();
        testObj.stop();
        testObj.push();

        verifyNoMoreInteractions(dockerMock);


    }

    private static TypeSafeMatcher<ILoggingEvent> loggedMessage(final String message) {
        return new TypeSafeMatcher<ILoggingEvent>() {
            @Override
            protected boolean matchesSafely(ILoggingEvent event) {
                return event.getFormattedMessage().contains(message);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText(String.format("message = <%s>", message));
            }
        };
    }
}