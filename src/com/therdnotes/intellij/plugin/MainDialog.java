package com.therdnotes.intellij.plugin;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.PredefinedClientConfigurations;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.auth.profile.internal.BasicProfile;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.PanelWithText;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;

public class MainDialog extends AnAction
{
    public static final String ALL_CONFIG = "ALL";
    ComboBox functionsComboBox;
    ComboBox profilesComboBox;
    State state;
    DialogBuilder mainBuilder;
    JButton jButtonDel;
    JButton jButtonEdit;

    @Override
    public void actionPerformed(AnActionEvent event)
    {
        System.out.println("Starting plugin");
        Project project = (Project)event.getData(PlatformDataKeys.PROJECT);
        if(null==project){
            String message = "No opened project found. Please open the project to use this tool.";
            raiseError(message);
        } else{
            StateService stateService = (StateService)ServiceManager.getService(project, StateService.class);
            this.state = stateService.getState();
//        System.out.println(this.state.toString());
//        System.out.println("State:" + this.state.toString());
            showMainDialog(project);
        }
    }

    private void showMainDialog(final Project project)
    {
        this.mainBuilder = new DialogBuilder(project);
        this.mainBuilder.setTitle("AWS Lambda JAR Updater");
        this.mainBuilder.addOkAction().setText("Update");
        this.mainBuilder.addCancelAction().setText("Cancel");

        PanelWithText row1Panel = new PanelWithText("Function:");
        this.functionsComboBox = new ComboBox();
        row1Panel.add(this.functionsComboBox);

        //Start:Add button
        JButton jButtonAdd = new JButton("Add");
        jButtonAdd.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                System.out.println("Add pressed");
                MainDialog.this.addConfig(project, false, null, null);
            }
        });
        //End: Add button

        //Start: Edit button
        jButtonEdit = new JButton("Edit");
        jButtonEdit.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                System.out.println("Edit pressed");
                String key = MainDialog.this.functionsComboBox.getSelectedItem().toString();
                System.out.println("Selected item:" + key);
                LambdaConfig lambdaConfig = (LambdaConfig)state.getConfigs().get(key);
                MainDialog.this.addConfig(project, true, key, lambdaConfig);
            }
        });
        //End: Edit button

        //Start: Del button
        this.jButtonDel = new JButton("Del");
        this.jButtonDel.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                System.out.println("Del pressed");
                if (MainDialog.this.functionsComboBox.getItemCount() > 0) {
                    String key = MainDialog.this.functionsComboBox.getSelectedItem().toString();
                    System.out.println("Selected item:" + key);
                    MainDialog.this.state.getConfigs().remove(key);
                    MainDialog.this.refreshComboBox();
                }
            }
        });
        //End: Del button

        //Start: profiles combobox
        PanelWithText row2Panel = new PanelWithText("AWS Profile:");
        this.profilesComboBox = new ComboBox();
        row2Panel.add(this.profilesComboBox);
        this.profilesComboBox.setModel(new DefaultComboBoxModel(getBasicProfiles()));
        //End: profiles comboBox

        row1Panel.add(jButtonAdd);
        row1Panel.add(jButtonEdit);
        row1Panel.add(this.jButtonDel);

        PanelWithText mainPanel = new PanelWithText("");
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(row1Panel);
        mainPanel.add(row2Panel);

        this.mainBuilder.setCenterPanel(mainPanel);
