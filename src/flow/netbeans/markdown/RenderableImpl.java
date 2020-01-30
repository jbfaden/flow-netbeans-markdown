package flow.netbeans.markdown;

import flow.netbeans.markdown.api.RenderOption;
import flow.netbeans.markdown.api.Renderable;
import flow.netbeans.markdown.options.MarkdownGlobalOptions;
import java.io.IOException;
import java.util.Set;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.Exceptions;
import org.pegdown.LinkRenderer;
import org.pegdown.ParsingTimeoutException;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class RenderableImpl implements Renderable {
    private static final String TITLE = "{%TITLE%}";

    private static final String CONTENT = "{%CONTENT%}";

    private static final String SWING_TEMPLATE = "<html><body>" + CONTENT;

    private final DataObject context;

    public RenderableImpl(DataObject context) {
        this.context = context;
    }

    @Override
    public String renderAsHtml(Set<RenderOption> renderOptions) throws IOException {
        MarkdownGlobalOptions markdownOptions = MarkdownGlobalOptions.getInstance();
        String sourceText = getSourceText(renderOptions);
        String bodyText = renderBodyText(renderOptions, markdownOptions, sourceText);
        return renderHtmlText(renderOptions, markdownOptions, bodyText);
    }

    private String renderBodyText(Set<RenderOption> renderOptions, MarkdownGlobalOptions markdownOptions,
            String sourceText) throws IOException {
        String bodyText;
        boolean usePegDown= false;
        if ( usePegDown ) {
            try {
                PegDownProcessor markdownProcessor = new PegDownProcessor(markdownOptions.getExtensionsValue());
                final boolean resolveImageUrls = renderOptions.contains(RenderOption.RESOLVE_IMAGE_URLS);
                final boolean resolveLinkUrls = renderOptions.contains(RenderOption.RESOLVE_LINK_URLS);
                if (resolveImageUrls || resolveLinkUrls) {
                    RootNode rootNode = markdownProcessor.parseMarkdown(sourceText.toCharArray());
                    FileObject sourceFile = context.getPrimaryFile();
                    final PreviewSerializer htmlSerializer
                            = new PreviewSerializer(sourceFile.toURL(), resolveImageUrls, resolveLinkUrls);
                    bodyText = htmlSerializer.toHtml(rootNode);
                }
                else {
                    RootNode rootNode = markdownProcessor.parseMarkdown(sourceText.toCharArray());
                    final ExportSerializer htmlSerializer
                            = new ExportSerializer(new LinkRenderer());
                    bodyText = htmlSerializer.toHtml(rootNode);
                }
            }
            catch (ParsingTimeoutException ex) {
                throw new IOException(ex);
            }
        } else {
            MutableDataSet options = new MutableDataSet();

        // uncomment to set optional extensions
        //options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()));

        // uncomment to convert soft-breaks to hard breaks
        //options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        // You can re-use parser and renderer instances
        Node document = parser.parse("This is *Sparta*");
        bodyText = renderer.render(document);  // "<p>This is <em>Sparta</em></p>\n"
        
        }
        return bodyText;
    }

    private String getSourceText(Set<RenderOption> renderOptions) throws IOException {
        String sourceText = null;
        if (renderOptions.contains(RenderOption.PREFER_EDITOR)) {
            EditorCookie ec = context.getLookup().lookup(EditorCookie.class);
            final StyledDocument sourceDoc = ec.getDocument();
            if (sourceDoc != null) {
                sourceText = getDocumentText(sourceDoc);
            }
        }
        if (sourceText == null) {
            FileObject sourceFile = context.getPrimaryFile();
            sourceText = sourceFile.asText();
        }
        return sourceText;
    }

    private String getDocumentText(final Document sourceDoc) {
        final String[] sourceTextRef = {null};
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    sourceTextRef[0] = sourceDoc.getText(0, sourceDoc.getLength());
                }
                catch (BadLocationException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        };
        sourceDoc.render(r);
        return sourceTextRef[0];
    }

    private String getHtmlTemplate(Set<RenderOption> renderOptions, MarkdownGlobalOptions markdownOptions) {
        String htmlTemplate;
        if (renderOptions.contains(RenderOption.SWING_COMPATIBLE)) {
            htmlTemplate = SWING_TEMPLATE;
        }
        else {
            htmlTemplate = markdownOptions.getHtmlTemplate();
        }
        return htmlTemplate;
    }

    private String renderHtmlText(Set<RenderOption> renderOptions, MarkdownGlobalOptions markdownOptions,
            String bodyText) {
        String htmlTemplate = getHtmlTemplate(renderOptions, markdownOptions);
        FileObject sourceFile = context.getPrimaryFile();
        String htmlText = htmlTemplate
                .replace(TITLE, sourceFile.getName())
                .replace(CONTENT, bodyText);
        return htmlText;
    }
}
