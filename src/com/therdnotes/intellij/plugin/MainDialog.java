package com.therdnotes.intellij.plugin;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfilesConfigFile;
import com.amazonaws.auth.profile.internal.BasicProfile;
import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
import com.amazonaws.services.lambda.model.FunctionConfiguration;
import com.amazonaws.services.lambda.model.ListFunctionsResult;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeRequest;
import com.amazonaws.services.lambda.model.UpdateFunctionCodeResult;
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
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
        StateService stateService = (StateService)ServiceManager.getService(project, StateService.class);
        this.state = stateService.getState();
//        System.out.println(this.state.toString());
//        System.out.println("State:" + this.state.toString());
        showMainDialog(project);
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
                            updateLambdaCode(lambdaConfig, project);
                        }
                    } else{
                        LambdaConfig lambdaConfig = (LambdaConfig)this.state.getConfigs().get(key);
                        updateLambdaCode(lambdaConfig, project);
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

    private void updateLambdaCode(final LambdaConfig lambdaConfig, Project project)
    {
//        AWSLambda lambdaClient = (AWSLambda)AWSLambdaClientBuilder.standard().withCredentials(getSelectedProfileCreds()).build();
//        ListFunctionsResult listFunctionsResult = lambdaClient.listFunctions();
//        List<FunctionConfiguration> functions = listFunctionsResult.getFunctions();
//        FunctionConfiguration functionConfiguration = functions.get(0);
//        System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
//        System.out.println(functionConfiguration.getFunctionName()+" : "+functionConfiguration.getFunctionArn());
//        System.out.println("^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Uploading jar to AWS Lambda:"+lambdaConfig.getFunctionName(), true)
        {
            public void run(@NotNull ProgressIndicator progressIndicator)
            {
                try{
                    System.out.println("Updating......");
                    UpdateFunctionCodeRequest updateFunctionCodeRequest = new UpdateFunctionCodeRequest();
                    updateFunctionCodeRequest.setFunctionName(lambdaConfig.getFunctionName());

                    updateFunctionCodeRequest.setZipFile(MainDialog.getJarByteBuffer(lambdaConfig.getJarFilePath()));
                    AWSLambda lambdaClient = (AWSLambda)AWSLambdaClientBuilder.standard().withCredentials(getSelectedProfileCreds()).build();
                    UpdateFunctionCodeResult updateFunctionCodeResult = lambdaClient.updateFunctionCode(updateFunctionCodeRequest);
                    System.out.println("Updated!!!");
                    Notification notification = new Notification("raevilman.awslambda","Success",lambdaConfig.getFunctionName()+" Lambda successfully updated.",
                            NotificationType.INFORMATION);
                    Notifications.Bus.notify(notification);
                }catch (Exception e){
                    String message = e.getMessage();
                    if(message==null||message.isEmpty()){
                        message = e.getClass().getName();
                    }
                    message = lambdaConfig.getFunctionName()+" " +message;
                    Notification notification = new Notification("raevilman.awslambda","Error", message,
                            NotificationType.ERROR);
                    Notifications.Bus.notify(notification);
                }
            }
        });
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

    private static MappedByteBuffer getJarByteBuffer(String jarFilePath)
    {
        File jarFile = new File(jarFilePath);
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(jarFile, "r");
            FileChannel fileChannel = randomAccessFile.getChannel();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileChannel.size());
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void addConfig(Project project, boolean isEditMode, String p_configName, LambdaConfig p_lambdaConfig) {

        String old_configName = "";
        String old_functionName = "";
        String old_jarFilePath = "";
        if(isEditMode&&null!=p_lambdaConfig){
            old_configName = p_configName;
            old_functionName = p_lambdaConfig.getFunctionName();
            old_jarFilePath = p_lambdaConfig.getJarFilePath();
        }
        JTextField configNameText = new JTextField(old_configName,20);
        JLabel configNameLabel = new JLabel("Config name:");
        configNameLabel.setLabelFor(configNameText);

        JTextField lambdaFunctionNameText = new JTextField(old_functionName,20);
        JLabel lambdaFunctionNameLabel = new JLabel("Lambda function name:");
        configNameLabel.setLabelFor(lambdaFunctionNameText);

        JTextField jarFileNameText = new JTextField(old_jarFilePath,20);
        JLabel jarFileNameLabel = new JLabel("Jar file path");
        configNameLabel.setLabelFor(jarFileNameText);

        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("AWS Lambda Jar updater");
        if(isEditMode){
            builder.addOkAction().setText("Update");
        } else{
            builder.addOkAction().setText("Add");
        }
        builder.addCancelAction().setText("Cancel");

        PanelWithText panel = new PanelWithText("");
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);

        layout.setAutoCreateGaps(true);

        layout.setAutoCreateContainerGaps(true);

        GroupLayout.SequentialGroup hGroup = layout.createSequentialGroup();

        hGroup.addGroup(layout.createParallelGroup()
                .addComponent(configNameLabel)
                .addComponent(lambdaFunctionNameLabel)
                .addComponent(jarFileNameLabel));

        hGroup.addGroup(layout.createParallelGroup()
                .addComponent(configNameText)
                .addComponent(lambdaFunctionNameText)
                .addComponent(jarFileNameText));

        layout.setHorizontalGroup(hGroup);

        GroupLayout.SequentialGroup vGroup = layout.createSequentialGroup();

        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(configNameLabel)
                .addComponent(configNameText));
        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(lambdaFunctionNameLabel)
                .addComponent(lambdaFunctionNameText));
        vGroup.addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(jarFileNameLabel)
                .addComponent(jarFileNameText));
        layout.setVerticalGroup(vGroup);

        builder.setCenterPanel(panel);
        builder.setOkActionEnabled(true);

        switch (builder.show())
        {
            case 0:
                System.out.println("Ok pressed");
                String configName = configNameText.getText();
                String jarFilePath = jarFileNameText.getText();
                String lambdaFunctionName = lambdaFunctionNameText.getText();
                LambdaConfig lambdaConfig = new LambdaConfig();
                lambdaConfig.setFunctionName(lambdaFunctionName);
                lambdaConfig.setJarFilePath(jarFilePath);
                this.state.getConfigs().put(configName, lambdaConfig);
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
}