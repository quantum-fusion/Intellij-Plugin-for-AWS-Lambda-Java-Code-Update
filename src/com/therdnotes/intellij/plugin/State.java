package com.therdnotes.intellij.plugin;

/**
 * Created by raman.dhawan on 7/18/2017.
 */
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class State
{
    Map<String, LambdaConfig> configs = new HashMap();
    String lastS3Bucket = "";
    String lastS3KeyPrefix = "";

    public String getLastS3Bucket() {
        return lastS3Bucket;
    }

    public void setLastS3Bucket(String lastS3Bucket) {
        this.lastS3Bucket = lastS3Bucket;
    }

    public String getLastS3KeyPrefix() {
        return lastS3KeyPrefix;
    }

    public void setLastS3KeyPrefix(String lastS3KeyPrefix) {
        this.lastS3KeyPrefix = lastS3KeyPrefix;
    }

    public Map<String, LambdaConfig> getConfigs() {
        return this.configs;
    }

    public void setConfigs(Map<String, LambdaConfig> configs) {
        this.configs = configs;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry entry : this.configs.entrySet()) {
            String configName = (String)entry.getKey();
            LambdaConfig lambdaConfig = (LambdaConfig)entry.getValue();
            String functionName = lambdaConfig.getFunctionName();
            String jarFilePath = lambdaConfig.getJarFilePath();
            sb.append(new StringBuilder().append(configName).append(" : ").append(functionName).append(" , ").append(jarFilePath).toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}
