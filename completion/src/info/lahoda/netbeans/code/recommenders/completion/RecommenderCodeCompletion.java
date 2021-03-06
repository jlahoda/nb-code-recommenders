package info.lahoda.netbeans.code.recommenders.completion;

import com.google.common.base.Optional;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree.Kind;
import static com.sun.source.tree.Tree.Kind.MEMBER_SELECT;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.swing.ImageIcon;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.eclipse.recommenders.calls.ICallModel;
import org.eclipse.recommenders.calls.SingleZipCallModelProvider;
import org.eclipse.recommenders.models.DependencyInfo;
import org.eclipse.recommenders.models.DependencyType;
import org.eclipse.recommenders.models.IInputStreamTransformer;
import org.eclipse.recommenders.models.IModelIndex;
import org.eclipse.recommenders.models.IModelRepository;
import org.eclipse.recommenders.models.ModelCoordinate;
import org.eclipse.recommenders.models.ProjectCoordinate;
import org.eclipse.recommenders.models.UniqueTypeName;
import org.eclipse.recommenders.models.advisors.ProjectCoordinateAdvisorService;
import org.eclipse.recommenders.utils.Recommendation;
import static org.eclipse.recommenders.utils.Recommendations.top;
import org.eclipse.recommenders.utils.names.IMethodName;
import org.eclipse.recommenders.utils.names.ITypeName;
import org.eclipse.recommenders.utils.names.VmMethodName;
import org.eclipse.recommenders.utils.names.VmTypeName;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.lexer.JavaTokenId;
import org.netbeans.api.java.source.ClasspathInfo.PathKind;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.JavaSource.Phase;
import org.netbeans.api.java.source.SourceUtils;
import org.netbeans.api.java.source.Task;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.modules.editor.java.JavaCompletionItem;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

/**
 *
 * @author lahvac
 */
public class RecommenderCodeCompletion extends AsyncCompletionQuery {

    private static final Logger LOG = Logger.getLogger(RecommenderCodeCompletion.class.getName());
            static       double MIN_RELEVANCE = 0.01d;
    private final IModelIndex index;
    private final IModelRepository repository;
    private final ProjectCoordinateAdvisorService coordinateService;

    public RecommenderCodeCompletion(IModelIndex index, IModelRepository repository, ProjectCoordinateAdvisorService coordinateService) {
        this.index = index;
        this.repository = repository;
        this.coordinateService = coordinateService;
    }

    @Override
    protected void query(final CompletionResultSet resultSet, Document doc, final int caretOffset) {
        JavaSource js = JavaSource.forDocument(doc);

        try {
            if (js == null) return ;
            
            js.runUserActionTask(new Task<CompilationController>() {
                @Override public void run(CompilationController parameter) throws Exception {
                    parameter.toPhase(Phase.ELEMENTS_RESOLVED); //XXX: should check the content and prevent attribution if possible

                    resolveCodeCompletion(parameter, caretOffset, resultSet);
                }
            }, true);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            resultSet.finish();
        }
    }

    private void resolveCodeCompletion(CompilationInfo info, int caretOffset, CompletionResultSet resultSet) throws Exception {
        resultSet.addAllItems(resolveCodeCompletion(info, caretOffset));
    }

