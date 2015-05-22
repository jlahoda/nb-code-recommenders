package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.recommenders.models.DownloadCallback;
import org.eclipse.recommenders.models.IModelIndex;
import org.eclipse.recommenders.models.IModelRepository;
import org.eclipse.recommenders.models.ModelCoordinate;
import org.eclipse.recommenders.models.ProjectCoordinate;
import org.eclipse.recommenders.utils.Pair;
import org.openide.filesystems.FileUtil;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.openide.util.NbPreferences;
import org.openide.util.RequestProcessor;
import org.openide.xml.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author lahvac
 */
public class Data implements IModelRepository, IModelIndex {

    private static final RequestProcessor DOWNLOADER = new RequestProcessor(Data.class.getName(), 1, false, false);
    private static int idCount = 0;

    private final URL repository;
    private final File cacheDir;
    private final String id;
    private IndexReader reader;

    public Data(URL repository) {
        this.repository = repository;
        Preferences recommenders = NbPreferences.forModule(Data.class).node("recommenders");
        String cacheDirName = recommenders.get(repository.toString(), null);

        if (cacheDirName == null) {
            int lastCache = recommenders.getInt("last-cache", -1);
            recommenders.putInt("last-cache", ++lastCache);
            recommenders.putInt(repository.toString(), lastCache);
            cacheDirName = Integer.toString(lastCache);
        }

        cacheDir = Places.getCacheSubdirectory("recommenders/" + cacheDirName);

        cacheDir.mkdirs();

        this.id = Integer.toString(idCount++);
    }

    public String getId() {
        return id;
    }

    public boolean validate() {
        File indexDir = indexDir();

        if (!indexDir.exists()) {
            Optional<File> indexFile = resolve(INDEX, true);

            if (!indexFile.isPresent())
                return false;

            try {
                ZipFile zipFile = new ZipFile(indexFile.get());
                Enumeration<? extends ZipEntry> entries = zipFile.entries();

                while (entries.hasMoreElements()) {
                    ZipEntry ze = entries.nextElement();

                    if (ze.isDirectory())
                        continue;

                    File target = new File(indexDir, ze.getName());

                    target.getParentFile().mkdirs();

                    try (InputStream in = zipFile.getInputStream(ze);
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
                        FileUtil.copy(in, out);
                    }
                }
            } catch (IOException ex) {
                //TODO: log
                return false;
            }
        }

        if (!indexDir.isDirectory())
            return false;

        //TODO: check Lucene index
        return true;
    }

    private File indexDir() {
        return new File(cacheDir, "index");
    }

    @Override
    public Optional<File> getLocation(final ModelCoordinate mc, boolean bln) {
        Pair<ModelCoordinate, String> coordAndVersion = resolveSnapshotCoordinate(mc, false);

        if (coordAndVersion != null) {
            String path = cachePath(coordAndVersion);
            File targetFile = cacheFile(path);

            if (targetFile.exists()) {
                return Optional.of(targetFile);
            }
        }

        if (bln) {
            DOWNLOADER.post(new Runnable() {
                @Override public void run() {
                    resolve(mc, true);
                }
            });
        }

        return Optional.absent();
    }

    @Override
    public Optional<File> resolve(ModelCoordinate mc, boolean bln) {
        return resolve(mc, bln, DownloadCallback.NULL);
    }

    @Override
    public Optional<File> resolve(ModelCoordinate mc, boolean bln, DownloadCallback dc) {
        Pair<ModelCoordinate, String> coordAndVersion = resolveSnapshotCoordinate(mc, true);

        if (coordAndVersion == null)
            return Optional.absent();

        return ensureInCache(cachePath(coordAndVersion));
    }

