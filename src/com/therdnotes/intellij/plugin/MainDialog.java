package com.therdnotes.intellij.plugin;

import com.amazonaws.services.lambda.AWSLambda;
import com.amazonaws.services.lambda.AWSLambdaClientBuilder;
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
import com.intellij.openapi.progress.Task.Backgroundable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogBuilder.CustomizableAction;
import com.intellij.openapi.ui.PanelWithText;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Map;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.ParallelGroup;
import javax.swing.GroupLayout.SequentialGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

public class MainDialog extends AnAction
{
    ComboBox comboBox;
    State state;
    DialogBuilder mainBuilder;
    JButton jButtonDel;

    @Override
    public void actionPerformed(AnActionEvent event)
    {
        System.out.println("Starting plugin");
        Project project = (Project)event.getData(PlatformDataKeys.PROJECT);
        StateService stateService = (StateService)ServiceManager.getService(project, StateService.class);
        this.state = stateService.getState();
        System.out.println(this.state.toString());
        System.out.println("State:" + this.state.toString());
        showMainDialog(project);
    }

    private void showMainDialog(final Project project)
    {
        this.mainBuilder = new DialogBuilder(project);
        this.mainBuilder.setTitle("AWS Lambda JAR Updater");
        this.mainBuilder.addOkAction().setText("Update");
        this.mainBuilder.addCancelAction().setText("Cancel");

        PanelWithText panelWithText = new PanelWithText("Select:");
        this.comboBox = new ComboBox();
        panelWithText.add(this.comboBox);
        JButton jButtonAdd = new JButton("Add");
        jButtonAdd.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                System.out.println("Add pressed");
                MainDialog.this.addConfig(project);
            }
        });
        this.jButtonDel = new JButton("Del");
        this.jButtonDel.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                System.out.println("Del pressed");
                if (MainDialog.this.comboBox.getItemCount() > 0) {
                    String key = MainDialog.this.comboBox.getSelectedItem().toString();
                    System.out.println("Selected item:" + key);
                    MainDialog.this.state.getConfigs().remove(key);
                    MainDialog.this.refreshComboBox();
                }
            }
        });
        panelWithText.add(jButtonAdd);
        panelWithText.add(this.jButtonDel);

        this.mainBuilder.setCenterPanel(panelWithText);
        refreshComboBox();

        switch (this.mainBuilder.show())
        {
            case 0:
                System.out.println("Ok pressed");
                if (this.comboBox.getItemCount() > 0) {
                    String key = this.comboBox.getSelectedItem().toString();
                    System.out.println("Selected item:" + key);
                    LambdaConfig lambdaConfig = (LambdaConfig)this.state.getConfigs().get(key);
                    updateLambdaCode(lambdaConfig, project);
                }break;
            case 1:
            default:
                System.out.println("Cancel pressed");
        }
    }

    private void updateLambdaCode(final LambdaConfig lambdaConfig, Project project)
    {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Uploading jar to AWS Lambda", true)
        {
            public void run(@NotNull ProgressIndicator progressIndicator)
            {
                try{
                    System.out.println("Updating......");
                    UpdateFunctionCodeRequest updateFunctionCodeRequest = new UpdateFunctionCodeRequest();
                    updateFunctionCodeRequest.setFunctionName(lambdaConfig.getFunctionName());

                    updateFunctionCodeRequest.setZipFile(MainDialog.getJarByteBuffer(lambdaConfig.getJarFilePath()));
                    AWSLambda lambdaClient = (AWSLambda)AWSLambdaClientBuilder.standard().build();
                    UpdateFunctionCodeResult updateFunctionCodeResult = lambdaClient.updateFunctionCode(updateFunctionCodeRequest);
                    System.out.println("Updated!!!");
                    Notification notification = new Notification("raevilman.awslambda","Success",lambdaConfig.getFunctionName()+" Lambda successfully updated.",
                            NotificationType.INFORMATION);
                    Notifications.Bus.notify(notification);
                }catch (Exception e){
                    Notification notification = new Notification("raevilman.awslambda","Error",e.getMessage(),
                            NotificationType.ERROR);
                    Notifications.Bus.notify(notification);
                }
            }
        });
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

    private void addConfig(Project project) {
        JTextField configNameText = new JTextField(20);
        JLabel configNameLabel = new JLabel("Config name:");
        configNameLabel.setLabelFor(configNameText);

        JTextField lambdaFunctionNameText = new JTextField(20);
        JLabel lambdaFunctionNameLabel = new JLabel("Lambda function name:");
        configNameLabel.setLabelFor(lambdaFunctionNameText);

        JTextField jarFileNameText = new JTextField(20);
        JLabel jarFileNameLabel = new JLabel("Jar file path");
        configNameLabel.setLabelFor(jarFileNameText);

        DialogBuilder builder = new DialogBuilder(project);
        builder.setTitle("AWS Lambda Jar updater");
        builder.addOkAction().setText("Add");
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
                refreshComboBox();
                break;
            case 1:
            default:
                System.out.println("Cancel pressed");
        }
    }

    private void refreshComboBox()
    {
        this.comboBox.setModel(new DefaultComboBoxModel(this.state.configs.keySet().toArray()));
        if (this.state.getConfigs().size() > 0) {
            this.mainBuilder.setOkActionEnabled(true);
            this.jButtonDel.setEnabled(true);
        } else {
            this.mainBuilder.setOkActionEnabled(false);
            this.jButtonDel.setEnabled(false);
        }
    }
}