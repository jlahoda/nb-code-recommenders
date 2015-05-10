package info.lahoda.netbeans.code.recommenders.completion;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.tools.Diagnostic.Kind;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.java.preprocessorbridge.spi.WrapperFactory;
import org.openide.modules.OnStart;

/**
 *
 * @author lahvac
 */
public class JavaCompletionFilter {

    private static boolean completionActive;
    private static List<String> seenElements;

    private static String element2String(Element el) {
        switch (el.getKind()) {
            case METHOD:
                return el.getKind().name() + ":" + el.getEnclosingElement().toString() + "." + el.toString();
            default:
                return el.getKind().name() + ":" + el.toString();
        }
    }

    public static void setSeenElements(List<Element> elements) {
        seenElements = new ArrayList<>();

        for (Element el : elements) {
            seenElements.add(element2String(el));
        }
    }

    public static final class TreesWrapperFactoryImpl implements WrapperFactory {

        private final WrapperFactory delegate;

        private TreesWrapperFactoryImpl(WrapperFactory delegate ){
            this.delegate = delegate;
        }

        @Override public Trees wrapTrees(Trees trees) {
            return new FilteringTrees((DocTrees) (delegate != null ? delegate.wrapTrees(trees) : trees));
        }
    }

    private static final class FilteringTrees extends DocTrees {
        private final DocTrees delegate;

        public FilteringTrees(DocTrees delegate) {
            this.delegate = delegate;
        }

        @Override
        public DocCommentTree getDocCommentTree(TreePath tp) {
            return delegate.getDocCommentTree(tp);
        }

        @Override
        public Element getElement(DocTreePath dtp) {
            return delegate.getElement(dtp);
        }

        @Override
        public DocSourcePositions getSourcePositions() {
            return delegate.getSourcePositions();
        }

        @Override
        public void printMessage(Kind kind, CharSequence cs, DocTree dt, DocCommentTree dct, CompilationUnitTree cut) {
            delegate.printMessage(kind, cs, dt, dct, cut);
        }

        @Override
        public Tree getTree(Element elmnt) {
            return delegate.getTree(elmnt);
        }

        @Override
        public ClassTree getTree(TypeElement te) {
            return delegate.getTree(te);
        }

        @Override
        public MethodTree getTree(ExecutableElement ee) {
            return delegate.getTree(ee);
        }

        @Override
        public Tree getTree(Element elmnt, AnnotationMirror am) {
            return delegate.getTree(elmnt, am);
        }

        @Override
        public Tree getTree(Element elmnt, AnnotationMirror am, AnnotationValue av) {
            return delegate.getTree(elmnt, am, av);
        }

        @Override
        public TreePath getPath(CompilationUnitTree cut, Tree tree) {
            return delegate.getPath(cut, tree);
        }

        @Override
        public TreePath getPath(Element elmnt) {
            return delegate.getPath(elmnt);
        }

        @Override
        public TreePath getPath(Element elmnt, AnnotationMirror am) {
            return delegate.getPath(elmnt, am);
        }

        @Override
        public TreePath getPath(Element elmnt, AnnotationMirror am, AnnotationValue av) {
            return delegate.getPath(elmnt, am, av);
        }

        @Override
        public Element getElement(TreePath tp) {
            return delegate.getElement(tp);
        }

        @Override
        public TypeMirror getTypeMirror(TreePath tp) {
            return delegate.getTypeMirror(tp);
        }

        @Override
        public Scope getScope(TreePath tp) {
            return delegate.getScope(tp);
        }

        @Override
        public String getDocComment(TreePath tp) {
            return delegate.getDocComment(tp);
        }

        @Override
        public boolean isAccessible(Scope scope, TypeElement te) {
            return delegate.isAccessible(scope, te);
        }

        @Override
        public boolean isAccessible(Scope scope, Element elmnt, DeclaredType dt) {
            if (completionActive && seenElements.contains(element2String(elmnt)))
                return false;
            return delegate.isAccessible(scope, elmnt, dt);
        }

        @Override
        public TypeMirror getOriginalType(ErrorType et) {
            return delegate.getOriginalType(et);
        }

        @Override
        public void printMessage(Kind kind, CharSequence cs, Tree tree, CompilationUnitTree cut) {
            delegate.printMessage(kind, cs, tree, cut);
        }

        @Override
        public TypeMirror getLub(CatchTree ct) {
            return delegate.getLub(ct);
        }

    }

    @OnStart
    public static final class RegisterListeners implements Runnable, PropertyChangeListener {

        @Override
        public void run() {
            EditorRegistry.addPropertyChangeListener(new PropertyChangeListener() {
                @Override public void propertyChange(PropertyChangeEvent evt) {
                    JTextComponent c = EditorRegistry.lastFocusedComponent();

                    if (c != null) {
                        Document doc = c.getDocument();
                        if (doc.getProperty(RegisterListeners.class) == null) {
                            WrapperFactory existing = (WrapperFactory) doc.getProperty(WrapperFactory.class);

                            doc.putProperty(WrapperFactory.class, new TreesWrapperFactoryImpl(existing));
                            doc.putProperty(RegisterListeners.class, Boolean.TRUE);
                        }

                        if (c.getClientProperty(RegisterListeners.class) == null) {
                            c.addPropertyChangeListener(RegisterListeners.this);
                            c.putClientProperty(RegisterListeners.class, Boolean.TRUE);
                        }
                    }
                }
            });
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() instanceof JTextComponent && "completion-active".equals(evt.getPropertyName())) {
                completionActive = ((JTextComponent) evt.getSource()).getClientProperty("completion-active") == Boolean.TRUE;

                if (!completionActive)
                    seenElements = Collections.emptyList();
            }
        }

    }
}
