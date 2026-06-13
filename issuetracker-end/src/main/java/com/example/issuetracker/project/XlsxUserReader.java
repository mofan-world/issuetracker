package com.example.issuetracker.project;

import com.example.issuetracker.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class XlsxUserReader {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_XML_ENTRY_SIZE = 30 * 1024 * 1024;
    private static final int MAX_ROWS = 50_000;

    public List<UserIdentifier> read(MultipartFile file) {
        validate(file);
        Map<String, byte[]> entries = unzip(file);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        byte[] sheet = entries.entrySet().stream()
                .filter(entry -> entry.getKey().matches("xl/worksheets/sheet\\d+\\.xml"))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> BusinessException.badRequest("INVALID_EXCEL", "Excel 中未找到工作表"));
        try {
            return parseSheet(sheet, sharedStrings);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw BusinessException.badRequest("INVALID_EXCEL", "Excel 文件内容格式不正确");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_EXCEL", "请选择 Excel 文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw BusinessException.badRequest("INVALID_EXCEL_TYPE", "仅支持 .xlsx 格式的 Excel 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("EXCEL_TOO_LARGE", "Excel 文件不能超过 10MB");
        }
    }

    private Map<String, byte[]> unzip(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream input = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()
                        && (entry.getName().equals("xl/sharedStrings.xml")
                        || entry.getName().matches("xl/worksheets/sheet\\d+\\.xml"))) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (output.size() + read > MAX_XML_ENTRY_SIZE) {
                            throw BusinessException.badRequest("EXCEL_TOO_LARGE", "Excel 解压后的内容过大");
                        }
                        output.write(buffer, 0, read);
                    }
                    entries.put(entry.getName(), output.toByteArray());
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw BusinessException.badRequest("INVALID_EXCEL", "Excel 文件读取失败");
        }
        return entries;
    }

    private List<String> parseSharedStrings(byte[] xml) {
        if (xml == null) return List.of();
        Document document = parseXml(xml);
        NodeList items = document.getElementsByTagNameNS("*", "si");
        List<String> values = new ArrayList<>(items.getLength());
        for (int i = 0; i < items.getLength(); i++) {
            NodeList texts = ((Element) items.item(i)).getElementsByTagNameNS("*", "t");
            StringBuilder value = new StringBuilder();
            for (int j = 0; j < texts.getLength(); j++) {
                value.append(texts.item(j).getTextContent());
            }
            values.add(value.toString().trim());
        }
        return values;
    }

    private List<UserIdentifier> parseSheet(byte[] xml, List<String> sharedStrings) {
        Document document = parseXml(xml);
        NodeList rows = document.getElementsByTagNameNS("*", "row");
        if (rows.getLength() < 2) {
            throw BusinessException.badRequest("EMPTY_EXCEL", "Excel 中没有可导入的用户数据");
        }
        Map<Integer, String> headers = cells((Element) rows.item(0), sharedStrings);
        int usernameColumn = findColumn(headers, "username", "用户名", "账号");
        int emailColumn = findColumn(headers, "email", "邮箱", "电子邮箱");
        if (usernameColumn < 0 && emailColumn < 0) {
            throw BusinessException.badRequest("INVALID_EXCEL_HEADER", "Excel 表头必须包含 username/用户名 或 email/邮箱");
        }

        List<UserIdentifier> result = new ArrayList<>();
        for (int i = 1; i < rows.getLength() && result.size() < MAX_ROWS; i++) {
            Map<Integer, String> values = cells((Element) rows.item(i), sharedStrings);
            String username = normalize(values.get(usernameColumn));
            String email = normalize(values.get(emailColumn));
            if (!username.isBlank() || !email.isBlank()) {
                result.add(new UserIdentifier(username, email));
            }
        }
        if (result.isEmpty()) {
            throw BusinessException.badRequest("EMPTY_EXCEL", "Excel 中没有可导入的用户数据");
        }
        return result;
    }

    private Map<Integer, String> cells(Element row, List<String> sharedStrings) {
        Map<Integer, String> values = new LinkedHashMap<>();
        NodeList cells = row.getElementsByTagNameNS("*", "c");
        for (int i = 0; i < cells.getLength(); i++) {
            Element cell = (Element) cells.item(i);
            int column = columnIndex(cell.getAttribute("r"));
            String type = cell.getAttribute("t");
            String value = "";
            if ("inlineStr".equals(type)) {
                NodeList texts = cell.getElementsByTagNameNS("*", "t");
                if (texts.getLength() > 0) value = texts.item(0).getTextContent();
            } else {
                NodeList rawValues = cell.getElementsByTagNameNS("*", "v");
                if (rawValues.getLength() > 0) {
                    value = rawValues.item(0).getTextContent();
                    if ("s".equals(type)) {
                        int index = Integer.parseInt(value);
                        value = index >= 0 && index < sharedStrings.size() ? sharedStrings.get(index) : "";
                    }
                }
            }
            values.put(column, value.trim());
        }
        return values;
    }

    private int findColumn(Map<Integer, String> headers, String... candidates) {
        for (var entry : headers.entrySet()) {
            String header = entry.getValue().trim().toLowerCase(Locale.ROOT).replace(" ", "");
            for (String candidate : candidates) {
                if (header.equals(candidate.toLowerCase(Locale.ROOT))) return entry.getKey();
            }
        }
        return -1;
    }

    private int columnIndex(String reference) {
        int result = 0;
        for (int i = 0; i < reference.length() && Character.isLetter(reference.charAt(i)); i++) {
            result = result * 26 + Character.toUpperCase(reference.charAt(i)) - 'A' + 1;
        }
        return result - 1;
    }

    private Document parseXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception ex) {
            throw BusinessException.badRequest("INVALID_EXCEL", "Excel 文件内容格式不正确");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record UserIdentifier(String username, String email) {
    }
}
