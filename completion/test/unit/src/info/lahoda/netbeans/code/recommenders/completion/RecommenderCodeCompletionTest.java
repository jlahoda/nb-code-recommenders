package info.lahoda.netbeans.code.recommenders.completion;

import com.sun.source.tree.Tree.Kind;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.eclipse.recommenders.models.ModelCoordinate;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.editor.java.JavaKit;
import org.netbeans.modules.java.hints.test.api.HintTest;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.mimelookup.MimeDataProvider;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.Hint.Options;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.TriggerTreeKind;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author lahvac
 */
public class RecommenderCodeCompletionTest extends NbTestCase {

    public RecommenderCodeCompletionTest(String testName) {
        super(testName);
    }

    public void testQuery1() throws Exception {
        HintTest.create()
                .input("package test;\n" +
                       "public class Test {\n" +
                       "    private void t(String str) {\n" +
                       "        str./*CARET*/\n" +
                       "    }\n" +
                       "}\n",
                       false)
                .run(RecommenderCodeCompletionTest.class)
                .assertWarnings("0:0-0:0:verifier:public boolean equals(Object o)",
                                "0:0-0:0:verifier:public int length()",
                                "0:0-0:0:verifier:public String substring(int i, int i1)",
                                "0:0-0:0:verifier:public boolean equalsIgnoreCase(String string)");
    }

    public void testQuery2() throws Exception {
        HintTest.create()
                .input("package test;\n" +
                       "public class Test {\n" +
                       "    private void t(String str) {\n" +
                       "        str.le/*CARET*/\n" +
                       "    }\n" +
                       "}\n",
                       false)
                .run(RecommenderCodeCompletionTest.class)
                .assertWarnings("0:0-0:0:verifier:public int length()");
    }

    public void testQuery3() throws Exception {
        HintTest.create()
                .input("package test;\n" +
                       "public class Test {\n" +
                       "    private void t(String str) {\n" +
                       "        str./*CARET*/length();\n" +
                       "    }\n" +
                       "}\n",
                       false)
                .run(RecommenderCodeCompletionTest.class)
                .assertWarnings("0:0-0:0:verifier:public boolean equals(Object o)",
                                "0:0-0:0:verifier:public int length()",
                                "0:0-0:0:verifier:public String substring(int i, int i1)",
                                "0:0-0:0:verifier:public boolean equalsIgnoreCase(String string)");
    }

    public void testApply1() throws Exception {
        HintTest.create()
                .input("package test;\n" +
                       "public class Test {\n" +
                       "    private void t(String str) {\n" +
                       "        str./*CARET*/\n" +
                       "    }\n" +
                       "}\n",
                       false)
                .run(RecommenderCodeCompletionTest.class)
                .findWarning("0:0-0:0:verifier:public int length()")
                .applyFix()
                .assertVerbatimOutput("package test;\n" +
                                      "public class Test {\n" +
                                      "    private void t(String str) {\n" +
                                      "        str.length()/*CARET*/\n" +
                                      "    }\n" +
                                      "}\n");
    }

    public void testApply2() throws Exception {
        HintTest.create()
                .input("package test;\n" +
                       "public class Test {\n" +
                       "    private void t(String str) {\n" +
                       "        str.le/*CARET*/\n" +
                       "    }\n" +
                       "}\n",
                       false)
                .run(RecommenderCodeCompletionTest.class)
                .findWarning("0:0-0:0:verifier:public int length()")
                .applyFix()
                .assertVerbatimOutput("package test;\n" +
                                      "public class Test {\n" +
                                      "    private void t(String str) {\n" +
                                      "        str.length()/*CARET*/\n" +
                                      "    }\n" +
                                      "}\n");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
        File userdir = new File(getWorkDir(), "userdir");
        userdir.mkdirs();
        System.setProperty("netbeans.user", userdir.getAbsolutePath());
    }

    static {
        RecommenderCodeCompletion.MIN_RELEVANCE = 0.001d;
    }

    @Hint(displayName="A", description="B", category="test", options = Options.NO_BATCH)
    @TriggerTreeKind(Kind.COMPILATION_UNIT)
    public static List<ErrorDescription> completion2Hints(HintContext ctx) throws Exception {
        Document doc = ctx.getInfo().getSnapshot().getSource().getDocument(true);
        final JTextComponent component = new JEditorPane();

        component.setDocument(doc);

        int caretLocation = ctx.getInfo().getText().indexOf("/*CARET*/");

        component.setCaretPosition(caretLocation);

        Data data = new Data(new URL(System.getProperty("recommenders.luna.repo", "http://download.eclipse.org/recommenders/models/luna/")));

        assertTrue(data.validate());
        assertTrue(data.resolve(new ModelCoordinate("jre", "jre", "call", "zip", "1.0.0"), true).isPresent());

        RecommenderCodeCompletion rcc = new RecommenderCodeCompletion(data, data, new AdvisorImpl());
        List<ErrorDescription> result = new ArrayList<>();
        List<CompletionItem> completions = new ArrayList<>(rcc.resolveCodeCompletion(ctx.getInfo(), caretLocation));

        Collections.sort(completions, new Comparator<CompletionItem>() {
            @Override public int compare(CompletionItem o1, CompletionItem o2) {
                return o1.getSortPriority() - o2.getSortPriority();
            }
        });

        for (final CompletionItem ci : completions) {
            result.add(ErrorDescriptionFactory.forSpan(ctx, 0, 0, ci.toString(), new Fix() {
                @Override public String getText() {
                    return "F";
                }
                @Override public ChangeInfo implement() throws Exception {
                    ci.defaultAction(component);
                    return null;
                }
            }));
        }

        return result;
    }

    @ServiceProvider(service=MimeDataProvider.class)
    public static final class MDPI implements MimeDataProvider {
        private final Lookup L = Lookups.singleton(new JavaKit());
        @Override
        public Lookup getLookup(MimePath mimePath) {
            if ("text/x-java".equals(mimePath.getPath())) return L;
            return null;
        }

    }
}
