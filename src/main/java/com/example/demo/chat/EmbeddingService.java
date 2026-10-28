package com.example.demo.chat;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务（本地推理版）。
 * 使用 bge-small-zh-v1.5 ONNX 模型在本机 CPU 上生成 512 维句向量（CLS 池化 + L2 归一化），
 * 不再依赖任何远端 embedding API。
 */
@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);

    private final BertTokenizer tokenizer;
    private final String modelDir;

    private volatile OrtSession session;
    private volatile boolean failed = false;

    public EmbeddingService(BertTokenizer tokenizer,
                            @Value("${embedding.local.model-dir:D:/Sekai_Object/models/bge-small-zh-v1.5}") String modelDir) {
        this.tokenizer = tokenizer;
        this.modelDir = modelDir;
    }

    private synchronized OrtSession session() throws IOException {
        if (session != null) return session;
        if (failed) throw new IOException("Local embedding model failed to load earlier");
        try {
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions so = new OrtSession.SessionOptions();
            so.setCPUArenaAllocator(true);
            so.setIntraOpNumThreads(2);
            session = env.createSession(modelDir + "/model.onnx", so);
            logger.info("Local embedding model loaded from {} (inputs: {})", modelDir, session.getInputNames());
            return session;
        } catch (Exception e) {
            failed = true;
            throw new IOException("Failed to load local embedding model: " + e.getMessage(), e);
        }
    }

    /** 生成句向量；失败返回空数组（调用方跳过），不影响主流程 */
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) return new float[0];
        try {
            OrtSession sess = session();
            int[] ids = tokenizer.encode(text);
            int len = ids.length;
            long[] inputIds = new long[len];
            long[] attention = new long[len];
            long[] tokenTypes = new long[len];
            for (int i = 0; i < len; i++) { inputIds[i] = ids[i]; attention[i] = 1; }

            OrtEnvironment env = OrtEnvironment.getEnvironment();
            Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
            inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), new long[]{1, len}));
            inputs.put("attention_mask", OnnxTensor.createTensor(env, LongBuffer.wrap(attention), new long[]{1, len}));
            if (sess.getInputNames().contains("token_type_ids")) {
                inputs.put("token_type_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypes), new long[]{1, len}));
            }

            try (OrtSession.Result result = sess.run(inputs)) {
                float[] vector;
                var sentenceEmb = result.get("sentence_embedding");
                if (sentenceEmb.isPresent()) {
                    float[][][] se = (float[][][]) sentenceEmb.get().getValue();
                    vector = se[0][0].clone();
                } else {
                    float[][][] hidden = (float[][][]) result.get(0).getValue();
                    vector = hidden[0][0].clone(); // CLS token
                }
                l2Normalize(vector);
                return vector;
            }
        } catch (Exception e) {
            logger.warn("Local embedding failed, returning empty vector: {}", e.getMessage());
            return new float[0];
        }
    }

    public List<float[]> embedBatch(List<String> texts) throws IOException {
        List<float[]> out = new ArrayList<>();
        if (texts == null) return out;
        for (String t : texts) out.add(embed(t));
        return out;
    }

    private void l2Normalize(float[] v) {
        double norm = 0;
        for (float x : v) norm += x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= norm;
    }
}
