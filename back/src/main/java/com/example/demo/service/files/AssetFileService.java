package com.example.demo.service.files;

import com.example.demo.service.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class AssetFileService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final int MAX_PREVIEW_LENGTH = 200_000;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "doc", "docx", "png", "jpg", "jpeg", "webp", "ppt", "pptx",
        "xls", "xlsx", "csv", "txt", "zip", "rtf", "odt"
    );
    private static final Set<String> ZIP_DOCUMENT_EXTENSIONS = Set.of("docx", "pptx", "xlsx", "odt");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("txt", "csv", "rtf");
    private static final Set<String> LEGACY_OFFICE_EXTENSIONS = Set.of("doc", "ppt", "xls");

    private final Path uploads;

    public AssetFileService(@Value("${sethub.data-dir:./data}") String dataDir) {
        this.uploads = Path.of(dataDir).toAbsolutePath().normalize().resolve("uploads");
    }

    public StoredAsset store(MultipartFile file, String type) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.bad("文件不能为空且不得超过 20 MB");
        }
        String original = safeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw ApiException.bad("支持 PDF、Office 文档、图片、CSV、TXT 和 ZIP 文件");
        }

        String storageName = UUID.randomUUID() + "." + extension;
        Files.createDirectories(uploads);
        Path target = resolve(storageName);
        byte[] bytes = file.getBytes();
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        return new StoredAsset(
            original,
            Objects.requireNonNullElse(type, "简历"),
            storageName,
            file.getSize(),
            Objects.requireNonNullElse(file.getContentType(), "application/octet-stream"),
            sha256(bytes)
        );
    }

    public Path requireFile(String storageName) {
        Path target = resolve(storageName);
        if (!Files.isRegularFile(target)) {
            throw new ApiException(404, "FILE_NOT_FOUND", "附件文件不存在，请联系管理员检查存储目录");
        }
        return target;
    }

    public void delete(String storageName) throws IOException {
        if (storageName == null || storageName.isBlank()) return;
        Files.deleteIfExists(resolve(storageName));
    }

    public void deleteAll(Collection<?> storageNames) throws IOException {
        if (storageNames == null) return;
        for (Object name : storageNames) delete(Objects.toString(name, ""));
    }

    public Map<String, Object> preview(Map<String, Object> asset) throws IOException {
        Path target = requireFile(Objects.toString(asset.get("storageName"), ""));
        String name = Objects.toString(asset.get("name"), "");
        String extension = extensionOf(name);
        String text;
        if (ZIP_DOCUMENT_EXTENSIONS.contains(extension)) text = zipDocumentText(target, extension);
        else if (extension.equals("zip")) text = zipEntryList(target);
        else if (TEXT_EXTENSIONS.contains(extension)) text = Files.readString(target, StandardCharsets.UTF_8);
        else if (LEGACY_OFFICE_EXTENSIONS.contains(extension)) text = legacyDocumentText(target);
        else text = "此附件将在预览窗口中直接显示。";

        if (text.length() > MAX_PREVIEW_LENGTH) {
            text = text.substring(0, MAX_PREVIEW_LENGTH) + "\n\n……内容较长，预览仅显示前 20 万字。";
        }
        return Map.of(
            "name", name,
            "extension", extension,
            "text", text.isBlank() ? "文档中没有提取到可显示的文字。" : text
        );
    }

    private String safeOriginalName(String originalFilename) {
        String original = Objects.requireNonNullElse(originalFilename, "附件").replace('\\', '/');
        original = original.substring(original.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        if (original.length() > 180) throw ApiException.bad("文件名过长");
        return original;
    }

    private String extensionOf(String fileName) {
        return fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    }

    private Path resolve(String storageName) {
        Path target = uploads.resolve(Objects.requireNonNullElse(storageName, "")).normalize();
        if (!target.startsWith(uploads)) throw ApiException.bad("附件存储路径不正确");
        return target;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    private String zipDocumentText(Path target, String extension) throws IOException {
        StringBuilder out = new StringBuilder();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(target), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                boolean readable = extension.equals("docx") && name.equals("word/document.xml")
                    || extension.equals("pptx") && name.matches("ppt/slides/slide\\d+\\.xml")
                    || extension.equals("xlsx") && (name.equals("xl/sharedStrings.xml") || name.matches("xl/worksheets/sheet\\d+\\.xml"))
                    || extension.equals("odt") && name.equals("content.xml");
                if (readable) {
                    if (!out.isEmpty()) out.append("\n\n");
                    out.append(stripXml(new String(zip.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        return out.toString().strip();
    }

    private String zipEntryList(Path target) throws IOException {
        StringBuilder out = new StringBuilder("压缩包内容：\n");
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(target), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                out.append(entry.isDirectory() ? "📁 " : "• ")
                    .append(entry.getName())
                    .append(entry.isDirectory() ? "" : "  （" + entry.getSize() + " 字节）")
                    .append('\n');
            }
        }
        return out.toString();
    }

    private String legacyDocumentText(Path target) throws IOException {
        byte[] bytes = Files.readAllBytes(target);
        String unicode = new String(bytes, StandardCharsets.UTF_16LE);
        String ascii = new String(bytes, StandardCharsets.ISO_8859_1);
        StringBuilder out = new StringBuilder("旧版 Office 文档文字预览：\n\n");
        for (String part : (unicode + "\n" + ascii).split("[^\\p{L}\\p{N}，。；：！？、（）()\\-—_./@\\s]+")) {
            String clean = part.replaceAll("\\s+", " ").strip();
            if (clean.length() >= 4) out.append(clean).append('\n');
            if (out.length() > MAX_PREVIEW_LENGTH) break;
        }
        return out.toString();
    }

    private String stripXml(String xml) {
        return xml.replaceAll("<[^>]+>", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replaceAll("\\s+", " ")
            .strip();
    }

    public record StoredAsset(
        String name,
        String type,
        String storageName,
        long size,
        String mimeType,
        String sha256
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> asset = new LinkedHashMap<>();
            asset.put("name", name);
            asset.put("type", type);
            asset.put("storageName", storageName);
            asset.put("size", size);
            asset.put("mimeType", mimeType);
            asset.put("sha256", sha256);
            asset.put("scanStatus", "未扫描");
            return asset;
        }
    }
}

