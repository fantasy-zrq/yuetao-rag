package com.rag.cn.yuetaoragbackend.service.file;

/**
 * @author zrq
 * 2026/04/26 10:10
 */
public interface FileService {

    boolean bucketExists(String bucketName);

    void createBucketIfAbsent(String bucketName);

    void deleteBucket(String bucketName);

    UploadObjectResult uploadObject(String bucketName, String objectKey, byte[] content, String contentType);

    void deleteObject(String bucketName, String objectKey);

    byte[] getObject(String bucketName, String objectKey);
}
