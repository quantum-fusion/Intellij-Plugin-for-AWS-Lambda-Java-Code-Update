# Intellij-Plugin-for-AWS-Lambda-Java-Code-Update
[JetBrains Plugins repo URL](https://plugins.jetbrains.com/plugin/9849-aws-lambda-java-code-updater)  

This plugin is used to update java code in AWS Lambda function i.e. uploads jar file to AWS lambda function.

**Important:** Make sure you have AWS CLI already setup on your system. The plugin uses default profile from AWS CLI to interact with your AWS account. Make sure your AWS user has sufficient IAM policy/roles to update lambda code.

AWSLambdaJavaCodeUpdater.zip is plugin file.  
You can install this from Intellij-> Settings-> Plugins-> Install plugin from disk  


  **Demo**  


![Alt Text](https://github.com/raevilman/Intellij-Plugin-for-AWS-Lambda-Java-Code-Update/raw/master/Demo.gif)  



**Tasks List**  

- [x] Edit Mode
- [x] Update All option
- [x] AWS CLI Profiles support
- [x] S3 support for artifact's size > 10MB
