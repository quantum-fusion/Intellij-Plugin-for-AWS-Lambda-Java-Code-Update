package com.therdnotes.intellij.plugin;

/**
 * Created by raman.dhawan on 7/18/2017.
 */
public class LambdaConfig
{
    String functionName="";
    String jarFilePath="";
    String s3BucketName="";
    String s3ObjectKey="";

    public String getS3BucketName() {
        return s3BucketName;
    }

    public void setS3BucketName(String s3BucketName) {
        this.s3BucketName = s3BucketName;
    }

    public String getS3ObjectKey() {
        return s3ObjectKey;
    }

    public void setS3ObjectKey(String s3ObjectKey) {
        this.s3ObjectKey = s3ObjectKey;
    }

    public String getFunctionName()
    {
        return this.functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getJarFilePath() {
        return this.jarFilePath;
    }

    public void setJarFilePath(String jarFilePath) {
        this.jarFilePath = jarFilePath;
    }
}
