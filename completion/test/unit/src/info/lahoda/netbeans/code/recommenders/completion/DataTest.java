package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import org.eclipse.recommenders.models.DependencyInfo;
import org.eclipse.recommenders.models.DependencyType;
import org.eclipse.recommenders.models.ModelCoordinate;
import org.eclipse.recommenders.models.ProjectCoordinate;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.platform.JavaPlatformManager;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.jumpto.type.TypeBrowser;
import org.netbeans.junit.NbTestCase;
import org.netbeans.spi.java.hints.JavaFixUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Utilities;

/**
 *
 * @author lahvac
 */
public class DataTest extends NbTestCase {

    public DataTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
        File userdir = new File(getWorkDir(), "userdir");
        userdir.mkdirs();
        System.setProperty("netbeans.user", userdir.getAbsolutePath());
    }

    public void testSuggest() throws IOException, URISyntaxException {
        Data data = new Data(new URL(System.getProperty("recommenders.luna.repo", "http://download.eclipse.org/recommenders/models/luna/")));

        assertTrue(data.validate());

        data.open();

        try {
            AdvisorImpl advisor = new AdvisorImpl();

            File jgitFile = Places.getCacheSubfile("test/org.eclipse.jgit-3.7.1.201504261725-r.jar");
            
            download(new URL(System.getProperty("jgit.location", "https://repo.eclipse.org/content/groups/releases/org/eclipse/jgit/org.eclipse.jgit/3.7.1.201504261725-r/org.eclipse.jgit-3.7.1.201504261725-r.jar")), jgitFile);
            checkKnown(jgitFile, advisor, data);

            ClassPath bcp = JavaPlatformManager.getDefault().getDefaultPlatform().getBootstrapLibraries();
            FileObject jlObject = bcp.findResource("java/lang/Object.class");

            assertNotNull(jlObject);

            FileObject root = bcp.findOwnerRoot(jlObject);

            assertNotNull(root);

            File rtJar = FileUtil.archiveOrDirForURL(root.toURL());

            assertNotNull(rtJar);
            assertTrue(rtJar.exists());

            checkKnown(rtJar, advisor, data);
        } finally {
            data.close();
        }
    }

    public void testSuggestNB() throws IOException, URISyntaxException {
        Data data = new Data(new URL(System.getProperty("recommenders.netbeans.repo", "http://download.codetrails.com/models/netbeans-2015-05-11/")));

        assertTrue(data.validate());

        data.open();

        try {
            AdvisorImpl advisor = new AdvisorImpl();

            checkKnown(jarFileForClass(JavaSource.class), advisor, data);
            checkKnown(jarFileForClass(JavaFixUtilities.class), advisor, data);
            checkKnown(jarFileForClass(TypeBrowser.class), advisor, data);
            checkKnown(jarFileForResource("javax/tools/overview.html"), advisor, data);
        } finally {
            data.close();
        }
    }

    private File jarFileForClass(Class dependencyClass) throws URISyntaxException {
        URL source = dependencyClass.getProtectionDomain().getCodeSource().getLocation();
        
        return Utilities.toFile(source.toURI());
    }

    private File jarFileForResource(String resource) throws URISyntaxException {
        URL source = DataTest.class.getClassLoader().getResource(resource);

        source = FileUtil.getArchiveFile(source);
        
        return Utilities.toFile(source.toURI());
    }

    private void checkKnown(File jar2Check, AdvisorImpl advisor, Data data) {
        DependencyInfo depInfo = new DependencyInfo(jar2Check, DependencyType.JAR);

        Optional<ProjectCoordinate> projCoord = advisor.suggest(depInfo);

        assertTrue(projCoord.isPresent());

        Optional<ModelCoordinate> modelCoord = data.suggest(projCoord.get(), "call");

        assertTrue(modelCoord.isPresent());
        assertTrue(data.resolve(modelCoord.get(), true).isPresent());
    }

    private void download(URL from, File to) throws IOException {
        try (InputStream in = from.openStream();
             OutputStream out = new BufferedOutputStream(new FileOutputStream(to))) {
            FileUtil.copy(in, out);
        }
    }
}
