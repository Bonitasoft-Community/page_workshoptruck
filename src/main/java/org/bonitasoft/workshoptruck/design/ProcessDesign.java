package org.bonitasoft.workshoptruck.design;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bonitasoft.engine.api.PageAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.flownode.ActivityDefinition;
import org.bonitasoft.engine.bpm.flownode.FlowElementContainerDefinition;
import org.bonitasoft.engine.bpm.flownode.TransitionDefinition;
import org.bonitasoft.engine.bpm.process.DesignProcessDefinition;
import org.bonitasoft.engine.bpm.process.ProcessDefinitionNotFoundException;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;
import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEvent.Level;


public class ProcessDesign {

    private static BEvent eventProcessNotFound = new BEvent(ProcessDesignBuilder.class.getName(), 1, Level.ERROR,
            "Can't find the process", "Process does not exist", "Process can't be load", "Check Exception");

    protected Long processDefinitionId;
     protected ProcessDeploymentInfo processDeployment;
    protected ProcessAPI processAPI;
    protected PageAPI pageAPI;

    protected DesignProcessDefinition sourceDesign;


    public ProcessDesign( ProcessAPI processAPI, PageAPI pageAPI) {
        this.processAPI=processAPI;
        this.pageAPI = pageAPI;
    }
    /**
     * LoadProcessDesign
     * @param processDefinitionId
     * @return
     */
    public List<BEvent> loadProcessDesign( Long processDefinitionId) {
        List<BEvent> listEvents = new ArrayList<>();
        try {
            this.processDefinitionId = processDefinitionId;
            
            processDeployment = processAPI.getProcessDeploymentInfo(processDefinitionId);
            sourceDesign =  processAPI.getDesignProcessDefinition(processDefinitionId);

        } catch (ProcessDefinitionNotFoundException e) {
            listEvents.add( new BEvent(eventProcessNotFound, "ProcessId["+processDefinitionId+"]"));
        }
        return listEvents;
    }
    
    public String getName() {
        return processDeployment.getName();
    }
    public String getVersion() {
        return processDeployment.getVersion();
    }

    public ActivityDefinition getActivityById( long activityId ) {
        FlowElementContainerDefinition sourceContainer=  sourceDesign.getFlowElementContainer();
        for (ActivityDefinition activity : sourceContainer.getActivities()) {
            if (activity.getId() == activityId)
                return activity;
        }
        return null;
    }
    public TransitionDefinition getTransitionById( long transitionId )
    {
        FlowElementContainerDefinition sourceContainer=  sourceDesign.getFlowElementContainer();
        
        for (TransitionDefinition transition : sourceContainer.getTransitions()) {
            if (transition.getId() == transitionId)
                return transition;
        }
        return null;
        
    }
    
    public List<Map<String,String>> getTransitions() {
        FlowElementContainerDefinition sourceContainer=  sourceDesign.getFlowElementContainer();
        List<Map<String,String>> listTransitions = new ArrayList<>();
        for (TransitionDefinition transition : sourceContainer.getTransitions()) {
            Map<String,String> record  = new HashMap<>();
            record.put("name", transition.getName());
            record.put("source", transition.getSourceFlowNode().getName());
            record.put("target", transition.getTargetFlowNode().getName());
            record.put("id", String.valueOf( transition.getId() ));
            listTransitions.add(record);
        }
        

        Collections.sort(listTransitions, new Comparator<Map<String,String>>()
           {
             public int compare(Map<String,String> s1,
                     Map<String,String> s2)
             {
                 String key1 = s1.get("source")+" # "+s1.get("target");
                 String key2 = s2.get("source")+" # "+s2.get("target");
                 
               return key1.compareTo(key2);
             }
           });
        return listTransitions;
    }
    
    public List<Map<String,String>> getModelActivities() {
        FlowElementContainerDefinition sourceContainer=  sourceDesign.getFlowElementContainer();
        List<Map<String,String>> listModels = new ArrayList<>();
        for (ActivityDefinition activity : sourceContainer.getActivities()) {
            Map<String,String> record  = new HashMap<>();
            if (! activity.getName().toLowerCase().startsWith("model"))
                continue;
            record.put("name", activity.getName());
            record.put("description", activity.getDescription());
            record.put("id", String.valueOf(activity.getId()));
            listModels.add(record);
        }

        Collections.sort(listModels, new Comparator<Map<String,String>>()
           {
             public int compare(Map<String,String> s1,
                     Map<String,String> s2)
             {
               return s1.get("name").compareTo(s2.get("name"));
             }
           });
        
        return listModels;
    }
    
    
    
    public ProcessDesignBuilder getProcessDesignBuilder() {
        return new ProcessDesignBuilder(this);
    }
    
    
    
}
