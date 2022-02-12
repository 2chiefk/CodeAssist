package com.tyron.completion.xml.providers;

import static com.tyron.completion.xml.util.XmlUtils.fullIdentifier;
import static com.tyron.completion.xml.util.XmlUtils.getAttributeItem;
import static com.tyron.completion.xml.util.XmlUtils.getAttributeNameFromPrefix;
import static com.tyron.completion.xml.util.XmlUtils.getElementNode;
import static com.tyron.completion.xml.util.XmlUtils.isEndTag;
import static com.tyron.completion.xml.util.XmlUtils.isInAttributeValue;
import static com.tyron.completion.xml.util.XmlUtils.isIncrementalCompletion;
import static com.tyron.completion.xml.util.XmlUtils.isTag;
import static com.tyron.completion.xml.util.XmlUtils.newPullParser;
import static com.tyron.completion.xml.util.XmlUtils.partialIdentifier;

import com.tyron.builder.project.Project;
import com.tyron.builder.project.api.AndroidModule;
import com.tyron.builder.project.api.Module;
import com.tyron.builder.util.PositionXmlParser;
import com.tyron.completion.CompletionParameters;
import com.tyron.completion.CompletionProvider;
import com.tyron.completion.index.CompilerService;
import com.tyron.completion.model.CompletionItem;
import com.tyron.completion.model.CompletionList;
import com.tyron.completion.model.DrawableKind;
import com.tyron.completion.xml.XmlIndexProvider;
import com.tyron.completion.xml.XmlRepository;
import com.tyron.completion.xml.model.AttributeInfo;
import com.tyron.completion.xml.model.DeclareStyleable;
import com.tyron.completion.xml.model.XmlCachedCompletion;
import com.tyron.completion.xml.util.StyleUtils;
import com.tyron.completion.xml.util.XmlUtils;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.dom.DOMNode;
import org.eclipse.lemminx.dom.DOMParser;
import org.openjdk.javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public class AndroidManifestCompletionProvider extends CompletionProvider {

    private static final Map<String, String> sManifestTagMappings = new HashMap<>();

    static {
        sManifestTagMappings.put("manifest", "AndroidManifest");
        sManifestTagMappings.put("application", "AndroidManifestApplication");
        sManifestTagMappings.put("permission", "AndroidManifestPermission");
        sManifestTagMappings.put("permission-group", "AndroidManifestPermissionGroup");
        sManifestTagMappings.put("permission-tree", "AndroidManifestPermissionTree");
        sManifestTagMappings.put("uses-permission", "AndroidManifestUsesPermission");
        sManifestTagMappings.put("required-feature", "AndroidManifestRequiredFeature");
        sManifestTagMappings.put("required-not-feature", "AndroidManifestRequiredNotFeature");
        sManifestTagMappings.put("uses-configuration", "AndroidManifestUsesConfiguration");
        sManifestTagMappings.put("uses-feature", "AndroidManifestUsesFeature");
        sManifestTagMappings.put("feature-group", "AndroidManifestFeatureGroup");
        sManifestTagMappings.put("uses-sdk", "AndroidManifestUsesSdk");
        sManifestTagMappings.put("extension-sdk", "AndroidManifestExtensionSdk");
        sManifestTagMappings.put("library", "AndroidManifestLibrary");
        sManifestTagMappings.put("static-library", "AndroidManifestStaticLibrary");
        sManifestTagMappings.put("uses-libraries", "AndroidManifestUsesLibrary");
        sManifestTagMappings.put("uses-native-library", "AndroidManifestUsesNativeLibrary");
        sManifestTagMappings.put("uses-static-library", "AndroidManifestUsesStaticLibrary");
        sManifestTagMappings.put("additional-certificate", "AndroidManifestAdditionalCertificate");
        sManifestTagMappings.put("uses-package", "AndroidManifestUsesPackage");
        sManifestTagMappings.put("supports-screens", "AndroidManifestSupportsScreens");
        sManifestTagMappings.put("processes", "AndroidManifestProcesses");
        sManifestTagMappings.put("process", "AndroidManifestProcess");
        sManifestTagMappings.put("deny-permission", "AndroidManifestDenyPermission");
        sManifestTagMappings.put("allow-permission", "AndroidManifestAllowPermission");
        sManifestTagMappings.put("provider", "AndroidManifestProvider");
        sManifestTagMappings.put("grant-uri-permission", "AndroidManifestGrantUriPermission");
        sManifestTagMappings.put("path-permission", "AndroidManifestPathPermission");
        sManifestTagMappings.put("service", "AndroidManifestService");
        sManifestTagMappings.put("receiver", "AndroidManifestReceiver");
        sManifestTagMappings.put("activity", "AndroidManifestActivity");
        sManifestTagMappings.put("activity-alias", "AndroidManifestActivityAlias");
        sManifestTagMappings.put("meta-data", "AndroidManifestMetaData");
        sManifestTagMappings.put("property", "AndroidManifestProperty");
        sManifestTagMappings.put("intent-filter", "AndroidManifestIntentFilter");
        sManifestTagMappings.put("action", "AndroidManifestAction");
        sManifestTagMappings.put("data", "AndroidManifestData");
        sManifestTagMappings.put("category", "AndroidManifestCategory");
        sManifestTagMappings.put("instrumentation", "AndroidManifestInstrumentation");
        sManifestTagMappings.put("screen", "AndroidManifestCompatibleScreensScreen");
        sManifestTagMappings.put("input-type", "AndroidManifestSupportsInputType");
        sManifestTagMappings.put("layout", "AndroidManifestLayout");
        sManifestTagMappings.put("restrict-update", "AndroidManifestRestrictUpdate");
        sManifestTagMappings.put("uses-split", "AndroidManifestUsesSplit");

    }

    private static String getTag(String tag) {
        return sManifestTagMappings.get(tag);
    }

    private XmlCachedCompletion mCachedCompletion;

    public AndroidManifestCompletionProvider() {

    }

    @Override
    public boolean accept(File file) {
        return file.isFile() && "AndroidManifest.xml".equals(file.getName());
    }

    @Override
    public CompletionList complete(CompletionParameters params) {

        if (!(params.getModule() instanceof AndroidModule)) {
            return CompletionList.EMPTY;
        }

        String partialIdentifier = partialIdentifier(params.getContents(), (int) params.getIndex());

        if (isIncrementalCompletion(mCachedCompletion, params)) {
            String prefix = params.getPrefix();
            if (mCachedCompletion.getCompletionType() == XmlCachedCompletion.TYPE_ATTRIBUTE_VALUE) {
                mCachedCompletion.setFilterPrefix(prefix);
            } else if (mCachedCompletion.getCompletionType() == XmlCachedCompletion.TYPE_TAG) {
                mCachedCompletion.setFilterPrefix(partialIdentifier);
            } else {
                mCachedCompletion.setFilterPrefix(partialIdentifier);
            }
            CompletionList completionList = mCachedCompletion.getCompletionList();
            if (!completionList.items.isEmpty()) {
                String filterPrefix = mCachedCompletion.getFilterPrefix();
                sort(completionList.items, filterPrefix);
                return completionList;
            }
        }
        try {
            XmlCachedCompletion list = completeInternal(params.getProject(),
                    (AndroidModule) params.getModule(),
                    params.getFile(),
                    params.getContents(),
                    params.getPrefix(),
                    params.getLine(),
                    params.getColumn(),
                    params.getIndex());
            mCachedCompletion = list;
            CompletionList completionList = list.getCompletionList();
            sort(completionList.items, list.getFilterPrefix());
            return completionList;
        } catch (XmlPullParserException | ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
        }

        return CompletionList.EMPTY;
    }

    public XmlCachedCompletion completeInternal(
            Project project, Module module, File file, String contents,
            String prefix, int line, int column, long index) throws XmlPullParserException, ParserConfigurationException, IOException, SAXException {
        CompletionList list = new CompletionList();
        XmlCachedCompletion xmlCachedCompletion = new XmlCachedCompletion(file, line, column,
                prefix, list);
        String fixedPrefix = partialIdentifier(contents, (int) index);
        String fullPrefix = fullIdentifier(contents, (int) index);

        XmlPullParser parser = newPullParser();
        parser.setInput(new StringReader(contents));

        XmlIndexProvider indexProvider =
                CompilerService.getInstance().getIndex(XmlIndexProvider.KEY);
        XmlRepository repository = indexProvider.get(project, module);
        repository.initialize((AndroidModule) module);
        Map<String, DeclareStyleable> manifestAttrs = repository.getManifestAttrs();

        DOMDocument parsed = DOMParser.getInstance().parse(contents, "", null);
        DOMNode node = parsed.findNodeAt((int) index);

        String parentTag = "";
        String tag = "";
        Element ownerNode = getElementNode(node);
        if (ownerNode != null) {
            parentTag = ownerNode.getParentNode() == null
                    ? ""
                    : ownerNode.getParentNode().getNodeName();
            tag = ownerNode.getTagName();
        }
        tag = getTag(tag);
        if (tag == null) {
            tag = "";
        }
        Set<DeclareStyleable> styles = StyleUtils.getStyles(manifestAttrs, tag);

        if (isTag(node, index) || isEndTag(node, index)) {
            addTagItems(prefix, list, xmlCachedCompletion);
        } if (isInAttributeValue(contents, (int) index)) {
            addAttributeValueItems(styles, repository, prefix, fixedPrefix, list,
                    xmlCachedCompletion);
        } else {
            addAttributeItems(styles, fullPrefix, fixedPrefix, repository, list,
                    xmlCachedCompletion);
        }

        return xmlCachedCompletion;
    }

    private void sort(List<CompletionItem> items, String filterPrefix) {
        items.sort(Comparator.comparingInt(it -> {
            if (it.label.equals(filterPrefix)) {
                return 100;
            }
            return FuzzySearch.ratio(it.label, filterPrefix);
        }));
        Collections.reverse(items);
    }

    private void addTagItems(String prefix, CompletionList list, XmlCachedCompletion xmlCachedCompletion) {
        xmlCachedCompletion.setCompletionType(XmlCachedCompletion.TYPE_TAG);
        xmlCachedCompletion.setFilterPrefix(prefix);


        xmlCachedCompletion.setFilter((item, pre) -> {
            String prefixSet = pre;

            if (pre.startsWith("</")) {
                prefixSet = pre.substring(2);
            } else if (pre.startsWith("<")) {
                prefixSet = pre.substring(1);
            }

            if (prefixSet.contains(".")) {
                if (FuzzySearch.partialRatio(prefixSet, item.detail) >= 80) {
                    return true;
                }
            } else {
                if (FuzzySearch.partialRatio(prefixSet, item.label) >= 80) {
                    return true;
                }
            }

            String className = item.detail + "." + item.label;
            return FuzzySearch.partialRatio(prefixSet, className) >= 30;

        });
        for (String s : sManifestTagMappings.keySet()) {
            CompletionItem item = new CompletionItem();
            String commitPrefix = "<";
            if (prefix.startsWith("</")) {
                commitPrefix = "</";
            }
            item.label = s;
            item.commitText = commitPrefix + s;
            item.cursorOffset = item.commitText.length();
            item.iconKind = DrawableKind.Package;
            item.detail = "Tag";
            list.items.add(item);
        }
    }

    private void addAttributeItems(Set<DeclareStyleable> styles, String fullPrefix,
                                   String fixedPrefix, XmlRepository repository,
                                   CompletionList list, XmlCachedCompletion xmlCachedCompletion) {
        boolean shouldShowNamespace = !fixedPrefix.contains(":");

        Set<AttributeInfo> attributeInfos = new HashSet<>();

        for (DeclareStyleable style : styles) {
            attributeInfos.addAll(style.getAttributeInfosWithParents(repository));
        }

        for (AttributeInfo attributeInfo : attributeInfos) {
            CompletionItem item = getAttributeItem(repository, attributeInfo, shouldShowNamespace
                    , fixedPrefix + fullPrefix);
            list.items.add(item);
        }

        xmlCachedCompletion.setCompletionType(XmlCachedCompletion.TYPE_ATTRIBUTE);
        xmlCachedCompletion.setFilterPrefix(fixedPrefix);
        xmlCachedCompletion.setFilter((it, pre) -> {
            if (pre.contains(":")) {
                if (it.label.contains(":")) {
                    if (!it.label.startsWith(pre)) {
                        return false;
                    }
                    it.label = it.label.substring(it.label.indexOf(':') + 1);
                }
            }
            if (it.label.startsWith(pre)) {
                return true;
            }

            String labelPrefix = getAttributeNameFromPrefix(it.label);
            String prePrefix = getAttributeNameFromPrefix(pre);
            return FuzzySearch.partialRatio(labelPrefix, prePrefix) >= 70;
        });
    }

    private void addAttributeValueItems(Set<DeclareStyleable> styles, XmlRepository repository,
                                        String prefix, String fixedPrefix, CompletionList list,
                                        XmlCachedCompletion xmlCachedCompletion) {
        Set<AttributeInfo> attributeInfos = new HashSet<>();
        for (DeclareStyleable style : styles) {
            attributeInfos.addAll(style.getAttributeInfosWithParents(repository));
        }

        String attributeName = getAttributeNameFromPrefix(fixedPrefix);
        String namespace = "";
        if (fixedPrefix.contains(":")) {
            namespace = fixedPrefix.substring(0, fixedPrefix.indexOf(':'));
            if (namespace.contains("=")) {
                namespace = namespace.substring(0, namespace.indexOf('='));
            }
        }

        for (AttributeInfo attributeInfo : attributeInfos) {
            if (!namespace.equals(attributeInfo.getNamespace())) {
                continue;
            }

            if (!attributeName.equals(attributeInfo.getName())) {
                continue;
            }
            List<String> values = attributeInfo.getValues();
            if (values == null || values.isEmpty()) {
                AttributeInfo extraAttribute =
                        repository.getExtraAttribute(attributeInfo.getName());
                if (extraAttribute != null) {
                    values = extraAttribute.getValues();
                }
            }

            if (values != null) {
                for (String value : values) {
                    CompletionItem item = new CompletionItem();
                    item.action = CompletionItem.Kind.NORMAL;
                    item.label = value;
                    item.commitText = value;
                    item.iconKind = DrawableKind.Attribute;
                    item.cursorOffset = value.length();
                    item.detail = "Attribute";
                    list.items.add(item);
                }
            }
        }
        xmlCachedCompletion.setCompletionType(XmlCachedCompletion.TYPE_ATTRIBUTE_VALUE);
        xmlCachedCompletion.setFilterPrefix(prefix);
        xmlCachedCompletion.setFilter((item, pre) -> item.label.startsWith(pre));
    }
}
