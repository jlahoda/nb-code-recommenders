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
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;

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
        Data data = new Data(new URL(System.getProperty("recommenders.repo", "http://download.eclipse.org/recommenders/models/juno/")));

        assertTrue(data.validate());

        data.open();

        try {
            AdvisorImpl advisor = new AdvisorImpl();

            File guavaFile = Places.getCacheSubfile("test/guava-10.0.jar");
            
            download(new URL(System.getProperty("guava.location", "http://central.maven.org/maven2/com/google/guava/guava/10.0/guava-10.0.jar")), guavaFile);
            checkKnown(guavaFile, advisor, data);

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
