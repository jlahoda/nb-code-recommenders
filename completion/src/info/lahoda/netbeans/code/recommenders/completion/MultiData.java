package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.recommenders.models.DownloadCallback;
import org.eclipse.recommenders.models.IModelIndex;
import org.eclipse.recommenders.models.IModelRepository;
import org.eclipse.recommenders.models.ModelCoordinate;
import org.eclipse.recommenders.models.ProjectCoordinate;

/**
 *
 * @author lahvac
 */
public class MultiData implements IModelRepository, IModelIndex {

    private static final String KEY_DELEGATE = "nb-delegate";
    
    private final List<Data> delegate;

    public MultiData(List<Data> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<File> getLocation(ModelCoordinate mc, boolean bln) {
        Optional<String> expectedId = mc.getHint(KEY_DELEGATE);

        if (!expectedId.isPresent())
            return Optional.absent();

        for (Data d : delegate) {
            if (d.getId().equals(expectedId.get()))
                return d.getLocation(mc, bln);
        }

        return Optional.absent();
    }

    @Override
    public Optional<File> resolve(ModelCoordinate mc, boolean bln) {
        Optional<String> expectedId = mc.getHint(KEY_DELEGATE);

        if (!expectedId.isPresent())
            return Optional.absent();

        for (Data d : delegate) {
            if (d.getId().equals(expectedId.get()))
                return d.resolve(mc, bln);
        }

        return Optional.absent();
    }

    @Override
    public Optional<File> resolve(ModelCoordinate mc, boolean bln, DownloadCallback dc) {
        Optional<String> expectedId = mc.getHint(KEY_DELEGATE);

        if (!expectedId.isPresent())
            return Optional.absent();

        for (Data d : delegate) {
            if (d.getId().equals(expectedId.get()))
                return d.resolve(mc, bln, dc);
        }

        return Optional.absent();
    }

    @Override
    public void open() throws IOException {
        for (Data d : delegate) {
            d.open();
        }
    }

    @Override
    public void close() throws IOException {
        for (Data d : delegate) {
            d.close();
        }
    }

    @Override
    public ImmutableSet<ModelCoordinate> getKnownModels(String type) {
        Builder<ModelCoordinate> result = ImmutableSet.builder();

        for (Data d : delegate) {
            result.addAll(d.getKnownModels(type));
        }

        return result.build();
    }

    @Override
    public Optional<ProjectCoordinate> suggestProjectCoordinateByArtifactId(String id) {
        for (Data d : delegate) {
            Optional<ProjectCoordinate> opt = d.suggestProjectCoordinateByArtifactId(id);

            if (opt.isPresent())
                return opt;
        }

        return Optional.absent();
    }

    @Override
    public Optional<ProjectCoordinate> suggestProjectCoordinateByFingerprint(String hash) {
        for (Data d : delegate) {
            Optional<ProjectCoordinate> opt = d.suggestProjectCoordinateByFingerprint(hash);

            if (opt.isPresent())
                return opt;
        }

        return Optional.absent();
    }

    @Override
    public ImmutableSet<ModelCoordinate> suggestCandidates(ProjectCoordinate pc, String type) {
        Builder<ModelCoordinate> result = ImmutableSet.builder();

        for (Data d : delegate) {
            for (ModelCoordinate candidate : d.suggestCandidates(pc, type)) {
                Map<String, String> hints = new HashMap<>(candidate.getHints());
                hints.put(KEY_DELEGATE, d.getId());
                result.add(new ModelCoordinate(candidate.getGroupId(),
                                               candidate.getArtifactId(),
                                               candidate.getClassifier(),
                                               candidate.getExtension(),
                                               candidate.getVersion(),
                                               hints));
            }
        }

        return result.build();
    }

    @Override
    public Optional<ModelCoordinate> suggest(ProjectCoordinate pc, String type) {
        return Utils.findBest(pc, suggestCandidates(pc, type));
    }

    @Override
    public void updateIndex(File file) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
