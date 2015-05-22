package info.lahoda.netbeans.code.recommenders.completion;

import com.sun.source.tree.Scope;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.util.Context;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.lib.nbjavac.services.NBJavacTrees;
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

    private static final class FilteringTrees extends NBJavacTrees {

        public static void preRegister(Context context) {
            context.put(JavacTrees.class, (JavacTrees) null);
            context.put(JavacTrees.class, new Context.Factory<JavacTrees>() {
                @Override public JavacTrees make(Context cntxt) {
                    return new FilteringTrees(cntxt);
                }
            });
        }

        public FilteringTrees(Context context) {
            super(context);
        }

        @Override
        public boolean isAccessible(Scope scope, Element elmnt, DeclaredType dt) {
            if (completionActive && seenElements.contains(element2String(elmnt)))
                return false;
            return super.isAccessible(scope, elmnt, dt);
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
                        if (c.getClientProperty(RegisterListeners.class) == null) {
                            c.addPropertyChangeListener(RegisterListeners.this);
                            c.putClientProperty(RegisterListeners.class, Boolean.TRUE);
                        }
                    }

                    Logger timerLogger = Logger.getLogger("TIMER");
                    timerLogger.setLevel(Level.FINE);
                    timerLogger.addHandler(new Handler() {
                        @Override public void publish(LogRecord record) {
                            if ("JavaC".equals(record.getMessage())) {
                                Context context = (Context) record.getParameters()[0];
                                FilteringTrees.preRegister(context);
                            }
                        }
                        @Override public void flush() {}
                        @Override public void close() throws SecurityException {}
                    });
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
