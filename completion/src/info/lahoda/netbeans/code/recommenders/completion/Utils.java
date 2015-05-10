package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.eclipse.recommenders.models.ModelCoordinate;
import org.eclipse.recommenders.models.ProjectCoordinate;
import org.eclipse.recommenders.utils.Version;
import org.eclipse.recommenders.utils.Versions;
import org.openide.util.NbPreferences;

/**
 *
 * @author lahvac
 */
public class Utils {

    private static final Logger LOG = Logger.getLogger(Utils.class.getName());
    private static final String KEY_LOCATIONS = "locations";

    public static ModelCoordinate parseCoordinate(String coordinate) {
        String[] parts = coordinate.split(":");
        String extension = "jar";
        String classifier = null;

        if (parts.length > 3) {
            extension = parts[2];
        }
        if (parts.length == 5) {
            classifier = parts[3];
        }

        return new ModelCoordinate(parts[0], parts[1], classifier, extension, parts[parts.length - 1]);
    }

    public static Optional<ModelCoordinate> findBest(ProjectCoordinate pc, Set<ModelCoordinate> candidates) {
        List<Version> candidateVersions = new ArrayList<>();

        for (ModelCoordinate coord : candidates) {
            candidateVersions.add(Version.valueOf(coord.getVersion()));
        }

        Version best = Versions.findClosest(Version.valueOf(pc.getVersion()), candidateVersions);

        for (ModelCoordinate coord : candidates) {
            if (best.equals(Version.valueOf(coord.getVersion())))
                return Optional.of(coord);
        }

        return Optional.absent();
    }

    public static List<String> getLocations() {
        Preferences root = NbPreferences.forModule(Utils.class);

        boolean values;
        
        try {
            values = root.nodeExists(KEY_LOCATIONS);
        } catch (BackingStoreException ex) {
            LOG.log(Level.FINE, null, ex);
            values = false;
        }

        if (!values) {
            return Collections.singletonList("http://download.eclipse.org/recommenders/models/juno/");
        }
        Preferences locations = NbPreferences.forModule(Utils.class).node(KEY_LOCATIONS);
        List<String> result = new ArrayList<>();
        String[] locationKeys;

        try {
            locationKeys = locations.keys();
        } catch (BackingStoreException ex) {
            LOG.log(Level.FINE, null, ex);
            locationKeys = new String[0];
        }

        for (String key : locationKeys) {
            result.add(locations.get(key, null));
        }

        return result;
    }

    public static void setLocations(List<String> locs) {
        Preferences root = NbPreferences.forModule(Utils.class);
        Preferences locations = root.node(KEY_LOCATIONS);

        try {
            locations.removeNode();
        } catch (BackingStoreException ex) {
            LOG.log(Level.FINE, null, ex);
        }

        locations = root.node(KEY_LOCATIONS);

        int i = 0;

        for (String loc : locs) {
            locations.put(Integer.toString(i++), loc);
        }
    }

}
