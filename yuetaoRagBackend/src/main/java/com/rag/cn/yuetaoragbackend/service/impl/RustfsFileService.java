package com.rag.cn.yuetaoragbackend.service.impl;

import com.rag.cn.yuetaoragbackend.framework.errorcode.BaseErrorCode;
import com.rag.cn.yuetaoragbackend.framework.exception.RemoteException;
import com.rag.cn.yuetaoragbackend.service.file.UploadObjectResult;
import com.rag.cn.yuetaoragbackend.service.file.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.rag.cn.yuetaoragbackend.config.properties.RustfsProperties;

/**
 * @author zrq
 * 2026/04/26 10:30
 */
@Service
@RequiredArgsConstructor
public class RustfsFileService implements FileService {

    private final S3Client rustfsS3Client;
    private final RustfsProperties rustfsProperties;

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            rustfsS3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            return true;
        } catch (NoSuchBucketException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw new RemoteException("查询 RustFS bucket 失败：" + bucketName, ex, BaseErrorCode.REMOTE_ERROR);
        } catch (Exception ex) {
            throw new RemoteException("查询 RustFS bucket 失败：" + bucketName, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public void createBucketIfAbsent(String bucketName) {
        if (bucketExists(bucketName)) {
            return;
        }
        try {
            rustfsS3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception ex) {
            throw new RemoteException("创建 RustFS bucket 失败：" + bucketName, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public void deleteBucket(String bucketName) {
        try {
            rustfsS3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception ex) {
            throw new RemoteException("删除 RustFS bucket 失败：" + bucketName, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public UploadObjectResult uploadObject(String bucketName, String objectKey, byte[] content, String contentType) {
        try {
            var response = rustfsS3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(content));
            return new UploadObjectResult(response.eTag(), buildObjectUrl(bucketName, objectKey));
        } catch (Exception ex) {
            throw new RemoteException("上传 RustFS 对象失败：" + bucketName + "/" + objectKey, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectKey) {
        try {
            rustfsS3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new RemoteException("删除 RustFS 对象失败：" + bucketName + "/" + objectKey, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    @Override
    public byte[] getObject(String bucketName, String objectKey) {
        try {
            return rustfsS3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build())
                    .asByteArray();
        } catch (Exception ex) {
            throw new RemoteException("获取 RustFS 对象失败：" + bucketName + "/" + objectKey, ex, BaseErrorCode.REMOTE_ERROR);
        }
    }

    private String buildObjectUrl(String bucketName, String objectKey) {
        return rustfsProperties.getUrl() + "/" + bucketName + "/" + objectKey;
    }
}
