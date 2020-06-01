package org.bonitasoft.workshoptruck;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bonitasoft.engine.api.PageAPI;
import org.bonitasoft.engine.api.ProcessAPI;
import org.bonitasoft.engine.bpm.flownode.ActivityDefinition;
import org.bonitasoft.engine.bpm.flownode.TransitionDefinition;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfo;
import org.bonitasoft.engine.bpm.process.ProcessDeploymentInfoSearchDescriptor;
import org.bonitasoft.engine.bpm.process.impl.ActivityDefinitionBuilder;
import org.bonitasoft.engine.exception.SearchException;
import org.bonitasoft.engine.search.SearchOptionsBuilder;
import org.bonitasoft.engine.search.SearchResult;
import org.bonitasoft.log.event.BEvent;
import org.bonitasoft.log.event.BEventFactory;
import org.bonitasoft.log.event.BEvent.Level;
import org.bonitasoft.workshoptruck.design.ProcessDesign;
import org.bonitasoft.workshoptruck.design.ProcessDesignBuilder;
import org.bonitasoft.workshoptruck.design.ProcessDesignBuilder.CloneExclusion;
import org.bonitasoft.workshoptruck.design.ProcessDesignBuilder.TypeElement;

public class WorkshopTruckAPI {

    static Logger logger = Logger.getLogger(WorkshopTruckAPI.class.getName());
    public final static SimpleDateFormat sdfHuman = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static BEvent eventProcessCreated = new BEvent(WorkshopTruckAPI.class.getName(), 1, Level.SUCCESS,
            "Process is created", "Process is created with success", "", "");

    public static class CollectResult {
        public List<Map<String, Object>> listProcesses = new ArrayList<>();
        public List listEvents = new ArrayList<>();
    }
    
    /**
     * 
     * @param processVersionName
     * @param processAPI
     * @return
     */
    public Map<String, Object> loadProcess(String processVersionName, ProcessAPI processAPI, PageAPI pageAPI ) {
        final Map<String, Object> mapDesign = new HashMap<>();
        try {
        SearchResult<ProcessDeploymentInfo> search = getListProcessDefinitionId( processVersionName, processAPI );
        if (search.getCount()==0)
            return mapDesign;
        
        
        return getDesign(search.getResult().get(0).getProcessId(), processAPI, pageAPI);
        }
        catch(Exception e ) {
            
            return mapDesign;
        }
    }
    
    
    public Map<String, Object> getDesign(Long processDefinitionId, ProcessAPI processAPI, PageAPI pageAPI)
    {
        final Map<String, Object> mapDesign = new HashMap<>();

        logger.info("getDesign");
        
        ProcessDesign processDesign = new ProcessDesign( processAPI, pageAPI );
        List<BEvent> listEvents = processDesign.loadProcessDesign(processDefinitionId);
        // get the transition
        mapDesign.put("transitions", processDesign.getTransitions());
        mapDesign.put("models", processDesign.getModelActivities());
        // must be a string : too long for JAVASCRIPT
        mapDesign.put("processDefinitionId", String.valueOf(processDefinitionId ));
        
        mapDesign.put("listevents", BEventFactory.getHtml(listEvents));
        return mapDesign;
    }
    
    public static class InsertActivityInformation {
        long processDefinitionId;
        long transitionId;
        long modelId;
        String nameNewActivity;
        public static InsertActivityInformation getInstance(Map<String,Object> jsonParam) {
            InsertActivityInformation insertActivity = new InsertActivityInformation();
            if (jsonParam == null)
                return insertActivity;

            try {
                insertActivity.processDefinitionId = Long.valueOf( jsonParam.get("processDefinitionId").toString());
                insertActivity.transitionId = Long.valueOf( jsonParam.get("transitionId").toString());
                insertActivity.modelId = Long.valueOf( jsonParam.get("modelId").toString());
                insertActivity.nameNewActivity = (String)jsonParam.get("nameNewActivity");
                
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                String exceptionDetails = sw.toString();
                logger.severe("Parameter: ~~~~~~~~~~  : ERROR " + e + " at " + exceptionDetails);
            }           
            return insertActivity;
        }
       
    }
    
