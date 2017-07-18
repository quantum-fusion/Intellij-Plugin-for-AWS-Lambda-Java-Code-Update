package com.therdnotes.intellij.plugin;

/**
 * Created by raman.dhawan on 7/18/2017.
 */
import com.intellij.openapi.components.PersistentStateComponent;
import java.io.PrintStream;
import org.jetbrains.annotations.Nullable;

@com.intellij.openapi.components.State(name="AWSLambdaStateService", storages={@com.intellij.openapi.components.Storage("raevilman_awslambdaupdate.xml")})
public class StateService
        implements PersistentStateComponent<State>
{
    State state = new State();

    @Nullable
    public State getState()
    {
        System.out.println("Service: getState");
        return this.state;
    }

    public void loadState(State p_state)
    {
        System.out.println("Service: loadState");
        this.state = p_state;
    }
}