    List<CompletionItem> resolveCodeCompletion(final CompilationInfo info, int caretOffset) throws Exception {
        String prefix = "";
        TokenSequence<JavaTokenId> ts = SourceUtils.getJavaTokenSequence(info.getTokenHierarchy(), caretOffset);
        ts.move(caretOffset);
        if (!ts.movePrevious())
            return Collections.emptyList();
        if (ts.offset() + ts.token().length() < caretOffset) {
            if (!ts.moveNext())
                return Collections.emptyList();
        }
        if (ts.token().id() == JavaTokenId.IDENTIFIER) {
            prefix = info.getText().substring(ts.offset(), caretOffset);
            caretOffset = ts.offset();
        }
        List<Element> seenMethods = new ArrayList<>();
        List<CompletionItem> result = new ArrayList<>();
        TreePath path = info.getTreeUtilities().pathFor(caretOffset);

        MethodTree methodDef = null;

        while (path != null) {
            if (path.getLeaf().getKind() == Kind.METHOD) {
                methodDef = (MethodTree) path.getLeaf();
                break;
            }
            path = path.getParentPath();
        }

        if (methodDef == null || methodDef.getBody() == null)
            return Collections.emptyList();
        
        int bodyStart = (int) info.getTrees().getSourcePositions().getStartPosition(info.getCompilationUnit(), methodDef.getBody());
        
        if (bodyStart > caretOffset)
            return Collections.emptyList();
            
        String limitedBody = info.getText().substring(bodyStart, caretOffset);
        SourcePositions[] positions = new SourcePositions[1];
        StatementTree newBody = info.getTreeUtilities().parseStatement(limitedBody, positions);
        Scope scope = info.getTrees().getScope(path);
        path = info.getTreeUtilities().pathFor(new TreePath(path, newBody), caretOffset - bodyStart, positions[0]);
        info.getTreeUtilities().attributeTreeTo(newBody, scope, path.getLeaf());

        switch (path.getLeaf().getKind()) {
            case MEMBER_SELECT:
                MemberSelectTree mst = (MemberSelectTree) path.getLeaf();
                TypeMirror type = info.getTrees().getTypeMirror(new TreePath(path, mst.getExpression()));
                TypeElement clazz = (TypeElement) info.getTypes().asElement(type);//XXX

                if (clazz == null) break;

                ClassPath sourceLocation = ClassPathSupport.createProxyClassPath(info.getClasspathInfo().getClassPath(PathKind.BOOT), info.getClasspathInfo().getClassPath(PathKind.COMPILE));
                String vmName = info.getElements().getBinaryName(clazz).toString().replace('.', '/');
                String fileName = vmName + ".class";
                FileObject foundResource = sourceLocation.findResource(fileName);

                if (foundResource == null) break;

                FileObject root = sourceLocation.findOwnerRoot(foundResource);

                if (root == null) break;

                root = FileUtil.getArchiveFile(root);

                if (root == null) break;
                
                ProjectCoordinate dependencyInfo = coordinateService.suggest(new DependencyInfo(FileUtil.toFile(root), DependencyType.JAR)).get();

                Optional<ModelCoordinate> modelData;

                index.open();

                try {
                    modelData = index.suggest(dependencyInfo, "call");
                } finally {
                    index.close();
                }

                if (!modelData.isPresent())
                    break;

                Optional<File> cache = repository.getLocation(modelData.get(), true);

                if (!cache.isPresent())
                    break;
                
                File modelFile = cache.get();
                SingleZipCallModelProvider store = new SingleZipCallModelProvider(modelFile, Collections.<String, IInputStreamTransformer>emptyMap());
                ITypeName typeName = VmTypeName.get("L" + vmName);
                UniqueTypeName name = new UniqueTypeName(dependencyInfo, typeName);
                store.open(); //XXX: cache?
                ICallModel net = store.acquireModel(name).orNull();
                
                try {
                    net.setObservedCalls(observedCalls(info, new TreePath(path, newBody), clazz));

                    List<Recommendation<IMethodName>> recommendations = new ArrayList<>(top(net.recommendCalls(), 5, MIN_RELEVANCE));

                    Collections.sort(recommendations, new Comparator<Recommendation<?>>() {
                        @Override public int compare(Recommendation<?> o1, Recommendation<?> o2) {
                            return (int) Math.signum(o1.getRelevance() - o2.getRelevance());
                        }
                    });

                    int priority = 0;
                    int substitutionOffset = caretOffset;

                    for (Recommendation<IMethodName> r : recommendations) {
                        ExecutableElement method = resolveMethod(info, r.getProposal());

                        if (method == null) {
                            LOG.log(Level.INFO, "Cannot resolve {0}.", r.toString());
                            continue;
                        }

                        if (!method.getSimpleName().toString().startsWith(prefix)) continue;

                        JavaCompletionItem i = JavaCompletionItem.createExecutableItem(info, method, (ExecutableType) info.getTypes().asMemberOf((DeclaredType) type, method), substitutionOffset, null, false, false, false, false, false, -1, false, null);

                        seenMethods.add(method);

                        result.add(new MethodCompletionItem(i, r.getRelevance(), priority++));
                    }
                } finally {
                    store.releaseModel(net);
                    store.close();
                }
        }

        JavaCompletionFilter.setSeenElements(seenMethods);

        return result;
    }

    private static ExecutableElement resolveMethod(CompilationInfo info, IMethodName method) {
        TypeElement type = ElementHandle.createTypeElementHandle(ElementKind.CLASS, method.getDeclaringType().toString().substring(1).replace('/', '.')).resolve(info);

        if (type == null) return null;

        String methodName = method.getName();
        String signature = method.getDescriptor();

        for (ExecutableElement enclosedMethod : ElementFilter.methodsIn(info.getElements().getAllMembers(type))) {
            if (!enclosedMethod.getSimpleName().contentEquals(methodName)) continue;

            if (signature.equals(SourceUtils.getJVMSignature(ElementHandle.create(enclosedMethod))[2]))
                return enclosedMethod;
        }

        return null;
    }