    /**
     * 
     * @param activityInformation
     * @param processAPI
     * @return
     */
    public Map<String, Object> insertActivity(InsertActivityInformation activityInformation, ProcessAPI processAPI, PageAPI pageAPI)
    {

        logger.info("InsertActivity");
        final Map<String, Object> mapResultInsert = new HashMap<>();
        ProcessDesign processDesign = new ProcessDesign( processAPI, pageAPI );
        List<BEvent> listEvents = processDesign.loadProcessDesign(activityInformation.processDefinitionId);
        if (! BEventFactory.isError(listEvents)) {
            ProcessDesignBuilder processBuilder = processDesign.getProcessDesignBuilder();   
            // processBuilder.test();
            
            CloneExclusion cloneExclusion = new CloneExclusion();
            if (activityInformation.transitionId > 0) {
                cloneExclusion.addElement(TypeElement.TRANSITION, activityInformation.transitionId);
            }                
            
            
            String nextVersion  = processBuilder.getNextProcessVersion(processDesign.getVersion(), false);
            
            processBuilder.clone( processDesign.getName(), nextVersion, cloneExclusion);
            // then add a new activity
            if (processBuilder.isGenerationOk() 
                    && activityInformation.nameNewActivity !=null
                    && activityInformation.modelId>0
                    && activityInformation.transitionId >0) {
                ActivityDefinition model        = processDesign.getActivityById( activityInformation.modelId);
                TransitionDefinition transition = processDesign.getTransitionById( activityInformation.transitionId);
                
                processBuilder.addActivity( activityInformation.nameNewActivity, model );
                processBuilder.associateForm(activityInformation.nameNewActivity, model);
                processBuilder.addTransition( transition.getSourceFlowNode().getName(), activityInformation.nameNewActivity );
                processBuilder.addTransition( activityInformation.nameNewActivity,transition.getTargetFlowNode().getName()  );
            }
            
            listEvents.addAll( processBuilder.done() );
            listEvents.addAll(processBuilder.getListEventsGeneration());

            // now, deploy it
            if (processBuilder.isGenerationOk()) {
                listEvents.addAll( processBuilder.deploy() );
                listEvents.addAll( processBuilder.copyActorsMapping() );
                listEvents.addAll( processBuilder.enable() );
            }
            if (!BEventFactory.isError(listEvents))
                listEvents.add( new BEvent( eventProcessCreated, "Process["+processDesign.getName()+"] Version ["+nextVersion+"]"));
        }
        mapResultInsert.put("listevents", BEventFactory.getHtml(listEvents));

        return mapResultInsert;
    }
    
    /**
     * 
     * @param processNameVersion
     * @param processAPI
     * @return
     * @throws SearchException
     */
    public static SearchResult<ProcessDeploymentInfo> getListProcessDefinitionId(String processNameVersion, ProcessAPI processAPI) throws SearchException {
        if (processNameVersion==null)
            processNameVersion="";
        processNameVersion = processNameVersion.trim();
        String processNameOnly = processNameVersion;
        String processVersionOnly = null;
        if (processNameVersion.endsWith(")")) {
            int firstParenthesis = processNameVersion.lastIndexOf('(');
            if (firstParenthesis != -1) {
                processNameOnly = processNameVersion.substring(0, firstParenthesis);
                processVersionOnly = processNameVersion.substring(firstParenthesis + 1, processNameVersion.length() - 1);
            }
        }
        SearchOptionsBuilder searchOption = new SearchOptionsBuilder(0, 1000);
        searchOption.filter(ProcessDeploymentInfoSearchDescriptor.NAME, processNameOnly.trim());
        if (processVersionOnly != null) {
            searchOption.filter(ProcessDeploymentInfoSearchDescriptor.VERSION, processVersionOnly.trim());
        }
        
        
        return processAPI.searchProcessDeploymentInfos(searchOption.done());

    }
}
