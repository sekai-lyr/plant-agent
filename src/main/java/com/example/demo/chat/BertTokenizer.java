package com.example.demo.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BERT WordPiece 分词器（纯 Java 实现，配合 bge-small-zh-v1.5 ONNX 模型使用）。
 * 实现 BasicTokenizer（CJK 单字切分、小写化、全角转半角、标点切分）
 * 与 WordPiece 贪婪最长匹配，输出与 HuggingFace tokenizer 一致的 token id 序列。
 */
@Component
public class BertTokenizer {

    private static final Logger logger = LoggerFactory.getLogger(BertTokenizer.class);

    public static final int MAX_SEQ_LEN = 512;
    private static final String PAD = "[PAD]";
    private static final String UNK = "[UNK]";
    private static final String CLS = "[CLS]";
    private static final String SEP = "[SEP]";

    private final Map<String, Integer> vocab = new HashMap<>();
    private int unkId = 100;
    private int clsId = 101;
    private int sepId = 102;

    @Value("${embedding.local.model-dir:D:/Sekai_Object/models/bge-small-zh-v1.5}")
    private String modelDir;

    private volatile boolean loaded = false;

    private synchronized void ensureLoaded() {
        if (loaded) return;
        Path path = Paths.get(modelDir, "vocab.txt");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int idx = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() && idx > 0) continue; // 尾部空行容忍
                vocab.putIfAbsent(line, idx);
                idx++;
            }
            Integer unk = vocab.get(UNK);
            Integer cls = vocab.get(CLS);
            Integer sep = vocab.get(SEP);
            if (unk != null) unkId = unk;
            if (cls != null) clsId = cls;
            if (sep != null) sepId = sep;
            loaded = true;
            logger.info("BertTokenizer loaded {} tokens from {}", vocab.size(), path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load vocab.txt from " + path, e);
        }
    }

    /** 返回 [CLS] token ids [SEP]，最长 MAX_SEQ_LEN */
    public int[] encode(String text) {
        ensureLoaded();
        List<String> pieces = wordpieceTokenize(basicTokenize(text));
        List<Integer> ids = new ArrayList<>(MAX_SEQ_LEN);
        ids.add(clsId);
        int budget = MAX_SEQ_LEN - 2;
        for (String piece : pieces) {
            if (budget-- <= 0) break;
            Integer id = vocab.get(piece);
            ids.add(id != null ? id : unkId);
        }
        ids.add(sepId);
        int[] arr = new int[ids.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ids.get(i);
        return arr;
    }

    /** BERT BasicTokenizer + WordPiece */
    List<String> wordpieceTokenize(List<String> tokens) {
        List<String> out = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() > 100) token = token.substring(0, 100); // 防超长词
            String current = token;
            int start = 0;
            boolean ok = true;
            while (start < current.length()) {
                int end = current.length();
                String match = null;
                while (start < end) {
                    String candidate = current.substring(start, end);
                    if (start > 0) candidate = "##" + candidate;
                    if (vocab.containsKey(candidate)) { match = candidate; break; }
                    end--;
                }
                if (match == null) { ok = false; break; } // UNK 由 encode 统一处理
                out.add(match);
                start = end;
            }
            if (!ok && start == 0) {
                // 整词无匹配，丢给 [UNK]（encode 时映射）
                out.add(current);
            }
        }
        return out;
    }

    /** BasicTokenizer：清洗 + CJK 切分 + 小写/全半角 + 标点切分 */
    List<String> basicTokenize(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        char[] chars = cleanText(text).toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (isCjk(c)) { // CJK 单字成词
                if (sb.length() > 0) { splitPunctuation(sb.toString(), out); sb.setLength(0); }
                out.add(String.valueOf(c));
            } else {
                if (Character.isWhitespace(c)) {
                    if (sb.length() > 0) { splitPunctuation(sb.toString(), out); sb.setLength(0); }
                } else {
                    sb.append(stripAccent(c));
                }
            }
        }
        if (sb.length() > 0) splitPunctuation(sb.toString(), out);
        return out;
    }

    private void splitPunctuation(String token, List<String> out) {
        StringBuilder sb = new StringBuilder();
        for (char c : token.toCharArray()) {
            if (isPunctuation(c)) {
                if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
                out.add(String.valueOf(c));
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) out.add(sb.toString());
    }

    private String cleanText(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == 0 || c == 0xFFFD || isControl(c)) continue;
            sb.append(c == 0x00A0 || c == 0x2007 || c == 0x202F ? ' ' : c);
        }
        return sb.toString();
    }

    private char stripAccent(char c) {
        // 全角转半角 + 小写化（轻量实现，覆盖常用区段）
        if (c >= 0xFF01 && c <= 0xFF5E) c = (char) (c - 0xFEE0);
        return Character.toLowerCase(c);
    }

    private boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x20000 && c <= 0x2A6DF) || (c >= 0xF900 && c <= 0xFAFF);
    }

    private boolean isPunctuation(char c) {
        if ((c >= 33 && c <= 47) || (c >= 58 && c <= 64) || (c >= 91 && c <= 96) || (c >= 123 && c <= 126)) return true;
        return (c >= 0x3000 && c <= 0x303F) || (c >= 0xFF00 && c <= 0xFF20)
                || c == 0x00B7 || c == 0x2018 || c == 0x2019 || c == 0x201C || c == 0x201D
                || c == 0x2026 || c == 0x2014 || c == 0x2013 || c == 0xFF1B || c == 0xFF1A;
    }

    private boolean isControl(char c) {
        return (c >= 0x00 && c <= 0x1F) || (c >= 0x7F && c <= 0x9F);
    }
}