//        this.mainBuilder.setCenterPanel(row1Panel);

        refreshComboBox();

        switch (this.mainBuilder.show())
        {
            case 0:
                System.out.println("Ok pressed");
                if (this.functionsComboBox.getItemCount() > 0) {
                    String key = this.functionsComboBox.getSelectedItem().toString();
                    System.out.println("Selected item:" + key);
                    if(key.equalsIgnoreCase(ALL_CONFIG)){
                        Map<String, LambdaConfig> configs = state.getConfigs();
                        for (LambdaConfig lambdaConfig : configs.values()) {
                            startUpdateProcess(lambdaConfig, project);
                        }
                    } else{
                        LambdaConfig lambdaConfig = (LambdaConfig)this.state.getConfigs().get(key);
                        startUpdateProcess(lambdaConfig, project);
                    }
                }
                break;
            case 1:
            default:
                System.out.println("Cancel pressed");
        }
    }

    private Object[] getBasicProfiles() {
        ProfilesConfigFile profilesConfigFile = new ProfilesConfigFile();
        Map<String, BasicProfile> allBasicProfiles = profilesConfigFile.getAllBasicProfiles();
        return allBasicProfiles.keySet().toArray();
    }

    private void startUpdateProcess(final LambdaConfig lambdaConfig, Project project) {
        String s3BucketName = lambdaConfig.getS3BucketName();
        String s3ObjectKey = lambdaConfig.getS3ObjectKey();

        //Check if jar file exists
        File jarFile = new File(lambdaConfig.getJarFilePath());
        if(!jarFile.exists()){
            raiseError("Provided JAR file path doesn't exists.");
        } else if(!jarFile.isFile()){
            raiseError("Provided JAR file path is not a path to file. Seems like its a directory");
        }
        else{
            if (!s3BucketName.isEmpty() && !s3ObjectKey.isEmpty()) {
                System.out.println("Going S3 way");
                updateViaS3(lambdaConfig,jarFile,project);
            } else {
                System.out.println("Going JAR way");
                updateDirect(lambdaConfig, jarFile, project);
            }
        }
    }

    private void updateDirect(LambdaConfig lambdaConfig, File jarFile, Project project) {
//        RandomAccessFile randomAccessFile = null;
//        FileChannel fileChannel = null;
        try {
            UpdateFunctionCodeRequest updateFunctionCodeRequest = new UpdateFunctionCodeRequest();
            updateFunctionCodeRequest.setFunctionName(lambdaConfig.getFunctionName());
            ByteBuffer byteBuffer = ByteBuffer.wrap(java.nio.file.Files.readAllBytes(jarFile.toPath()));
            System.out.println("byte buffer "+byteBuffer);
            updateFunctionCodeRequest.setZipFile(byteBuffer);
            updateLambdaCode(lambdaConfig, project, updateFunctionCodeRequest);

//                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(jarFile);
//                System.out.println("VF is "+virtualFile);
//                System.out.println("Path"+virtualFile.getCanonicalPath());
//                System.out.println("VF isValid "+virtualFile.isValid());
//                System.out.println("VF exists "+virtualFile.exists());
//                System.out.println("VF isLocal "+virtualFile.isInLocalFileSystem());
//                ByteBuffer byteBuffer = ByteBuffer.wrap(virtualFile.contentsToByteArray());
//                randomAccessFile = new RandomAccessFile(jarFile, "r");
//                fileChannel = randomAccessFile.getChannel();
//                MappedByteBuffer byteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileChannel.size());
        }catch (Exception e){
            e.printStackTrace();
            String message = e.getMessage();
            if(message==null||message.isEmpty()){
                message = e.getClass().getName();
            }
            message = lambdaConfig.getFunctionName()+" " +message;
            raiseError(message);
        }
//        finally {
//            System.out.println("finally release the file");
//            try{
//                if(null!=fileChannel){
//                    fileChannel.close();
//                }
//                if(null!=randomAccessFile){
//                    randomAccessFile.close();
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
    }


    private void updateViaS3(LambdaConfig lambdaConfig, File jarFile, Project project) {
        String s3BucketName = lambdaConfig.getS3BucketName();
        String s3ObjectKey = lambdaConfig.getS3ObjectKey();
        String processTitle = "Uploading artifact of "+lambdaConfig.getFunctionName() + " to S3";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, processTitle, false)
        {

            public void run(@NotNull ProgressIndicator progressIndicator)
            {
                AmazonS3 s3client = AmazonS3ClientBuilder
                        .standard()
                        .withClientConfiguration(new ClientConfiguration()
                                .withSocketBufferSizeHints(2048000,2048000))
                        .withCredentials(getSelectedProfileCreds())
                        .build();

                try {
                    System.out.println("Uploading a new object to S3 from a file");
                    PutObjectResult putObjectResult = s3client.putObject(new PutObjectRequest(s3BucketName,
                            s3ObjectKey, jarFile));
                    UpdateFunctionCodeRequest updateFunctionCodeRequest = new UpdateFunctionCodeRequest();
                    updateFunctionCodeRequest.setFunctionName(lambdaConfig.getFunctionName());
                    updateFunctionCodeRequest.setS3Bucket(s3BucketName);
                    updateFunctionCodeRequest.setS3Key(s3ObjectKey);
                    updateLambdaCode(lambdaConfig, project, updateFunctionCodeRequest);
                } catch (AmazonServiceException ase) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Caught an AmazonServiceException, which " +
                            "means your request made it " +
                            "to Amazon S3, but was rejected with an error response" +
                            " for some reason.");
                    sb.append("\nError Message:    " + ase.getMessage());
                    sb.append("\nHTTP Status Code: " + ase.getStatusCode());
                    sb.append("\nAWS Error Code:   " + ase.getErrorCode());
                    sb.append("\nError Type:       " + ase.getErrorType());
                    sb.append("\nRequest ID:       " + ase.getRequestId());
                    raiseError(sb.toString());
                } catch (AmazonClientException ace) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Caught an AmazonClientException, which " +
                            "means the client encountered " +
                            "an internal error while trying to " +
                            "communicate with S3, " +
                            "such as not being able to access the network.");
                    sb.append("\nError Message: " + ace.getMessage());
                    raiseError(sb.toString());
                }catch (Exception e){
                    e.printStackTrace();
                    String message = e.getMessage();
                    if(message==null||message.isEmpty()){
                        message = e.getClass().getName();
                    }
                    message = lambdaConfig.getFunctionName()+" " +message;
                    raiseError(message);
                }
            }
        });
    }
    private void updateLambdaCode(LambdaConfig lambdaConfig, Project project, UpdateFunctionCodeRequest request) {
        String processTitle = "Updating "+lambdaConfig.getFunctionName() + " Lambda";
        ProgressManager.getInstance().run(new Task.Backgroundable(project, processTitle, false)
        {

            public void run(@NotNull ProgressIndicator progressIndicator)
            {
                try {
                    AWSLambda lambdaClient = (AWSLambda) AWSLambdaClientBuilder.standard().withCredentials(getSelectedProfileCreds()).build();
                    UpdateFunctionCodeResult updateFunctionCodeResult = lambdaClient.updateFunctionCode(request);
                    showInfo("Successfully updated lambda "+lambdaConfig.getFunctionName());
                }catch (Exception e){
                    e.printStackTrace();
                    String message = e.getMessage();
                    if(message==null||message.isEmpty()){
                        message = e.getClass().getName();
                    }
                    message = lambdaConfig.getFunctionName()+" " +message;
                    raiseError(message);
                }
            }
        });
    }

    private void showInfo(String message) {
        Notification notification = new Notification("raevilman.awslambda","Success",message,
                NotificationType.INFORMATION);
        Notifications.Bus.notify(notification);
    }
    private void raiseError(String message) {
        Notification notification = new Notification("raevilman.awslambda","Error", message,
                NotificationType.ERROR);
        Notifications.Bus.notify(notification);
    }

    private AWSCredentialsProvider getSelectedProfileCreds() {
        if (profilesComboBox.getItemCount() > 0) {
            String key = this.profilesComboBox.getSelectedItem().toString();
            System.out.println("Selected profile:" + key);
            return new ProfileCredentialsProvider(key);
        }else{
            return new ProfileCredentialsProvider();
        }
    }

    private void addConfig(Project project, boolean isEditMode, String p_configName, LambdaConfig p_lambdaConfig) {

        String old_configName = "";
        String old_functionName = "";
        String old_jarFilePath = "";
        String old_s3BucketName = "";
        String old_s3ObjectKey = "";
        if(isEditMode&&null!=p_lambdaConfig){
            old_configName = p_configName;
            old_functionName = p_lambdaConfig.getFunctionName();
            old_jarFilePath = p_lambdaConfig.getJarFilePath();
            old_s3BucketName = p_lambdaConfig.getS3BucketName();
            old_s3ObjectKey = p_lambdaConfig.getS3ObjectKey();
        } else{
            old_s3BucketName = state.getLastS3Bucket();
            old_s3ObjectKey = state.getLastS3KeyPrefix();
        }
        String helpPrefix = "[?]  ";
        JTextField configNameText = new JTextField(old_configName,20);
        JLabel configNameLabel = new JLabel(helpPrefix+"Config name:");
        configNameLabel.setLabelFor(configNameText);
        configNameLabel.setToolTipText("Name for this configuration");

        JTextField lambdaFunctionNameText = new JTextField(old_functionName,20);
        JLabel lambdaFunctionNameLabel = new JLabel(helpPrefix+"Lambda function name:");
        lambdaFunctionNameLabel.setLabelFor(lambdaFunctionNameText);
        lambdaFunctionNameLabel.setToolTipText("Name of the Lambda function in AWS");

        JTextField jarFileNameText = new JTextField(old_jarFilePath,20);
        JLabel jarFileNameLabel = new JLabel(helpPrefix+"JAR file path");
        jarFileNameLabel.setLabelFor(jarFileNameText);
        jarFileNameLabel.setToolTipText("Absolute path to artifact file including extension");

        JTextField s3BucketText = new JTextField(old_s3BucketName,20);
        JLabel s3BucketNameLabel = new JLabel(helpPrefix+"S3 Bucket Name");
        s3BucketNameLabel.setLabelFor(s3BucketText);
        s3BucketNameLabel.setToolTipText("Name of the AWS S3 Bucket");

        JTextField s3ObjectKeyText = new JTextField(old_s3ObjectKey,20);
        JLabel s3ObjectKeyLabel = new JLabel(helpPrefix+"S3 Object Key");
        s3ObjectKeyLabel.setLabelFor(s3ObjectKeyText);
        s3ObjectKeyLabel.setToolTipText("Object key to be used for this artifact");

        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("AWS Lambda Jar updater");
        if(isEditMode){
            builder.addOkAction().setText("Update");
        } else{
            builder.addOkAction().setText("Add");
        }
        builder.addCancelAction().setText("Cancel");

        PanelWithText panel = new PanelWithText("Tip: Use S3 options if artifact size > 10MB to avoid failures");

        GridLayout gridLayout = new GridLayout(0,2);

        panel.setLayout(gridLayout);

        panel.add(getLabel(""));
        panel.add(getLabel(""));panel.add(getLabel(""));
        panel.add(configNameLabel);panel.add(configNameText);
        panel.add(lambdaFunctionNameLabel);panel.add(lambdaFunctionNameText);
        panel.add(jarFileNameLabel);panel.add(jarFileNameText);
        panel.add(s3BucketNameLabel);panel.add(s3BucketText);
        panel.add(s3ObjectKeyLabel);panel.add(s3ObjectKeyText);



//        GroupLayout layout = new GroupLayout(panel);
//        panel.setLayout(layout);
//
//        layout.setAutoCreateGaps(true);
//
//        layout.setAutoCreateContainerGaps(true);
//
//        GroupLayout.SequentialGroup hGroup = layout.createSequentialGroup();
//
//        hGroup.addGroup(layout.createParallelGroup()
//                .addComponent(configNameLabel)
//                .addComponent(lambdaFunctionNameLabel)
//                .addComponent(jarFileNameLabel)
//                .addComponent(s3BucketNameLabel)
//                .addComponent(s3ObjectKeyLabel));
//
//        hGroup.addGroup(layout.createParallelGroup()
//                .addComponent(configNameText)
//                .addComponent(lambdaFunctionNameText)
//                .addComponent(jarFileNameText)
//                .addComponent(s3BucketText)
//                .addComponent(s3ObjectKeyText)
//        );
//
//        layout.setHorizontalGroup(hGroup);
//
//        GroupLayout.SequentialGroup vGroup = layout.createSequentialGroup();
//
//        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
//                .addComponent(configNameLabel)
//                .addComponent(configNameText));
//        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
//                .addComponent(lambdaFunctionNameLabel)
//                .addComponent(lambdaFunctionNameText));
//        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
//                .addComponent(jarFileNameLabel)
//                .addComponent(jarFileNameText));
//        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
//                .addComponent(s3BucketNameLabel)
//                .addComponent(s3BucketText));
//        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
//                .addComponent(s3ObjectKeyLabel)
//                .addComponent(s3ObjectKeyText));
//        layout.setVerticalGroup(vGroup);

        builder.setCenterPanel(panel);
        builder.setOkActionEnabled(true);

        switch (builder.show())
        {
            case 0:
                System.out.println("Ok pressed");
                String configName = configNameText.getText();
                String jarFilePath = jarFileNameText.getText();
                String lambdaFunctionName = lambdaFunctionNameText.getText();
                String bucketName = s3BucketText.getText();
                String s3ObjectKey = s3ObjectKeyText.getText();
                LambdaConfig lambdaConfig = new LambdaConfig();
                lambdaConfig.setFunctionName(lambdaFunctionName);
                lambdaConfig.setJarFilePath(jarFilePath);
                lambdaConfig.setS3BucketName(bucketName);
                lambdaConfig.setS3ObjectKey(s3ObjectKey);
                this.state.getConfigs().put(configName, lambdaConfig);
                if(!s3ObjectKey.isEmpty()){
                    String temp = s3ObjectKey.substring(0, s3ObjectKey.lastIndexOf("/")+1);
                    state.setLastS3KeyPrefix(temp);
                }
                if(!bucketName.isEmpty()){
                    state.setLastS3Bucket(bucketName);
                }
                if(!old_configName.equalsIgnoreCase(configName)){
                    state.getConfigs().remove(old_configName);
                }
                refreshComboBox();
                break;
            case 1:
            default:
                System.out.println("Cancel pressed");
        }
    }
    private JLabel getLabel(String text){
        JLabel jLabel = new JLabel(text);
        return jLabel;
    }

    private void refreshComboBox()
    {
        int size = this.state.getConfigs().size();
        if (size > 0) {
            this.mainBuilder.setOkActionEnabled(true);
            this.jButtonDel.setEnabled(true);
            this.jButtonEdit.setEnabled(true);
        } else {
            this.mainBuilder.setOkActionEnabled(false);
            this.jButtonDel.setEnabled(false);
            this.jButtonEdit.setEnabled(false);
        }
        Set<String> keys = this.state.configs.keySet();
        if(keys.size()<2){
            this.functionsComboBox.setModel(new DefaultComboBoxModel(keys.toArray()));
        } else{
            String[] objArr = new String[keys.size()];
            int i=0;
            for (String key : keys) {
                objArr[i] = key;
                i++;
            }
            Arrays.sort(objArr);
            String[] objArrNew = new String[keys.size()+1];
            for (int j = 0; j < objArr.length; j++) {
                objArrNew[j] = objArr[j];
            }
            objArrNew[keys.size()] = ALL_CONFIG;
            this.functionsComboBox.setModel(new DefaultComboBoxModel(objArrNew));
        }

    }


//    public static void main(String[] args) {
//        ClientConfiguration clientConfiguration = PredefinedClientConfigurations.defaultConfig();
//        int maxConnections = clientConfiguration.getMaxConnections();
//        System.out.println("maxConnections "+maxConnections);
//        int[] socketBufferSizeHints = clientConfiguration.getSocketBufferSizeHints();
//        System.out.println("Buffer "+ Arrays.toString(socketBufferSizeHints));
//    }
}