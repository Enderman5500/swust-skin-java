package cn.edu.swust.mc.skin.plugins.activity;

import cn.edu.swust.mc.skin.Web;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * DOCX 模板渲染与打包。模板是普通 .docx，正文使用 {{name}} 等占位符，
 * 参与者名单写在第一个表格中（每行两人：姓名/专业班级/学号 | 空列 | 姓名/专业班级/学号）。
 */
final class ProofService {
    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    record Paragraph(int index, String text) {
    }

    static void validateTemplate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw Web.badRequest("模板必须是 .docx 文件");
        }
        boolean document = false;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    document = true;
                    break;
                }
            }
        } catch (ZipException e) {
            throw Web.badRequest("模板不是有效的 DOCX 文件");
        }
        if (!document) throw Web.badRequest("模板中缺少 word/document.xml，请上传 Word 文档");
    }

    static List<Paragraph> paragraphs(byte[] template) throws IOException {
        Document document = parse(documentXml(template));
        List<String> texts = editableTexts(document);
        List<Paragraph> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (!texts.get(i).isBlank()) result.add(new Paragraph(i, texts.get(i)));
        }
        return result;
    }

    /** 在线编辑：按段落序号替换正文文字，保留段落与首个文本片段的格式。 */
    static byte[] updateParagraphs(byte[] template, Map<Integer, String> texts) throws IOException {
        if (texts == null || texts.isEmpty()) return template;
        Map<String, byte[]> entries = unzip(template);
        byte[] xml = entries.get("word/document.xml");
        if (xml == null) throw Web.badRequest("模板中缺少 word/document.xml");
        Document document = parse(xml);
        List<Element> targets = editableParagraphs(document);
        for (Map.Entry<Integer, String> entry : texts.entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= targets.size()) continue;
            setParagraphText(targets.get(index), entry.getValue() == null ? "" : entry.getValue());
        }
        entries.put("word/document.xml", serialize(document).getBytes(StandardCharsets.UTF_8));
        return zip(entries);
    }

    /** 渲染团体证明：替换正文占位符，并用参与者名单重建表格数据行。 */
    static byte[] renderGroup(byte[] template, Map<String, String> values, List<Map<String, String>> participants)
            throws IOException {
        Map<String, byte[]> entries = unzip(template);
        byte[] documentBytes = entries.get("word/document.xml");
        if (documentBytes == null) throw Web.badRequest("模板中缺少 word/document.xml");
        Document document = parse(documentBytes);
        replaceTextInDocument(document, values);
        if (participants != null) fillTable(document, participants);
        entries.put("word/document.xml", serialize(document).getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String name = entry.getKey();
            if ("word/document.xml".equals(name) || !name.endsWith(".xml") || !name.startsWith("word/")) continue;
            String text = new String(entry.getValue(), StandardCharsets.UTF_8);
            if (!text.contains("{{")) continue;
            entry.setValue(replaceInXml(text, values).getBytes(StandardCharsets.UTF_8));
        }
        return zip(entries);
    }

    static byte[] render(byte[] template, Map<String, String> values) throws IOException {
        return renderGroup(template, values, null);
    }

    static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey());
                zip.putNextEntry(item);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- 表格

    private static final int CELLS = 7;

    private static void fillTable(Document document, List<Map<String, String>> participants) {
        NodeList tables = document.getElementsByTagNameNS(W_NS, "tbl");
        if (tables.getLength() == 0) return;
        Element table = (Element) tables.item(0);
        List<Element> rows = directChildren(table, "tr");
        if (rows.size() < 2) return;
        Element templateRow = (Element) rows.get(1).cloneNode(true);
        for (int i = 1; i < rows.size(); i++) table.removeChild(rows.get(i));
        for (int i = 0; i < participants.size(); i += 2) {
            Element row = (Element) templateRow.cloneNode(true);
            List<Element> cells = directChildren(row, "tc");
            fillCell(cells, 0, participants.get(i).getOrDefault("name", ""));
            fillCell(cells, 1, participants.get(i).getOrDefault("className", ""));
            fillCell(cells, 2, participants.get(i).getOrDefault("studentNo", ""));
            fillCell(cells, 3, "");
            if (i + 1 < participants.size()) {
                fillCell(cells, 4, participants.get(i + 1).getOrDefault("name", ""));
                fillCell(cells, 5, participants.get(i + 1).getOrDefault("className", ""));
                fillCell(cells, 6, participants.get(i + 1).getOrDefault("studentNo", ""));
            } else {
                fillCell(cells, 4, "");
                fillCell(cells, 5, "");
                fillCell(cells, 6, "");
            }
            table.appendChild(row);
        }
    }

    private static void fillCell(List<Element> cells, int index, String text) {
        if (index >= cells.size()) return;
        List<Element> paragraphs = directChildren(cells.get(index), "p");
        if (paragraphs.isEmpty()) return;
        setParagraphText(paragraphs.get(0), text);
        for (int i = 1; i < paragraphs.size(); i++) setParagraphText(paragraphs.get(i), "");
    }

    private static void setParagraphText(Element paragraph, String text) {
        NodeList texts = paragraph.getElementsByTagNameNS(W_NS, "t");
        if (texts.getLength() == 0) {
            if (text == null || text.isEmpty()) return;
            return;
        }
        for (int i = 0; i < texts.getLength(); i++) {
            Node node = texts.item(i);
            node.setTextContent(i == 0 ? (text == null ? "" : text) : "");
            if (i == 0 && node instanceof Element element) {
                element.setAttributeNS(XML_NS, "xml:space", "preserve");
            }
        }
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) result.add(element);
        }
        return result;
    }

    // ---------------------------------------------------------------- 文本

    private static void replaceTextInDocument(Document document, Map<String, String> values) {
        NodeList paragraphs = document.getElementsByTagNameNS(W_NS, "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Element paragraph = (Element) paragraphs.item(i);
            NodeList texts = paragraph.getElementsByTagNameNS(W_NS, "t");
            if (texts.getLength() == 0) continue;
            StringBuilder raw = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) raw.append(texts.item(j).getTextContent());
            if (raw.indexOf("{{") < 0) continue;
            setParagraphText(paragraph, replace(raw.toString(), values));
        }
    }

    private static String replace(String text, Map<String, String> values) {
        String result = text;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private static List<Element> editableParagraphs(Document document) {
        List<Element> result = new ArrayList<>();
        NodeList paragraphs = document.getElementsByTagNameNS(W_NS, "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Element paragraph = (Element) paragraphs.item(i);
            if (!insideTable(paragraph)) result.add(paragraph);
        }
        return result;
    }

    private static List<String> editableTexts(Document document) {
        List<String> result = new ArrayList<>();
        for (Element paragraph : editableParagraphs(document)) {
            NodeList texts = paragraph.getElementsByTagNameNS(W_NS, "t");
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < texts.getLength(); i++) value.append(texts.item(i).getTextContent());
            result.add(value.toString());
        }
        return result;
    }

    private static boolean insideTable(Node node) {
        Node current = node.getParentNode();
        while (current != null) {
            if (current instanceof Element element && "tbl".equals(element.getLocalName())) return true;
            current = current.getParentNode();
        }
        return false;
    }

    private static String replaceInXml(String xml, Map<String, String> values) {
        try {
            Document document = parse(xml.getBytes(StandardCharsets.UTF_8));
            replaceTextInDocument(document, values);
            return serialize(document);
        } catch (Exception e) {
            throw new IllegalArgumentException("模板内容解析失败：" + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- XML / ZIP

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("模板内容解析失败：" + e.getMessage(), e);
        }
    }

    private static String serialize(Document document) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("模板内容写入失败：" + e.getMessage(), e);
        }
    }

    private static byte[] documentXml(byte[] template) throws IOException {
        byte[] xml = unzip(template).get("word/document.xml");
        if (xml == null) throw Web.badRequest("模板中缺少 word/document.xml");
        return xml;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return entries;
    }
}