    private Pair<ModelCoordinate, String> resolveSnapshotCoordinate(ModelCoordinate mc, boolean download) {
        StringBuilder metadataPath = new StringBuilder();
        metadataPath.append(mc.getGroupId().replace('.', '/'));
        metadataPath.append("/" + mc.getArtifactId());
        metadataPath.append("/" + mc.getVersion() + "-SNAPSHOT");
        metadataPath.append("/maven-metadata.xml");

        Optional<File> metadataFile = download ? ensureInCache(metadataPath.toString()) : Optional.of(cacheFile(metadataPath.toString()));

        if (!metadataFile.isPresent() || !metadataFile.get().exists())
            return null;

        try (InputStream in = new FileInputStream(metadataFile.get())) {
            org.w3c.dom.Document xmlDoc = XMLUtil.parse(new InputSource(in), false, false, null, null);

            Element snapshotVersions = findSubElement(findSubElement(xmlDoc.getDocumentElement(), "versioning"), "snapshotVersions");
            NodeList nl = snapshotVersions.getChildNodes();

            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);

                if (n.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                if (!"snapshotVersion".equals(n.getNodeName()))
                    continue;

                if (mc.getClassifier() != null && !mc.getClassifier().equals(findSubElement((Element) n, "classifier").getTextContent()))
                    continue;

                Element value = findSubElement((Element) n, "value");

                return Pair.newPair(mc, value.getTextContent());
            }

            return null;
        } catch (IOException | SAXException ex) {
            Exceptions.printStackTrace(ex);
            return null;
        }
    }

    private Element findSubElement(Element el, String toFind) {
        NodeList nl = el.getChildNodes();

        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);

            if (n.getNodeType() != Node.ELEMENT_NODE)
                continue;

            if (toFind.equals(n.getNodeName()))
                return (Element) n;
        }

        return null;
    }

    private Optional<File> ensureInCache(String path) {
        try {
            File targetFile = cacheFile(path);
            
            if (targetFile.exists()) {
                return Optional.of(targetFile);
            }
            
            targetFile.getParentFile().mkdirs();
            
            URL target = new URL(repository.getProtocol(), repository.getHost(), repository.getPort(), repository.getPath() + path);

            try (InputStream in = target.openStream();
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                FileUtil.copy(in, out);
            }

            return Optional.of(targetFile);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return Optional.absent();
        }
    }

    private String cachePath(Pair<ModelCoordinate, String> coordAndVersion) {
        ModelCoordinate mc = coordAndVersion.getFirst();
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(mc.getGroupId().replace('.', '/'));
        pathBuilder.append("/" + mc.getArtifactId());
        pathBuilder.append("/" + mc.getVersion() + "-SNAPSHOT");
        pathBuilder.append("/" + mc.getArtifactId());
        pathBuilder.append("-" + coordAndVersion.getSecond());
        if (mc.getClassifier() != null)
            pathBuilder.append("-" + mc.getClassifier());
        pathBuilder.append("." + mc.getExtension());
        return pathBuilder.toString();
    }
    
    private File cacheFile(String path) {
        return new File(new File(cacheDir, "cache"), path);
    }

    @Override
    public void open() throws IOException {
        reader = IndexReader.open(FSDirectory.open(indexDir()));
    }

    @Override
    public void close() throws IOException {
        reader.close();
        reader = null;
    }

    @Override
    public ImmutableSet<ModelCoordinate> getKnownModels(String string) {
        try {
            Builder<ModelCoordinate> result = ImmutableSet.builder();
            int maxDoc = reader.maxDoc();

            for (int i = 0; i < maxDoc; i++) {
                if (reader.isDeleted(i))
                    continue;
                Document doc = reader.document(i);
                Fieldable field = doc.getFieldable(string);

                if (field != null) {
                    result = result.add(Utils.parseCoordinate(field.stringValue()));
                }
            }

            return result.build();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            return ImmutableSet.of();
        }
    }

    @Override
    public Optional<ProjectCoordinate> suggestProjectCoordinateByArtifactId(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Optional<ProjectCoordinate> suggestProjectCoordinateByFingerprint(String string) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ImmutableSet<ModelCoordinate> suggestCandidates(ProjectCoordinate pc, String string) {
        Builder<ModelCoordinate> result = ImmutableSet.builder();

        for (ModelCoordinate coord : getKnownModels(string)) {
            if (coord.getGroupId().equals(pc.getGroupId()) &&
                (coord.getArtifactId().equals(pc.getArtifactId()) ||
                 coord.getArtifactId().equals(alternateArtifactID(pc)))) {
                result = result.add(coord);
            }
        }

        return result.build();
    }

    private String alternateArtifactID(ProjectCoordinate pc) {
        String artID = pc.getArtifactId().replace('-', '.');
        int dot = artID.indexOf('.');

        if (dot == (-1)) dot = artID.length();

        String prefix = artID.substring(0, dot);

        if (pc.getGroupId().endsWith("." + prefix)) {
            return pc.getGroupId() + artID.substring(dot);
        } else {
            return pc.getGroupId() + "." + artID;
        }
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
