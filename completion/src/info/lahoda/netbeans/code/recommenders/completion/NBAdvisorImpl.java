package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Manifest;
import org.eclipse.recommenders.models.DependencyInfo;
import org.eclipse.recommenders.models.DependencyType;
import org.eclipse.recommenders.models.IProjectCoordinateAdvisor;
import org.eclipse.recommenders.models.ProjectCoordinate;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author lahvac
 */
public class NBAdvisorImpl implements IProjectCoordinateAdvisor {

    @Override
    public Optional<ProjectCoordinate> suggest(DependencyInfo di) {
        if (di.getType() != DependencyType.JAR)
            return Optional.absent();

        if (di.getFile().getAbsolutePath().endsWith("java/modules/ext/nb-javac-api.jar")) {
            return Optional.of(new ProjectCoordinate("org.netbeans.external", "nb-javac-api", "0.0.0"));
        }

        FileObject dir = FileUtil.getArchiveRoot(FileUtil.toFileObject(di.getFile()));
        try (InputStream in = dir.getFileObject("META-INF/MANIFEST.MF").getInputStream()) {
            Manifest m = new Manifest(in);
            String moduleName = m.getMainAttributes().getValue("OpenIDE-Module");
            if (moduleName == null)
                return Optional.absent();
            if (moduleName.contains("/")) {
                moduleName = moduleName.substring(0, moduleName.indexOf('/'));
            }
            String groupId = "org.netbeans.modules";
            if (!"-".equals(m.getMainAttributes().getValue("OpenIDE-Module-Public-Packages")) &&
                m.getMainAttributes().getValue("OpenIDE-Module-Friends") == null) {
                groupId = "org.netbeans.api";
            }
            return Optional.of(new ProjectCoordinate(groupId, moduleName.replace('.', '-'), "0.0.0"));
        } catch (IOException ex) {
            //TODO: log
            return Optional.absent();
        }
    }

}
