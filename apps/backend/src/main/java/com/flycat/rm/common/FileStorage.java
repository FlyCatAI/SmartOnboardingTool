package com.flycat.rm.common;

/**
 * 行内文件存储 SPI。任务 1.5 输出接口契约；OQ 3.4 决定保留期限与自动清理策略。
 * 跟进记录 9 张 + 完成回报 6 张图片附件走此通道，端上压缩至 ≤ 1MB 后上传。
 */
public interface FileStorage {

    /** 请求一次预签名上传（直传 CDN / 对象存储）。 */
    PresignedUpload presignUpload(String objectKey, String contentType);

    /** 请求一次预签名下载。 */
    String presignDownload(String objectKey, int ttlSeconds);

    record PresignedUpload(String objectKey, String uploadUrl, java.util.Map<String, String> headers) {}
}