    private static Set<IMethodName> observedCalls(final CompilationInfo info, TreePath newBody, final TypeElement clazz) {
        final Set<IMethodName> calls = new HashSet<>();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMemberSelect(MemberSelectTree node, Void p) {
                TypeMirror type = info.getTrees().getTypeMirror(new TreePath(getCurrentPath(), node.getExpression()));
                TypeElement clazz2 = (TypeElement) info.getTypes().asElement(type);//XXX
                Element current = info.getTrees().getElement(getCurrentPath());

                if (clazz == clazz2 && current != null && current.getKind() == ElementKind.METHOD) {
                    String[] sig = SourceUtils.getJVMSignature(ElementHandle.create(current));
                    calls.add(VmMethodName.get("L" + sig[0].replace('.', '/'), sig[1] + sig[2]));
                }

                return super.visitMemberSelect(node, p);
            }
        }.scan(newBody, null);

        return calls;
    }

    private static final class MethodCompletionItem implements CompletionItem {

        private final JavaCompletionItem delegate;
        private final String relevance;
        private final int priority;

        public MethodCompletionItem(JavaCompletionItem delegate, double relevance, int priority) {
            this.delegate = delegate;
            this.relevance = " - " + String.format("%2.2f", relevance * 100) + "%";
            this.priority = priority;
        }

        @Override
        public void defaultAction(JTextComponent component) {
            delegate.defaultAction(component);
        }

        @Override
        public void processKeyEvent(KeyEvent evt) {
            delegate.processKeyEvent(evt);
        }

        private <T> T invokeDelegateMethod(String name, Class<T> result) {
            try {
                Method method = JavaCompletionItem.class.getDeclaredMethod(name);

                method.setAccessible(true);
                return result.cast(method.invoke(delegate));
            } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Exceptions.printStackTrace(ex);
            }

            return null;
        }
        @Override
        public int getPreferredWidth(Graphics g, Font defaultFont) {
            String left = invokeDelegateMethod("getLeftHtmlText", String.class) + relevance;
            String right = invokeDelegateMethod("getRightHtmlText", String.class);

            return CompletionUtilities.getPreferredWidth(left, right, g, defaultFont);
        }

        @Override
        public void render(Graphics g, Font defaultFont, Color defaultColor, Color backgroundColor, int width, int height, boolean selected) {
            ImageIcon icon = invokeDelegateMethod("getIcon", ImageIcon.class);
            String left = invokeDelegateMethod("getLeftHtmlText", String.class) + relevance;
            String right = invokeDelegateMethod("getRightHtmlText", String.class);

            CompletionUtilities.renderHtml(icon, left, right, g, defaultFont, defaultColor, width, height, selected);
        }

        @Override
        public CompletionTask createDocumentationTask() {
            return delegate.createDocumentationTask();
        }

        @Override
        public CompletionTask createToolTipTask() {
            return delegate.createToolTipTask();
        }

        @Override
        public boolean instantSubstitution(JTextComponent component) {
            return false;
        }

        @Override
        public int getSortPriority() {
            return -1000 - priority;
        }

        @Override
        public CharSequence getSortText() {
            return delegate.getSortText();
        }

        @Override
        public CharSequence getInsertPrefix() {
            return delegate.getInsertPrefix();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

    }

    @MimeRegistration(mimeType="text/x-java", service=CompletionProvider.class, position=50)
    public static final class RecommenderCodeCompletionProvider implements CompletionProvider {

        private IModelIndex index;
        private IModelRepository repository;
        private ProjectCoordinateAdvisorService coordinateService;

        public RecommenderCodeCompletionProvider() {
            try {
                List<Data> delegates = new ArrayList<>();

                for (String loc : Utils.getLocations()) {
                    Data data = new Data(new URL(loc));
                    data.validate();
                    delegates.add(data);
                }

                MultiData multiData = new MultiData(delegates);
                
                index = multiData;
                repository = multiData;
                coordinateService = new AdvisorImpl();
            } catch (Exception ex) {
                Exceptions.printStackTrace(ex);
            }
        }


        @Override
        public CompletionTask createTask(int queryType, JTextComponent component) {
            return new AsyncCompletionTask(new RecommenderCodeCompletion(index, repository, coordinateService), component);
        }

        @Override
        public int getAutoQueryTypes(JTextComponent component, String typedText) {
            return 0;
        }

    }
}
