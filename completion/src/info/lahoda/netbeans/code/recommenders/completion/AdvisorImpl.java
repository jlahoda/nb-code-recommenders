package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import java.io.File;
import org.eclipse.recommenders.models.DependencyInfo;
import org.eclipse.recommenders.models.DependencyType;
import org.eclipse.recommenders.models.ProjectCoordinate;
import org.eclipse.recommenders.models.advisors.JREDirectoryNameAdvisor;
import org.eclipse.recommenders.models.advisors.JREReleaseFileAdvisor;
import org.eclipse.recommenders.models.advisors.MavenPomPropertiesAdvisor;
import org.eclipse.recommenders.models.advisors.MavenPomXmlAdvisor;
import org.eclipse.recommenders.models.advisors.ProjectCoordinateAdvisorService;

/**
 *
 * @author lahvac
 */
public class AdvisorImpl extends ProjectCoordinateAdvisorService {

    public AdvisorImpl() {
        addAdvisor(new MavenPomXmlAdvisor());
        addAdvisor(new MavenPomPropertiesAdvisor());
        addAdvisor(new JREAdvisor());
        addAdvisor(new NBAdvisorImpl());
    }

    private static final class JREAdvisor extends ProjectCoordinateAdvisorService {

        public JREAdvisor() {
            addAdvisor(new JREReleaseFileAdvisor());
            addAdvisor(new JREDirectoryNameAdvisor());
        }

        @Override
        public Optional<ProjectCoordinate> suggest(DependencyInfo di) {
            File root = null;

            if (isJDKRoot(di.getFile().getParentFile().getParentFile())) {
                root = di.getFile().getParentFile().getParentFile();
            } else if (isJDKRoot(di.getFile().getParentFile().getParentFile().getParentFile())) {
                root = di.getFile().getParentFile().getParentFile().getParentFile();
            }

            if (root != null) {
                return super.suggest(new DependencyInfo(root, DependencyType.JRE));
            } else {
                return Optional.absent();
            }
        }

        private boolean isJDKRoot(File root) {
            return new File(new File(root, "bin"), "javac").isFile() ||
                   new File(new File(root, "bin"), "javac.exe").isFile();
        }

    }

}
