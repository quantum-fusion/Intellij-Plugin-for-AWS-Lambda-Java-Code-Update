package com.therdnotes.intellij.plugin;

/**
 * Created by raman.dhawan on 7/18/2017.
 */
public class LambdaConfig
{
    String functionName;
    String jarFilePath;

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
