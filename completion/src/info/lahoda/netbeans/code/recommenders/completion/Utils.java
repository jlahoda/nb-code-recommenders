package info.lahoda.netbeans.code.recommenders.completion;

import org.eclipse.recommenders.models.ModelCoordinate;

/**
 *
 * @author lahvac
 */
public class Utils {

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

}
